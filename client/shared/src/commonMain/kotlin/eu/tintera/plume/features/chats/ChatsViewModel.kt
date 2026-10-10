package eu.tintera.plume.features.chats

import kotlinx.coroutines.flow.flowOf
import eu.tintera.plume.core.telegram.ChatDraft
import eu.tintera.plume.core.telegram.TopicDrafts
import eu.tintera.plume.core.telegram.AccountRepository
import kotlin.time.Duration
import co.touchlab.kermit.Logger
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.snooze.ChatSnoozeState
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.core.snooze.SnoozeOption
import eu.tintera.plume.core.snooze.snoozeUntil
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import eu.tintera.plume.core.settings.ChatSwipeActions
import eu.tintera.plume.core.telegram.ArchiveSettings
import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.core.telegram.DraftRestore
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatFolders
import eu.tintera.plume.core.telegram.ChatFolderMembership
import eu.tintera.plume.core.telegram.ChatFolderRepository
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.RecommendedChatFolder
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_failed
import eu.tintera.plume.resources.chat_folders_failed

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
    /** Vybraná složka přežije zabití procesu v pozadí; nové spuštění začne na všech chatech. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** „Přidat do složky" v nabídce nad chatem; bez něj se položka nenabízí. */
    private val folderRepository: ChatFolderRepository? = null,
    /** Koncepty v tématech skupin do přehledu konceptů; bez nich jen koncepty chatů. */
    private val topicDrafts: TopicDrafts? = null,
    /** Odložení chatů; bez něj se „Odložit" nenabízí. */
    private val snoozes: ChatSnoozes? = null,
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

    private val _newFolderChats = MutableStateFlow<NewFolderChats?>(null)

    /**
     * Chaty, které autor sdílené složky přidal po tom, co si ji uživatel přidal — karta nad
     * seznamem vybrané složky. Ptá se jen u PŘIDANÉ sdílené složky (vlastní odkazy nemá);
     * vlastní složku nikdo jiný nemění a dotaz by byl k ničemu.
     */
    val newFolderChats: StateFlow<NewFolderChats?> = _newFolderChats.asStateFlow()

    init {
        if (folder == ChatFolder.Main && folderRepository != null) {
            viewModelScope.launch {
                combine(_selectedFolderId, repository.chatFolders) { selected, folders ->
                    folders.folders.firstOrNull { it.id == selected }
                        ?.takeIf { it.isShareable && !it.hasMyInviteLinks }
                }.distinctUntilChangedBy { it?.id }.collectLatest { tab ->
                    _newFolderChats.value = null
                    tab ?: return@collectLatest
                    folderRepository.newChats(tab.id)
                        .onSuccess { ids ->
                            if (ids.isEmpty()) return@onSuccess
                            val chats = repository.summaries(ids)
                            _newFolderChats.value = NewFolderChats(
                                folderId = tab.id,
                                title = tab.title,
                                titleEntities = tab.titleEntities,
                                iconName = tab.iconName,
                                chats = chats,
                                selected = chats.mapTo(LinkedHashSet()) { it.id },
                            )
                        }
                        .onFailure { failure -> logger.w(failure) { "Nové chaty složky ${tab.id} se nepodařilo zjistit." } }
                }
            }
        }
    }

    fun openNewFolderChats() = _newFolderChats.update { it?.copy(sheetOpen = true) }
    fun closeNewFolderChats() = _newFolderChats.update { it?.copy(sheetOpen = false) }

    fun toggleNewFolderChat(chatId: Long) {
        _newFolderChats.update { state ->
            state?.takeUnless { it.busy }?.copy(
                selected = if (chatId in state.selected) state.selected - chatId else state.selected + chatId,
            ) ?: state
        }
    }

    /** Připojí se k vybraným novým chatům (prázdný výběr = „Skrýt": nové chaty se vezmou jako viděné). */
    fun processNewFolderChats(join: Boolean) {
        val state = _newFolderChats.value?.takeUnless { it.busy } ?: return
        val repo = folderRepository ?: return
        _newFolderChats.value = state.copy(busy = true)
        viewModelScope.launch {
            repo.processNewChats(state.folderId, if (join) state.chats.map { it.id }.filter { it in state.selected } else emptyList())
                .onSuccess { _newFolderChats.value = null }
                .onFailure { failure ->
                    logger.w(failure) { "Nové chaty složky ${state.folderId} se nepodařilo vyřídit." }
                    _newFolderChats.value = state.copy(busy = false)
                    _actions.update { it.copy(error = failure.userMessage(Res.string.chats_action_failed)) }
                }
        }
    }

    /**
     * Vybere složku Telegramu ([id]), nebo celý seznam (`null`). Žije jako filtr: přežije návrat
     * z konverzace i zabití procesu, nové spuštění začne na všech chatech.
     */
    fun selectFolder(id: Int?) {
        // Výběr patří složce, ve které vznikl; v jiné by ukazoval na chaty, které nejsou vidět.
        _selection.value = null
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

    private val _folderSuggestions = MutableStateFlow<FolderSuggestions?>(null)

    /**
     * Sheet za „+" na konci řádku složek: doporučené složky od Telegramu a cesta do správy složek.
     * `null` = zavřený. Doporučené se čtou při každém otevření — server je počítá podle toho, jaké
     * složky už uživatel má, a přidaná z nabídky zmizí.
     */
    val folderSuggestions: StateFlow<FolderSuggestions?> = _folderSuggestions.asStateFlow()

    fun openFolderSuggestions() {
        val repo = folderRepository ?: return
        _folderSuggestions.value = FolderSuggestions()
        viewModelScope.launch {
            val recommended = repo.recommended().getOrElse { failure ->
                // Bez doporučených zůstane v sheetu nová složka a správa — slepá ulička to není.
                logger.w(failure) { "Doporučené složky se nepodařilo načíst." }
                emptyList()
            }
            _folderSuggestions.update { it?.copy(loading = false, recommended = recommended) }
        }
    }

    fun closeFolderSuggestions() {
        _folderSuggestions.value = null
    }

    /**
     * Založí doporučenou složku a rovnou ji vybere — kdo si „Nepřečtené" přidá, chce je vidět,
     * ne hledat v řádku. Server ji do řádku pošle sám (`UpdateChatFolders`); výběr se uloží hned,
     * a dokud složka v řádku není, platí celý seznam ([selectedFolderId]).
     */
    fun addRecommendedFolder(recommended: RecommendedChatFolder) {
        val repo = folderRepository ?: return
        val state = _folderSuggestions.value?.takeIf { it.adding == null } ?: return
        _folderSuggestions.value = state.copy(adding = recommended.draft.title)
        viewModelScope.launch {
            repo.create(recommended.draft)
                .onSuccess { id ->
                    _folderSuggestions.value = null
                    selectFolder(id)
                }
                .onFailure { failure ->
                    logger.w(failure) { "Doporučenou složku se nepodařilo založit." }
                    _folderSuggestions.update { it?.copy(adding = null) }
                    _actions.update { it.copy(error = failure.userMessage(Res.string.chat_folders_failed)) }
                }
        }
    }

    private val _account = MutableStateFlow<AccountInfo?>(null)

    /** Přihlášený účet — jeho fotka je v hledání vstupem do menu a Nastavení. */
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    /**
     * Nepřečtené chaty v archivu — odznak u vstupu do archivu v menu účtu. Počítá TDLib, takže
     * to platí i pro archiv, který uživatel v téhle relaci ještě neotevřel.
     */
    val archiveUnread: StateFlow<ArchiveUnread> = repository.archiveUnread

    /**
     * Chaty s rozepsanou zprávou — přehled konceptů z menu účtu. `null`, dokud se nenačtou:
     * prázdný seznam znamená „žádné koncepty" a přehled se při něm zavře.
     */
    val drafts: StateFlow<List<DraftItem>?> = combine(
        repository.drafts,
        topicDrafts?.drafts ?: flowOf(emptyList()),
    ) { chats, topics ->
        (chats.mapNotNull { chat -> chat.draft?.let { DraftItem(chat, topic = null, draft = it) } } +
            topics.map { DraftItem(it.chat, DraftTopic(it.topicId, it.topicName), it.draft) })
            .sortedByDescending { it.draft.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _draftDeleted = MutableStateFlow<DraftDeleted?>(null)

    /** Koncept smazaný z přehledu — přehled ukáže snackbar s „Vrátit" a událost vynuluje. */
    val draftDeleted: StateFlow<DraftDeleted?> = _draftDeleted.asStateFlow()

    /**
     * Smaže koncept z přehledu. Ve `viewModelScope`: zavřený přehled nesmí smazání přerušit.
     * Výsledek se ohlásí VŽDY, i bez čeho vracet — přehled podle něj pozná, že smazání doběhlo.
     */
    fun deleteDraft(item: DraftItem) {
        val chatId = item.chat.id
        viewModelScope.launch {
            val topicId = item.topic?.id
            val deleted = if (topicId == null) {
                repository.deleteDraft(chatId)
            } else {
                topicDrafts?.delete(chatId, topicId) ?: Result.success(null)
            }
            val restore = deleted
                .onFailure {
                    logger.w(it) { "Smazání konceptu v chatu $chatId (téma $topicId) selhalo." }
                    reportFailure(it)
                }
                .getOrNull()
            _draftDeleted.value = DraftDeleted(chatId, restore)
        }
    }

    fun consumeDraftDeleted() {
        _draftDeleted.value = null
    }

    /** „Vrátit" ve snackbaru přehledu konceptů. */
    fun undoDeleteDraft(deleted: DraftDeleted) {
        val restore = deleted.restore ?: return
        viewModelScope.launch {
            restore.restore().onFailure {
                logger.w(it) { "Vrácení konceptu v chatu ${deleted.chatId} selhalo." }
                reportFailure(it)
            }
        }
    }

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

    /** Odložené chaty: kdo spí a do kdy, kdo se už vynořil (viz [ChatSnoozes]). */
    val snoozeState: StateFlow<ChatSnoozeState> =
        snoozes?.state?.stateIn(viewModelScope, SharingStarted.Eagerly, ChatSnoozeState())
            ?: MutableStateFlow(ChatSnoozeState())

    private val _snoozePicker = MutableStateFlow<ChatSummary?>(null)

    /** Chat, kterému se po tahu vybírá doba odložení; `null` = nabídka zavřená. */
    val snoozePicker: StateFlow<ChatSummary?> = _snoozePicker.asStateFlow()

    fun closeSnoozePicker() {
        _snoozePicker.value = null
    }

    /** Nabízí se „Odložit"? */
    val canSnooze: Boolean get() = snoozes != null

    /** Odloží chat na [option] a zavře nabídku. */
    fun snooze(chat: ChatSummary, option: SnoozeOption) {
        val snoozes = snoozes ?: return
        snoozes.snooze(chat.id, snoozeUntil(option, Clock.System.now(), TimeZone.currentSystemDefault()), chat.title)
        hideActions()
        closeSnoozePicker()
    }

    /** Zruší odložení a zavře nabídku. */
    fun unsnooze(chat: ChatSummary) {
        snoozes?.clear(chat.id)
        hideActions()
    }

    /** Chat otevřený ze seznamu: vynořená připomínka splnila účel. */
    fun opened(chatId: Long) {
        snoozes?.opened(chatId)
    }

    /** Co dělá tah chatem doprava a doleva (Nastavení → Obecné → Chaty). */
    val swipeActions: StateFlow<ChatSwipeActions> = settings.chatSwipeActions

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

    private val _reorder = MutableStateFlow<PinnedReorder?>(null)

    /** Režim uspořádání připnutých chatů; `null` = seznam jako obvykle. */
    val reorder: StateFlow<PinnedReorder?> = _reorder.asStateFlow()

    /**
     * „Změnit pořadí připnutých" z nabídky nad připnutým chatem. Začíná pořadím, které seznam
     * ukazuje; nabídku zavře.
     */
    fun startReorder() {
        _actions.value = ChatActionsState()
        val chats = (state.value as? ChatListState.Ready)?.chats ?: return
        val pinned = pinnedChats(chats)
        if (pinned.size < 2) return
        _reorder.value = PinnedReorder(shownFolder.value, pinned)
    }

    fun moveInReorder(fromId: Long, toId: Long) =
        _reorder.update { it?.copy(chats = it.chats.movedChat(fromId, toId)) }

    fun cancelReorder() {
        _reorder.value = null
    }

    /**
     * Odešle nové pořadí jedním voláním (`setPinnedChats`). Beze změny se nic neposílá. Seznam
     * se pak přeskládá sám — nové pořadí přijde updaty pozic, na rozdíl od témat (tam `order`
     * pořadí připnutých nenese). Když uložení selže, režim zůstane otevřený, ať se dá zkusit znovu.
     */
    fun saveReorder() {
        val reorder = _reorder.value?.takeUnless { it.busy } ?: return
        val chats = (state.value as? ChatListState.Ready)?.chats ?: return
        val current = pinnedChats(chats).map { it.id }
        val ids = pinnedOrderToSave(reorder.chats.map { it.id }, current)
        if (ids == current) {
            cancelReorder()
            return
        }
        _reorder.update { it?.copy(busy = true) }
        viewModelScope.launch {
            repository.setPinnedOrder(reorder.folder, ids)
                .onSuccess { _reorder.value = null }
                .onFailure { failure ->
                    logger.w(failure) { "Pořadí připnutých chatů se nepodařilo uložit." }
                    _reorder.update { it?.copy(busy = false) }
                    _actions.update { it.copy(error = failure.userMessage(Res.string.chats_action_failed)) }
                }
        }
    }

    /**
     * Do archivu a zpátky. Vratné, takže bez potvrzení — místo něj snackbar s „Vrátit"
     * ([ChatActionsState.archived]), jako u odepnutí zprávy.
     */
    fun toggleArchived(chat: ChatSummary) = setArchived(chat, !chat.isArchived)

    private fun setArchived(chat: ChatSummary, archived: Boolean) {
        run(chat.id, onSuccess = {
            _actions.update { it.copy(archived = ArchiveChange(chat.id, archived)) }
        }) {
            repository.setArchived(chat.id, archived)
        }
    }

    /**
     * Tah chatem do strany. Provede se přesně [effect], ne přepnutí podle [chat]: efekt se
     * počítá z čerstvého stavu při tahu, kdežto chat může být z dřívějšího snímku. Ztlumení
     * tahem je natrvalo, jako z lišty výběru — doba se tahem zvolit nedá a zrušit jde týmž tahem.
     */
    internal fun swipe(chat: ChatSummary, effect: ChatSwipeEffect) {
        when (effect) {
            ChatSwipeEffect.Archive -> setArchived(chat, archived = true)
            ChatSwipeEffect.Unarchive -> setArchived(chat, archived = false)
            ChatSwipeEffect.MarkRead -> run(chat.id) { repository.readChat(chat.id) }
            ChatSwipeEffect.MarkUnread -> run(chat.id) { repository.setMarkedAsUnread(chat.id, true) }
            ChatSwipeEffect.Mute -> mute(chat, duration = null)
            ChatSwipeEffect.Unmute -> unmute(chat)
            ChatSwipeEffect.Pin -> run(chat.id) { repository.setPinned(chat.id, chat.folder, true) }
            ChatSwipeEffect.Unpin -> run(chat.id) { repository.setPinned(chat.id, chat.folder, false) }
            ChatSwipeEffect.Snooze -> _snoozePicker.value = chat
            ChatSwipeEffect.Unsnooze -> unsnooze(chat)
        }
    }

    /** Snackbar s „Vrátit" už je vidět — událost se nuluje hned, jinak by se po otočení ukázal znovu. */
    fun consumeArchived() {
        _actions.update { it.copy(archived = null) }
    }

    /** „Vrátit" ve snackbaru. Oprava omylu, takže se na ni už další „Vrátit" nenabízí. */
    fun undoArchive(change: ArchiveChange) {
        _actions.value = ChatActionsState()
        viewModelScope.launch {
            val failure = change.chatIds.firstNotNullOfOrNull { id ->
                repository.setArchived(id, !change.archived).exceptionOrNull()
            }
            failure?.let {
                logger.w(it) { "Vrácení archivace chatů ${change.chatIds} selhalo." }
                reportFailure(it)
            }
        }
    }

    /** Ztlumení na dobu; `null` = navždy. */
    fun mute(chat: ChatSummary, duration: Duration?) = run(chat.id) {
        if (duration == null) repository.setMuted(chat.id, true) else repository.muteFor(chat.id, duration)
    }

    fun unmute(chat: ChatSummary) = run(chat.id) {
        repository.setMuted(chat.id, false)
    }

    /**
     * Přečtené přes [ChatRepository.readChat], ne shozením ručního příznaku: ten nechá
     * nepřečtené zprávy, jak jsou, a u chatu s nimi se po „Označit jako přečtené" nic nezměnilo.
     */
    fun toggleUnread(chat: ChatSummary) = run(chat.id) {
        if (chat.hasUnread) repository.readChat(chat.id) else repository.setMarkedAsUnread(chat.id, true)
    }

    private val _selection = MutableStateFlow<ChatSelection?>(null)

    /** Výběr víc chatů; `null` = seznam jako obvykle. */
    val selection: StateFlow<ChatSelection?> = _selection.asStateFlow()

    /** Co lišta výběru nabídne; `null` mimo výběr. */
    val selectionActions: StateFlow<ChatSelectionActions?> =
        combine(_selection, state, repository.savedMessagesChatId) { selection, state, savedId ->
            selection ?: return@combine null
            chatSelectionActions(selectedChats(selection, state), savedId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Chat, který ze seznamu odešel (archivace jinde, opuštěná skupina), z výběru vypadne;
        // poslední odchozí výběr ukončí.
        viewModelScope.launch {
            state.collect { current ->
                val chats = (current as? ChatListState.Ready)?.chats ?: return@collect
                _selection.update { it?.within(chats) }
            }
        }
    }

    /** „Vybrat" z nabídky nad chatem: zavře nabídku a začne výběr tímhle chatem. */
    fun startSelection(chat: ChatSummary) {
        _actions.value = ChatActionsState()
        if (chat.isSelectable()) _selection.value = ChatSelection(setOf(chat.id))
    }

    fun toggleSelected(chat: ChatSummary) {
        if (!chat.isSelectable()) return
        _selection.update { it?.toggled(chat.id) }
    }

    fun closeSelection() {
        _selection.value = null
    }

    fun archiveSelected() {
        val (actions, selected) = selectionNow() ?: return
        if (!actions.canArchive) return
        runOnSelection(actions.archiveTargets(selected), onDone = { done ->
            _actions.update { it.copy(archived = ArchiveChange(done, actions.archive)) }
        }) { repository.setArchived(it.id, actions.archive) }
    }

    /** Ztlumení z výběru je natrvalo, jako „Ztlumit" bez doby; zrušit jde stejnou lištou. */
    fun muteSelected() {
        val (actions, selected) = selectionNow() ?: return
        runOnSelection(actions.muteTargets(selected)) { repository.setMuted(it.id, actions.mute) }
    }

    fun readSelected() {
        val (actions, selected) = selectionNow() ?: return
        runOnSelection(actions.readTargets(selected)) {
            if (actions.read) repository.readChat(it.id) else repository.setMarkedAsUnread(it.id, true)
        }
    }

    private fun selectionNow(): Pair<ChatSelectionActions, List<ChatSummary>>? {
        val selection = _selection.value ?: return null
        val selected = selectedChats(selection, state.value)
        return chatSelectionActions(selected, repository.savedMessagesChatId.value) to selected
    }

    private fun selectedChats(selection: ChatSelection, state: ChatListState): List<ChatSummary> =
        (state as? ChatListState.Ready)?.chats?.filter { it.id in selection.ids }.orEmpty()

    /**
     * Akce nad celým výběrem: výběr se ukončí hned (jako po akci v Gmailu), chaty jdou postupně
     * a selhání jednoho ostatní nezastaví — co prošlo, prošlo, a chyba se ohlásí na konci.
     * Stav se lokálně nepřepisuje, stejně jako u [run].
     */
    private fun runOnSelection(
        targets: List<ChatSummary>,
        onDone: (List<Long>) -> Unit = {},
        action: suspend (ChatSummary) -> Result<Unit>,
    ) {
        _selection.value = null
        _actions.value = ChatActionsState()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            var failure: Throwable? = null
            val done = targets.mapNotNull { chat ->
                action(chat).fold(
                    onSuccess = { chat.id },
                    onFailure = { failure = failure ?: it; null },
                )
            }
            if (done.isNotEmpty()) onDone(done)
            failure?.let {
                logger.w(it) { "Akce nad vybranými chaty selhala." }
                reportFailure(it)
            }
        }
    }

    private fun reportFailure(failure: Throwable) {
        _actions.update { it.copy(error = failure.userMessage(Res.string.chats_action_failed)) }
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

/** Chaty se přesunuly do archivu ([archived]), nebo z něj — jeden z nabídky, víc z výběru. */
data class ArchiveChange(val chatIds: List<Long>, val archived: Boolean) {
    constructor(chatId: Long, archived: Boolean) : this(listOf(chatId), archived)
}

/** Protějšek chatu se zablokoval ([blocked]), nebo odblokoval. */
data class BlockChange(val chatId: Long, val blocked: Boolean)

/** Nepřečteno = nepřečtené zprávy NEBO ruční příznak. Obojí se v seznamu chová stejně. */
val ChatSummary.hasUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread

/** Klíč filtru v [SavedStateHandle] seznamu chatů. */

/** Klíč vybrané složky Telegramu v [SavedStateHandle]. */
private const val FOLDER_KEY = "folder"

/** Nové chaty v přidané sdílené složce a jestli je otevřený sheet s nimi. */
data class NewFolderChats(
    val folderId: Int,
    val title: String,
    val titleEntities: List<MessageEntity>,
    val iconName: String?,
    val chats: List<ChatSummary>,
    val selected: Set<Long>,
    val sheetOpen: Boolean = false,
    val busy: Boolean = false,
)

/**
 * Doběhlé smazání konceptu z přehledu a jak ho vrátit; [restore] `null` = smazání selhalo, nebo
 * koncept mezitím zmizel sám. Instance je událost — dvě smazání se neslijí.
 */
class DraftDeleted(val chatId: Long, val restore: DraftRestore?)

/** Položka přehledu konceptů: koncept celého chatu ([topic] `null`), nebo tématu ve skupině. */
data class DraftItem(val chat: ChatSummary, val topic: DraftTopic?, val draft: ChatDraft) {
    /** Klíč do seznamu: chat i téma, jeden chat může mít koncepty ve víc tématech. */
    val key: String get() = "${chat.id}:${topic?.id ?: ""}"
}

data class DraftTopic(val id: Int, val name: String)
