package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.clear_history_done
import eu.tintera.plume.resources.clear_history_failed
import eu.tintera.plume.resources.clear_history_period_done
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

/**
 * Kalendář zpráv v panelu vedle konverzace (`ChatCalendarRoute`). Kalendář sám je tentýž
 * [ConversationCalendar] jako v sheetu; ViewModel je vlastní, protože panel je samostatná položka
 * navigace s vlastním úložištěm — na ViewModel konverzace vedle nedosáhne.
 *
 * Konverzaci proto neřídí přímo: skok na den nebo zprávu pošle sdíleným
 * [ConversationJumpRequests] a ViewModel konverzace si ho vyzvedne, jako záložku z profilu.
 * O chatu (název, práva mazat po dnech, skupina) si řekne sám — konverzace mu je nepředává.
 */
@OptIn(FlowPreview::class)
class ChatCalendarViewModel(
    val chatId: Long,
    /** Zúžené na téma Uložených zpráv nebo vlákno přímých zpráv, je-li panel jejich. */
    private val repository: ConversationRepository,
    private val jumpRequests: ConversationJumpRequests,
    /** Co má konverzace vedle načtené — z toho se dopočítají dny, na které se z panelu skočilo. */
    loadedHistory: LoadedHistory,
    savedTopicId: Long?,
    directTopicId: Long?,
    /** Zpráva, na které konverzace stála — vstup do ostrůvku a měsíc, na kterém panel začne. */
    private val anchor: Long?,
) : ViewModel() {

    val calendar: ConversationCalendar = ConversationCalendar(chatId = chatId, repository = repository, scope = viewModelScope)

    private val _info = MutableStateFlow<ChatInfo?>(null)
    val info: StateFlow<ChatInfo?> = _info.asStateFlow()

    private val _initialDay = MutableStateFlow<Int?>(null)

    /** Čas zprávy, na které konverzace stála; kalendář se otevře na jejím měsíci. */
    val initialDay: StateFlow<Int?> = _initialDay.asStateFlow()

    private val _notice = MutableStateFlow<UiText?>(null)

    /** Výsledek vymazání historie za den — panel nemá konverzaci, která by ho ohlásila. */
    val notice: StateFlow<UiText?> = _notice.asStateFlow()

    init {
        calendar.open(anchor)
        viewModelScope.launch { _info.value = repository.chatInfo(chatId).getOrNull() }
        if (anchor != null) {
            viewModelScope.launch { _initialDay.value = repository.message(chatId, anchor).getOrNull()?.timestamp }
        }
        // Panel po ťuknutí na den zůstává otevřený a konverzace vedle si stáhne okno kolem
        // dne — TDLib to neohlásí, konverzace ano. Pauza, ať se při rolování, kdy okno roste
        // po stránkách, nepočítá s každou znovu.
        viewModelScope.launch {
            loadedHistory.window
                .filterNotNull()
                .filter { it.chatId == chatId && it.savedTopicId == savedTopicId && it.directTopicId == directTopicId }
                .debounce(REFRESH_DEBOUNCE_MS)
                .collect { window -> calendar.refresh(window.newestMessageId) }
        }
    }

    fun jumpToDay(epochSeconds: Int) = jumpRequests.requestDay(chatId, epochSeconds)

    fun jumpToMessage(messageId: Long) = jumpRequests.request(chatId, messageId)

    /**
     * Vymazání z podržení dne (nebo celé historie, když ho uživatel v sheetu přepne). Kalendář se
     * pak spočítá znovu — ze smazaného dne by jinak zůstal počet.
     */
    fun clearHistory(from: Int?, to: Int?, forAll: Boolean) {
        viewModelScope.launch {
            val result = if (from != null && to != null) {
                repository.clearHistoryBetween(chatId, from, to, forAll)
            } else {
                repository.clearHistory(chatId, forAll)
            }
            _notice.value = UiText(
                when {
                    result.isFailure -> Res.string.clear_history_failed
                    from != null -> Res.string.clear_history_period_done
                    else -> Res.string.clear_history_done
                },
            )
            if (result.isSuccess) calendar.open(anchor)
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }
}

/** Jak dlouho se po poslední změně okna konverzace čeká, než se kalendář přepočítá. */
private const val REFRESH_DEBOUNCE_MS = 600L
