package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.REPLY_CONTEXT_MESSAGES
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
) : ViewModel() {

    private val logger = Logger.withTag("ReplySuggestions")

    /** Nabízet jen se zapnutou volbou a na zařízení, které model má. */
    val available: StateFlow<Boolean> = combine(
        settings.replySuggestionsEnabled,
        settings.onDeviceAiVariant.flatMapLatest { variant ->
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

        val lines = transcriptLines(replyContext(messages), chatTitle, me)
        if (lines.isEmpty()) return

        job = viewModelScope.launch {
            val variant = settings.onDeviceAiVariant.value
            if (ai.status(variant) != OnDeviceAiStatus.Ready) return@launch
            try {
                val prompt = replyPrompt(chatTitle, lines, platformLanguageCode)
                // Bere se až hotový text: čipy, které se přestavují během psaní modelu, by pod
                // prstem měnily šířku i obsah. Generování je krátké (na Pixelu zlomek vteřiny).
                var last = ""
                ai.generate(variant, prompt).collect { last = it.text }
                _suggestions.value = parseReplySuggestions(last)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Návrh je doplněk; když se nepovede, prostě žádný není.
                logger.i(e) { "Návrhy odpovědi se nepovedly" }
                _suggestions.value = emptyList()
            }
        }
    }

    /** Uživatel začal psát, odešel, nebo se cíl změnil na „nenabízet". */
    fun clear() {
        job?.cancel()
        job = null
        lastTarget = null
        _suggestions.value = emptyList()
    }

    /** Ťuknutí na čip: návrh se použil, další se k téže zprávě nenabízí. */
    fun consume() {
        job?.cancel()
        job = null
        _suggestions.value = emptyList()
    }
}
