package eu.tintera.plume.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
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
class ConversationState(private val chatId: Long, private val conversations: ConversationRepository) {
    var messages by mutableStateOf<List<ChatMessage>?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    private var loadingOlder = false
    private var historyEnd = false

    /** Odběry a první stránka; běží, dokud je konverzace v kompozici. */
    suspend fun run() = coroutineScope {
        conversations.openChat(chatId)
        // Nové, upravené a smazané se odebírají DŘÍV než historie, ať se nic mezi tím neztratí.
        launch {
            conversations.newMessages(chatId).collect { message ->
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

/**
 * [ConversationState] chatu [chatId]: odběry běží s kompozicí, chat se zavře v aplikačním scope.
 * [threadRootId] zúží konverzaci na vlákno komentářů pod tímhle příspěvkem
 * (`ThreadConversationRepository` z `core`, tentýž obal jako na telefonu).
 */
@Composable
fun rememberConversationState(chatId: Long, threadRootId: Long? = null): ConversationState {
    val base = koinInject<ConversationRepository>()
    val conversations = remember(base, threadRootId) {
        threadRootId?.let { ThreadConversationRepository(base, it) } ?: base
    }
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    val state = remember(chatId, threadRootId) { ConversationState(chatId, conversations) }
    LaunchedEffect(state) { state.run() }
    // Zavření v aplikačním scope: scope kompozice je v tu chvíli už zrušený.
    DisposableEffect(chatId) { onDispose { appScope.launch { conversations.closeChat(chatId) } } }
    return state
}
