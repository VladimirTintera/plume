package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.Generated
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.TranscriptLine
import eu.tintera.plume.core.ai.keptLineCount
import eu.tintera.plume.core.ai.shrunkTranscriptChars
import eu.tintera.plume.core.ai.summaryPrompt
import eu.tintera.plume.core.ai.transcriptCharsFor
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.platformLanguageCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Kolik posledních zpráv se shrne, když v chatu nic nepřečteného není. */
const val SUMMARY_RECENT_MESSAGES = 60

/**
 * Má shrnutí dost načtené historie, nebo se má dotáhnout další stránka?
 *
 * [messages] jde od NEJNOVĚJŠÍ (tak je drží konverzace), takže nejstarší načtená je poslední.
 * S nepřečtenými stačí dosáhnout za hranici — co je pod ní, se stejně nešrnuje; bez nich se
 * čeká na [count] zpráv. Prázdný seznam znamená „ještě nic nenačteno", tedy dotahovat.
 *
 * Vlastní funkce, ne podmínka v lambdě uvnitř ViewModelu: rozhoduje o tom, kolik toho shrnutí
 * uvidí, a hraniční případy (prázdno, hranice přesně na nejstarší zprávě) se v lambdě neověří.
 */
fun summaryHasEnough(messages: List<ChatMessage>, unreadBoundaryId: Long?, count: Int): Boolean {
    val oldest = messages.lastOrNull() ?: return false
    return if (unreadBoundaryId != null) oldest.id <= unreadBoundaryId else messages.size >= count
}

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

/**
 * Co je pod shrnutím vidět drobným písmem: který model psal a jak plný byl vstup. Prototyp má
 * ukázat, jak model funguje, a bez toho by se nedalo poznat, jestli přepis musel být zkrácený.
 */
data class SummaryDetails(
    val modelName: String?,
    val promptTokens: Int?,
    val tokenLimit: Int?,
    /** Kolik zpráv z přepisu se do promptu vešlo; méně než celek znamená zkrácení. */
    val keptLines: Int,
    val totalLines: Int,
)

sealed interface ChatSummaryState {
    data object Idle : ChatSummaryState

    /** Čeká se na první slovo — model se v AICore při prvním požadavku teprve načítá. */
    data object Preparing : ChatSummaryState

    /**
     * [done] = model domluvil; do té doby text přibývá. [truncated] = nedomluvil, došel mu
     * strop výstupu — text je platný, ale NEÚPLNÝ, a to se musí ukázat: jinak si uživatel
     * přečte shrnutí poloviny zpráv jako shrnutí všech.
     */
    data class Text(
        val text: String,
        val done: Boolean,
        val details: SummaryDetails,
        val truncated: Boolean = false,
    ) : ChatSummaryState

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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatSummaryViewModel(
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
) : ViewModel() {

    private val logger = Logger.withTag("ChatSummary")

    /**
     * Nabídnout „Shrnout" jen se zapnutou volbou a na zařízení, které model ve zvolené variantě
     * má nebo mít může.
     */
    val available: StateFlow<Boolean> = combine(
        settings.onDeviceAiEnabled,
        settings.onDeviceAiVariant.flatMapLatest { variant ->
            // `catch` je pojistka, ne ozdoba: kdyby ze zjišťování stavu cokoli vyletělo,
            // shodilo by to celý `stateIn` scope, tedy ViewModel obrazovky — kvůli volitelné
            // funkci. Nepodařilo se zjistit = nenabízet.
            flow { emit(ai.status(variant) != OnDeviceAiStatus.Unsupported) }.catch { emit(false) }
        },
    ) { enabled, supported -> enabled && supported }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _state = MutableStateFlow<ChatSummaryState>(ChatSummaryState.Idle)
    val state: StateFlow<ChatSummaryState> = _state.asStateFlow()

    /** Kolik zpráv se shrnuje a jestli nepřečtené — pro nadpis sheetu. */
    private val _source = MutableStateFlow<SummarySource?>(null)
    val source: StateFlow<SummarySource?> = _source.asStateFlow()

    private var job: Job? = null

    /**
     * [source] je `suspend`, protože se kvůli shrnutí dotahuje historie (viz
     * `ConversationViewModel.loadForSummary`). Sheet se proto ukáže HNED jako „Připravuji" —
     * kdyby se čekalo na zdroj, ťuknutí by chvíli nedělalo nic.
     */
    fun summarize(chatTitle: String, me: String, source: suspend () -> SummarySource) {
        job?.cancel()
        _source.value = null
        _state.value = ChatSummaryState.Preparing
        job = viewModelScope.launch {
            val picked = source()
            _source.value = picked
            val lines = transcriptLines(picked.messages, chatTitle, me)
            if (lines.isEmpty()) {
                _state.value = ChatSummaryState.NothingToSummarize
                return@launch
            }
            val variant = settings.onDeviceAiVariant.value
            if (ai.status(variant) != OnDeviceAiStatus.Ready) {
                _state.value = ChatSummaryState.ModelMissing
                return@launch
            }
            val info = ai.info(variant)
            val limit = info?.tokenLimit

            /*
             * Přepis se napřed odhadne podle limitu a pak se ověří počítáním tokenů. Když se
             * nevejde, zkrátí se (zahazují se nejstarší zprávy) a počítá se znovu. Tři pokusy
             * stačí: zmenšuje se úměrně přesahu. Když model tokeny spočítat neumí, jde se
             * s odhadem a případný přesah ohlásí až generování.
             */
            var maxChars = transcriptCharsFor(limit)
            var prompt = summaryPrompt(chatTitle, lines, platformLanguageCode, maxChars)
            var tokens = ai.countTokens(variant, prompt)
            repeat(MAX_FIT_ATTEMPTS) {
                val over = tokens
                if (limit == null || over == null || over <= limit) return@repeat
                maxChars = shrunkTranscriptChars(maxChars, over, limit)
                prompt = summaryPrompt(chatTitle, lines, platformLanguageCode, maxChars)
                tokens = ai.countTokens(variant, prompt)
            }
            val details = SummaryDetails(
                modelName = info?.name,
                promptTokens = tokens,
                tokenLimit = limit,
                keptLines = keptLineCount(lines, maxChars),
                totalLines = lines.size,
            )

            var last = Generated("")
            try {
                ai.generate(variant, prompt).collect { chunk ->
                    last = chunk
                    _state.value = ChatSummaryState.Text(
                        text = chunk.text,
                        done = false,
                        details = details,
                        truncated = chunk.truncated,
                    )
                }
                _state.value = if (last.text.isBlank()) {
                    ChatSummaryState.Failed
                } else {
                    if (last.truncated) logger.i { "Model shrnutí nedopsal, došel mu strop výstupu" }
                    ChatSummaryState.Text(
                        text = last.text,
                        done = true,
                        details = details,
                        truncated = last.truncated,
                    )
                }
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

    private companion object {
        const val MAX_FIT_ATTEMPTS = 3
    }
}
