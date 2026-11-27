package eu.tintera.plume.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.SentMessageUpdate
import eu.tintera.plume.core.telegram.WriteAccess
import androidx.compose.runtime.snapshotFlow
import eu.tintera.plume.core.telegram.ThreadConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private val logger = Logger.withTag("TvConversation")

/** Kolik zpráv se načte při otevření a při každém dočtení. */
private const val PAGE = 40

/**
 * Stav otevřené konverzace: zprávy od nejstarší, dočítání starší historie a živé změny od TDLib.
 *
 * **V kompozici, ne ve ViewModelu**: konverzace je na televizi jediná obrazovka nad seznamem
 * a z kompozice odchází jen zavřením. ViewModel by žil v úložišti aktivity i po zavření chatu
 * a chat by se TDLib nikdy neodhlásil (`closeChat`).
 */
class ConversationState(val chatId: Long, private val conversations: ConversationRepository) {
    var messages by mutableStateOf<List<ChatMessage>?>(null)
        private set

    /** Citované zprávy, které v načtené historii nejsou (dočtené z TDLib); `null` = zpráva neexistuje. */
    var quoted by mutableStateOf<Map<Long, ChatMessage?>>(emptyMap())
        private set

    /** Smí se do chatu psát (odpovídat)? Kanál ne, diskusní skupina a soukromý chat ano. */
    var canWrite by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /**
     * Kolik zpráv se odeslalo z televize. Na změnu se fokus přesune na novou zprávu — jen na tu
     * odeslanou odsud: zpráva z telefonu téhož účtu by jinak odrolovala čtení starší historie.
     */
    var sentCount by mutableIntStateOf(0)
        private set

    /**
     * Načtené je okno uprostřed historie (skok na zprávu), ne konec: novější zprávy se dočítají,
     * jak se fokus blíží spodku ([loadNewer]), a živé nové zprávy se do okna nepřidávají —
     * stály by za mezerou v historii.
     */
    var hasNewer by mutableStateOf(false)
        private set

    /**
     * Zpráva, na kterou má konverzace skočit (fokus a posun), s pořadím žádosti — týž cíl
     * dvakrát po sobě musí skočit znovu. Obrazovka ho spotřebuje ([consumeJump]).
     */
    var jump by mutableStateOf<JumpRequest?>(null)
        private set
    private var jumps = 0

    private var loadingOlder = false
    private var loadingNewer = false
    private var historyEnd = false

    /**
     * Odběry a první stránka; běží, dokud je konverzace v kompozici. S [startAt] se místo konce
     * historie načte okno kolem té zprávy a konverzace na ni skočí (hledání, Rozkoukaná videa).
     */
    suspend fun run(startAt: Long? = null) = coroutineScope {
        conversations.openChat(chatId)
        // Nové, upravené a smazané se odebírají DŘÍV než historie, ať se nic mezi tím neztratí.
        launch {
            conversations.newMessages(chatId).collect { message ->
                // V okně uprostřed historie by nová zpráva stála za mezerou; přijde s dočtením.
                if (hasNewer) return@collect
                upsert(message)
                if (!message.isOutgoing) conversations.markRead(chatId, listOf(message.id))
            }
        }
        launch {
            conversations.editedMessages(chatId).collect { edited ->
                messages = messages?.map { if (it.id == edited.id) edited else it }
            }
        }
        launch {
            conversations.deletedMessages(chatId).collect { ids -> messages = messages?.filterNot { it.id in ids } }
        }
        launch {
            conversations.interactionUpdates(chatId).collect { change ->
                messages = messages?.map { if (it.id == change.messageId) it.copy(reactions = change.reactions) else it }
            }
        }
        launch {
            // TDLib po potvrzení zprávě změní id: dočasnou je potřeba nahradit, ne aktualizovat.
            conversations.sentMessageUpdates(chatId).collect { update ->
                val (oldId, message) = when (update) {
                    is SentMessageUpdate.Succeeded -> update.oldMessageId to update.message
                    is SentMessageUpdate.Failed -> update.oldMessageId to update.message
                }
                messages = (messages.orEmpty().filterNot { it.id == oldId || it.id == message.id } + message).sortedBy { it.id }
            }
        }
        launch {
            conversations.chatInfo(chatId)
                .onSuccess { canWrite = it.writeAccess == WriteAccess.Allowed }
                .onFailure { logger.w(it) { "Práva chatu $chatId se na televizi nenačetla." } }
        }
        // Citace odpovědi bere zprávu z načtené historie; co v ní není, dočte jednou z TDLib.
        launch {
            snapshotFlow { messages }.collect { loaded -> fetchMissingQuotes(loaded.orEmpty()) }
        }
        if (startAt != null) {
            loadAround(startAt)
            return@coroutineScope
        }
        loadHistory()
            .onSuccess { loaded ->
                messages = (loaded + messages.orEmpty()).distinctBy { it.id }.sortedBy { it.id }
                if (loaded.isNotEmpty()) conversations.markRead(chatId, loaded.map { it.id })
            }
            .onFailure {
                logger.w(it) { "Historie chatu $chatId se na televizi nenačetla." }
                failed = true
            }
    }

    /**
     * Skok na zprávu [messageId]: když je načtená, jen fokus; jinak se historie nahradí oknem
     * kolem ní (polovina starších, polovina novějších).
     */
    suspend fun jumpTo(messageId: Long) {
        if (messages?.any { it.id == messageId } == true) {
            jump = JumpRequest(messageId, ++jumps)
            return
        }
        loadAround(messageId)
    }

    fun consumeJump() {
        jump = null
    }

    private suspend fun loadAround(messageId: Long) {
        // Starší polovina od zprávy (ta sama je v ní) a novější polovina záporným posunem.
        val window = conversations.history(chatId, fromMessageId = messageId, limit = PAGE, offset = -(PAGE / 2))
            .onFailure { logger.w(it) { "Okno historie chatu $chatId kolem zprávy $messageId se nenačetlo." } }
            .getOrNull()
        if (window == null) {
            if (messages == null) failed = true
            return
        }
        // Zpráva, která už není (smazaná), okno nemá: aspoň se ukáže okolí.
        historyEnd = false
        hasNewer = true
        messages = window.distinctBy { it.id }.sortedBy { it.id }
        if (window.isNotEmpty()) conversations.markRead(chatId, window.map { it.id })
        jump = JumpRequest(messageId, ++jumps)
    }

    /**
     * Dočte [PAGE] novějších zpráv za nejnovější načtenou (jen v okně uprostřed historie).
     * Nic nového = jsme u konce a od té chvíle se přidávají i živé nové zprávy.
     */
    suspend fun loadNewer() {
        val newest = messages?.lastOrNull()?.id ?: return
        if (!hasNewer || loadingNewer) return
        loadingNewer = true
        conversations.history(chatId, fromMessageId = newest, limit = PAGE, offset = -(PAGE - 1))
            .onSuccess { page ->
                val newer = page.filter { it.id > newest }
                if (newer.isEmpty()) {
                    hasNewer = false
                } else {
                    messages = (messages.orEmpty() + newer).distinctBy { it.id }.sortedBy { it.id }
                    conversations.markRead(chatId, newer.map { it.id })
                }
            }
            .onFailure { logger.w(it) { "Novější historie chatu $chatId se na televizi nenačetla." } }
        loadingNewer = false
    }

    /** Konec historie místo okna uprostřed (po odeslání zprávy z okna). */
    private suspend fun loadLatest() {
        loadHistory().onSuccess { loaded ->
            messages = loaded.distinctBy { it.id }.sortedBy { it.id }
            hasNewer = false
            historyEnd = false
        }
    }

    /** Dočte [PAGE] starších zpráv od nejstarší načtené; prázdná stránka = začátek chatu. */
    suspend fun loadOlder() {
        val oldest = messages?.firstOrNull()?.id ?: return
        if (loadingOlder || historyEnd) return
        loadingOlder = true
        loadHistory(fromMessageId = oldest)
            .onSuccess { older ->
                if (older.isEmpty()) historyEnd = true
                messages = (older + messages.orEmpty()).distinctBy { it.id }.sortedBy { it.id }
            }
            .onFailure { logger.w(it) { "Starší historie chatu $chatId se na televizi nenačetla." } }
        loadingOlder = false
    }

    /** Citovaná zpráva [id]: z načtené historie, nebo dočtená. */
    fun quote(id: Long): ChatMessage? = messages?.firstOrNull { it.id == id } ?: quoted[id]

    /** Pošle text jako odpověď na [replyTo] (`null` = bez citace; ve vláknu komentářů odpoví kořeni). */
    suspend fun send(text: String, replyTo: Long?): Result<Unit> =
        conversations.send(chatId, text, replyToMessageId = replyTo)
            .onSuccess {
                // Odesláno z okna uprostřed historie: nová zpráva patří na konec, ne za mezeru.
                if (hasNewer) loadLatest()
                upsert(it)
                sentCount++
            }
            .onFailure { logger.w(it) { "Zpráva do chatu $chatId se z televize neodeslala." } }
            .map { }

    /**
     * Přidá nebo odebere reakci [emoji] u [message] — hned v zobrazení (odezva na ovladač nemá
     * čekat na server), potvrdí ji update interakcí od TDLib. Nepovedená se vrátí zpátky — jen
     * u téhle zprávy: celý seznam z doby před reakcí by zahodil, co mezitím přišlo.
     */
    suspend fun toggleReaction(message: ChatMessage, emoji: String) {
        val chosen = message.reactions.any { it.emoji == emoji && it.isChosen }
        applyReaction(message.id, emoji, add = !chosen)
        conversations.setReaction(chatId, message.id, emoji, add = !chosen).onFailure {
            logger.w(it) { "Reakce na zprávu ${message.id} se z televize neposlala." }
            applyReaction(message.id, emoji, add = chosen)
        }
    }

    private fun applyReaction(messageId: Long, emoji: String, add: Boolean) {
        messages = messages?.map { if (it.id == messageId) it.copy(reactions = toggled(it.reactions, emoji, add)) else it }
    }

    /** Emoji, která jdou u zprávy [messageId] dát. */
    suspend fun reactionChoices(messageId: Long): List<String> =
        conversations.availableReactions(chatId, messageId).getOrNull()?.emoji.orEmpty()

    private suspend fun fetchMissingQuotes(loaded: List<ChatMessage>) {
        val missing = loaded.mapNotNull { it.replyToMessageId }
            .filter { id -> id !in quoted && loaded.none { it.id == id } }
            .distinct()
        if (missing.isEmpty()) return
        quoted = quoted + missing.associateWith { id -> conversations.message(chatId, id).getOrNull() }
    }

    private fun upsert(message: ChatMessage) {
        messages = (messages.orEmpty().filterNot { it.id == message.id } + message).sortedBy { it.id }
    }

    /**
     * [PAGE] zpráv starších než [fromMessageId] (0 = nejnovější). `getChatHistory` vrací TDLib často
     * jen to, co má v lokální databázi (klidně jednu zprávu), takže se dočítá, dokud stránka není
     * plná nebo historie neskončí.
     */
    private suspend fun loadHistory(fromMessageId: Long = 0L): Result<List<ChatMessage>> = runCatching {
        val loaded = mutableListOf<ChatMessage>()
        var from = fromMessageId
        repeat(3) {
            if (loaded.size >= PAGE) return@runCatching loaded
            val page = conversations.history(chatId, fromMessageId = from, limit = PAGE - loaded.size).getOrThrow()
                .filterNot { message -> loaded.any { it.id == message.id } }
            if (page.isEmpty()) return@runCatching loaded
            loaded += page
            from = loaded.minOf { it.id }
        }
        loaded
    }
}

/** Žádost o skok na zprávu [messageId]; [serial] odliší dva skoky na tutéž zprávu. */
data class JumpRequest(val messageId: Long, val serial: Int)

/**
 * [ConversationState] chatu [chatId]: odběry běží s kompozicí, chat se zavře v aplikačním scope.
 * [threadRootId] zúží konverzaci na vlákno komentářů pod tímhle příspěvkem
 * (`ThreadConversationRepository` z `core`, tentýž obal jako na telefonu).
 */
@Composable
fun rememberConversationState(chatId: Long, threadRootId: Long? = null, startAt: Long? = null): ConversationState {
    val base = koinInject<ConversationRepository>()
    val conversations = remember(base, threadRootId) {
        threadRootId?.let { ThreadConversationRepository(base, it) } ?: base
    }
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    val state = remember(chatId, threadRootId) { ConversationState(chatId, conversations) }
    LaunchedEffect(state) { state.run(startAt) }
    // Zavření v aplikačním scope: scope kompozice je v tu chvíli už zrušený.
    DisposableEffect(chatId) { onDispose { appScope.launch { conversations.closeChat(chatId) } } }
    return state
}

/** Reakce po přidání nebo odebrání [emoji] přihlášeným uživatelem (počty i příznak „moje"). */
internal fun toggled(reactions: List<MessageReaction>, emoji: String, add: Boolean): List<MessageReaction> {
    val existing = reactions.firstOrNull { it.emoji == emoji }
    return when {
        add && existing == null -> reactions + MessageReaction(emoji, count = 1, isChosen = true)
        add -> reactions.map { if (it.emoji == emoji) it.copy(count = it.count + 1, isChosen = true) else it }
        existing == null -> reactions
        existing.count <= 1 -> reactions.filterNot { it.emoji == emoji }
        else -> reactions.map { if (it.emoji == emoji) it.copy(count = it.count - 1, isChosen = false) else it }
    }
}
