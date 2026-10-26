package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.withoutIntro
import eu.tintera.plume.core.ai.voiceSummaryPrompt
import kotlin.time.TimeSource
import kotlin.time.TimeMark
import kotlinx.coroutines.flow.update
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.variantsFor
import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.ai.AiTask
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.ai.explainPrompt
import eu.tintera.plume.core.ui.resolveNow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.Generated
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiException
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.TranscriptLine
import eu.tintera.plume.core.ai.emptyOutputMessage
import eu.tintera.plume.core.ai.isReportable
import eu.tintera.plume.core.ai.keptLineCount
import eu.tintera.plume.core.ai.shrunkTranscriptChars
import eu.tintera.plume.core.ai.summaryPrompt
import eu.tintera.plume.core.ai.MAX_SUMMARY_PARTS
import eu.tintera.plume.core.ai.combinedSummaryPrompt
import eu.tintera.plume.core.ai.completeLines
import eu.tintera.plume.core.ai.partSummaryPrompt
import eu.tintera.plume.core.ai.summaryParts
import eu.tintera.plume.core.ai.transcriptCharsFor
import eu.tintera.plume.core.ai.userMessage
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.summary_failed
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
import kotlinx.coroutines.flow.lastOrNull
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
data class SummarySource(
    val messages: List<ChatMessage>,
    val unread: Boolean,
    /** Vysvětluje se jedna zpráva (`ChatSummaryViewModel.explain`), ne shrnuje konverzace. */
    val explain: Boolean = false,
    /** Shrnuje se přepis jedné hlasovky nebo kolečka (`ChatSummaryViewModel.summarizeVoice`). */
    val voice: Boolean = false,
)

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
 *
 * Zpráva bez vlastního textu (servisní oznámení, anketa) přispěje naším popisem v jazyce
 * aplikace — ten se překládá až tady (`suspend`), převedená zpráva ho nese jako `UiText`.
 */
suspend fun transcriptLines(messages: List<ChatMessage>, chatTitle: String, me: String): List<TranscriptLine> =
    messages.mapNotNull { message ->
        val text = message.text.ifBlank {
            message.description?.resolveNow() ?: if (message.media != null) "[attachment]" else ""
        }
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
    /** Po kolika částech se shrnovalo; 1 = jeden prompt (viz [summaryParts]). */
    val parts: Int = 1,
)

sealed interface ChatSummaryState {
    data object Idle : ChatSummaryState

    /** Čeká se na první slovo — model se v AICore při prvním požadavku teprve načítá. */
    data object Preparing : ChatSummaryState

    /**
     * Dlouhá konverzace se čte po částech ([part] z [parts], od 1) a teprve pak se skládá
     * shrnutí. Každá část je celé generování, takže bez průběhu by sheet dlouho jen „připravoval".
     */
    data class ReadingParts(val part: Int, val parts: Int) : ChatSummaryState

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
        /** Jak dlouho model počítal, od dotazu na stav po poslední slovo; známé až u hotového. */
        val elapsedMs: Long? = null,
    ) : ChatSummaryState

    /** Model v zařízení ještě není — stahuje se v Nastavení. */
    data object ModelMissing : ChatSummaryState

    data object NothingToSummarize : ChatSummaryState

    /** [reason] říká uživateli, proč — a kde to spravit, když je chyba mimo aplikaci. */
    data class Failed(val reason: UiText) : ChatSummaryState
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
        ai.variantsFor(AiTask.Summary, settings).flatMapLatest { variant ->
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

    /**
     * Nabídnout „Porovnat s dalšími modely"? Jen s víc zapnutými modely — s jedním není s čím.
     * Text zvládne každý model, takže stačí počet; jestli je druhý připravený, řekne až jeho
     * výsledek ([ChatSummaryState.ModelMissing]).
     */
    val canCompare: StateFlow<Boolean> = combine(settings.onDeviceAiEnabled, settings.onDeviceAiModels) { enabled, models ->
        enabled && models.candidates(AiTask.Summary).size > 1
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _comparison = MutableStateFlow<List<ComparedSummary>>(emptyList())

    /** Totéž shrnutí (vysvětlení) z ostatních zapnutých modelů, v pořadí, v jakém se počítají. */
    val comparison: StateFlow<List<ComparedSummary>> = _comparison.asStateFlow()

    private var job: Job? = null
    private var compareJob: Job? = null

    /** Co se naposledy počítalo a čím — kvůli porovnání ([compare]) a „Zkusit znovu" ([retry]). */
    private var request: SummaryRequest? = null

    private val _mainProvider = MutableStateFlow<AiProvider?>(null)

    /** Kdo počítal hlavní výsledek — nadpis nad ním, když je pod ním porovnání. */
    val mainProvider: StateFlow<AiProvider?> = _mainProvider.asStateFlow()

    /**
     * [source] je `suspend`, protože se kvůli shrnutí dotahuje historie (viz
     * `ConversationViewModel.loadForSummary`). Sheet se proto ukáže HNED jako „Připravuji" —
     * kdyby se čekalo na zdroj, ťuknutí by chvíli nedělalo nic.
     */
    fun summarize(chatTitle: String, me: String, source: suspend () -> SummarySource) {
        reset()
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
            start(SummaryRequest.Summary(chatTitle, lines, me))
        }
    }

    /**
     * Vysvětlení JEDNÉ dlouhé zprávy („o co jde", `MessageMenuAction.Explain`). Tentýž sheet
     * a tentýž průběh jako shrnutí konverzace, jen jiný prompt ([explainPrompt]) a zdroj
     * s příznakem [SummarySource.explain] — podle něj sheet ukáže jiný nadpis.
     */
    fun explain(message: ChatMessage) {
        reset()
        _source.value = SummarySource(listOf(message), unread = false, explain = true)
        _state.value = ChatSummaryState.Preparing
        job = viewModelScope.launch { start(SummaryRequest.Explain(message)) }
    }

    /**
     * Shrnutí PŘEPISU dlouhé hlasovky nebo kolečka ([transcript], `MessageMenuAction.SummarizeVoice`).
     * Přepis už je hotový (ze serveru nebo z telefonu), takže tu jde jen o text — u Nano + Gemmy
     * přepisuje Gemma a shrnuje Nano, každý svou úlohou ([AiTask.Transcription], [AiTask.Summary]).
     * Tentýž sheet i porovnání modelů jako u shrnutí konverzace; [speaker] je, kdo mluví.
     */
    fun summarizeVoice(message: ChatMessage, transcript: String, speaker: String) {
        reset()
        _source.value = SummarySource(listOf(message), unread = false, voice = true)
        _state.value = ChatSummaryState.Preparing
        job = viewModelScope.launch { start(SummaryRequest.Voice(message, transcript, speaker)) }
    }

    /** „Zkusit znovu": vysvětlení a hlasovka zopakují sebe, jinak [summarize] obrazovky. */
    fun retry(summarize: () -> Unit) {
        when (val current = request) {
            is SummaryRequest.Explain -> explain(current.message)
            is SummaryRequest.Voice -> summarizeVoice(current.message, current.transcript, current.speaker)
            else -> summarize()
        }
    }

    /**
     * Spočítá totéž shrnutí (vysvětlení) ostatními zapnutými modely, jedním po druhém. Kvůli
     * porovnání češtiny a rychlosti na SKUTEČNÉ konverzaci: dojem z jednotlivých chatů, kde
     * každé shrnutí počítal jiný model, se porovnat nedá.
     *
     * **Po sobě, ne souběžně**: Gemma drží gigabajty a dva modely naráz by si braly paměť
     * i procesor — naměřený čas by pak nic neříkal. Ze stejného důvodu se nabízí až po hotovém
     * výsledku hlavního modelu (sheet tlačítko do té doby neukáže).
     */
    fun compare() {
        val current = request ?: return
        if (compareJob?.isActive == true) return
        val models = settings.onDeviceAiModels.value
        val others = models.candidates(current.task) - setOfNotNull(_mainProvider.value)
        if (others.isEmpty()) return
        _comparison.value = others.map { ComparedSummary(it, ChatSummaryState.Preparing) }
        compareJob = viewModelScope.launch {
            others.forEachIndexed { index, provider ->
                run(current, models.variant(provider)) { state ->
                    _comparison.update { list ->
                        list.mapIndexed { i, compared -> if (i == index) compared.copy(state = state) else compared }
                    }
                }
            }
        }
    }

    /** Hlavní výpočet: model podle úlohy (automaticky nebo ručně přiřazený v Nastavení). */
    private suspend fun start(request: SummaryRequest) {
        this.request = request
        val variant = ai.variantFor(request.task, settings)
        _mainProvider.value = variant.provider
        run(request, variant) { _state.value = it }
    }

    /** Rozpracované shrnutí i porovnání se zahodí — nový dotaz nebo zavřený sheet. */
    private fun reset() {
        job?.cancel()
        compareJob?.cancel()
        job = null
        compareJob = null
        request = null
        _mainProvider.value = null
        _comparison.value = emptyList()
    }

    /**
     * Celý výpočet jedním modelem [variant]; stavy jdou do [out]. Stejná cesta pro hlavní
     * výsledek i pro porovnání, ať se porovnává totéž — přepis, zkrácení i části.
     */
    private suspend fun run(request: SummaryRequest, variant: ModelVariant, out: (ChatSummaryState) -> Unit) {
        val started = TimeSource.Monotonic.markNow()
        if (ai.status(variant) != OnDeviceAiStatus.Ready) {
            out(ChatSummaryState.ModelMissing)
            return
        }
        when (request) {
            is SummaryRequest.Summary -> runSummary(request, variant, started, out)
            is SummaryRequest.Explain ->
                runSingle(variant, explainPrompt(request.message.text, platformLanguageCode), started, out)
            is SummaryRequest.Voice ->
                runSingle(variant, voiceSummaryPrompt(request.transcript, request.speaker, platformLanguageCode), started, out)
        }
    }

    /** Jedna zpráva (vysvětlení, přepis hlasovky): vejde se celá, takže jeden prompt bez částí. */
    private suspend fun runSingle(variant: ModelVariant, prompt: String, started: TimeMark, out: (ChatSummaryState) -> Unit) {
        val info = ai.info(variant)
        val details = SummaryDetails(
            modelName = info?.name,
            promptTokens = ai.countTokens(variant, prompt),
            tokenLimit = info?.tokenLimit,
            keptLines = 1,
            totalLines = 1,
        )
        generateInto(variant, prompt, details, started, out)
    }

    private suspend fun runSummary(
        request: SummaryRequest.Summary,
        variant: ModelVariant,
        started: TimeMark,
        out: (ChatSummaryState) -> Unit,
    ) {
        val (chatTitle, lines, me) = request
        val info = ai.info(variant)
        val limit = info?.tokenLimit

        /*
         * Přepis se napřed odhadne podle limitu a pak se ověří počítáním tokenů. Když se
         * nevejde, zkrátí se (zahazují se nejstarší zprávy) a počítá se znovu. Tři pokusy
         * stačí: zmenšuje se úměrně přesahu. Když model tokeny spočítat neumí, jde se
         * s odhadem a případný přesah ohlásí až generování.
         */
        var maxChars = transcriptCharsFor(limit)
        var prompt = summaryPrompt(chatTitle, lines, platformLanguageCode, maxChars, me = me)
        var tokens = ai.countTokens(variant, prompt)
        repeat(MAX_FIT_ATTEMPTS) {
            val over = tokens
            if (limit == null || over == null || over <= limit) return@repeat
            maxChars = shrunkTranscriptChars(maxChars, over, limit)
            prompt = summaryPrompt(chatTitle, lines, platformLanguageCode, maxChars, me = me)
            tokens = ai.countTokens(variant, prompt)
        }
        val kept = keptLineCount(lines, maxChars)
        if (kept < lines.size) {
            summarizeInParts(variant, chatTitle, me, lines, maxChars, info?.name, tokens, limit, started, out)
            return
        }
        val details = SummaryDetails(
            modelName = info?.name,
            promptTokens = tokens,
            tokenLimit = limit,
            keptLines = kept,
            totalLines = lines.size,
        )
        generateInto(variant, prompt, details, started, out)
    }

    /**
     * Konverzace, která se do jednoho promptu nevejde: každá část ([summaryParts], nejvýš
     * [MAX_SUMMARY_PARTS]) dostane krátké poznámky a z nich se složí jedno shrnutí. Dřív se
     * u chatu s tisíci nepřečtenými shrnul jen konec a zbytek tiše odpadl — patička sice
     * řekla „vešlo se 80 z 2 000 zpráv", ale shrnutí se tvářilo jako shrnutí všeho.
     *
     * Části mají stejnou délku jako jeden prompt, který se do limitu vešel ([maxChars]), takže
     * se znovu nepočítají; poznámky jsou krátké a složený prompt je menší než kterákoli část.
     */
    private suspend fun summarizeInParts(
        variant: ModelVariant,
        chatTitle: String,
        me: String,
        lines: List<TranscriptLine>,
        maxChars: Int,
        modelName: String?,
        promptTokens: Int?,
        limit: Int?,
        started: TimeMark,
        out: (ChatSummaryState) -> Unit,
    ) {
        val parts = summaryParts(lines, maxChars)
        val details = SummaryDetails(
            modelName = modelName,
            promptTokens = promptTokens,
            tokenLimit = limit,
            keptLines = parts.sumOf { it.size },
            totalLines = lines.size,
            parts = parts.size,
        )
        val notes = mutableListOf<String>()
        parts.forEachIndexed { index, part ->
            out(ChatSummaryState.ReadingParts(part = index + 1, parts = parts.size))
            val prompt = partSummaryPrompt(chatTitle, part, index + 1, parts.size, platformLanguageCode, me = me)
            val note = runAi(out) { ai.generate(variant, prompt).lastOrNull()?.text } ?: return
            if (note.isNotBlank()) notes += withoutIntro(note)
        }
        if (notes.isEmpty()) {
            logger.w { "Model shrnutí nenapsal k žádné části nic" }
            out(ChatSummaryState.Failed(emptyOutputMessage))
            return
        }
        generateInto(variant, combinedSummaryPrompt(chatTitle, notes, platformLanguageCode, me = me), details, started, out)
    }

    /**
     * Generování části, s týmiž chybovými stavy jako [generateInto]. `null` = selhalo a stav
     * už je [ChatSummaryState.Failed] (prázdná poznámka je text `""`, ne `null`).
     */
    private suspend fun runAi(out: (ChatSummaryState) -> Unit, block: suspend () -> String?): String? =
        try {
            block().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e, out)
            null
        }

    /** Pustí model a průběžně zapisuje text do [out]; chyba skončí jako [ChatSummaryState.Failed]. */
    private suspend fun generateInto(
        variant: ModelVariant,
        prompt: String,
        details: SummaryDetails,
        started: TimeMark,
        out: (ChatSummaryState) -> Unit,
    ) {
        var last = Generated("")
        try {
            ai.generate(variant, prompt).collect { chunk ->
                last = chunk
                out(
                    ChatSummaryState.Text(
                        // Úvod („Zde je shrnutí…") zmizí, jakmile pod ním začne první odrážka.
                        text = withoutIntro(chunk.text),
                        done = false,
                        details = details,
                        truncated = chunk.truncated,
                    ),
                )
            }
            out(
                if (last.text.isBlank()) {
                    logger.w { "Model shrnutí nenapsal žádný text" }
                    ChatSummaryState.Failed(emptyOutputMessage)
                } else {
                    if (last.truncated) logger.i { "Model shrnutí nedopsal, došel mu strop výstupu" }
                    ChatSummaryState.Text(
                        // Useknutá věta by vypadala jako chyba modelu; celé odrážky ne.
                        text = withoutIntro(if (last.truncated) completeLines(last.text) else last.text),
                        done = true,
                        details = details,
                        truncated = last.truncated,
                        elapsedMs = started.elapsedNow().inWholeMilliseconds,
                    )
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e, out)
        }
    }

    private fun fail(e: Exception, out: (ChatSummaryState) -> Unit) {
        if (e is OnDeviceAiException) {
            // Rozbitý model je problém pro Crashlytics (`e` s výjimkou), vytížený ne.
            if (e.isReportable) logger.e(e) { "Shrnutí se nepovedlo" } else logger.w(e) { "Shrnutí se nepovedlo" }
            out(ChatSummaryState.Failed(e.userMessage()))
        } else {
            logger.e(e) { "Shrnutí se nepovedlo" }
            out(ChatSummaryState.Failed(UiText(Res.string.summary_failed)))
        }
    }

    /** Zavření sheetu: rozpracované generování nemá kam dopsat, zahodí se. */
    fun dismiss() {
        reset()
        _state.value = ChatSummaryState.Idle
        _source.value = null
    }

    private companion object {
        const val MAX_FIT_ATTEMPTS = 3
    }
}

/** Co sheet počítá: shrnutí konverzace, nebo vysvětlení jedné zprávy. */
private sealed interface SummaryRequest {
    val task: AiTask

    /** [me] je podpis uživatelových zpráv v přepisu („Já"); model o něm píše ve druhé osobě. */
    data class Summary(val chatTitle: String, val lines: List<TranscriptLine>, val me: String) : SummaryRequest {
        override val task get() = AiTask.Summary
    }

    data class Explain(val message: ChatMessage) : SummaryRequest {
        override val task get() = AiTask.Explain
    }

    /** Přepis je text jako každý jiný, proto úloha shrnutí — model pro zvuk tu potřeba není. */
    data class Voice(val message: ChatMessage, val transcript: String, val speaker: String) : SummaryRequest {
        override val task get() = AiTask.Summary
    }
}

/** Výsledek téhož shrnutí z jiného modelu ([ChatSummaryViewModel.compare]). */
data class ComparedSummary(val provider: AiProvider, val state: ChatSummaryState)
