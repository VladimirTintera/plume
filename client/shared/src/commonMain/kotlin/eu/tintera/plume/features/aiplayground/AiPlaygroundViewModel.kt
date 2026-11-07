package eu.tintera.plume.features.aiplayground

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.AiModels
import eu.tintera.ai.core.AiProvider
import eu.tintera.plume.core.ai.AiTask
import eu.tintera.plume.core.ai.ChatExchange
import eu.tintera.ai.core.Generated
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiException
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.ai.chatPrompt
import eu.tintera.plume.core.ai.chatReply
import eu.tintera.plume.core.ai.chatReplyEnded
import eu.tintera.plume.core.ai.emptyOutputMessage
import eu.tintera.ai.core.estimatedTokenCount
import eu.tintera.ai.core.isReportable
import eu.tintera.plume.core.ai.userMessage
import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.summary_failed
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kde je odpověď modelu. */
enum class AnswerPhase {
    /** Čeká se na první slovo — model se načítá, nebo čte dlouhou historii. */
    Waiting,
    Writing,
    Done,

    /** Uživatel generování zastavil; co model stihl, zůstává. */
    Stopped,
    Failed,

    /** Vybraný model není stažený (nebo se stahuje). */
    ModelMissing,
}

/**
 * Odpověď modelu s měřením — kvůli měření hřiště je.
 *
 * @property firstWordMs od odeslání po první slovo: načtení modelu a přečtení historie.
 * @property totalMs od odeslání po poslední slovo; známé až u hotové odpovědi.
 * @property tokens kolik tokenů odpověď má; u LiteRT-LM a llama.cpp jen odhad
 *   ([estimatedTokenCount]), proto se rychlost ukazuje s vlnovkou.
 * @property truncated model nedomluvil, došel mu strop výstupu.
 */
data class PlaygroundAnswer(
    val provider: AiProvider?,
    val modelName: String? = null,
    val text: String = "",
    val phase: AnswerPhase = AnswerPhase.Waiting,
    val error: UiText? = null,
    val truncated: Boolean = false,
    val firstWordMs: Long? = null,
    val totalMs: Long? = null,
    val tokens: Int? = null,
) {
    val running: Boolean get() = phase == AnswerPhase.Waiting || phase == AnswerPhase.Writing

    /**
     * Rychlost psaní: tokeny za dobu od prvního slova po poslední, bez načtení modelu — to
     * nese [firstWordMs]. `null`, dokud odpověď není hotová nebo byla moc krátká na měření.
     */
    val tokensPerSecond: Double?
        get() {
            val tokens = tokens ?: return null
            val writingMs = (totalMs ?: return null) - (firstWordMs ?: return null)
            return if (writingMs < MIN_MEASURED_MS) null else tokens * 1000.0 / writingMs
        }
}

/** Jedno kolo: otázka uživatele a odpověď na ni. [id] je stálé, index v seznamu ne. */
data class PlaygroundTurn(val id: Long, val question: String, val answer: PlaygroundAnswer)

/**
 * @property rememberedFromId první kolo, které se do kontextu modelu při posledním dotazu
 *   ještě vešlo; nad ním obrazovka ukáže předěl. `null` = model viděl celý rozhovor.
 */
@Immutable
data class PlaygroundState(
    val turns: List<PlaygroundTurn> = emptyList(),
    val rememberedFromId: Long? = null,
) {
    val generating: Boolean get() = turns.lastOrNull()?.answer?.running == true
}

/** Zapnutý model v nabídce hřiště a jestli jde hned použít. */
data class PlaygroundModel(val provider: AiProvider, val ready: Boolean)

/**
 * Volný rozhovor s modelem v zařízení (Nastavení → AI v zařízení → Vyzkoušet model).
 *
 * **Každé kolo je jedno [OnDeviceAi.generate] s celou historií v promptu** ([chatPrompt]), žádná
 * session v runtime: `generate` mají všichni poskytovatelé, takže se modely porovnávají stejnou
 * cestou. Co se do limitu vstupu nevejde, odpadne od nejstarších kol ([PlaygroundState.rememberedFromId]).
 *
 * Rozhovor se nikam neukládá — žije, dokud žije ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiPlaygroundViewModel(
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
) : ViewModel() {

    private val logger = Logger.withTag("AiPlayground")

    private val _state = MutableStateFlow(PlaygroundState())
    val state: StateFlow<PlaygroundState> = _state.asStateFlow()

    /** Ručně vybraný model; `null` = ten, komu úlohu dá automatika ([variantFor]). */
    private val chosen = MutableStateFlow<AiProvider?>(null)

    /** Stav modelů se čte znovu s každým odesláním a otevřením nabídky — dostahovaný model tok sám nepozná. */
    private val statusTick = MutableStateFlow(0)

    /** Zapnuté modely v pořadí automatiky. */
    val models: StateFlow<List<PlaygroundModel>> = combine(settings.onDeviceAiModels, statusTick) { models, _ -> models }
        .mapLatest { models ->
            models.candidates(AiTask.Chat).map { provider -> PlaygroundModel(provider, ready = isReady(models.variant(provider))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Kdo odpoví na příští zprávu a jestli je vybraný ručně. */
    val current: StateFlow<CurrentModel?> = combine(chosen, settings.onDeviceAiModels, statusTick) { chosen, models, _ -> chosen to models }
        .mapLatest { (chosen, models) ->
            val manual = chosen?.takeIf { it in models.enabled }
            CurrentModel(manual ?: variantOf(null, models).provider, automatic = manual == null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var job: Job? = null
    private var nextId = 1L

    /** Model pro další odpovědi; `null` vrátí automatiku. Rozběhnutou odpověď nemění. */
    fun choose(provider: AiProvider?) {
        chosen.value = provider
    }

    /** Přečte stav modelů znovu — volá se při otevření nabídky modelů. */
    fun refreshModels() {
        statusTick.update { it + 1 }
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _state.value.generating) return
        val id = nextId++
        _state.update { it.copy(turns = it.turns + PlaygroundTurn(id, question, PlaygroundAnswer(provider = current.value?.provider))) }
        launchAnswer(id)
    }

    /** Odpověď na poslední otázku znovu, aktuálně vybraným modelem — i jiným než poprvé. */
    fun regenerate() {
        if (_state.value.generating) return
        val last = _state.value.turns.lastOrNull() ?: return
        updateAnswer(last.id) { PlaygroundAnswer(provider = current.value?.provider) }
        launchAnswer(last.id)
    }

    /** Zastaví psaní; co model stihl, zůstává a počítá se do další historie. */
    fun stop() {
        job?.cancel()
        job = null
        val last = _state.value.turns.lastOrNull() ?: return
        if (last.answer.running) updateAnswer(last.id) { it.copy(phase = AnswerPhase.Stopped) }
    }

    fun newConversation() {
        job?.cancel()
        job = null
        _state.value = PlaygroundState()
    }

    private fun launchAnswer(id: Long) {
        statusTick.update { it + 1 }
        job = viewModelScope.launch {
            val variant = variantOf(chosen.value, settings.onDeviceAiModels.value)
            answer(id, variant)
        }
    }

    private suspend fun answer(id: Long, variant: ModelVariant) {
        val started = TimeSource.Monotonic.markNow()
        updateAnswer(id) { it.copy(provider = variant.provider) }
        if (!isReady(variant)) {
            updateAnswer(id) { it.copy(phase = AnswerPhase.ModelMissing) }
            return
        }
        val info = runCatching { ai.info(variant) }.getOrNull()
        updateAnswer(id) { it.copy(modelName = info?.name) }

        val turns = _state.value.turns
        val index = turns.indexOfFirst { it.id == id }
        if (index < 0) return
        val prompt = fitPrompt(variant, info?.tokenLimit, history = turns.subList(0, index), current = turns[index])
        generate(id, variant, prompt, started)
    }

    /**
     * Prompt, který se vejde do limitu vstupu: odpadají nejstarší kola CELÁ, nikdy půl otázky.
     * Kola bez odpovědi (chyba, chybějící model) se do historie nedávají — model by z nich
     * viděl jen nezodpovězenou otázku. Když se nevejde ani samotná nová zpráva, jde i tak:
     * selhání ohlásí model.
     */
    private suspend fun fitPrompt(variant: ModelVariant, limit: Int?, history: List<PlaygroundTurn>, current: PlaygroundTurn): String {
        val answered = history.filter { it.answer.text.isNotBlank() }
        fun prompt(dropped: Int) = chatPrompt(answered.drop(dropped).map { ChatExchange(it.question, it.answer.text) }, current.question)

        var dropped = 0
        var prompt = prompt(0)
        if (limit != null) {
            while (dropped < answered.size && tokens(variant, prompt) > limit) {
                dropped++
                prompt = prompt(dropped)
            }
        }
        val rememberedFrom = if (dropped == 0) null else answered.getOrNull(dropped)?.id ?: current.id
        _state.update { it.copy(rememberedFromId = rememberedFrom) }
        return prompt
    }

    private suspend fun generate(id: Long, variant: ModelVariant, prompt: String, started: TimeMark) {
        var last = Generated("")
        var firstWordMs: Long? = null
        try {
            ai.generate(variant, prompt)
                // Malý model rád dopíše i další repliku uživatele; jakmile začne, odpověď je u konce.
                .transformWhile { emit(it); !chatReplyEnded(it.text) }
                .collect { chunk ->
                    last = chunk
                    val reply = chatReply(chunk.text)
                    if (firstWordMs == null && reply.isNotBlank()) firstWordMs = started.elapsedNow().inWholeMilliseconds
                    updateAnswer(id) {
                        it.copy(text = reply, phase = if (reply.isBlank()) AnswerPhase.Waiting else AnswerPhase.Writing, firstWordMs = firstWordMs)
                    }
                }
            val reply = chatReply(last.text)
            val totalMs = started.elapsedNow().inWholeMilliseconds
            if (reply.isBlank()) {
                logger.w { "Model nenapsal žádnou odpověď" }
                updateAnswer(id) { it.copy(phase = AnswerPhase.Failed, error = emptyOutputMessage) }
                return
            }
            val tokens = tokens(variant, reply)
            updateAnswer(id) {
                it.copy(
                    text = reply,
                    phase = AnswerPhase.Done,
                    // Useknutí za repliku uživatele není „nedomluvil": model domluvil a pokračoval.
                    truncated = last.truncated && !chatReplyEnded(last.text),
                    totalMs = totalMs,
                    tokens = tokens,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = if (e is OnDeviceAiException) {
                // Rozbitý model je problém pro Crashlytics (`e` s výjimkou), vytížený ne.
                if (e.isReportable) logger.e(e) { "Odpověď se nepovedla" } else logger.w(e) { "Odpověď se nepovedla" }
                e.userMessage()
            } else {
                logger.e(e) { "Odpověď se nepovedla" }
                UiText(Res.string.summary_failed)
            }
            updateAnswer(id) { it.copy(phase = AnswerPhase.Failed, error = reason) }
        }
    }

    private suspend fun variantOf(chosen: AiProvider?, models: AiModels): ModelVariant =
        chosen?.takeIf { it in models.enabled }?.let(models::variant) ?: ai.variantFor(AiTask.Chat, models)

    private suspend fun isReady(variant: ModelVariant): Boolean =
        runCatching { ai.status(variant) }.getOrNull() == OnDeviceAiStatus.Ready

    private suspend fun tokens(variant: ModelVariant, text: String): Int =
        runCatching { ai.countTokens(variant, text) }.getOrNull() ?: estimatedTokenCount(text)

    private fun updateAnswer(id: Long, change: (PlaygroundAnswer) -> PlaygroundAnswer) {
        _state.update { state ->
            state.copy(turns = state.turns.map { turn -> if (turn.id == id) turn.copy(answer = change(turn.answer)) else turn })
        }
    }
}

/** Model pro příští odpověď; [automatic] = vybrala ho automatika, ne uživatel. */
data class CurrentModel(val provider: AiProvider, val automatic: Boolean)

/** Kratší psaní se neměří: pár desítek milisekund dělí jen zaokrouhlení a rychlost by vyšla nesmyslná. */
private const val MIN_MEASURED_MS = 200L
