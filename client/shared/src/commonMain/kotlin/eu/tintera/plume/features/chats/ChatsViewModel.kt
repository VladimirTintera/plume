package eu.tintera.plume.features.chats

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.telegram.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_failed

class ChatsViewModel(
    private val repository: ChatRepository,
    connection: ConnectionRepository,
) : ViewModel() {

    private val logger = Logger.withTag("Chats")

    /**
     * Stav se bere přímo z repository — ta je singleton a drží živý seznam nezávisle na tom,
     * jestli obrazovka existuje. ViewModel tu nic nekešuje, jen přeposílá a spouští dotažení.
     */
    val state: StateFlow<ChatListState> = repository.chats

    /**
     * Stav spojení. Ve stavu seznamu schválně není — se seznamem chatů nijak nesouvisí a v
     * hlavičce se ukazuje i tehdy, když se seznam teprve načítá nebo skončil chybou.
     */
    val connection: StateFlow<ConnectionState> = connection.state

    private val _actions = MutableStateFlow(ChatActionsState())

    /** Stav nabídky akcí nad chatem (dlouhý stisk v seznamu). */
    val actions: StateFlow<ChatActionsState> = _actions.asStateFlow()

    fun loadMore() {
        viewModelScope.launch { repository.loadMore() }
    }

    fun showActions(chat: ChatSummary) {
        _actions.value = ChatActionsState(target = chat)
    }

    fun hideActions() {
        _actions.value = _actions.value.copy(target = null)
    }

    fun togglePinned(chat: ChatSummary) = run(chat) {
        repository.setPinned(chat.id, !chat.isPinned)
    }

    fun toggleMuted(chat: ChatSummary) = run(chat) {
        repository.setMuted(chat.id, !chat.isMuted)
    }

    fun toggleUnread(chat: ChatSummary) = run(chat) {
        repository.setMarkedAsUnread(chat.id, !chat.hasUnread)
    }

    /** Krok mezi „smazat" a smazáním — nevratnou akci musí uživatel potvrdit. */
    fun askDelete(chat: ChatSummary) {
        _actions.value = ChatActionsState(pendingDelete = chat)
    }

    fun cancelDelete() {
        _actions.value = _actions.value.copy(pendingDelete = null)
    }

    fun confirmDelete(chat: ChatSummary) = run(chat) {
        repository.deleteOrLeave(chat.id)
    }

    fun dismissError() {
        _actions.value = _actions.value.copy(error = null)
    }

    /**
     * Společný závěr všech akcí: nabídka se zavře hned, chyba se ukáže, změna stavu dorazí
     * updatem z TDLib.
     *
     * Stav se **nepřepisuje lokálně**. U reakcí v konverzaci to smysl dávalo, protože tam je
     * mezi ťuknutím a potvrzením vidět prodleva; tady se položka jen přeskládá a předběhnout
     * server by znamenalo riskovat, že se seznam rozejde se skutečností.
     */
    private fun run(chat: ChatSummary, action: suspend () -> Result<Unit>) {
        _actions.value = ChatActionsState()

        viewModelScope.launch {
            action().onFailure { failure ->
                // Surová hláška od TDLib patří do logu, ne do snackbaru — viz userMessage().
                logger.w(failure) { "Akce nad chatem ${chat.id} selhala." }
                _actions.value = ChatActionsState(
                    error = failure.userMessage(Res.string.chats_action_failed),
                )
            }
        }
    }
}

/**
 * Nabídka akcí nad jedním chatem.
 *
 * [target] je otevřený bottom sheet, [pendingDelete] potvrzovací dialog. Jsou to dva stavy,
 * ne jeden: mezi zavřením nabídky a potvrzením smazání nesmí zůstat nic otevřené.
 */
data class ChatActionsState(
    val target: ChatSummary? = null,
    val pendingDelete: ChatSummary? = null,
    val error: UiText? = null,
)

/** Nepřečteno = nepřečtené zprávy NEBO ruční příznak. Obojí se v seznamu chová stejně. */
val ChatSummary.hasUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread
