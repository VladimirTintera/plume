package eu.tintera.plume.features.chats

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.update.AppUpdateState
import eu.tintera.plume.core.update.AppUpdates
import eu.tintera.plume.core.update.updateCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_failed

/**
 * Seznam jedné složky — hlavních chatů, nebo archivu. Obrazovky se liší jen hlavičkou (archiv
 * nemá hledání ani kartu aktualizace), seznam a akce nad chatem jsou tytéž, takže ViewModel je
 * jeden a složku dostane parametrem.
 */
class ChatsViewModel(
    val folder: ChatFolder,
    private val repository: ChatRepository,
    connection: ConnectionRepository,
    private val settings: AppSettings,
    /** Obrazovka ho potřebuje ke spuštění aktualizace (`rememberStartUpdate`). */
    val appUpdates: AppUpdates,
) : ViewModel() {

    private val logger = Logger.withTag("Chats")

    /**
     * Stav se bere přímo z repository — ta je singleton a drží živý seznam nezávisle na tom,
     * jestli obrazovka existuje. ViewModel tu nic nekešuje, jen přeposílá a spouští dotažení.
     */
    val state: StateFlow<ChatListState> = repository.chats(folder)

    init {
        // Hlavní seznam se načítá sám po přihlášení. Archiv až tady: na seznam, který nikdo
        // neotevře, se nemá co ptát serveru.
        if (folder == ChatFolder.Archive) loadMore()
    }

    /**
     * Stav spojení. Ve stavu seznamu schválně není — se seznamem chatů nijak nesouvisí a v
     * hlavičce se ukazuje i tehdy, když se seznam teprve načítá nebo skončil chybou.
     */
    val connection: StateFlow<ConnectionState> = connection.state

    /**
     * Mají nepřečtené chaty vlastní blok? Bere se z nastavení, které je načtené od startu —
     * seznam ho tedy má v prvním snímku a neproblikne z výchozí hodnoty na uloženou.
     */
    val separateUnreadChats: StateFlow<Boolean> = settings.separateUnreadChats

    /** Hledání v hlavičce seznamu (chaty, veřejné chaty i zprávy). Archiv hledání nemá, u něj zůstane bez dotazu a nic nehledá. */
    val search = ChatSearch(repository, viewModelScope, everywhere = true)

    /** Id Uložených zpráv — v seznamu nejsou, pokud nejsou připnuté (viz `chatGroups`). */
    val savedMessagesChatId: StateFlow<Long?> = repository.savedMessagesChatId

    /** Karta s aktualizací nahoře v seznamu, `null` = žádná. Pravidlo je v [updateCard]. */
    val updateCard: StateFlow<AppUpdateState?> =
        combine(appUpdates.state, settings.dismissedUpdateVersionCode, ::updateCard)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                updateCard(appUpdates.state.value, settings.dismissedUpdateVersionCode.value),
            )

    /** Při každém návratu na seznam — viz [AppUpdates.check]. */
    fun checkForUpdate() = appUpdates.check()

    fun dismissUpdate(versionCode: Int) = settings.dismissUpdate(versionCode)

    fun completeUpdate() = appUpdates.complete()

    private val _actions = MutableStateFlow(ChatActionsState())

    /** Stav nabídky akcí nad chatem (dlouhý stisk v seznamu). */
    val actions: StateFlow<ChatActionsState> = _actions.asStateFlow()

    /** Uložené zprávy z menu horní lišty. Chat se v TDLib případně nejdřív založí. */
    fun openSavedMessages(onOpened: (chatId: Long) -> Unit) {
        viewModelScope.launch {
            repository.openSavedMessages()
                .onSuccess(onOpened)
                .onFailure { failure ->
                    logger.w(failure) { "Uložené zprávy se nepodařilo otevřít." }
                    _actions.value = ChatActionsState(error = failure.userMessage(Res.string.chats_action_failed))
                }
        }
    }

    fun loadMore() {
        viewModelScope.launch { repository.loadMore(folder) }
    }

    fun showActions(chat: ChatSummary) {
        _actions.value = ChatActionsState(target = chat)
    }

    fun hideActions() {
        _actions.value = _actions.value.copy(target = null)
    }

    fun togglePinned(chat: ChatSummary) = run(chat.id) {
        repository.setPinned(chat.id, chat.folder, !chat.isPinned)
    }

    /**
     * Do archivu a zpátky. Vratné, takže bez potvrzení — místo něj snackbar s „Vrátit"
     * ([ChatActionsState.archived]), jako u odepnutí zprávy.
     */
    fun toggleArchived(chat: ChatSummary) {
        val archived = chat.folder != ChatFolder.Archive
        run(chat.id, onSuccess = {
            _actions.update { it.copy(archived = ArchiveChange(chat.id, archived)) }
        }) {
            repository.setArchived(chat.id, archived)
        }
    }

    /** Snackbar s „Vrátit" už je vidět — událost se nuluje hned, jinak by se po otočení ukázal znovu. */
    fun consumeArchived() {
        _actions.update { it.copy(archived = null) }
    }

    /** „Vrátit" ve snackbaru. Oprava omylu, takže se na ni už další „Vrátit" nenabízí. */
    fun undoArchive(change: ArchiveChange) = run(change.chatId) {
        repository.setArchived(change.chatId, !change.archived)
    }

    fun toggleMuted(chat: ChatSummary) = run(chat.id) {
        repository.setMuted(chat.id, !chat.isMuted)
    }

    fun toggleUnread(chat: ChatSummary) = run(chat.id) {
        repository.setMarkedAsUnread(chat.id, !chat.hasUnread)
    }

    /** Krok mezi „smazat" a smazáním — nevratnou akci musí uživatel potvrdit. */
    fun askDelete(chat: ChatSummary) {
        _actions.value = ChatActionsState(pendingDelete = chat)
    }

    fun cancelDelete() {
        _actions.value = _actions.value.copy(pendingDelete = null)
    }

    fun confirmDelete(chat: ChatSummary) = run(chat.id) {
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
    private fun run(
        chatId: Long,
        onSuccess: () -> Unit = {},
        action: suspend () -> Result<Unit>,
    ) {
        _actions.value = ChatActionsState()

        viewModelScope.launch {
            action().onSuccess { onSuccess() }.onFailure { failure ->
                // Surová hláška od TDLib patří do logu, ne do snackbaru — viz userMessage().
                logger.w(failure) { "Akce nad chatem $chatId selhala." }
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
    /** Jednorázová událost pro snackbar s „Vrátit" po archivaci nebo vrácení z archivu. */
    val archived: ArchiveChange? = null,
)

/** Chat se přesunul do archivu ([archived]), nebo z něj. */
data class ArchiveChange(val chatId: Long, val archived: Boolean)

/** Nepřečteno = nepřečtené zprávy NEBO ruční příznak. Obojí se v seznamu chová stejně. */
val ChatSummary.hasUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread
