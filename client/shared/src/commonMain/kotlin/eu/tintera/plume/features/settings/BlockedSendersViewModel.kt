package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.BlockedSender
import eu.tintera.plume.core.telegram.BlockedSendersPage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_blocked_failed
import eu.tintera.plume.resources.settings_blocked_load_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kolik blokovaných se čte naráz; víc `getBlockedMessageSenders` nevezme. */
internal const val BLOCKED_PAGE_SIZE = 100

/**
 * Seznam blokovaných.
 *
 * [unblocked] jsou ti, kterým uživatel v otevřeném seznamu blokování právě zrušil. **Ze seznamu
 * nezmizí** — řádek řekne „Odblokováno" a nabídne zablokovat znovu. Zmizelý řádek by šel vrátit
 * jen snackbarem, a ten by pod modálním sheetem nebyl vidět.
 */
data class BlockedSendersState(
    /** `null`, dokud nedorazí první stránka. */
    val totalCount: Int? = null,
    val senders: List<BlockedSender> = emptyList(),
    /** Kolik položek seznamu na serveru už je přečteno — další stránka začíná tady. */
    val nextOffset: Int = 0,
    val unblocked: Set<Long> = emptySet(),
    /** Odesílatelé, u kterých změna blokování právě běží — tlačítko je mezitím neaktivní. */
    val busy: Set<Long> = emptySet(),
    val loadingMore: Boolean = false,
    val error: UiText? = null,
) {
    /** Kolik blokovaných doopravdy je — bez těch, kterým uživatel blokování právě zrušil. */
    val blockedCount: Int? get() = totalCount?.let { (it - unblocked.size).coerceAtLeast(0) }

    /** Konec seznamu podle serveru, ne podle toho, kolik položek se podařilo dočíst. */
    val endReached: Boolean get() = totalCount != null && nextOffset >= totalCount

    /**
     * Připojí stránku přečtenou od [offset]. Stránka, která nenavazuje (seznam se mezitím četl
     * znovu), se zahodí. Tentýž odesílatel na dvou stránkách (seznam se mezi čteními posunul)
     * zůstane jednou — dva stejné klíče by `LazyColumn` shodily.
     */
    fun withPage(offset: Int, page: BlockedSendersPage): BlockedSendersState {
        if (offset != nextOffset) return this
        return copy(
            totalCount = page.totalCount,
            senders = (senders + page.senders).distinctBy { it.id },
            nextOffset = offset + BLOCKED_PAGE_SIZE,
            loadingMore = false,
        )
    }
}

/**
 * Seznam blokovaných v Nastavení → Obecné. Jediné místo, kde jde zrušit blokování někoho, s kým
 * uživatel chat smazal — nabídka nad chatem i profil stojí na chatu.
 *
 * Blokování jde přes [ChatRepository], jako všude jinde v aplikaci (viz `CLAUDE.md`, „Blokování
 * a nahlášení spamu").
 */
class BlockedSendersViewModel(private val chats: ChatRepository) : ViewModel() {

    private val logger = Logger.withTag("Blocked")

    private val _state = MutableStateFlow(BlockedSendersState())
    val state: StateFlow<BlockedSendersState> = _state.asStateFlow()

    init {
        reload()
    }

    /**
     * Seznam od začátku. Volá se i po zavření seznamu: co blokování udělalo (a jestli server
     * poslechl), ví jen server — stejná zásada jako u zařízení v Nastavení.
     */
    fun reload() {
        _state.value = BlockedSendersState(loadingMore = true)
        load(offset = 0)
    }

    /** Další stránka, když se seznam blíží ke konci. */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.endReached || current.totalCount == null) return
        _state.update { it.copy(loadingMore = true) }
        load(current.nextOffset)
    }

    fun setBlocked(sender: BlockedSender, blocked: Boolean) {
        if (sender.id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + sender.id) }
        viewModelScope.launch {
            chats.setSenderBlocked(sender.id, blocked)
                .onSuccess {
                    _state.update {
                        it.copy(unblocked = if (blocked) it.unblocked - sender.id else it.unblocked + sender.id)
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Blokování odesílatele ${sender.id} se nepodařilo změnit." }
                    _state.update { it.copy(error = failure.userMessage(Res.string.settings_blocked_failed)) }
                }
            _state.update { it.copy(busy = it.busy - sender.id) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun load(offset: Int) {
        viewModelScope.launch {
            chats.blockedSenders(offset = offset, limit = BLOCKED_PAGE_SIZE)
                .onSuccess { page -> _state.update { it.withPage(offset, page) } }
                .onFailure { failure ->
                    logger.w(failure) { "Seznam blokovaných se nepodařilo načíst." }
                    _state.update {
                        it.copy(loadingMore = false, error = failure.userMessage(Res.string.settings_blocked_load_failed))
                    }
                }
        }
    }
}
