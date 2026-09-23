package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.TranscriptLine
import eu.tintera.plume.core.ai.summaryPrompt
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.platformLanguageCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Kolik posledních zpráv se shrne, když v chatu nic nepřečteného není. */
const val SUMMARY_RECENT_MESSAGES = 60

/** Co se bude shrnovat: zprávy (chronologicky) a jestli jsou to nepřečtené. */
data class SummarySource(val messages: List<ChatMessage>, val unread: Boolean)

/**
 * Vybere zprávy ke shrnutí.
 *
 * Nepřečtené mají přednost: kvůli nim se shrnutí otevírá nejčastěji („co jsem zmeškal").
 * Hranice je `unreadBoundaryId` zachycená při otevření chatu, takže se nepřečtené počítají
 * i po tom, co je konverzace mezitím označila za přečtené. Jinak posledních
 * [SUMMARY_RECENT_MESSAGES] zpráv. Servisní oznámení se vynechávají — o obsahu nic neříkají.
 *
 * [messages] jsou ve stavu konverzace od nejnovější; výsledek je od nejstarší.
 */
fun summarySource(messages: List<ChatMessage>, unreadBoundaryId: Long?): SummarySource {
    val content = messages.filter { !it.isService }
    val unread = unreadBoundaryId
        ?.let { boundary -> content.filter { it.id > boundary } }
        ?.takeIf { unread -> unread.any { !it.isOutgoing } }
    return if (unread != null) {
        SummarySource(unread.asReversed(), unread = true)
    } else {
        SummarySource(content.take(SUMMARY_RECENT_MESSAGES).asReversed(), unread = false)
    }
}

/**
 * Řádky přepisu. Odchozí zprávy podepisuje [me], příchozí jméno odesílatele — v soukromém chatu
 * ho TDLib nevyplňuje, pak je to název chatu. Příloha bez textu je `[attachment]`: model pak ví,
 * že tam něco bylo, a prompt je anglicky, takže značka nemusí jít přes překlad.
 */
fun transcriptLines(messages: List<ChatMessage>, chatTitle: String, me: String): List<TranscriptLine> =
    messages.mapNotNull { message ->
        val text = message.text.ifBlank { if (message.media != null) "[attachment]" else "" }
        if (text.isBlank()) return@mapNotNull null
        val author = if (message.isOutgoing) me else message.senderName ?: chatTitle
        TranscriptLine(author, text)
    }

sealed interface ChatSummaryState {
    data object Idle : ChatSummaryState

    /** Čeká se na první slovo — model se v AICore při prvním požadavku teprve načítá. */
    data object Preparing : ChatSummaryState

    /** [done] = model domluvil; do té doby text přibývá. */
    data class Text(val text: String, val done: Boolean) : ChatSummaryState

    /** Model v zařízení ještě není — stahuje se v Nastavení. */
    data object ModelMissing : ChatSummaryState

    data object NothingToSummarize : ChatSummaryState

    data object Failed : ChatSummaryState
}

/**
 * Shrnutí konverzace modelem v zařízení.
 *
 * Samostatný ViewModel, ne kus `ConversationViewModel`: je to experimentální doplněk, který se
 * dá celý vyjmout, a zprávy dostane od obrazovky — ty už v paměti jsou.
 */
class ChatSummaryViewModel(
    private val ai: OnDeviceAi,
    settings: AppSettings,
) : ViewModel() {

    private val logger = Logger.withTag("ChatSummary")

    /** Nabídnout „Shrnout" jen se zapnutou volbou a na zařízení, které model má nebo mít může. */
    val available: StateFlow<Boolean> = combine(
        settings.onDeviceAiEnabled,
        flow { emit(ai.status() != OnDeviceAiStatus.Unsupported) },
    ) { enabled, supported -> enabled && supported }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _state = MutableStateFlow<ChatSummaryState>(ChatSummaryState.Idle)
    val state: StateFlow<ChatSummaryState> = _state.asStateFlow()

    /** Kolik zpráv se shrnuje a jestli nepřečtené — pro nadpis sheetu. */
    private val _source = MutableStateFlow<SummarySource?>(null)
    val source: StateFlow<SummarySource?> = _source.asStateFlow()

    private var job: Job? = null

    fun summarize(chatTitle: String, source: SummarySource, me: String) {
        job?.cancel()
        _source.value = source
        val lines = transcriptLines(source.messages, chatTitle, me)
        if (lines.isEmpty()) {
            _state.value = ChatSummaryState.NothingToSummarize
            return
        }
        _state.value = ChatSummaryState.Preparing
        job = viewModelScope.launch {
            if (ai.status() != OnDeviceAiStatus.Ready) {
                _state.value = ChatSummaryState.ModelMissing
                return@launch
            }
            val prompt = summaryPrompt(chatTitle, lines, platformLanguageCode)
            var last = ""
            try {
                ai.generate(prompt).collect { text ->
                    last = text
                    _state.value = ChatSummaryState.Text(text, done = false)
                }
                _state.value = if (last.isBlank()) ChatSummaryState.Failed else ChatSummaryState.Text(last, done = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Shrnutí se nepovedlo" }
                _state.value = ChatSummaryState.Failed
            }
        }
    }

    /** Zavření sheetu: rozpracované generování nemá kam dopsat, zahodí se. */
    fun dismiss() {
        job?.cancel()
        job = null
        _state.value = ChatSummaryState.Idle
        _source.value = null
    }
}
