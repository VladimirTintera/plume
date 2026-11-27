package eu.tintera.plume.features.chats

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.SharedChats
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.isPrivate
import eu.tintera.plume.resources.chat_folder_delete_failed
import eu.tintera.plume.core.settings.StoredPhotoDescription
import eu.tintera.plume.features.media.PhotoDescriptions
import kotlinx.coroutines.flow.mapLatest
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import eu.tintera.plume.core.chatcolor.ChatColors
import eu.tintera.plume.core.chatlabels.ChatLabel
import eu.tintera.plume.core.chatlabels.ChatLabels
import eu.tintera.plume.core.settings.ThemeColor
import kotlinx.coroutines.flow.flowOf
import eu.tintera.plume.core.telegram.ChatDraft
import eu.tintera.plume.resources.clear_history_period_done
import eu.tintera.plume.resources.clear_history_failed
import eu.tintera.plume.resources.clear_history_done
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ClearHistoryRights
import eu.tintera.plume.core.telegram.TopicDrafts
import eu.tintera.plume.core.drafts.CommentDrafts
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
import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.StoryListKind
import eu.tintera.plume.core.telegram.StoryPostCheck
import eu.tintera.plume.core.telegram.StoryPostResult
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.resources.story_photo_only
import eu.tintera.plume.resources.story_posted
import eu.tintera.plume.features.stories.storyListOrder
import kotlinx.coroutines.launch
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_failed
import eu.tintera.plume.core.telegram.AccountFreeze
import eu.tintera.plume.core.telegram.CloseBirthday
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.displayPhone
import eu.tintera.plume.resources.suggestion_failed
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
    /** Koncepty ve vláknech komentářů do přehledu konceptů ([CommentDrafts]). */
    private val commentDrafts: CommentDrafts? = null,
    /** Odložení chatů; bez něj se „Odložit" nenabízí. */
    private val snoozes: ChatSnoozes? = null,
    /** Barvy konverzací; bez nich mají všechny odznaky barvu aplikace. */
    private val chatColorStore: ChatColors? = null,
    /** Vlastní štítky chatů; bez nich řádky štítky nemají a filtr není. */
    private val labelStore: ChatLabels? = null,
    /** Zmrazení účtu a doporučené akce pro karty nahoře v seznamu; bez něj karty nejsou. */
    private val accountNotices: AccountNotices? = null,
    /** Formát telefonního čísla v doporučené akci; bez něj číslo zůstane bez mezer. */
    private val session: TelegramSession? = null,
    /** Odznak „čeká na odpověď" (model v zařízení); bez něj se nepočítá. */
    private val triage: ChatTriage? = null,
    /** Záložky zpráv přes všechny chaty (menu účtu → Záložky); bez nich položka není. */
    private val bookmarks: MessageBookmarks? = null,
    /** Příběhy: kroužky kolem avatarů, hromádka v pilulce a nový příběh; bez nich nic z toho. */
    private val stories: StoryRepository? = null,
    /** Smazání s „Vrátit" ([PendingChatDeletes]); bez něj se maže hned. */
    private val pendingDeletes: PendingChatDeletes? = null,
    /** „Vymazat historii" v nabídce nad chatem; bez něj se nenabízí. */
    private val history: ConversationRepository? = null,
    /** „Přidat na plochu" v nabídce nad chatem; bez něj (a mimo Android) se nenabízí. */
    private val shareTargets: ShareTargets? = null,
    /** Účet seznamu, pro přenos přečtení spojené společné skupiny ([sharedChats]). */
    private val slot: AccountSlot? = null,
    /** „Označit jako přečtené" u společné skupiny přečte i ostatní účty v ní; bez něj jen tenhle. */
    private val sharedChats: SharedChats? = null,
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
    val state: StateFlow<ChatListState> = combine(
        shownFolder.flatMapLatest { repository.chats(it) },
        pendingDeletes?.pending ?: flowOf(emptySet()),
    ) { state, pending -> state.without(pending) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.chats(shownFolder.value).value)

    /**
     * Chaty čekající na odpověď (chat → id poslední zprávy, nad kterou to model řekl; viz
     * [ChatTriage]). Odznak patří řádku, jen když id sedí s jeho poslední zprávou a chat je
     * nepřečtený. Vypnutá volba schová všechny hned, ne až s další zprávou.
     */
    val awaitingReply: StateFlow<Map<Long, Long>> =
        triage?.let { triage ->
            combine(triage.awaiting, settings.chatTriageEnabled, settings.onDeviceAiEnabled) { awaiting, on, ai ->
                if (on && ai) awaiting else emptyMap()
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
        } ?: MutableStateFlow(emptyMap())

    /** Chaty z karty „Čeká na tvou odpověď", které uživatel křížkem schoval: (chat, poslední zpráva). */
    private val hiddenAwaiting = MutableStateFlow<Set<Pair<Long, Long>>>(emptySet())

    /**
     * Karta „Čeká na tvou odpověď" nahoře v hlavním seznamu (`AwaitingReplyCard`): nepřečtené
     * chaty, nad jejichž poslední zprávou triáž řekla „čeká na odpověď", v pořadí seznamu.
     * Prázdná = karta není. Jen v hlavním seznamu bez vybrané složky — ve složce i v archivu
     * má uživatel výběr, o který si řekl, a karta by ho přebíjela.
     *
     * **Schovaná karta se vrátí, až přibude chat, který na ní nebyl** (nová otázka): schovávají
     * se dvojice (chat, zpráva), ne karta. Drží se v paměti procesu; po restartu se karta ukáže
     * znovu, stejně jako se znovu posoudí chaty.
     */
    val awaitingReplyCard: StateFlow<List<ChatSummary>> =
        if (folder != ChatFolder.Main) {
            MutableStateFlow(emptyList())
        } else {
            combine(state, awaitingReply, hiddenAwaiting, _selectedFolderId) { list, awaiting, hidden, selectedFolder ->
                if (selectedFolder != null) return@combine emptyList()
                awaitingReplyChats((list as? ChatListState.Ready)?.chats.orEmpty(), awaiting, hidden)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        }

    /** Křížek na kartě „Čeká na tvou odpověď": schovat, dokud nepřibude další chat. */
    fun hideAwaitingReply() {
        val shown = awaitingReplyCard.value.mapNotNull { chat -> chat.lastMessageId?.let { chat.id to it } }
        hiddenAwaiting.update { it + shown }
    }

    private val _newFolderChats = MutableStateFlow<NewFolderChats?>(null)

    /**
     * Chaty, které autor sdílené složky přidal po tom, co si ji uživatel přidal — karta nad
     * seznamem vybrané složky. Ptá se jen u PŘIDANÉ sdílené složky (vlastní odkazy nemá);
     * vlastní složku nikdo jiný nemění a dotaz by byl k ničemu.
     */
    val newFolderChats: StateFlow<NewFolderChats?> = _newFolderChats.asStateFlow()

    init {
        // Triáž běží nad tím, co seznam ukazuje: s každou změnou (nová zpráva, přečtení) se
        // zařadí nové kandidáty. Zapnutí volby i volba modelu se projeví hned — po startu se
        // model z nastavení načte až po prvním seznamu a do té doby triáž nemá na čem běžet.
        if (triage != null) {
            viewModelScope.launch {
                combine(
                    state,
                    repository.savedMessagesChatId,
                    settings.chatTriageEnabled,
                    settings.onDeviceAiEnabled,
                    settings.onDeviceAiModels,
                ) { list, savedId, _, _, _ ->
                    (list as? ChatListState.Ready)?.chats.orEmpty() to savedId
                }.collect { (chats, savedId) -> triage.check(chats, savedId) }
            }
        }
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
     * Obsah sheetu za „+" na konci řádku složek: doporučené složky od Telegramu. Jestli je sheet
     * otevřený, ví obrazovka (viz [ChatSheets]); `null` = ještě nenačteno. Doporučené se čtou při
     * každém otevření ([loadFolderSuggestions]) — server je počítá podle toho, jaké složky už
     * uživatel má, a přidaná z nabídky zmizí.
     */
    val folderSuggestions: StateFlow<FolderSuggestions?> = _folderSuggestions.asStateFlow()

    fun loadFolderSuggestions() {
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

    /**
     * Založí doporučenou složku a rovnou ji vybere — kdo si „Nepřečtené" přidá, chce je vidět,
     * ne hledat v řádku. Server ji do řádku pošle sám (`UpdateChatFolders`); výběr se uloží hned,
     * a dokud složka v řádku není, platí celý seznam ([selectedFolderId]).
     */
    /**
     * Podržení složky v liště → „Označit vše jako přečtené" (`readChatList`). [id] `null` je
     * hlavní seznam. Odznaky se přepočítají samy updaty; hláška jde jen při selhání.
     */
    fun readFolder(id: Int?) = readAll(if (id == null) ChatFolder.Main else ChatFolder.Custom(id))

    /**
     * „Smazat složku" z nabídky nad záložkou, po potvrzení. Sdílená se tu nenabízí (viz
     * `FolderTabActions`). Vybraná smazaná složka přestane platit sama (`selectedFolderId`)
     * a seznam spadne na všechny chaty.
     */
    fun deleteFolder(id: Int) {
        val repo = folderRepository ?: return
        viewModelScope.launch {
            repo.delete(id).onFailure { failure ->
                logger.w(failure) { "Složku $id se nepodařilo smazat." }
                _actions.update { it.copy(error = failure.userMessage(Res.string.chat_folder_delete_failed)) }
            }
        }
    }

    /** Archiv jako přečtený — ikona v liště archivu, ten lištu složek nemá. */
    fun readArchive() = readAll(ChatFolder.Archive)

    private fun readAll(folder: ChatFolder) {
        viewModelScope.launch {
            repository.readAll(folder).onFailure { failure ->
                logger.w(failure) { "Seznam $folder se nepodařilo označit jako přečtený." }
                _actions.update { it.copy(error = failure.userMessage(Res.string.chats_action_failed)) }
            }
        }
    }

    /** Založí doporučenou složku; [onAdded] (zavření sheetu) až po úspěchu, při chybě sheet zůstane. */
    fun addRecommendedFolder(recommended: RecommendedChatFolder, onAdded: () -> Unit) {
        val repo = folderRepository ?: return
        val state = _folderSuggestions.value?.takeIf { it.adding == null } ?: return
        _folderSuggestions.value = state.copy(adding = recommended.draft.title)
        viewModelScope.launch {
            repo.create(recommended.draft)
                .onSuccess { id ->
                    _folderSuggestions.update { it?.copy(adding = null) }
                    onAdded()
                    selectFolder(id)
                }
                .onFailure { failure ->
                    logger.w(failure) { "Doporučenou složku se nepodařilo založit." }
                    _folderSuggestions.update { it?.copy(adding = null) }
                    _actions.update { it.copy(error = failure.userMessage(Res.string.chat_folders_failed)) }
                }
        }
    }

    /** Aktivní příběhy po autorech — kroužek kolem avataru v řádku chatu. */
    val storyRings: StateFlow<Map<Long, ActiveStories>> = stories?.active ?: MutableStateFlow(emptyMap())

    /**
     * Autoři jednoho seznamu příběhů v pořadí od TDLib — hromádka v pilulce hledání (hlavní
     * seznam) a v liště archivu (skrytí autoři). Patří sem i kontakty, se kterými chat nemáme,
     * a ty jinde vidět nejsou.
     */
    val storyStack: StateFlow<List<ActiveStories>> = run {
        val list = when (folder) {
            ChatFolder.Main -> StoryListKind.Main
            ChatFolder.Archive -> StoryListKind.Archive
            else -> null
        }
        if (stories == null || list == null) {
            MutableStateFlow(emptyList())
        } else {
            stories.active
                .map { all -> storyListOrder(all.values, list) }
                // Hned s hodnotou: hromádka by jinak naskočila snímek po složení lišty.
                .stateIn(viewModelScope, SharingStarted.Eagerly, storyListOrder(stories.active.value.values, list))
        }
    }

    private val _storyNotice = MutableStateFlow<UiText?>(null)

    /** Hláška k vlastnímu příběhu pro snackbar (limit, zveřejněno, chyba); UI ji sundá [storyNoticeShown]. */
    val storyNotice: StateFlow<UiText?> = _storyNotice.asStateFlow()

    /** Jde se zveřejnit příběh? Jen pro hlavní seznam — jinde nabídka „+" není. */
    val canComposeStory: Boolean get() = stories != null && folder == ChatFolder.Main

    /**
     * Nový příběh: napřed se zeptá, jestli limit pustí (free účet jeden za den), a teprve pak
     * otevře výběr fotky ([openPicker]). Jinak by si uživatel příběh připravil zbytečně.
     */
    fun startStory(openPicker: () -> Unit) {
        val repository = stories ?: return
        viewModelScope.launch {
            when (val check = repository.canPost()) {
                StoryPostCheck.Allowed -> openPicker()
                is StoryPostCheck.Denied -> _storyNotice.value = check.reason
            }
        }
    }

    /** Z výběru přišlo něco, z čeho příběh nejde (video tam, kde ho platforma nepřevede — desktop). */
    fun storyNeedsPhoto() {
        _storyNotice.value = UiText(Res.string.story_photo_only)
    }

    fun storyError(error: UiText) {
        _storyNotice.value = error
    }

    fun storyNoticeShown() {
        _storyNotice.value = null
    }

    private val _account = MutableStateFlow<AccountInfo?>(null)

    /** Přihlášený účet — jeho fotka je v hledání vstupem do menu a Nastavení. */
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    /**
     * Nepřečtené chaty v archivu — odznak u vstupu do archivu v menu účtu. Počítá TDLib, takže
     * to platí i pro archiv, který uživatel v téhle relaci ještě neotevřel.
     */
    val archiveUnread: StateFlow<ArchiveUnread> = repository.archiveUnread

    /** Rozepsané zprávy — jejich počet u položky Koncepty v menu účtu; `null`, dokud se nenačtou. */
    val drafts: StateFlow<List<DraftItem>?> = draftItems(repository, topicDrafts, commentDrafts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Záložky přihlášeného účtu ze všech chatů, nejnovější první. */
    val allBookmarks: StateFlow<List<MessageBookmark>> = (bookmarks?.all ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Popsané fotky a názvy chatů pro hledání jsou v `ChatSearchViewModel`.

    private val _archiveSettings = MutableStateFlow<ArchiveSettings?>(null)

    /**
     * Nastavení archivu, `null` dokud (nebo když) ho TDLib nevydá — pak se tlačítko v hlavičce
     * archivu nenabízí. TDLib na jeho změnu update neposílá, takže je tohle jediný zdroj pravdy
     * a zápis se do něj promítne rovnou; při selhání se vrátí zpátky.
     */
    val archiveSettings: StateFlow<ArchiveSettings?> = _archiveSettings.asStateFlow()

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
            // Jak dopadlo nahrávání vlastního příběhu: obrazovka příběhu je už zavřená.
            stories?.let { storyRepository ->
                viewModelScope.launch {
                    storyRepository.postResults.collect { result ->
                        _storyNotice.value = when (result) {
                            StoryPostResult.Posted -> UiText(Res.string.story_posted)
                            is StoryPostResult.Failed -> result.reason
                        }
                    }
                }
            }
        }
    }

    /**
     * Stav spojení. Ve stavu seznamu schválně není — se seznamem chatů nijak nesouvisí a v
     * hlavičce se ukazuje i tehdy, když se seznam teprve načítá nebo skončil chybou.
     */
    val connection: StateFlow<ConnectionState> = connection.state

    /** Odložené chaty: kdo spí a do kdy, kdo se už vynořil (viz [ChatSnoozes]). */
    /** Chat → barva, kterou mu uživatel dal; odznak nepřečtených se kreslí v ní. */
    val chatColors: StateFlow<Map<Long, ThemeColor>> =
        chatColorStore?.colors?.stateIn(viewModelScope, SharingStarted.Eagerly, chatColorStore.current())
            ?: MutableStateFlow(emptyMap())

    /** Štítky účtu v pořadí (`ChatLabels`). */
    val labels: StateFlow<List<ChatLabel>> =
        labelStore?.labels?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()) ?: MutableStateFlow(emptyList())

    /** Chat → jeho štítky. */
    val chatLabels: StateFlow<Map<Long, List<ChatLabel>>> =
        labelStore?.assignments?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap()) ?: MutableStateFlow(emptyMap())

    private val _labelFilter = MutableStateFlow<String?>(null)

    /**
     * Vybraný štítek ve filtru nad seznamem; `null` = všechny chaty. Filtruje se v seznamu
     * (`ChatListContent`), ne ve [state]: prázdný výsledek nesmí vypadat jako prázdný účet
     * a filtr musí zůstat vidět, aby šel zrušit.
     */
    val labelFilter: StateFlow<String?> = _labelFilter

    /** Vybere štítek ve filtru; znovu ťuknutý (nebo `null`) filtr zruší. */
    fun setLabelFilter(id: String?) {
        _labelFilter.value = id.takeIf { it != _labelFilter.value }
    }

    val snoozeState: StateFlow<ChatSnoozeState> =
        snoozes?.state?.stateIn(viewModelScope, SharingStarted.Eagerly, ChatSnoozeState())
            ?: MutableStateFlow(ChatSnoozeState())

    /** Nabízí se „Odložit"? */
    val canSnooze: Boolean get() = snoozes != null

    /** Odloží chat na [option]. */
    fun snooze(chat: ChatSummary, option: SnoozeOption) {
        val snoozes = snoozes ?: return
        val until = snoozeUntil(option, Clock.System.now(), TimeZone.currentSystemDefault())
        snoozes.snooze(chat.id, until, chat.title)
        snoozesElsewhere(chat.id) { snooze(it, until, chat.title) }
    }

    /** Zruší odložení. */
    fun unsnooze(chat: ChatSummary) {
        snoozes?.clear(chat.id)
        snoozesElsewhere(chat.id) { clear(it) }
    }

    /** Chat otevřený ze seznamu: vynořená připomínka splnila účel. */
    fun opened(chatId: Long) {
        snoozes?.opened(chatId)
        // Spojený řádek by jinak zůstal nahoře kvůli připomínce v druhém účtu.
        snoozesElsewhere(chatId) { opened(it) }
    }

    /**
     * Odložení spojeného řádku i v ostatních účtech ([SharedChats.inOthers]): každý účet má
     * odložení vlastní (`ChatSnoozes` v kontejneru účtu), řádek je ale jeden.
     */
    private fun snoozesElsewhere(chatId: Long, change: ChatSnoozes.(Long) -> Unit) {
        val slot = slot ?: return
        if (snoozes == null) return
        sharedChats?.inOthers(slot, chatId) { account, id ->
            account.koin.getOrNull<ChatSnoozes>()?.change(id)
            Result.success(Unit)
        }
    }

    /** Co dělá tah chatem doprava a doleva (Nastavení → Chaty). */
    val swipeActions: StateFlow<ChatSwipeActions> = settings.chatSwipeActions

    // Hledání má vlastní ViewModel (`ChatSearchViewModel`), viz docs/navrh-hledani.md.

    /** Id Uložených zpráv — v seznamu nejsou, pokud nejsou připnuté (viz `chatGroups`). */
    val savedMessagesChatId: StateFlow<Long?> = repository.savedMessagesChatId

    /**
     * Uložené zprávy, které seznam schovává — ve složce Telegramu žádné: tam je uživatel zařadil
     * sám, takže je chce vidět i nepřipnuté.
     */
    val hiddenSavedMessagesChatId: StateFlow<Long?> =
        combine(repository.savedMessagesChatId, shownFolder) { id, shown -> id.takeUnless { shown is ChatFolder.Custom } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, repository.savedMessagesChatId.value)

    /** Zmrazený účet — karta nahoře v hlavním seznamu (v archivu ne), `null` = účet zmrazený není. */
    val freeze: StateFlow<AccountFreeze?> =
        accountNotices?.freeze?.takeIf { folder == ChatFolder.Main } ?: MutableStateFlow(null)

    /**
     * Doporučená akce od Telegramu — karta nahoře v hlavním seznamu, vždy jen první. `null` =
     * žádná (nebo archiv).
     */
    val suggestion: StateFlow<SuggestedAction?> =
        accountNotices?.suggestedActions?.takeIf { folder == ChatFolder.Main }
            ?.map { it.firstOrNull() }
            ?.stateIn(viewModelScope, SharingStarted.Eagerly, accountNotices.suggestedActions.value.firstOrNull())
            ?: MutableStateFlow(null)

    /** Narozeniny kontaktů (včera, dnes, zítra) — karta nahoře v hlavním seznamu, v archivu ne. */
    val birthdays: StateFlow<List<CloseBirthday>> =
        accountNotices?.closeBirthdays?.takeIf { folder == ChatFolder.Main } ?: MutableStateFlow(emptyList())

    /** Křížek na kartě narozenin: schovat na 24 hodin, na serveru (platí i v jiných klientech). */
    fun hideBirthdays() {
        val notices = accountNotices ?: return
        viewModelScope.launch {
            notices.hideCloseBirthdays().onFailure { failure ->
                _actions.value = ChatActionsState(error = failure.userMessage(Res.string.suggestion_failed))
            }
        }
    }

    /**
     * Chat s kontaktem z karty narozenin. TDLib ho nemusí znát (nikdy se nepsalo), proto
     * `privateChat` — založí ho lokálně, nic se neposílá.
     */
    fun openPrivateChat(userId: Long, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            repository.privateChat(userId)
                .onSuccess(onReady)
                .onFailure { failure -> _actions.value = ChatActionsState(error = failure.userMessage(Res.string.suggestion_failed)) }
        }
    }

    /** Telefon účtu pro „Je … pořád tvoje číslo?", naformátovaný jako v Telegramu. */
    val accountPhone: StateFlow<String?> = accounts.account
        .map { account -> account?.phoneNumber?.takeIf { it.isNotBlank() } }
        .distinctUntilChanged()
        .map { raw -> raw?.let { session?.displayPhone(it) ?: "+${it.removePrefix("+")}" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** „Teď ne", „Pamatuji", „Ano, je": schovat akci na serveru. */
    fun hideSuggestion(action: SuggestedAction) {
        val notices = accountNotices ?: return
        viewModelScope.launch {
            notices.hideSuggestedAction(action).onFailure { failure ->
                logger.w(failure) { "Doporučenou akci se nepodařilo schovat." }
                _actions.value = ChatActionsState(error = failure.userMessage(Res.string.suggestion_failed))
            }
        }
    }

    /**
     * Zapne archivaci nových chatů od neznámých. Nastavení archivu se zapisuje celé, ostatní volby
     * se přenesou. Po úspěchu se akce schová — server ji sám odebrat nemusí.
     */
    fun enableArchiveUnknownChats(action: SuggestedAction) {
        viewModelScope.launch {
            repository.archiveSettings()
                .mapCatching { settings -> repository.setArchiveSettings(settings.copy(archiveUnknownSenders = true)).getOrThrow() }
                .onSuccess { hideSuggestion(action) }
                .onFailure { failure ->
                    logger.w(failure) { "Archivaci chatů od neznámých se nepodařilo zapnout." }
                    _actions.value = ChatActionsState(error = failure.userMessage(Res.string.suggestion_failed))
                }
        }
    }

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

    /** Výsledky akcí nad chaty pro snackbary (chyba, „Vrátit" po archivaci, smazání…). */
    val actions: StateFlow<ChatActionsState> = _actions.asStateFlow()

    private val _actionDetails = MutableStateFlow<ChatActionDetails?>(null)

    /**
     * Co nabídnout v nabídce nad chatem, které se zjišťuje až při otevření ([loadActionDetails]).
     * Platí jen pro chat [ChatActionDetails.chatId]; jestli je nabídka otevřená, ví obrazovka.
     */
    val actionDetails: StateFlow<ChatActionDetails?> = _actionDetails.asStateFlow()

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

    /** Nabídka nad [chat] se otevřela: dočte, co v ní nabídnout (viz [actionDetails]). */
    fun loadActionDetails(chat: ChatSummary) {
        _actionDetails.value = ChatActionDetails(chat.id)

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
            // Nabídka mezitím mohla patřit jinému chatu.
            updateDetails(chat.id) { it.copy(spam = spam) }
        }
        /*
         * Práva vymazat historii taky až na dotaz (`Chat.canBeDeleted…` seznam chatů nenese).
         * Uložené zprávy jako celek ne, stejně jako v konverzaci: tam se maže po tématech.
         */
        if (history != null && chat.id != repository.savedMessagesChatId.value) {
            viewModelScope.launch {
                history.clearHistoryRights(chat.id)
                    .onSuccess { rights -> updateDetails(chat.id) { it.copy(clearHistory = rights) } }
                    .onFailure { failure -> logger.w(failure) { "Práva vymazat historii chatu ${chat.id} se nepodařilo zjistit." } }
            }
        }
        // Složky, stejně jako spam, až na dotaz: `Chat.chatLists` a kam chat přidat jde.
        if (folderRepository != null && repository.chatFolders.value.folders.isNotEmpty()) {
            viewModelScope.launch {
                folderRepository.membership(chat.id)
                    .onSuccess { membership -> updateDetails(chat.id) { it.copy(folders = membership) } }
                    .onFailure { failure -> logger.w(failure) { "Složky chatu ${chat.id} se nepodařilo zjistit." } }
            }
        }
    }

    /** Změní [actionDetails], jen pokud pořád patří chatu [chatId] — nabídka mezitím mohla patřit jinému. */
    private fun updateDetails(chatId: Long, change: (ChatActionDetails) -> ChatActionDetails) =
        _actionDetails.update { details -> if (details != null && details.chatId == chatId) change(details) else details }

    /**
     * Chat do složky, nebo ven. Nabídka zůstává otevřená a zaškrtnutí se přepne hned —
     * přidává se často do víc složek po sobě; při selhání se vrátí.
     */
    fun toggleFolder(chat: ChatSummary, folderId: Int) {
        val repo = folderRepository ?: return
        val before = _actionDetails.value?.takeIf { it.chatId == chat.id }?.folders ?: return
        val member = folderId in before.member
        updateDetails(chat.id) { it.copy(folders = before.copy(member = if (member) before.member - folderId else before.member + folderId)) }
        viewModelScope.launch {
            val result = if (member) repo.removeChat(folderId, chat.id) else repo.addChat(folderId, chat.id)
            result.onFailure { failure ->
                logger.w(failure) { "Chat ${chat.id} se nepodařilo ${if (member) "odebrat ze" else "přidat do"} složky $folderId." }
                updateDetails(chat.id) { it.copy(folders = before) }
                _actions.update { it.copy(error = failure.userMessage(Res.string.chats_action_failed)) }
            }
        }
    }

    /**
     * Seznam opouští kompozici: otevřený průvodce nahlášením se zruší. Jako jediný sheet seznamu
     * žije ve ViewModelu (kroky určuje server a čeká se na odpověď), takže by se jinak vrátil
     * s prvním složením seznamu — viz [ChatSheets], proč to vadí. Jen otevřený: zrušení bez něj
     * by zahodilo i to, co čeká na odeslané nahlášení.
     */
    fun cancelReportOnLeave() {
        if (reportFlow.wizard.value != null) reportFlow.cancel()
    }

    /** Jde chat připnout na plochu (Android s launcherem, který to dovolí). */
    val canAddToHomeScreen: Boolean get() = shareTargets?.canPinToHomeScreen == true

    /** „Přidat na plochu": potvrzení převezme dialog launcheru. */
    fun addToHomeScreen(chat: ChatSummary) {
        shareTargets?.pinToHomeScreen(chat.id)
    }

    /**
     * Vymaže historii chatu z nabídky, celou ([from] `null`), nebo jen vybrané dny — totéž co
     * ⋮ v konverzaci (`ConversationChatActions.clearHistory`). Chat zůstane v seznamu.
     */
    fun clearHistory(chat: ChatSummary, from: Int?, to: Int?, forAll: Boolean) {
        val repo = history ?: return
        viewModelScope.launch {
            val result = if (from != null && to != null) {
                repo.clearHistoryBetween(chat.id, from, to, forAll)
            } else {
                repo.clearHistory(chat.id, forAll)
            }
            result
                .onSuccess {
                    _actions.update {
                        it.copy(notice = UiText(if (from != null) Res.string.clear_history_period_done else Res.string.clear_history_done))
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Historii chatu ${chat.id} se nepodařilo vymazat." }
                    _actions.update { it.copy(error = failure.userMessage(Res.string.clear_history_failed)) }
                }
        }
    }

    fun dismissNotice() {
        _actions.update { it.copy(notice = null) }
    }

    fun togglePinned(chat: ChatSummary) = run(chat.id) {
        everywhere(chat.id) { repository, id -> repository.setPinned(id, chat.folder, !chat.isPinned) }
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
            everywhere(chat.id) { repository, id -> repository.setArchived(id, archived) }
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
            ChatSwipeEffect.MarkRead -> run(chat.id) { readChatEverywhere(chat.id) }
            ChatSwipeEffect.MarkUnread -> run(chat.id) { repository.setMarkedAsUnread(chat.id, true) }
            ChatSwipeEffect.Mute -> mute(chat, duration = null)
            ChatSwipeEffect.Unmute -> unmute(chat)
            ChatSwipeEffect.Pin -> run(chat.id) { everywhere(chat.id) { repository, id -> repository.setPinned(id, chat.folder, true) } }
            ChatSwipeEffect.Unpin -> run(chat.id) { everywhere(chat.id) { repository, id -> repository.setPinned(id, chat.folder, false) } }
            // Doba se vybírá v sheetu; ten otevírá seznam (`ChatSheets.snooze`), sem tah nedojde.
            ChatSwipeEffect.Snooze -> Unit
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
            val failure = change.chatIds.firstNotNullOfOrNull { chatId ->
                everywhere(chatId) { repository, id -> repository.setArchived(id, !change.archived) }.exceptionOrNull()
            }
            failure?.let {
                logger.w(it) { "Vrácení archivace chatů ${change.chatIds} selhalo." }
                reportFailure(it)
            }
        }
    }

    /** Ztlumení na dobu; `null` = navždy. */
    fun mute(chat: ChatSummary, duration: Duration?) = run(chat.id) {
        everywhere(chat.id) { repository, id -> if (duration == null) repository.setMuted(id, true) else repository.muteFor(id, duration) }
    }

    fun unmute(chat: ChatSummary) = run(chat.id) {
        everywhere(chat.id) { repository, id -> repository.setMuted(id, false) }
    }

    /**
     * Akce nad řádkem [chatId] v tomhle účtu, a když proběhla, i nad týmž chatem v ostatních
     * účtech ([SharedChats.inOthers]): ve společném seznamu je spojený řádek jeden (krok 26
     * návrhu). Výsledek je výsledek tohohle účtu — chyba jinde se jen zaloguje. Mimo režim
     * Všechny účty jen tenhle účet.
     *
     * Jen vratné akce, které patří řádku jako celku (ztlumení, archiv, připnutí). Smazání,
     * opuštění, zablokování a vymazání historie zůstávají v účtu řádku: opustit skupinu oběma
     * účty kvůli jednomu ťuknutí by bylo moc.
     */
    private suspend fun everywhere(chatId: Long, action: suspend (ChatRepository, Long) -> Result<Unit>): Result<Unit> =
        action(repository, chatId).onSuccess {
            slot?.let { slot -> sharedChats?.inOthers(slot, chatId) { account, id -> action(account.koin.get(), id) } }
        }

    /**
     * Přečtené přes [ChatRepository.readChat], ne shozením ručního příznaku: ten nechá
     * nepřečtené zprávy, jak jsou, a u chatu s nimi se po „Označit jako přečtené" nic nezměnilo.
     */
    /**
     * Přečte chat [chatId] celý, a je-li to spojená společná skupina, i v ostatních účtech
     * ([SharedChats.readAll]): řádek je jeden, takže „přečteno" musí platit za všechny.
     */
    private suspend fun readChatEverywhere(chatId: Long): Result<Unit> =
        repository.readChat(chatId).onSuccess { slot?.let { sharedChats?.readAll(it, chatId) } }

    fun toggleUnread(chat: ChatSummary) = run(chat.id) {
        if (chat.hasUnread) readChatEverywhere(chat.id) else repository.setMarkedAsUnread(chat.id, true)
    }

    private val _selection = MutableStateFlow<ChatSelection?>(null)

    /** Výběr víc chatů; `null` = seznam jako obvykle. */
    val selection: StateFlow<ChatSelection?> = _selection.asStateFlow()

    /** Co lišta výběru nabídne; `null` mimo výběr. */
    val selectionActions: StateFlow<ChatSelectionActions?> =
        combine(_selection, state, repository.savedMessagesChatId, repository.chatFolders) { selection, state, savedId, folders ->
            selection ?: return@combine null
            // Do složky jen tam, kde složky jdou upravovat (`folderRepository`).
            chatSelectionActions(selectedChats(selection, state), savedId, folders.folders.takeIf { folderRepository != null }.orEmpty())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Jsou vybrané všechny chaty, které seznam ukazuje? Pak „Vybrat vše" zšedne. */
    val allSelected: StateFlow<Boolean> = combine(_selection, state) { selection, state ->
        val chats = (state as? ChatListState.Ready)?.chats ?: return@combine true
        selection?.hasAll(chats) ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Co lišta a hlavička výběru volají — jedna instance na ViewModel (viz [ChatSelectionCallbacks]). */
    internal val selectionCallbacks = ChatSelectionCallbacks(
        onArchive = ::archiveSelected,
        onPin = ::pinSelected,
        onMute = ::muteSelected,
        onRead = ::readSelected,
        onAddToFolder = ::addSelectedToFolder,
        onDelete = ::deleteSelected,
    )

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
            if (actions.read) readChatEverywhere(it.id) else repository.setMarkedAsUnread(it.id, true)
        }
    }

    /**
     * Připnutí z výběru, přepínač podle všech vybraných jako ostatní akce. Strop připnutých hlídá
     * server: chat nad strop selže s vlastní hláškou (`userMessage`) a ty před ním zůstanou připnuté.
     */
    fun pinSelected() {
        val (actions, selected) = selectionNow() ?: return
        runOnSelection(actions.pinTargets(selected)) { repository.setPinned(it.id, it.folder, actions.pin) }
    }

    /**
     * Přidá vybrané do složky. Chat, který tam už je, TDLib přijme bez chyby; chat, který do
     * složky nejde (strop chatů ve složce), selže a ohlásí se.
     */
    fun addSelectedToFolder(folderId: Int) {
        val repo = folderRepository ?: return
        val (_, selected) = selectionNow() ?: return
        runOnSelection(selected) { repo.addChat(folderId, it.id) }
    }

    /**
     * Smaže soukromé chaty a opustí skupiny a kanály ve výběru — tatáž operace jako „Smazat" /
     * „Opustit" u jednoho chatu ([ChatRepository.deleteOrLeave]), potvrzená dialogem lišty.
     * Uložené zprávy ve výběru akci zakážou ([ChatSelectionActions.canDelete]).
     */
    fun deleteSelected() {
        val (actions, selected) = selectionNow() ?: return
        if (!actions.canDelete) return
        if (selected.isNotEmpty() && pendingDeletes != null) {
            _selection.value = null
            scheduleDelete(selected)
            return
        }
        runOnSelection(selected) { repository.deleteOrLeave(it.id) }
    }

    /** „Vybrat vše" v hlavičce výběru: všechny načtené chaty seznamu, které jde vybrat. */
    fun selectAll() {
        val chats = (state.value as? ChatListState.Ready)?.chats ?: return
        _selection.update { it?.withAll(chats) }
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

    /**
     * Odloží smazání [chats] s „Vrátit" ([PendingChatDeletes]); `false` = odložit nejde (bez
     * [pendingDeletes]) a volající maže hned.
     */
    private fun scheduleDelete(chats: List<ChatSummary>): Boolean {
        val pending = pendingDeletes ?: return false
        val ids = chats.map { it.id }
        val batch = pending.schedule(ids)
        _actions.update { it.copy(deleted = DeleteChange(batch, ids, left = chats.none { chat -> chat.isPrivate })) }
        return true
    }

    fun consumeDeleted() {
        _actions.update { it.copy(deleted = null) }
    }

    /** „Vrátit" ve snackbaru po smazání: chaty se vrátí do seznamu a nic se nesmaže. */
    fun undoDelete(change: DeleteChange) {
        pendingDeletes?.undo(change.batch, change.chatIds)
    }

    init {
        // Smazání po lhůtě „Vrátit" běží mimo ViewModel; jeho selhání ohlásí seznam, který žije.
        pendingDeletes?.let { pending ->
            viewModelScope.launch {
                pending.failures.collect { failure ->
                    _actions.update { it.copy(error = failure.userMessage(Res.string.chats_action_failed)) }
                }
            }
        }
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
        // Bez spamu jde smazání vrátit: odloží se a nabídne se „Vrátit". Nahlášení a blokování
        // vrátit nejde, tak u nich se maže hned po nich, jako dřív.
        if (!cleanup.report && !cleanup.block && scheduleDelete(listOf(chat))) return
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
 * Výsledky akcí nad chaty, které hlásí snackbar. Otevřenou nabídku tu nedrží — ta je stav
 * obrazovky ([ChatSheets]); akce doběhne, až je zavřená, a její výsledek na ní viset nesmí.
 */
data class ChatActionsState(
    val error: UiText? = null,
    /** Jednorázová událost pro snackbar s „Vrátit" po archivaci nebo vrácení z archivu. */
    val archived: ArchiveChange? = null,
    /** Jednorázová událost pro snackbar s „Vrátit" po zablokování nebo odblokování. */
    val blocked: BlockChange? = null,
    /** Jednorázová událost pro snackbar s „Vrátit" po smazání / opuštění ([PendingChatDeletes]). */
    val deleted: DeleteChange? = null,
    /** Hláška o povedené akci bez „Vrátit" (vymazaná historie). */
    val notice: UiText? = null,
)

/**
 * Co nabídka nad chatem [chatId] nabídne, zjištěné až při jejím otevření (dotazy na server, na
 * které se nemá ptát u každého chatu v seznamu). Položka se kreslí, až odpověď dorazí.
 */
data class ChatActionDetails(
    val chatId: Long,
    /** Co proti spamu nabídnout; `null`, dokud to nedorazí ze serveru. */
    val spam: SpamActions? = null,
    /** Složky, ve kterých chat je a kam jde přidat; `null` = nenabízet (nezjištěno, žádné složky). */
    val folders: ChatFolderMembership? = null,
    /** Co jde s historií chatu; `null` = „Vymazat historii" nenabízet (nebo nezjištěno). */
    val clearHistory: ClearHistoryRights? = null,
)

/** Chat a co s jeho historií jde, pro sheet „Vymazat historii" z nabídky nad chatem. */
data class ClearHistoryTarget(val chat: ChatSummary, val rights: ClearHistoryRights)

/** Chaty se přesunuly do archivu ([archived]), nebo z něj — jeden z nabídky, víc z výběru. */
data class ArchiveChange(val chatIds: List<Long>, val archived: Boolean) {
    constructor(chatId: Long, archived: Boolean) : this(listOf(chatId), archived)
}

/** Protějšek chatu se zablokoval ([blocked]), nebo odblokoval. */
data class BlockChange(val chatId: Long, val blocked: Boolean)

/**
 * Chaty čekající na smazání s „Vrátit" ([PendingChatDeletes]); [batch] je dávka pro vrácení.
 * [left] = jen skupiny a kanály (z nich se odchází), jinak se maže — podle toho zní snackbar.
 */
data class DeleteChange(val batch: Long, val chatIds: List<Long>, val left: Boolean)

/** Seznam bez chatů, jejichž smazání čeká na „Vrátit". */
internal fun ChatListState.without(hidden: Set<Long>): ChatListState =
    if (hidden.isEmpty() || this !is ChatListState.Ready) this else copy(chats = chats.filterNot { it.id in hidden })

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
    val busy: Boolean = false,
)

/**
 * Doběhlé smazání konceptu z přehledu a jak ho vrátit; [restore] `null` = smazání selhalo, nebo
 * koncept mezitím zmizel sám. Instance je událost — dvě smazání se neslijí.
 */
class DraftDeleted(val chatId: Long, val restore: DraftRestore?)

/**
 * Položka přehledu konceptů: koncept celého chatu ([topic] i [thread] `null`), tématu ve skupině,
 * nebo vlákna komentářů v diskusní skupině [chat].
 */
data class DraftItem(
    val chat: ChatSummary,
    val topic: DraftTopic?,
    val draft: ChatDraft,
    val thread: DraftThread? = null,
) {
    /** Klíč do seznamu: chat, téma i vlákno, jeden chat může mít koncepty ve víc tématech a vláknech. */
    val key: String get() = "${chat.id}:${topic?.id ?: ""}:${thread?.id ?: ""}"
}

data class DraftTopic(val id: Int, val name: String)

/** Vlákno komentářů s konceptem: kořen vlákna a náhled příspěvku, ke kterému patří. */
data class DraftThread(val id: Long, val post: UiText?)
