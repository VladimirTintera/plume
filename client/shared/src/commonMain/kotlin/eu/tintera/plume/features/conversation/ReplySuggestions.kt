package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.proactiveVariant
import eu.tintera.ai.core.NoDeviceLoad
import eu.tintera.ai.core.DeviceLoad
import eu.tintera.plume.core.ai.variantsFor
import eu.tintera.plume.core.ai.AiTask
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiException
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.ai.core.isReportable
import eu.tintera.plume.core.ai.REPLY_CONTEXT_MESSAGES
import eu.tintera.plume.core.ai.ReplySuggestionsOutput
import eu.tintera.ai.core.StructuredOutputException
import eu.tintera.plume.core.ai.TranscriptLine
import eu.tintera.plume.core.ai.cleanReplySuggestions
import eu.tintera.plume.core.ai.parseReplySuggestions
import eu.tintera.plume.core.ai.replyPrompt
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

/**
 * Na kterou zprávu se má nabídnout odpověď, nebo `null`, když se nabízet nemá.
 *
 * Vrací id zprávy, ne jen `Boolean`: obrazovka podle něj pozná, že jde o JINOU zprávu než
 * posledně, a nemusí si pamatovat nic navíc. Návrh se dělá jednou na zprávu — bez toho by se
 * inference pouštěla při každé změně stavu konverzace (přečtení, reakce, scroll).
 *
 * Nabízí se, jen když:
 * - poslední zpráva je PŘÍCHOZÍ a není servisní — na vlastní zprávu se neodpovídá a „X se
 *   přidal do skupiny" není nic, na co by šlo odpovědět,
 * - uživatel nemá nic rozepsaného — kdo píše, našeptávat si nenechá,
 * - neupravuje zprávu — pole v tu chvíli patří úpravě,
 * - do chatu se smí psát ([canWrite], tedy totéž pravidlo jako vstupní pole).
 *
 * Čistá funkce s testy, ne podmínka v efektu: rozhoduje, kdy běží model, a hraniční případy
 * (prázdná historie, servisní zpráva na konci, rozepsaný text) se v kompozici neověří.
 */
fun replySuggestionTarget(
    messages: List<ChatMessage>,
    draft: String,
    editing: ChatMessage?,
    canWrite: Boolean,
): Long? {
    if (!canWrite || editing != null || draft.isNotBlank()) return null
    // Seznam jde od NEJNOVĚJŠÍ (tak ho drží konverzace), takže poslední zpráva je první.
    val newest = messages.firstOrNull() ?: return null
    if (newest.isOutgoing || newest.isService) return null
    return newest.id
}

/** Kontext pro model: posledních pár zpráv chronologicky, servisní vynechané. */
fun replyContext(messages: List<ChatMessage>): List<ChatMessage> =
    messages.filter { !it.isService }.take(REPLY_CONTEXT_MESSAGES).asReversed()

/**
 * Návrhy odpovědi od modelu v zařízení.
 *
 * Samostatný ViewModel jako [ChatSummaryViewModel] — je to experimentální doplněk, který se dá
 * celý vyjmout, a zprávy dostane od obrazovky.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReplySuggestionsViewModel(
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
    /** Úsporný režim a teplota: Gemma pod nimi sama od sebe nic nepočítá ([proactiveVariant]). */
    private val load: DeviceLoad = NoDeviceLoad,
) : ViewModel() {

    private val logger = Logger.withTag("ReplySuggestions")

    /** Nabízet jen se zapnutou volbou a na zařízení, které model má. */
    val available: StateFlow<Boolean> = combine(
        settings.replySuggestionsEnabled,
        ai.variantsFor(AiTask.Replies, settings).flatMapLatest { variant ->
            // `catch` je pojistka, ne ozdoba: kdyby ze zjišťování stavu cokoli vyletělo,
            // shodilo by to celý `stateIn` scope, tedy ViewModel obrazovky — kvůli volitelné
            // funkci. Nepodařilo se zjistit = nenabízet.
            flow { emit(ai.status(variant) != OnDeviceAiStatus.Unsupported) }.catch { emit(false) }
        },
    ) { enabled, supported -> enabled && supported }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private var job: Job? = null

    /** Pro kterou zprávu už návrh běžel — aby se inference nepouštěla znovu a znovu. */
    private var lastTarget: Long? = null

    fun suggest(targetMessageId: Long, chatTitle: String, messages: List<ChatMessage>, me: String) {
        if (targetMessageId == lastTarget) return
        lastTarget = targetMessageId
        job?.cancel()
        _suggestions.value = emptyList()

        job = viewModelScope.launch {
            val lines = transcriptLines(replyContext(messages), chatTitle, me)
            if (lines.isEmpty()) return@launch
            // Návrhy se objevují samy, takže pod zátěží telefonu bez Gemmy.
            val variant = ai.proactiveVariant(AiTask.Replies, settings, load) ?: return@launch
            if (ai.status(variant) != OnDeviceAiStatus.Ready) return@launch
            try {
                _suggestions.value = structuredSuggestions(variant, chatTitle, lines)
                    ?: textSuggestions(variant, chatTitle, lines)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Návrh je doplněk; když se nepovede, prostě žádný není. Rozbitý model se ale
                // nahlásí do Crashlytics — jednou za běh procesu, protože návrh se pouští sám
                // u každé příchozí zprávy a hlášení by se jinak sypala desítkami.
                if (e is OnDeviceAiException && e.isReportable && !reportedBrokenModel) {
                    reportedBrokenModel = true
                    logger.e(e) { "Návrhy odpovědi se nepovedly" }
                } else {
                    logger.i(e) { "Návrhy odpovědi se nepovedly" }
                }
                _suggestions.value = emptyList()
            }
        }
    }

    /**
     * Návrhy ze strukturovaného výstupu, kde ho model má; `null` = jít textem.
     *
     * **Špatný tvar není konec** ([StructuredOutputException]): strukturovaný výstup je alfa
     * (`genai-schema`) a model může vrátit JSON, který do typu nesedí. Pak se zkusí text — jako
     * by strukturovaný výstup nebyl. Dvakrát se tak platí jen na zařízení, kde typy selhávají,
     * a to se v logu ukáže. Totéž, když po úklidu nezbyde žádný návrh. Rozbitý model
     * ([OnDeviceAiException]) se ale nezkouší podruhé: text by selhal stejně a hlášení by šlo
     * pryč.
     */
    private suspend fun structuredSuggestions(
        variant: ModelVariant,
        chatTitle: String,
        lines: List<TranscriptLine>,
    ): List<String>? {
        if (!ai.supportsStructuredOutput(variant)) return null
        return try {
            val prompt = replyPrompt(chatTitle, lines, platformLanguageCode, structured = true)
            cleanReplySuggestions(ai.generateStructured(variant, prompt, ReplySuggestionsOutput::class))
                // Nic použitelného (samé dlouhé či prázdné návrhy) je pro uživatele totéž co
                // špatný tvar — a text je za zlomek vteřiny.
                .ifEmpty { null }
        } catch (e: StructuredOutputException) {
            logger.w(e) { "Strukturované návrhy odpovědi se nepovedly, zkouším text" }
            null
        }
    }

    private suspend fun textSuggestions(
        variant: ModelVariant,
        chatTitle: String,
        lines: List<TranscriptLine>,
    ): List<String> {
        val prompt = replyPrompt(chatTitle, lines, platformLanguageCode)
        // Bere se až hotový text: čipy, které se přestavují během psaní modelu, by pod
        // prstem měnily šířku i obsah. Generování je krátké (na Pixelu zlomek vteřiny).
        var last = ""
        ai.generate(variant, prompt).collect { last = it.text }
        return parseReplySuggestions(last)
    }

    /** Uživatel začal psát, odešel, nebo se cíl změnil na „nenabízet". */
    fun clear() {
        job?.cancel()
        job = null
        lastTarget = null
        _suggestions.value = emptyList()
    }

    /**
     * Ťuknutí na čip nebo „Skrýt návrhy": k téže zprávě se už nenabízí (id zůstává), k další
     * zase ano.
     */
    fun consume() {
        job?.cancel()
        job = null
        _suggestions.value = emptyList()
    }

    private val _turnedOff = MutableStateFlow(false)

    /** Návrhy se právě vypnuly z nabídky u čipů; obrazovka ukáže snackbar s Vrátit. */
    val turnedOff: StateFlow<Boolean> = _turnedOff.asStateFlow()

    /**
     * Vypnout návrhy z nabídky u čipů — tatáž volba jako v Nastavení → AI v zařízení. Čipy
     * zmizí hned, ne až po přepnutí [available].
     */
    fun turnOff() {
        consume()
        settings.setReplySuggestionsEnabled(false)
        _turnedOff.value = true
    }

    fun consumeTurnedOff() {
        _turnedOff.value = false
    }

    /**
     * Vrátit ze snackbaru. [available] se přepne zpátky, obrazovka zavolá [suggest] znovu,
     * a protože mezitím při vypnutí proběhlo [clear], návrh přijde i k téže zprávě.
     */
    fun turnOn() {
        settings.setReplySuggestionsEnabled(true)
    }

    private companion object {
        /** Hlášení rozbitého modelu už odešlo (za celý proces, ne za obrazovku). */
        var reportedBrokenModel = false
    }
}
