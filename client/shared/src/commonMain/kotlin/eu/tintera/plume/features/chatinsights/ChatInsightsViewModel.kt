package eu.tintera.plume.features.chatinsights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageStat
import eu.tintera.plume.core.telegram.MessageStatsPage
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.SharedMediaCounts
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_insights_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** Stav obrazovky statistiky chatu ([ChatInsightsViewModel]). */
@Immutable
data class ChatInsightsState(
    /** `null`, dokud nedoběhne první úsek (nebo když selhal — [failure]). */
    val insights: ChatInsights? = null,
    /** Prochází se lokální databáze (první počítání). */
    val scanning: Boolean = true,
    /** Běží „Načíst starší" ze serveru. */
    val loadingOlder: Boolean = false,
    /** Server starší zprávy nemá — statistika je za celou historii chatu. */
    val reachedStart: Boolean = false,
    /** Poslední „Načíst starší" selhalo; tlačítko zůstává jako zkusit znovu. */
    val olderFailed: Boolean = false,
    /** Jména a tváře nejvíc píšících, jak se dohledávají. */
    val senders: Map<Long, SenderInfo> = emptyMap(),
    /** Počty druhů obsahu za CELOU historii, přesně ze serveru; `null`, dokud nepřijdou. */
    val mediaCounts: SharedMediaCounts? = null,
    /** První úsek se nepodařilo spočítat; obrazovka ukáže chybu se „Zkusit znovu". */
    val failure: UiText? = null,
) {
    val canLoadOlder: Boolean get() = insights != null && !scanning && !loadingOlder && !reachedStart
}

/**
 * Statistika chatu (`ChatInsightsRoute`): projde historii jako kalendář zpráv — napřed lokální
 * databázi TDLib po úsecích (rychle, bez dotazů na server; obrazovka se plní po každém úseku),
 * „Načíst starší" pak bere ze serveru. Proč lokální databáze a ne odhad, viz
 * `docs/poznatky/konverzace-pohyb.md`, „Kalendář zpráv z nabídky".
 *
 * Statistika se přepočítá z celého prošlého úseku po každém kroku ([computeChatInsights] na
 * [computeContext]); třicet tisíc otisků je na to málo.
 */
class ChatInsightsViewModel(
    val chatId: Long,
    private val repository: ConversationRepository,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val today: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
    private val computeContext: CoroutineContext = Dispatchers.Default,
) : ViewModel() {

    private val logger = Logger.withTag("ChatInsights")

    private val _state = MutableStateFlow(ChatInsightsState())
    val state: StateFlow<ChatInsightsState> = _state.asStateFlow()

    private val messages = mutableListOf<MessageStat>()

    /** Nejstarší prošlá zpráva — odsud se pokračuje; `0` = od nejnovější. */
    private var oldestId = 0L
    private var job: Job? = null

    /** Soukromý chat (odpovědi a začátky se počítají jen v něm); zjistí se při prvním načtení. */
    private var privateChat = false

    init {
        load()
    }

    /** Spočítá statistiku znovu od nejnovější zprávy (otevření, „Zkusit znovu" po chybě). */
    fun load() {
        job?.cancel()
        messages.clear()
        oldestId = 0L
        _state.value = ChatInsightsState()
        job = viewModelScope.launch {
            launch {
                repository.sharedMediaCounts(chatId).onSuccess { counts -> _state.update { it.copy(mediaCounts = counts) } }
            }
            privateChat = repository.chatInfo(chatId).getOrNull()?.let { !it.isGroup && !it.isChannel && !it.isSavedMessages } == true
            while (messages.size < LOCAL_MAX) {
                val page = repository.messageStats(chatId, oldestId, onlyLocal = true, maxMessages = LOCAL_CHUNK)
                    .getOrElse { error ->
                        logger.w(error) { "Statistiku chatu $chatId se nepodařilo spočítat." }
                        _state.update { it.copy(scanning = false, failure = error.userMessage(Res.string.chat_insights_failed)) }
                        return@launch
                    }
                append(page)
                publish()
                if (page.reachedEnd || page.oldestMessageId == null) break
            }
            // V zařízení nic není (chat se ještě neotevřel): rovnou první dávka ze serveru.
            if (messages.isEmpty()) loadOlderNow()
            _state.update { it.copy(scanning = false) }
        }
    }

    /** „Načíst starší": další dávka ze serveru. */
    fun loadOlder() {
        if (!_state.value.canLoadOlder) return
        job = viewModelScope.launch { loadOlderNow() }
    }

    private suspend fun loadOlderNow() {
        _state.update { it.copy(loadingOlder = true, olderFailed = false) }
        repository.messageStats(chatId, oldestId, onlyLocal = false, maxMessages = SERVER_CHUNK)
            .onSuccess { page ->
                append(page)
                _state.update { it.copy(reachedStart = page.reachedEnd) }
                publish()
            }
            .onFailure { error ->
                logger.w(error) { "Starší zprávy pro statistiku chatu $chatId se nepodařilo načíst." }
                _state.update { it.copy(olderFailed = true) }
            }
        _state.update { it.copy(loadingOlder = false) }
    }

    private fun append(page: MessageStatsPage) {
        messages += page.messages
        page.oldestMessageId?.let { oldestId = it }
    }

    private suspend fun publish() {
        val snapshot = messages.toList()
        val insights = withContext(computeContext) { computeChatInsights(snapshot, zone(), today(), privateChat) }
        // Insights: `null` znamená „počítá se", takže prázdný chat musí dostat prázdnou statistiku.
        _state.update { it.copy(insights = insights) }
        resolveSenders(insights)
    }

    /** Jména jen u těch, kdo jsou v žebříčku vidět, a každé jednou. */
    private suspend fun resolveSenders(insights: ChatInsights) {
        val missing = insights.senders.take(MAX_SENDERS).map { it.senderId }.filterNot { it in _state.value.senders }
        for (id in missing) {
            repository.sender(id).onSuccess { info -> _state.update { it.copy(senders = it.senders + (id to info)) } }
        }
    }
}

/** Kolik lidí ukáže žebříček „Kdo píše" jménem; ostatní jsou jeden řádek. */
internal const val MAX_SENDERS = 10

/** Lokální databáze po úsecích — obrazovka se plní po každém. */
private const val LOCAL_CHUNK = 2_000

/** Strop lokálního průchodu (jako kalendář); dál jen „Načíst starší". */
private const val LOCAL_MAX = 30_000

/** Dávka „Načíst starší" ze serveru (jako kalendář, na kanálu ~4 s). */
private const val SERVER_CHUNK = 1_000
