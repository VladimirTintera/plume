package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.AccountRepository
import kotlin.time.Duration
import co.touchlab.kermit.Logger
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ArchiveSettings
import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatFolders
import eu.tintera.plume.core.telegram.ChatFolderMembership
import eu.tintera.plume.core.telegram.ChatFolderRepository
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.telegram.SpamActions
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.DeviceRepository
import eu.tintera.plume.core.telegram.UnconfirmedDevice
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.update.AppUpdateState
import eu.tintera.plume.core.update.AppUpdates
import eu.tintera.plume.core.update.updateCard
import eu.tintera.plume.features.report.ReportFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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
    private val devices: DeviceRepository,
    private val accounts: AccountRepository,
    /** Filtr přežije zabití procesu v pozadí; nové spuštění aplikace začne na všech chatech. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** „Přidat do složky" v nabídce nad chatem; bez něj se položka nenabízí. */
    private val folderRepository: ChatFolderRepository? = null,
) : ViewModel() {

    private val logger = Logger.withTag("Chats")

    private val _selectedFolderId = MutableStateFlow(savedState.get<Int>(FOLDER_KEY))

    /** Složky Telegramu — pro řádek nad seznamem a „Přidat do složky" v nabídce nad chatem. */
    val chatFolders: StateFlow<ChatFolders> = repository.chatFolders

    /**
     * Složky Telegramu nad hlavním seznamem a jejich nepřečtené. Archiv složky nenabízí — jsou to
     * výběry z celého účtu, archiv je jen jeden z nich.
     */
    val folderTabs: StateFlow<List<FolderTab>> =
        if (folder == ChatFolder.Main) {
            combine(repository.chatFolders, repository.folderUnread, _selectedFolderId) { folders, unread, selected ->
                folderTabs(folders, unread, selectedFolderId(selected, folders))
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        } else {
            MutableStateFlow(emptyList())
        }

    /**
     * Složka, jejíž chaty se ukazují: vybraná složka Telegramu, nebo [folder]. Složka, která mezitím
     * zmizela (smazaná na jiném zařízení), spadne na celý seznam — výběr se tím ale nepřepíše,
     * takže když se složky po startu teprve načítají, uložená volba se neztratí.
     */
    private val shownFolder: StateFlow<ChatFolder> =
        if (folder == ChatFolder.Main) {
            combine(_selectedFolderId, repository.chatFolders) { selected, folders ->
                selectedFolderId(selected, folders)?.let { ChatFolder.Custom(it) } ?: folder
            }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                selectedFolderId(_selectedFolderId.value, repository.chatFolders.value)?.let { ChatFolder.Custom(it) } ?: folder,
            )
        } else {
            MutableStateFlow(folder)
        }

    /**
     * Stav se bere přímo z repository — ta je singleton a drží živý seznam nezávisle na tom,
     * jestli obrazovka existuje. ViewModel tu nic nekešuje, jen přeposílá a spouští dotažení.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChatListState> = shownFolder
        .flatMapLatest { repository.chats(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.chats(shownFolder.value).value)

    /**
     * Vybere složku Telegramu ([id]), nebo celý seznam (`null`). Žije jako filtr: přežije návrat
     * z konverzace i zabití procesu, nové spuštění začne na všech chatech.
     */
    fun selectFolder(id: Int?) {
        _selectedFolderId.value = id
        savedState[FOLDER_KEY] = id
        // Složka se načítá až při prvním otevření, jako archiv.
        if (id != null) viewModelScope.launch { repository.loadMore(ChatFolder.Custom(id)) }
    }

    /**
     * Průvodce nahlášením — z položky „Nahlásit…" v nabídce nad chatem i z mazání s volbou
     * „nahlásit". Kreslí ho `ChatActionsHost`.
     */
    val reportFlow = ReportFlow(viewModelScope, repository::report)

    private val _filter = MutableStateFlow(
        savedState.get<String>(FILTER_KEY)?.let { name -> ChatFilter.entries.find { it.name == name } }
            ?: ChatFilter.All,
    )

    /**
     * Filtr z plovoucího toolbaru (jen hlavní seznam). Žije s položkou navigace: návrat
     * z konverzace ho zachová a přes [SavedStateHandle] přežije i zabití procesu v pozadí — kdo se
     * vrátí do obnovené konverzace, nemá najít přepnutý filtr. Nové spuštění aplikace ale začne na
     * všech chatech: filtr je chvilkové zúžení, ne nastavení, a uložené Nepřečtené by při startu
     * vypadaly jako seznam, ze kterého zmizely chaty.
     *
     * Ukládá se názvem, ne pořadím — stejně jako výčty v `AppSettings`.
     */
    val filter: StateFlow<ChatFilter> = _filter.asStateFlow()

    fun setFilter(filter: ChatFilter) {
        _filter.value = filter
        savedState[FILTER_KEY] = filter.name
    }

    private val _account = MutableStateFlow<AccountInfo?>(null)

    /** Přihlášený účet — jeho fotka je v hledání vstupem do menu a Nastavení. */
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    /**
     * Nepřečtené chaty v archivu — odznak u vstupu do archivu v menu účtu. Počítá TDLib, takže
     * to platí i pro archiv, který uživatel v téhle relaci ještě neotevřel.
     */
    val archiveUnread: StateFlow<ArchiveUnread> = repository.archiveUnread

    private val _archiveSettings = MutableStateFlow<ArchiveSettings?>(null)

    /**
     * Nastavení archivu, `null` dokud (nebo když) ho TDLib nevydá — pak se tlačítko v hlavičce
     * archivu nenabízí. TDLib na jeho změnu update neposílá, takže je tohle jediný zdroj pravdy
     * a zápis se do něj promítne rovnou; při selhání se vrátí zpátky.
     */
    val archiveSettings: StateFlow<ArchiveSettings?> = _archiveSettings.asStateFlow()

    private val _archiveSettingsOpen = MutableStateFlow(false)

    val archiveSettingsOpen: StateFlow<Boolean> = _archiveSettingsOpen.asStateFlow()

    fun openArchiveSettings() {
        _archiveSettingsOpen.value = true
    }

    fun closeArchiveSettings() {
        _archiveSettingsOpen.value = false
    }

    fun setArchiveUnknownSenders(value: Boolean) =
        writeArchiveSettings { it.copy(archiveUnknownSenders = value) }

    fun setKeepUnmutedArchived(value: Boolean) =
        writeArchiveSettings { it.copy(keepUnmutedArchived = value) }

    fun setKeepFromFoldersArchived(value: Boolean) =
        writeArchiveSettings { it.copy(keepFromFoldersArchived = value) }

    /**
     * Přepínač se pohne hned a při selhání se vrátí. Na rozdíl od akcí nad chatem tu potvrzení
     * updatem nepřijde — kdyby se čekalo na server, přepínač by se po ťuknutí nehýbal vůbec.
     */
    private fun writeArchiveSettings(change: (ArchiveSettings) -> ArchiveSettings) {
        val previous = _archiveSettings.value ?: return
        val updated = change(previous)
        if (updated == previous) return
        _archiveSettings.value = updated

        viewModelScope.launch {
            repository.setArchiveSettings(updated).onFailure { failure ->
                logger.w(failure) { "Nastavení archivu se nepodařilo uložit." }
                _archiveSettings.value = previous
                _actions.update { it.copy(error = failure.userMessage(Res.string.chats_action_failed)) }
            }
        }
    }

    init {
        // Hlavní seznam se načítá sám po přihlášení. Archiv až tady: na seznam, který nikdo
        // neotevře, se nemá co ptát serveru.
        if (folder == ChatFolder.Archive) {
            loadMore()
            // Nastavení archivu se čte hned, ne až při otevření sheetu: prázdný sheet, který se
            // teprve plní, by problikl — a když ho TDLib nevydá, tlačítko se vůbec nenabídne.
            viewModelScope.launch { _archiveSettings.value = repository.archiveSettings().getOrNull() }
        }
        // Obnovená složka (zabití procesu) se musí načíst stejně jako nově vybraná.
        _selectedFolderId.value?.let { id -> viewModelScope.launch { repository.loadMore(ChatFolder.Custom(id)) } }
        // Účet potřebuje jen hlavička hlavního seznamu. Selhání nevadí — místo fotky jsou iniciály.
        if (folder == ChatFolder.Main) {
            // Živě: po změně fotky v úpravě profilu nesmí pilulka ukazovat tu starou.
            viewModelScope.launch {
                accounts.account.collect { account -> if (account != null) _account.value = account }
            }
        }
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

    /**
     * Uložené zprávy, které seznam schovává — ve složce Telegramu žádné: tam je uživatel zařadil
     * sám, takže je chce vidět i nepřipnuté.
     */
    val hiddenSavedMessagesChatId: StateFlow<Long?> =
        combine(repository.savedMessagesChatId, shownFolder) { id, shown -> id.takeUnless { shown is ChatFolder.Custom } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, repository.savedMessagesChatId.value)

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

    /**
     * Nové přihlášení, které uživatel ještě neuznal — karta „Byli jste to vy?" nahoře v seznamu,
     * jako v oficiálním klientu. V Nastavení by ji nikdo nehledal.
     */
    val unconfirmedLogin: StateFlow<UnconfirmedDevice?> = devices.unconfirmed

    fun confirmLogin(sessionId: Long) = resolveLogin { devices.confirm(sessionId) }

    fun terminateLogin(sessionId: Long) = resolveLogin { devices.terminate(sessionId) }

    private fun resolveLogin(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action().onFailure { failure ->
                logger.w(failure) { "Nepotvrzené přihlášení se nepodařilo vyřídit" }
                _actions.update { it.copy(error = failure.userMessage(Res.string.chats_action_failed)) }
            }
        }
    }

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
        viewModelScope.launch { repository.loadMore(shownFolder.value) }
    }

    fun showActions(chat: ChatSummary) {
        _actions.value = ChatActionsState(target = chat)

        /*
         * Blokování a nahlášení se zjišťují až tady, jako práva u nabídky nad zprávou: jsou to
         * dotazy na server a ptát se dopředu by znamenalo dotaz na každý chat v seznamu kvůli
         * nabídce, kterou uživatel skoro nikdy neotevře. Dokud odpověď nedorazí, položka se
         * nekreslí — objevení ruší míň než zmizení.
         */
        viewModelScope.launch {
            val spam = repository.spamActions(chat.id).getOrElse { failure ->
                // Nezjištěno = nenabízet. Akce, která skončí chybou od serveru, je horší než žádná.
                logger.w(failure) { "Volby proti spamu u chatu ${chat.id} se nepodařilo zjistit." }
                SpamActions()
            }
            // Nabídka se mezitím mohla zavřít, nebo už patřit jinému chatu.
            _actions.update { if (it.target?.id == chat.id) it.copy(spam = spam) else it }
        }
        // Složky, stejně jako spam, až na dotaz: `Chat.chatLists` a kam chat přidat jde.
        if (folderRepository != null && repository.chatFolders.value.folders.isNotEmpty()) {
            viewModelScope.launch {
                folderRepository.membership(chat.id)
                    .onSuccess { membership -> _actions.update { if (it.target?.id == chat.id) it.copy(folders = membership) else it } }
                    .onFailure { failure -> logger.w(failure) { "Složky chatu ${chat.id} se nepodařilo zjistit." } }
            }
        }
    }

    /**
     * Chat do složky, nebo ven. Nabídka zůstává otevřená a zaškrtnutí se přepne hned —
     * přidává se často do víc složek po sobě; při selhání se vrátí.
     */
    fun toggleFolder(chat: ChatSummary, folderId: Int) {
        val repo = folderRepository ?: return
        val before = _actions.value.folders ?: return
        val member = folderId in before.member
        _actions.update { it.copy(folders = before.copy(member = if (member) before.member - folderId else before.member + folderId)) }
        viewModelScope.launch {
            val result = if (member) repo.removeChat(folderId, chat.id) else repo.addChat(folderId, chat.id)
            result.onFailure { failure ->
                logger.w(failure) { "Chat ${chat.id} se nepodařilo ${if (member) "odebrat ze" else "přidat do"} složky $folderId." }
                _actions.update {
                    it.copy(
                        folders = if (it.target?.id == chat.id) before else it.folders,
                        error = failure.userMessage(Res.string.chats_action_failed),
                    )
                }
            }
        }
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
        val archived = !chat.isArchived
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

    /** Ztlumení na dobu; `null` = navždy. */
    fun mute(chat: ChatSummary, duration: Duration?) = run(chat.id) {
        if (duration == null) repository.setMuted(chat.id, true) else repository.muteFor(chat.id, duration)
    }

    fun unmute(chat: ChatSummary) = run(chat.id) {
        repository.setMuted(chat.id, false)
    }

    fun toggleUnread(chat: ChatSummary) = run(chat.id) {
        repository.setMarkedAsUnread(chat.id, !chat.hasUnread)
    }

    /**
     * Zablokuje protějšek, nebo blokování zruší.
     *
     * **Chat v seznamu zůstává** — blokování a mazání jsou dvě různé věci; kdo chce obojí, zapne
     * si zablokování v podmenu smazání ([delete]). Právě proto se to musí ohlásit
     * snackbarem: v seznamu není nic, z čeho by se zablokování poznalo.
     *
     * Bez potvrzovacího dialogu: je to vratné a „Vrátit" ve snackbaru je lepší než dialog, který
     * před vratnou akcí jen zdržuje (stejně jako u archivace a odepnutí zprávy).
     */
    fun setBlocked(chat: ChatSummary, blocked: Boolean) = run(chat.id, onSuccess = {
        _actions.update { it.copy(blocked = BlockChange(chat.id, blocked)) }
    }) {
        repository.setBlocked(chat.id, blocked)
    }

    /** Snackbar s „Vrátit" už je vidět — nuluje se hned, jinak by se po otočení ukázal znovu. */
    fun consumeBlocked() {
        _actions.update { it.copy(blocked = null) }
    }

    /** „Vrátit" ve snackbaru. Oprava omylu, takže se na ni už další „Vrátit" nenabízí. */
    fun undoBlock(change: BlockChange) = run(change.chatId) {
        repository.setBlocked(change.chatId, !change.blocked)
    }

    /** Samostatné nahlášení z nabídky nad chatem, bez mazání. */
    fun report(chat: ChatSummary) {
        _actions.value = ChatActionsState()
        reportFlow.start(chat.id)
    }

    /**
     * Smazání z podmenu v nabídce — nevratnou akci uživatel potvrdil tím, že podmenu rozbalil
     * a ťukl na tlačítko v něm. S volbou „nahlásit" se napřed projde průvodce a teprve jeho
     * úspěch spustí zablokování a smazání; pořadí a chování po chybě drží [cleanUpSpam].
     */
    fun delete(chat: ChatSummary, cleanup: SpamCleanup = SpamCleanup()) {
        _actions.value = ChatActionsState()
        reportFlow.thenAfterReport(cleanup.report, chat.id) {
            cleanUpSpam(
                cleanup = cleanup,
                block = { repository.setBlocked(chat.id, true) },
                delete = { repository.deleteOrLeave(chat.id) },
            ).onFailure { failure ->
                logger.w(failure) { "Smazání chatu ${chat.id} selhalo." }
                _actions.value = ChatActionsState(error = failure.userMessage(Res.string.chats_action_failed))
            }
        }
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
 * Nabídka akcí nad jedním chatem; [target] je otevřený bottom sheet. Smazání se potvrzuje
 * v podmenu téhož sheetu, žádný další stav k tomu není.
 */
data class ChatActionsState(
    val target: ChatSummary? = null,
    val error: UiText? = null,
    /** Jednorázová událost pro snackbar s „Vrátit" po archivaci nebo vrácení z archivu. */
    val archived: ArchiveChange? = null,
    /** Jednorázová událost pro snackbar s „Vrátit" po zablokování nebo odblokování. */
    val blocked: BlockChange? = null,
    /** Co proti spamu nabídnout chatu v [target]; `null`, dokud to nedorazí ze serveru. */
    val spam: SpamActions? = null,
    /** Složky, ve kterých chat je a kam jde přidat; `null` = nenabízet (nezjištěno, žádné složky). */
    val folders: ChatFolderMembership? = null,
)

/** Chat se přesunul do archivu ([archived]), nebo z něj. */
data class ArchiveChange(val chatId: Long, val archived: Boolean)

/** Protějšek chatu se zablokoval ([blocked]), nebo odblokoval. */
data class BlockChange(val chatId: Long, val blocked: Boolean)

/** Nepřečteno = nepřečtené zprávy NEBO ruční příznak. Obojí se v seznamu chová stejně. */
val ChatSummary.hasUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread

/** Klíč filtru v [SavedStateHandle] seznamu chatů. */
private const val FILTER_KEY = "filter"

/** Klíč vybrané složky Telegramu v [SavedStateHandle]. */
private const val FOLDER_KEY = "folder"
