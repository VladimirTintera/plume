package eu.tintera.plume.features.profile

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.telegram.CollectibleInfo
import eu.tintera.plume.core.telegram.CollectibleItem
import eu.tintera.plume.core.telegram.StoryAlbum
import kotlinx.coroutines.flow.distinctUntilChanged
import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.location.mapsSearchUrl
import eu.tintera.plume.core.location.mapsUrl
import eu.tintera.plume.core.telegram.ProfileGift
import eu.tintera.plume.core.telegram.GiftCollection
import eu.tintera.plume.core.navigation.ContactOffers
import eu.tintera.plume.core.telegram.SharedMediaPositions
import eu.tintera.plume.core.telegram.mergeCalendarDays
import eu.tintera.plume.core.telegram.MediaCalendarPage
import eu.tintera.plume.core.telegram.CalendarDayMedia
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.telegram.RelatedChat
import eu.tintera.plume.core.chatcolor.ChatColors
import eu.tintera.plume.core.settings.ThemeColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayer
import eu.tintera.plume.core.audio.MusicQueue
import eu.tintera.plume.core.audio.QueueTrack
import eu.tintera.plume.resources.profile_action_failed
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AdminRights
import eu.tintera.plume.core.telegram.ChatProfile
import eu.tintera.plume.core.telegram.MemberDetail
import eu.tintera.plume.core.telegram.OwnershipTransferCheck
import eu.tintera.plume.resources.error_transfer_needs_password
import eu.tintera.plume.resources.error_transfer_too_fresh
import eu.tintera.plume.resources.error_transfer_too_fresh_wait
import eu.tintera.plume.resources.member_banned
import eu.tintera.plume.resources.member_dismissed
import eu.tintera.plume.resources.member_load_failed
import eu.tintera.plume.resources.member_rights_saved
import eu.tintera.plume.resources.member_unrestricted
import eu.tintera.plume.resources.member_transferred
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.ProfileKind
import eu.tintera.plume.core.telegram.MEMBERS_PAGE_SIZE
import eu.tintera.plume.core.telegram.MemberPage
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.waitUiText
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.profile_failed
import eu.tintera.plume.resources.profile_mute_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SharedMediaCounts
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.telegram.ChatNotificationOption
import eu.tintera.plume.core.telegram.ChatNotificationPrefs
import eu.tintera.plume.core.telegram.NotificationSettingsRepository
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.resources.settings_notif_error
import kotlin.time.Duration
import eu.tintera.plume.resources.document_download_failed
import eu.tintera.plume.resources.document_open_failed
import eu.tintera.plume.resources.profile_shared_failed
import eu.tintera.plume.resources.auto_delete_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource

/**
 * Profil chatu: údaje se načtou jednou při otevření, živé jsou jen ztlumení a dostupnost.
 * Členové skupiny se dotahují až po profilu — jen když je server vydá.
 */
class ChatProfileViewModel(
    private val slot: AccountSlot,
    val chatId: Long,
    private val profiles: ProfileRepository,
    private val chats: ChatRepository,
    private val conversations: ConversationRepository,
    private val files: FileRepository,
    private val fileOpener: FileOpener,
    private val urlOpener: UrlOpener,
    private val notificationSettings: NotificationSettingsRepository,
    private val notificationPermission: NotificationPermission,
    private val voicePlayer: VoicePlayer,
    private val musicQueue: MusicQueue,
    /** Záložky zpráv tohoto chatu; bez nich se řádek „Záložky" neukazuje. */
    private val bookmarks: MessageBookmarks? = null,
    /** Barva konverzace; bez ní se řádek „Barva konverzace" neukazuje. */
    private val chatColors: ChatColors? = null,
    /** Profil otevřený z kontaktního odkazu: „Přidat do kontaktů" jako hlavní akce. */
    contactOffers: ContactOffers? = null,
    /** Příběhy: lem kolem avataru a záložka „Příběhy"; bez nich obojí chybí. */
    private val stories: StoryRepository? = null,
) : ViewModel() {

    /** Barva, kterou uživatel konverzaci dal; `null` = barvy aplikace. */
    val chatColor: StateFlow<ThemeColor?> = (chatColors?.colors ?: flowOf(emptyMap()))
        .map { it[chatId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), chatColors?.current()?.get(chatId))

    /** Řádek „Barva konverzace" se nabízí, jen když je kam barvu uložit. */
    val supportsChatColor: Boolean = chatColors != null

    fun setChatColor(color: ThemeColor?) {
        chatColors?.set(chatId, color)
    }

    /** Záložky zpráv v tomhle chatu, nejnovější první. */
    val chatBookmarks: StateFlow<List<MessageBookmark>> = (bookmarks?.all ?: flowOf(emptyList()))
        .map { list -> list.filter { it.chatId == chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    private val logger = Logger.withTag("Profile")

    /** Běžící stahování podle `fileId`; druhé ťuknutí ho zruší. */
    private val documentJobs = mutableMapOf<Int, Job>()

    /** První stránka členů (i hledání); nová ji ruší. */
    private var membersJob: Job? = null
    private var moreMembersJob: Job? = null

    /** Rozběhnuté čtení první stránky po záložkách; nové hledání to předchozí zruší. */
    private val sharedJobs = mutableMapOf<SharedMediaKind, Job>()

    /** Rozběhnuté dotahování další stránky po záložkách; nové hledání ho zruší taky. */
    private val moreSharedJobs = mutableMapOf<SharedMediaKind, Job>()

    // Nabídka z kontaktního odkazu se vyzvedne hned, jednou: platí pro TENTO profil, a profil
    // otevřený příště z hlavičky už „Přidat do kontaktů" jako hlavní akci nemá (je v ⋮).
    private val _state = MutableStateFlow(ChatProfileState(offerContact = contactOffers?.consume(chatId) == true))
    val state: StateFlow<ChatProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.profile(chatId)
                .onSuccess { profile ->
                    _state.update { it.copy(profile = profile, loading = false) }
                    syncProfileAudios(profile)
                    if (profile.canListMembers) loadMembers(query = "")
                    if (profile.kind == ProfileKind.User) observePresence()
                    loadRelatedChats(profile)
                    if (profile.giftCount > 0) loadMoreGifts()
                    // Uložené zprávy profil s příběhy nemají; vlastní příběhy jsou v prohlížeči.
                    if (profile.kind != ProfileKind.SavedMessages) loadMoreStories()
                }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, error = failure.reported(Res.string.profile_failed)) }
                }
        }
        viewModelScope.launch {
            chats.muted(chatId).collect { muted -> _state.update { it.copy(muted = muted) } }
        }
        stories?.let { repository ->
            viewModelScope.launch {
                repository.active.map { it[chatId] }.distinctUntilChanged().collect { active ->
                    _state.update { it.copy(activeStories = active) }
                }
            }
        }
        viewModelScope.launch {
            /*
             * Změna postavení člena (i naše vlastní, i z jiného klienta) znamená přečíst seznam
             * znovu — a s ním i profil: počet členů v hlavičce by po vyhození jinak dál hlásil
             * toho, kdo už ve skupině není. `conflate`, protože „odebrat všem práva" pošle updatů
             * dávku a čtení jich snese jedno.
             */
            profiles.memberUpdates(chatId).conflate().collect { reloadProfile() }
        }
        viewModelScope.launch {
            /*
             * Přejmenování, nová fotka, změněné bio či jméno, zablokování z jiného zařízení —
             * profil se čte znovu (lokálně). Dřív se četl jen při otevření a po návratu z úpravy,
             * takže změna odjinud zůstala stará, dokud se profil neotevřel znovu. Členy to
             * nečte: o ty se stará `memberUpdates`. `conflate`, protože změna jména a fotky
             * přijdou těsně po sobě.
             */
            profiles.profileUpdates(chatId).conflate().collect { refreshProfile() }
        }
        viewModelScope.launch {
            notificationSettings.chatSettings(chatId).collect { prefs -> _state.update { it.copy(notifications = prefs) } }
        }
        viewModelScope.launch {
            // Počty rozhodují, které záložky vůbec budou — proto hned při otevření, ne až
            // s první stránkou. Selhání se nehlásí: profil je k užitku i bez sdíleného obsahu.
            conversations.sharedMediaCounts(chatId)
                .onSuccess { counts -> _state.update { it.copy(counts = counts) } }
                .onFailure { failure ->
                    logger.w(failure) { "Počty sdíleného obsahu se nepodařilo načíst." }
                    _state.update { it.copy(counts = SharedMediaCounts.Empty) }
                }
        }
    }

    /**
     * První stránka záložky. Volá se, až když se stránka poprvé ukáže — záložek je několik
     * a dotazovat se dopředu na všechny by znamenalo tři dotazy za každé otevření profilu.
     */
    fun loadShared(kind: SharedMediaKind) {
        if (_state.value.shared[kind] != null) return
        loadFirstShared(kind, query = "")
        if (kind == SharedMediaKind.Media && offersFastScroll(_state.value.counts?.media ?: 0)) loadMediaPositions()
    }

    /**
     * Řídké pozice Médií pro rychlé rolování; čtou se jednou s první stránkou. Bez nich se úchyt
     * neukáže — mřížka se dál roluje prstem, nic se nerozbije.
     */
    private fun loadMediaPositions() {
        viewModelScope.launch {
            conversations.sharedMediaPositions(chatId, SharedMediaKind.Media)
                .onSuccess { positions -> _state.update { it.copy(mediaPositions = positions) } }
                .onFailure { failure -> logger.w(failure) { "Pozice médií pro rychlé rolování se nepodařilo načíst." } }
        }
    }

    /**
     * Skok rychlým rolováním na zprávu [messageId] na pozici [position] (od nejnovější).
     *
     * Blízko začátku se načte seznam od nejnovější jako vždy a mřížka na zprávu jen doroluje.
     * Dál se načte OKNO kolem ní ([SharedMediaTab.withWindow]) — pár novějších nad ní, zbytek
     * stránky starších pod ní — a nahradí celý seznam, jako skok na zprávu v konverzaci.
     * Načítat stovky stránek, než se k ní dojde, by trvalo a stálo data.
     */
    fun jumpShared(kind: SharedMediaKind, messageId: Long, position: Int) {
        val tab = _state.value.shared[kind] ?: return
        if (tab.messages.any { it.id == messageId }) {
            _state.update { it.copy(shared = it.shared + (kind to tab.copy(scrollTo = messageId))) }
            return
        }
        moreSharedJobs[kind]?.cancel()
        sharedJobs[kind]?.cancel()
        sharedJobs[kind] = viewModelScope.launch {
            val nearNewest = position < JUMP_WINDOW_NEWER
            val result = if (nearNewest) {
                conversations.sharedMedia(chatId, kind)
            } else {
                conversations.sharedMedia(chatId, kind, fromMessageId = messageId, offset = -JUMP_WINDOW_NEWER)
            }
            ensureActive()
            result
                .onSuccess { page ->
                    _state.update { state ->
                        val current = state.shared[kind] ?: SharedMediaTab()
                        val next = if (nearNewest) {
                            current.withFirstPage(query = "", page).copy(scrollTo = messageId)
                        } else {
                            current.withWindow(messageId, page)
                        }
                        state.copy(shared = state.shared + (kind to next))
                    }
                }
                .onFailure { failure -> sharedFailed(kind, query = "", failure) }
        }
    }

    /** Novější zprávy nad oknem po skoku; spouští je mřížka, když se dorolovalo k jeho začátku. */
    fun loadNewerShared(kind: SharedMediaKind) {
        val tab = _state.value.shared[kind] ?: return
        if (!tab.canLoadNewer) return
        val from = tab.messages.first().id
        _state.update { it.copy(shared = it.shared + (kind to tab.copy(loadingNewer = true))) }
        viewModelScope.launch {
            conversations.sharedMedia(chatId, kind, fromMessageId = from, offset = -SHARED_NEWER_PAGE)
                .onSuccess { page ->
                    _state.update { state ->
                        val current = state.shared[kind] ?: return@update state
                        state.copy(shared = state.shared + (kind to current.withNewerPage(from, page)))
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Novější média se nepodařilo načíst." }
                    // Jako by okno k nejnovější došlo: nezkoušet to s každým scrollem znovu.
                    _state.update { state ->
                        val current = state.shared[kind] ?: return@update state
                        state.copy(shared = state.shared + (kind to current.copy(loadingNewer = false, reachedNewest = true)))
                    }
                }
        }
    }

    /** Mřížka na zprávu po skoku dorolovala. */
    fun consumeSharedScroll(kind: SharedMediaKind) {
        _state.update { state ->
            val current = state.shared[kind] ?: return@update state
            state.copy(shared = state.shared + (kind to current.copy(scrollTo = null)))
        }
    }

    /**
     * Hledání v záložce. Text drží pole samo; sem jde kvůli dotazu na server, s pauzou, aby se
     * neptalo na každé písmeno. Do té doby zůstávají předchozí výsledky — seznam by jinak při
     * každém znaku problikl načítáním. Totéž co hledání členů.
     */
    fun searchShared(kind: SharedMediaKind, query: String) {
        _state.update { it.copy(sharedQueries = it.sharedQueries + (kind to query)) }
        val search = query.trim()
        if (search == _state.value.shared[kind]?.query && sharedJobs[kind]?.isActive != true) return
        loadFirstShared(kind, search, delayed = true)
    }

    /**
     * První stránka pro [query]. Zruší rozběhnuté čtení téže záložky — i dotahování další
     * stránky předchozího dotazu —, takže pomalá odpověď na „fak" nepřepíše „faktura".
     */
    private fun loadFirstShared(kind: SharedMediaKind, query: String, delayed: Boolean = false) {
        moreSharedJobs[kind]?.cancel()
        sharedJobs[kind]?.cancel()
        if (_state.value.shared[kind] == null) {
            _state.update { it.copy(shared = it.shared + (kind to SharedMediaTab(loading = true))) }
        }
        sharedJobs[kind] = viewModelScope.launch {
            if (delayed) delay(SHARED_SEARCH_DELAY)
            val result = conversations.sharedMedia(chatId, kind, query = query)
            // Repository chytá výjimky do `Result`, i zrušení; zrušený dotaz stav měnit nesmí.
            ensureActive()
            result
                .onSuccess { page ->
                    _state.update { state ->
                        val current = state.shared[kind] ?: SharedMediaTab()
                        state.copy(shared = state.shared + (kind to current.withFirstPage(query, page)))
                    }
                }
                .onFailure { failure -> sharedFailed(kind, query, failure) }
        }
    }

    /** Další stránka; spouští ji seznam, když se dorolovalo ke konci. */
    fun loadMoreShared(kind: SharedMediaKind) {
        val tab = _state.value.shared[kind] ?: return
        if (!tab.canLoadMore) return
        val query = tab.query
        val from = tab.nextFromMessageId
        _state.update { it.copy(shared = it.shared + (kind to tab.copy(loading = true))) }
        moreSharedJobs[kind] = viewModelScope.launch {
            val result = conversations.sharedMedia(chatId, kind, fromMessageId = from, query = query)
            ensureActive()
            result
                .onSuccess { page ->
                    _state.update { state ->
                        val current = state.shared[kind] ?: return@update state
                        state.copy(shared = state.shared + (kind to current.withNextPage(query, from, page)))
                    }
                }
                .onFailure { failure -> sharedFailed(kind, query, failure) }
        }
    }

    private fun sharedFailed(kind: SharedMediaKind, query: String, failure: Throwable) {
        logger.w(failure) { "Sdílený obsah se nepodařilo načíst." }
        _state.update { state ->
            val current = state.shared[kind] ?: SharedMediaTab(query = query)
            state.copy(
                shared = state.shared + (kind to current.withFailedPage(query)),
                error = failure.reported(Res.string.profile_shared_failed),
            )
        }
    }

    /** Fotka nebo video: otevře se prohlížeč médií, stejně jako z konverzace. */
    fun openMedia(messageId: Long, articleMedia: Int = NO_ARTICLE_MEDIA) {
        _state.update { it.copy(openMedia = MediaTarget(messageId, articleMedia)) }
    }

    fun consumeOpenMedia() {
        _state.update { it.copy(openMedia = null) }
    }

    /** Běžící čtení stránky kalendáře médií; zavření kalendáře ho zruší. */
    private var calendarJob: Job? = null

    /**
     * Kalendář fotek a videí po dnech (`getChatMessageCalendar`). Otevírá se pokaždé od
     * nejnovějšího dne — co přibylo od minula, má v něm být.
     */
    fun openMediaCalendar() {
        calendarJob?.cancel()
        _state.update { it.copy(mediaCalendar = MediaCalendarState(loading = true)) }
        loadCalendarPage(fromMessageId = 0)
    }

    /** Další (starší) stránka dnů; dotáhne ji kalendář, když dojede ke konci. */
    fun loadMoreCalendar() {
        val calendar = _state.value.mediaCalendar ?: return
        if (calendar.loading || calendar.reachedEnd) return
        _state.update { it.copy(mediaCalendar = calendar.copy(loading = true)) }
        loadCalendarPage(calendar.nextFromMessageId)
    }

    private fun loadCalendarPage(fromMessageId: Long) {
        calendarJob = viewModelScope.launch {
            val result = conversations.mediaCalendar(chatId, fromMessageId)
            ensureActive()
            result
                .onSuccess { page ->
                    _state.update { state ->
                        val calendar = state.mediaCalendar ?: return@update state
                        state.copy(mediaCalendar = calendar.withPage(page))
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Kalendář médií se nepodařilo načíst." }
                    _state.update { state ->
                        val calendar = state.mediaCalendar ?: return@update state
                        // Konec, ať se neptá znovu s každým složením posledního měsíce.
                        state.copy(
                            mediaCalendar = calendar.copy(loading = false, reachedEnd = true),
                            error = failure.reported(Res.string.profile_shared_failed),
                        )
                    }
                }
        }
    }

    /** Den v kalendáři: kalendář se zavře a prohlížeč se otevře na první fotce dne. */
    fun pickCalendarDay(messageId: Long) {
        closeMediaCalendar()
        openMedia(messageId)
    }

    fun closeMediaCalendar() {
        calendarJob?.cancel()
        _state.update { it.copy(mediaCalendar = null) }
    }

    /** Co právě hraje — tentýž přehrávač jako v konverzaci, zvuk je v aplikaci jen jeden. */
    val playback: StateFlow<VoicePlayback?> = voicePlayer.state

    /**
     * Skladba ze záložky Hudba: pustí ji, nebo pozastaví. Hraje TENTÝŽ `VoicePlayer` jako
     * v konverzaci pod profilem, takže tam je vidět průběh a po dohrání naváže další skladba.
     * U ucha nepřepíná do sluchátka, jako hudba v bublině.
     */
    fun toggleMusic(messageId: Long) {
        val audio = _state.value.sharedMessage(messageId)?.media as? MessageMedia.Audio ?: return
        musicQueue.toggle(slot, chatId, messageId, audio, rememberPosition = _state.value.profile?.isSecretChat != true)
    }

    /**
     * Hlasovka ze záložky: hraje tímtéž přehrávačem a klíčem jako v konverzaci, takže po návratu
     * do chatu běží dál ve své bublině. Stahuje se až tímhle ťuknutím.
     */
    fun toggleVoice(messageId: Long) {
        val voice = _state.value.sharedMessage(messageId)?.media as? MessageMedia.VoiceNote ?: return
        // Pozice v dlouhé hlasovce se pamatuje jako v konverzaci, v tajném chatu ne.
        val secret = _state.value.profile?.isSecretChat == true
        voicePlayer.toggle(VoiceKey(slot, chatId, messageId), resumeKey = voice.fileKey.takeUnless { secret }) {
            when (val file = files.file(voice.fileId).first { it is FileState.Ready || it is FileState.Failed }) {
                is FileState.Ready -> file.path
                is FileState.Failed -> error(file.message)
                else -> error("Nedosažitelné")
            }
        }
    }

    /**
     * Soubor se stáhne a předá systému — táž cesta jako v konverzaci, včetně průběhu v řádku
     * a druhého ťuknutí jako zrušení.
     */
    fun openDocument(messageId: Long) {
        val document = _state.value.sharedMessage(messageId)?.media as? MessageMedia.Document ?: return

        documentJobs.remove(document.fileId)?.let { running ->
            running.cancel()
            // Zrušit i stahování v TDLib — jinak by soubor tekl dál a potichu se dostáhl.
            viewModelScope.launch { files.cancelDownload(document.fileId) }
            _state.update { it.copy(documentDownloads = it.documentDownloads - document.fileId) }
            return
        }

        _state.update { it.copy(documentDownloads = it.documentDownloads + (document.fileId to 0f)) }
        documentJobs[document.fileId] = viewModelScope.launch {
            try {
                // Ťuknutí je výslovný pokyn: stahování se spustí znovu, i kdyby předchozí viselo.
                files.retryDownload(document.fileId)
                val states = files.file(document.fileId).onEach { fileState ->
                    if (fileState is FileState.Downloading) {
                        _state.update { it.copy(documentDownloads = it.documentDownloads + (document.fileId to fileState.progress)) }
                    }
                }
                // Na rozběh se čeká jen chvíli, na dokončení pak jak dlouho je potřeba.
                val started = withTimeoutOrNull(DOWNLOAD_START_TIMEOUT) { states.first { it !is FileState.Pending } }
                val file = started?.let { first ->
                    if (first is FileState.Ready || first is FileState.Failed) {
                        first
                    } else {
                        states.first { it is FileState.Ready || it is FileState.Failed }
                    }
                }
                when (file) {
                    is FileState.Ready -> if (!fileOpener.open(file.path, document.mimeType)) {
                        _state.update { it.copy(error = UiText(Res.string.document_open_failed)) }
                    }
                    else -> _state.update { it.copy(error = UiText(Res.string.document_download_failed)) }
                }
            } finally {
                // I při zrušení musí průběh ze stavu zmizet.
                documentJobs.remove(document.fileId)
                _state.update { it.copy(documentDownloads = it.documentDownloads - document.fileId) }
            }
        }
    }

    /**
     * Hledání v záložce Členové. Text drží pole samo; sem jde kvůli dotazu na server, s pauzou,
     * aby se neptalo na každé písmeno. Do té doby zůstávají předchozí výsledky — seznam by jinak
     * při každém znaku problikl načítáním.
     */
    fun searchMembers(query: String) {
        _state.update { it.copy(memberQuery = query) }
        val search = query.trim()
        if (search == _state.value.members.query && membersJob?.isActive != true) return
        loadMembers(search, delayed = true)
    }

    /**
     * První stránka členů (i po obnovení). Každé volání zruší předchozí, takže pomalá odpověď na
     * „ja" nepřepíše výsledky pro „jana" — a s ní i rozdělané dotahování další stránky.
     */
    private fun loadMembers(query: String, delayed: Boolean = false, limit: Int = MEMBERS_PAGE_SIZE) {
        moreMembersJob?.cancel()
        membersJob?.cancel()
        membersJob = viewModelScope.launch {
            if (delayed) delay(MEMBER_SEARCH_DELAY)
            val result = profiles.members(chatId, query, offset = 0, limit = limit)
            // Repository chytá výjimky do `Result`, i zrušení; zrušený dotaz stav měnit nesmí.
            ensureActive()
            result
                .onSuccess { page -> _state.update { it.copy(members = it.members.withFirstPage(query, page)) } }
                // Bez členů je profil pořád k užitku; hlásit to snackbarem by jinak jen rušilo.
                .onFailure { failure ->
                    logger.w(failure) { "Členy chatu se nepodařilo načíst." }
                    _state.update { it.copy(members = it.members.withFirstPage(query, MemberPage(emptyList(), null))) }
                }
        }
    }

    /** Další stránka; spouští ji seznam, když se doroluje ke konci. */
    fun loadMoreMembers() {
        val list = _state.value.members
        val offset = list.nextOffset
        if (!list.canLoadMore || offset == null) return
        val query = list.query
        _state.update { it.copy(members = it.members.copy(loadingMore = true)) }
        moreMembersJob = viewModelScope.launch {
            val result = profiles.members(chatId, query, offset = offset)
            ensureActive()
            result
                .onSuccess { page ->
                    val before = _state.value.members.members?.size
                    _state.update { it.copy(members = it.members.withNextPage(query, offset, page)) }
                    /*
                     * Stránka bez nikoho nového (samé smazané účty, samí správci z první stránky)
                     * seznam neprodlouží, a seznam, který se neprodloužil, si o další stránku
                     * znovu neřekne. Dotahuje se proto hned tady.
                     */
                    val after = _state.value.members
                    if (after.members?.size == before) loadMoreMembers()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Další členy chatu se nepodařilo načíst." }
                    _state.update { it.copy(members = it.members.withFailedPage(query, offset)) }
                }
        }
    }

    private fun observePresence() {
        viewModelScope.launch {
            conversations.presenceUpdates(chatId).collect { presence -> _state.update { it.copy(presence = presence) } }
        }
    }

    /** Ztlumit / zapnout upozornění. Stav se nepřepisuje lokálně — potvrzení přijde updatem. */
    fun toggleMute() {
        val muted = _state.value.muted ?: return
        viewModelScope.launch {
            chats.setMuted(chatId, !muted).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.profile_mute_failed)) }
            }
        }
    }

    fun openAutoDeleteSheet() = _state.update { it.copy(autoDeleteSheet = true) }

    fun closeAutoDeleteSheet() = _state.update { it.copy(autoDeleteSheet = false) }

    /**
     * Nastaví automatické mazání (0 = vypnout). Sheet se zavře hned; stav v profilu se přepíše až
     * po potvrzení serveru — do chatu přibude servisní zpráva a na tom se nic předbíhat nemá.
     */
    fun setAutoDelete(seconds: Int) {
        _state.update { it.copy(autoDeleteSheet = false) }
        if (seconds == _state.value.profile?.autoDeleteTime) return
        viewModelScope.launch {
            profiles.setAutoDeleteTime(chatId, seconds)
                .onSuccess {
                    _state.update { state -> state.copy(profile = state.profile?.copy(autoDeleteTime = seconds)) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.auto_delete_failed)) }
                }
        }
    }

    fun openNotificationSheet() = _state.update { it.copy(notificationSheet = true) }

    fun closeNotificationSheet() = _state.update { it.copy(notificationSheet = false) }

    /** Ztlumení na dobu; `null` = navždy. Sheet se zavře — rozhodnuto, víc tam není co dělat. */
    fun mute(duration: Duration?) {
        _state.update { it.copy(notificationSheet = false) }
        viewModelScope.launch {
            val result = if (duration == null) chats.setMuted(chatId, true) else chats.muteFor(chatId, duration)
            result.onFailure { failure -> _state.update { it.copy(error = failure.reported(Res.string.profile_mute_failed)) } }
        }
    }

    fun unmute() {
        _state.update { it.copy(notificationSheet = false) }
        viewModelScope.launch {
            chats.setMuted(chatId, false).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.profile_mute_failed)) }
            }
        }
    }

    /** Přepínač v sheetu; stav se nepřepisuje lokálně, potvrzení přijde updatem chatu. */
    fun setNotificationOption(option: ChatNotificationOption, value: Boolean) {
        viewModelScope.launch {
            notificationSettings.setChatOption(chatId, option, value).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.settings_notif_error)) }
            }
        }
    }

    fun resetNotifications() {
        viewModelScope.launch {
            notificationSettings.resetToDefault(chatId).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.settings_notif_error)) }
            }
        }
    }

    val supportsConversationSettings: Boolean = notificationPermission.supportsChannelSettings

    fun openConversationSettings() {
        val prefs = _state.value.notifications ?: return
        val title = _state.value.profile?.title ?: return
        notificationPermission.openConversationSettings(slot, chatId, title, prefs.scope.channel, prefs.soundId)
    }

    fun reportError(text: UiText) {
        _state.update { it.copy(error = text) }
    }

    /** Propojený chat z řádku v Informacích — diskusní skupina kanálu, nebo kanál skupiny. */
    fun openLinkedChat() {
        val linked = _state.value.profile?.linkedChat ?: return
        _state.update { it.copy(openChat = linked.chatId) }
    }

    /** Přímé zprávy kanálu: odběratel v nich píše, správce je čte po vláknech. */
    fun openDirectMessages() {
        val chatId = _state.value.profile?.directMessagesChatId ?: return
        _state.update { it.copy(openChat = chatId) }
    }

    fun openPersonalChannel() {
        val channel = _state.value.profile?.personalChannel ?: return
        _state.update { it.copy(openChat = channel.chatId) }
    }

    /** Člen skupiny: otevře se soukromý chat s ním (TDLib ho založí lokálně, nic se neposílá). */
    /**
     * Společné skupiny (u člověka a bota), podobné kanály (u kanálu) a podobní boti (u bota).
     * Selhání se nehlásí, jen
     * se záložka neukáže — profil je k užitku i bez nich, stejně jako bez sdíleného obsahu.
     */
    private fun loadRelatedChats(profile: ChatProfile) {
        viewModelScope.launch {
            when (profile.kind) {
                ProfileKind.User, ProfileKind.Bot -> {
                    if (profile.kind == ProfileKind.Bot && profile.hasMainWebApp) {
                        launch {
                            profiles.botMediaPreviews(profile.userId ?: chatId)
                                .onSuccess { found -> _state.update { it.copy(appPreviews = found) } }
                                .onFailure { logger.w(it) { "Ukázky mini aplikace se nepodařilo načíst." } }
                        }
                    }
                    if (profile.kind == ProfileKind.Bot) {
                        launch {
                            profiles.similarBots(profile.userId ?: chatId)
                                .onSuccess { found -> _state.update { it.copy(similarChannels = found) } }
                                .onFailure { logger.w(it) { "Podobné boty se nepodařilo načíst." } }
                        }
                    }
                    profiles.commonGroups(profile.userId ?: chatId)
                        .onSuccess { found -> _state.update { it.copy(commonGroups = found) } }
                        .onFailure { logger.w(it) { "Společné skupiny se nepodařilo načíst." } }
                }
                ProfileKind.Channel -> profiles.similarChannels(chatId)
                    .onSuccess { found -> _state.update { it.copy(similarChannels = found) } }
                    .onFailure { logger.w(it) { "Podobné kanály se nepodařilo načíst." } }
                ProfileKind.Group, ProfileKind.SavedMessages -> Unit
            }
        }
    }

    /**
     * Další stránka příběhů v profilu (první, když ještě žádná není) — všech, nebo vybraného
     * alba ([ProfileStories.albumId]). Selhání se nehlásí — záložka se jen neukáže, jako
     * u dárků. Konec je, když stránka nepřinese nic nového nebo je načteno všechno.
     */
    fun loadMoreStories() {
        val repository = stories ?: return
        val current = _state.value.profileStories
        if (current?.loading == true || (current != null && !current.hasMore)) return
        val albumId = current?.albumId
        _state.update { it.copy(profileStories = (it.profileStories ?: ProfileStories()).copy(loading = true)) }
        viewModelScope.launch {
            val page = if (albumId == null) {
                repository.profileStories(chatId, fromStoryId = current?.items?.lastOrNull()?.storyId ?: 0)
            } else {
                repository.albumStories(chatId, albumId, offset = current.items.size)
            }
            page
                .onSuccess { loaded ->
                    _state.update { state ->
                        val stories = state.profileStories ?: ProfileStories()
                        // Odpověď na album, které už uživatel přepnul, se zahodí.
                        if (stories.albumId != albumId) return@update state
                        val items = (stories.items + loaded.stories).distinctBy { it.storyId }
                        state.copy(
                            profileStories = stories.copy(
                                items = items,
                                total = loaded.totalCount,
                                pinnedIds = stories.pinnedIds + loaded.pinnedIds,
                                hasMore = items.size > stories.items.size && items.size < loaded.totalCount,
                                loading = false,
                                // Záložka se ukáže podle VŠECH příběhů; prázdné album ji neschová.
                                available = stories.available || items.isNotEmpty(),
                            ),
                        )
                    }
                    if (albumId == null && current == null) loadStoryAlbums()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Příběhy v profilu se nepodařilo načíst." }
                    _state.update { it.copy(profileStories = it.profileStories?.copy(loading = false, hasMore = false)) }
                }
        }
    }

    /** Alba autora — čipy nad mřížkou. Selhání nevadí, mřížka zůstane bez nich. */
    private fun loadStoryAlbums() {
        val repository = stories ?: return
        viewModelScope.launch {
            repository.albums(chatId)
                .onSuccess { albums ->
                    _state.update { state ->
                        val stories = state.profileStories ?: ProfileStories()
                        state.copy(profileStories = stories.copy(albums = albums, available = stories.available || albums.isNotEmpty()))
                    }
                }
                .onFailure { logger.w(it) { "Alba příběhů se nepodařilo načíst." } }
        }
    }

    /** Přepne mřížku na album [albumId], nebo (`null`) na všechny příběhy v profilu. */
    fun selectStoryAlbum(albumId: Int?) {
        val current = _state.value.profileStories ?: return
        if (current.albumId == albumId) return
        _state.update {
            it.copy(
                profileStories = current.copy(
                    albumId = albumId,
                    items = emptyList(),
                    total = 0,
                    pinnedIds = emptySet(),
                    hasMore = true,
                    loading = false,
                ),
            )
        }
        loadMoreStories()
    }

    /**
     * Další stránka dárků (první, když ještě žádná není). Selhání se nehlásí — záložka se jen
     * neukáže nebo neprodlouží, jako u společných skupin.
     */
    fun loadMoreGifts() {
        val current = _state.value.gifts
        if (current?.loading == true || (current != null && current.nextOffset == null)) return
        val collectionId = current?.collectionId
        _state.update { it.copy(gifts = (it.gifts ?: ProfileGifts()).copy(loading = true)) }
        viewModelScope.launch {
            profiles.receivedGifts(chatId, current?.nextOffset.orEmpty(), collectionId ?: 0)
                .onSuccess { page ->
                    _state.update { state ->
                        val gifts = state.gifts ?: ProfileGifts()
                        // Odpověď ke sbírce, kterou uživatel mezitím přepnul, se zahodí.
                        if (gifts.collectionId != collectionId) return@update state
                        state.copy(
                            gifts = gifts.copy(
                                items = (gifts.items + page.gifts).distinctBy { it.key },
                                total = page.total,
                                nextOffset = page.nextOffset,
                                loading = false,
                            ),
                        )
                    }
                    if (current == null) loadGiftCollections()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Dárky v profilu se nepodařilo načíst." }
                    _state.update { it.copy(gifts = it.gifts?.copy(loading = false, nextOffset = null)) }
                }
        }
    }

    /** Sbírky dárků — čipy nad mřížkou, jako alba příběhů. Selhání nevadí, mřížka zůstane bez nich. */
    private fun loadGiftCollections() {
        viewModelScope.launch {
            profiles.giftCollections(chatId)
                .onSuccess { collections ->
                    _state.update { state ->
                        state.copy(gifts = (state.gifts ?: ProfileGifts()).copy(collections = collections.filter { it.giftCount > 0 }))
                    }
                }
                .onFailure { logger.w(it) { "Sbírky dárků se nepodařilo načíst." } }
        }
    }

    /** Přepne mřížku na sbírku [collectionId], nebo (`null`) na všechny dárky v profilu. */
    fun selectGiftCollection(collectionId: Int?) {
        val current = _state.value.gifts ?: return
        if (current.collectionId == collectionId) return
        _state.update {
            it.copy(gifts = current.copy(collectionId = collectionId, items = emptyList(), total = 0, nextOffset = "", loading = false))
        }
        loadMoreGifts()
    }

    /** Adresa firmy v mapách: podle souřadnic, a když je firma nezadala, hledáním adresy. */
    fun openBusinessAddress() {
        val business = _state.value.profile?.business ?: return
        val latitude = business.latitude
        val longitude = business.longitude
        val url = when {
            latitude != null && longitude != null -> mapsUrl(latitude, longitude)
            else -> mapsSearchUrl(business.address ?: return)
        }
        openLink(url)
    }

    fun openGift(gift: ProfileGift) = _state.update { it.copy(openGift = gift) }

    fun closeGift() = _state.update { it.copy(openGift = null) }

    /**
     * Sheet „z Fragmentu" nad řádkem se jménem nebo číslem. Údaje se čtou až teď (dotaz na server
     * za každou položku), ne s profilem — většina profilů žádnou sběratelskou položku nemá.
     */
    fun openCollectible(items: List<CollectibleItem>) {
        if (items.isEmpty()) return
        _state.update { it.copy(collectible = CollectibleSheet(items)) }
        items.forEach { item ->
            viewModelScope.launch {
                val result = profiles.collectibleInfo(item)
                _state.update { state ->
                    val sheet = state.collectible?.takeIf { it.items == items } ?: return@update state
                    state.copy(
                        collectible = result.fold(
                            onSuccess = { sheet.copy(infos = sheet.infos + (item to it)) },
                            onFailure = { sheet.copy(failed = sheet.failed + item) },
                        ),
                    )
                }
            }
        }
    }

    fun closeCollectible() = _state.update { it.copy(collectible = null) }

    /** Ťuknutí na skupinu nebo kanál v záložce: otevřít ho jako konverzaci. */
    fun openRelatedChat(chatId: Long) {
        _state.update { it.copy(openChat = chatId) }
    }

    /**
     * Ťuknutí na kanál nebo bota v záložce Podobné: TDLib se dozví, odkud uživatel přišel
     * (server podle toho ladí doporučení), a chat se otevře. Bot nemusí mít chat založený,
     * proto jde přes [openMember] (`createPrivateChat`).
     */
    fun openSimilar(openedId: Long) {
        val profile = _state.value.profile ?: return
        val bots = profile.kind == ProfileKind.Bot
        viewModelScope.launch { profiles.similarOpened(if (bots) profile.userId ?: chatId else chatId, openedId, bots) }
        if (bots) openMember(openedId) else openRelatedChat(openedId)
    }

    fun openMember(userId: Long) {
        viewModelScope.launch {
            chats.privateChat(userId)
                .onSuccess { target -> _state.update { it.copy(openChat = target) } }
                .onFailure { failure -> _state.update { it.copy(error = failure.reported(Res.string.profile_failed)) } }
        }
    }

    // --- Správa členů ---

    /**
     * Dlouhý stisk na členovi. Člen se načítá až teď: `getChatMember` je dotaz na server
     * a ptát se na každého ze seznamu dopředu by znamenalo dvě stě dotazů za otevření profilu.
     *
     * Nabídka se otevře, jen když má co ukázat ([MemberActions.any]) — běžnému členovi by se
     * jinak otevřel prázdný sheet a vypadalo by to jako chyba.
     */
    fun openMemberActions(userId: Long) {
        viewModelScope.launch {
            profiles.member(chatId, userId)
                .onSuccess { member -> if (member.actions.any) _state.update { it.copy(memberSheet = member) } }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.member_load_failed)) }
                }
        }
    }

    fun closeMemberActions() = _state.update { it.copy(memberSheet = null) }

    /** Nabídka se zavře a obrazovka otevře práva správce, nebo omezení — podle [admin]. */
    fun openMemberRights(admin: Boolean) {
        val member = _state.value.memberSheet ?: return
        _state.update {
            it.copy(memberSheet = null, openMemberRights = MemberRightsTarget(member.userId, admin))
        }
    }

    fun consumeOpenMemberRights() = _state.update { it.copy(openMemberRights = null) }

    /** Sesazení je uložení prázdných práv — vlastní volání na to TDLib nemá. */
    fun dismissAdmin() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null) }
        runMemberAction(UiText(Res.string.member_dismissed, member.name)) {
            profiles.setAdminRights(chatId, member.userId, AdminRights())
        }
    }

    fun unrestrictMember() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null) }
        runMemberAction(UiText(Res.string.member_unrestricted)) {
            profiles.unrestrictMember(chatId, member.userId)
        }
    }

    fun confirmBan() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null, memberBan = member) }
    }

    fun cancelBan() = _state.update { it.copy(memberBan = null) }

    /** Vyhození; [duration] `null` = navždy. */
    fun banMember(duration: Duration?) {
        val member = _state.value.memberBan ?: return
        _state.update { it.copy(memberBan = null) }
        runMemberAction(UiText(Res.string.member_banned, member.name)) {
            profiles.banMember(chatId, member.userId, duration)
        }
    }

    fun editMemberTag() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null, memberTag = member) }
    }

    fun cancelMemberTag() = _state.update { it.copy(memberTag = null) }

    fun setMemberTag(tag: String) {
        val member = _state.value.memberTag ?: return
        _state.update { it.copy(memberTag = null) }
        runMemberAction(UiText(Res.string.member_rights_saved)) {
            profiles.setMemberTag(chatId, member.userId, tag.trim())
        }
    }

    /**
     * Jestli převod vůbec jde, se ptáme PŘED otevřením dialogu: bez dvoufázového ověření
     * (nebo s čerstvě nastaveným heslem) to Telegram nedovolí a uživatel by psal heslo zbytečně.
     */
    fun startOwnershipTransfer() {
        val member = _state.value.memberSheet ?: return
        _state.update { it.copy(memberSheet = null) }
        viewModelScope.launch {
            profiles.canTransferOwnership()
                .onSuccess { check ->
                    when (check) {
                        OwnershipTransferCheck.Ready -> _state.update { it.copy(memberTransfer = member) }
                        OwnershipTransferCheck.PasswordNeeded ->
                            _state.update { it.copy(error = UiText(Res.string.error_transfer_needs_password)) }
                        // Kdy to půjde, říká server; bez toho by uživatel zkoušel naslepo.
                        is OwnershipTransferCheck.TooFresh -> _state.update {
                            it.copy(
                                error = if (check.retryAfter > 0) {
                                    UiText(Res.string.error_transfer_too_fresh_wait, waitUiText(check.retryAfter))
                                } else {
                                    UiText(Res.string.error_transfer_too_fresh)
                                },
                            )
                        }
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.profile_action_failed)) }
                }
        }
    }

    fun cancelOwnershipTransfer() = _state.update { it.copy(memberTransfer = null) }

    /** Heslo jde rovnou do repository a nikde se nedrží — ani ve stavu, ani v logu. */
    fun transferOwnership(password: String) {
        val member = _state.value.memberTransfer ?: return
        _state.update { it.copy(transferBusy = true) }
        viewModelScope.launch {
            profiles.transferOwnership(chatId, member.userId, password)
                .onSuccess {
                    _state.update {
                        it.copy(
                            memberTransfer = null,
                            transferBusy = false,
                            error = UiText(Res.string.member_transferred, member.name),
                        )
                    }
                    // Vlastníkem je teď někdo jiný, takže se změnila i naše vlastní práva.
                    reloadProfile()
                }
                .onFailure { failure ->
                    // Dialog zůstane otevřený: chybné heslo se opravuje na místě.
                    _state.update {
                        it.copy(transferBusy = false, error = failure.reported(Res.string.profile_action_failed))
                    }
                }
        }
    }

    /**
     * Akce nad členem: po úspěchu se seznam členů ČTE ZNOVU, nepřepisuje se lokálně. Co změna
     * postavení udělala (a jestli server vůbec poslechl), ví jen server — stejně jako u relací
     * v Nastavení → Zařízení.
     */
    private fun runMemberAction(success: UiText, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action()
                .onSuccess {
                    _state.update { it.copy(error = success) }
                    reloadMembers()
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.profile_action_failed)) }
                }
        }
    }

    /**
     * Po návratu z úpravy práv: role v seznamu se mohla změnit, a s ní i to, co sami smíme.
     *
     * Čte se tolik členů, kolik už jich bylo vidět ([MemberList.reloadLimit]) — s jedinou stránkou
     * by seznam pod člověkem, se kterým se zrovna něco dělalo, zmizel. A s dotazem, který je
     * v poli, ne s tím, na který se naposledy odpovědělo: rozepsané hledání se tím nezahodí.
     */
    fun reloadMembers() {
        if (_state.value.profile?.canListMembers != true) return
        val state = _state.value
        loadMembers(state.memberQuery.trim(), limit = state.members.reloadLimit)
    }

    private var composedOnce = false

    /**
     * Profil se znovu skládá po návratu z obrazovky nad ním (úprava chatu, správa členů) —
     * NavDisplay kreslí jen horní scénu. Změny chodí i updaty (`profileUpdates`), tohle je
     * pojistka pro to, co se při úpravě změní bez updatu, který by profil hlídal (převod
     * základní skupiny na supergroupu mění celý chat). Poprvé nic: první načtení dělá `init`.
     */
    fun refreshOnReturn() {
        if (!composedOnce) {
            composedOnce = true
            return
        }
        reloadProfile()
    }

    private fun reloadProfile() {
        viewModelScope.launch {
            refreshProfile()
            reloadMembers()
        }
    }

    /** Jen profil, bez členů. Selhání se nehlásí — na obrazovce zůstane, co tam bylo. */
    private suspend fun refreshProfile() {
        profiles.profile(chatId).onSuccess { profile ->
            _state.update { it.copy(profile = profile) }
            syncProfileAudios(profile)
        }
    }

    /** Soubor první skladby, ke kterému je načtený [ChatProfileState.profileAudios]. */
    private var profileAudiosFor: Int? = null

    /**
     * Hudba v profilu: celý seznam se dočte, až když profil nějakou má, a znovu, když se změní
     * první skladba (přidaná, odebraná, přeřazená — `UserFullInfo` přijde updatem). Do té doby
     * se ukazuje aspoň ta první, kterou profil nese sám.
     */
    private fun syncProfileAudios(profile: ChatProfile) {
        val first = profile.firstProfileAudio
        val userId = profile.userId
        if (first == null || userId == null) {
            profileAudiosFor = null
            _state.update { it.copy(profileAudios = null) }
            return
        }
        if (profileAudiosFor == first.fileId) return
        profileAudiosFor = first.fileId
        _state.update { it.copy(profileAudios = it.profileAudios ?: listOf(first)) }
        viewModelScope.launch {
            profiles.profileAudios(userId)
                .onSuccess { audios -> if (audios.isNotEmpty()) _state.update { it.copy(profileAudios = audios) } }
                .onFailure { failure -> logger.w(failure) { "Hudbu v profilu se nepodařilo načíst." } }
        }
    }

    /**
     * Skladba z hudby v profilu: pustí ji, nebo pozastaví. Hraje fronta hudby se seznamem
     * z profilu, takže po dohrání naváže další skladba profilu a notifikace má „další"
     * i „předchozí". Id skladby ve frontě je záporné id souboru ([profileAudioKey]) — skladba
     * v profilu není zpráva a s id zpráv chatu se potkat nesmí.
     */
    fun toggleProfileAudio(index: Int) {
        val audios = _state.value.profileAudios ?: return
        musicQueue.togglePlaylist(slot, chatId, audios.map { QueueTrack(profileAudioKey(it.fileId), it) }, index)
    }

    fun openLink(url: String) {
        urlOpener.open(url)
    }

    fun consumeOpenChat() {
        _state.update { it.copy(openChat = null) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    private fun Throwable.reported(message: StringResource): UiText {
        logger.w(this) { message.key }
        return userMessage(message)
    }
}

/** Kolik novějších zpráv načte okno nad tou, na kterou se skočilo — kus nad ní je vidět. */
private const val JUMP_WINDOW_NEWER = 15

/** Kolik novějších zpráv dotáhne rolování nahoru v okně. */
private const val SHARED_NEWER_PAGE = 30

/** Kalendář médií: načtené dny od nejnovějšího a kde pokračovat ke starším. */
data class MediaCalendarState(
    val days: List<CalendarDayMedia> = emptyList(),
    val loading: Boolean = false,
    val reachedEnd: Boolean = false,
    val nextFromMessageId: Long = 0,
) {
    /**
     * Stránka od serveru přidaná k načteným dnům ([mergeCalendarDays]). Konec je prázdná
     * stránka — a pro jistotu i stránka, po které by se pokračovalo od téže zprávy znovu.
     */
    fun withPage(page: MediaCalendarPage): MediaCalendarState = copy(
        days = mergeCalendarDays(days, page.days),
        loading = false,
        reachedEnd = page.reachedEnd || page.nextFromMessageId == nextFromMessageId,
        nextFromMessageId = page.nextFromMessageId,
    )
}

/** Id skladby z hudby v profilu ve frontě hudby: záporné, ať se nepotká s id zpráv chatu. */
fun profileAudioKey(fileId: Int): Long = -fileId.toLong()

/** Položky v sheetu „z Fragmentu" a co o nich server řekl; chybějící = ještě se načítá. */
data class CollectibleSheet(
    val items: List<CollectibleItem>,
    val infos: Map<CollectibleItem, CollectibleInfo> = emptyMap(),
    val failed: Set<CollectibleItem> = emptySet(),
)

data class ChatProfileState(
    val profile: ChatProfile? = null,
    /** Hudba v profilu; `null` = žádná (nebo ji uživatel skrývá). */
    val profileAudios: List<MessageMedia.Audio>? = null,
    val loading: Boolean = true,
    /** Načtení členové; prázdné = nejsou, nenašli se, nebo je server nevydal. */
    val members: MemberList = MemberList(),
    /** Text v poli hledání členů — může předbíhat [MemberList.query], dokud server neodpoví. */
    val memberQuery: String = "",
    val presence: Presence? = null,
    /** `null`, dokud chat repository nezná — tlačítko se pak nekreslí. */
    val muted: Boolean? = null,
    /** Upozornění chatu i s tím, co přebírá z výchozího nastavení; `null` = řádek se nekreslí. */
    val notifications: ChatNotificationPrefs? = null,
    val notificationSheet: Boolean = false,
    /** Otevřený výběr doby automatického mazání. */
    val autoDeleteSheet: Boolean = false,
    /** `null`, dokud počty nedorazí; teprve podle nich se vykreslí záložky. */
    val counts: SharedMediaCounts? = null,
    /** Společné skupiny s uživatelem; `null` = nenačtené nebo se nehodí (záložka se nekreslí). */
    val commonGroups: List<RelatedChat>? = null,
    /** Podobné kanály; `null` = nenačtené nebo nejde o kanál. */
    val similarChannels: List<RelatedChat>? = null,
    /** Dárky v profilu; `null` = žádné nebo se ještě nenačetly (záložka se nekreslí). */
    val gifts: ProfileGifts? = null,
    /** Příběhy, které autor nechal v profilu; `null` = nenačtené (záložka se nekreslí). */
    val profileStories: ProfileStories? = null,
    /** Aktivní příběhy chatu — lem kolem avataru v hlavičce; `null` = žádné. */
    val activeStories: ActiveStories? = null,
    /** Dárek otevřený v sheetu. */
    val openGift: ProfileGift? = null,
    /** Ukázky mini aplikace bota (fotky a videa); prázdné = žádné. */
    val appPreviews: List<MessageMedia> = emptyList(),
    /** Sběratelská jména nebo číslo otevřené v sheetu „z Fragmentu". */
    val collectible: CollectibleSheet? = null,
    /** Načtený obsah záložek; chybějící klíč = záložka se ještě neukázala. */
    val shared: Map<SharedMediaKind, SharedMediaTab> = emptyMap(),
    /** Text v poli hledání záložky — může předbíhat [SharedMediaTab.query], dokud server neodpoví. */
    val sharedQueries: Map<SharedMediaKind, String> = emptyMap(),
    /** Průběh stahování souboru podle `fileId`, dokud se neotevře. */
    val documentDownloads: Map<Int, Float> = emptyMap(),
    val error: UiText? = null,
    /** Jednorázová událost: otevřít chat (člen skupiny). */
    val openChat: Long? = null,
    /**
     * Profil otevřený z kontaktního odkazu (`t.me/contact/…`, QR kód): „Přidat do kontaktů" je
     * hlavní akce nad dlaždicemi, dokud člověk v kontaktech není. Viz `ContactOffers`.
     */
    val offerContact: Boolean = false,
    /** Jednorázová událost: otevřít prohlížeč médií na této zprávě (u článku na jeho médiu). */
    val openMedia: MediaTarget? = null,
    /** Řídké pozice Médií pro rychlé rolování; `null` = nejsou (málo médií, nenačtené). */
    val mediaPositions: SharedMediaPositions? = null,
    /** Otevřený kalendář médií po dnech; `null` = zavřený. */
    val mediaCalendar: MediaCalendarState? = null,
    /** Člen s otevřenou nabídkou akcí; `null` = nabídka není. */
    val memberSheet: MemberDetail? = null,
    /** Člen, u kterého se potvrzuje vyhození. */
    val memberBan: MemberDetail? = null,
    /** Člen, kterému se mění štítek. */
    val memberTag: MemberDetail? = null,
    /** Člen, kterému se předává vlastnictví; dialog se ptá na heslo. */
    val memberTransfer: MemberDetail? = null,
    /** Převod běží — tlačítko v dialogu se nesmí dát zmáčknout podruhé. */
    val transferBusy: Boolean = false,
    /** Jednorázová událost: otevřít obrazovku práv správce nebo omezení. */
    val openMemberRights: MemberRightsTarget? = null,
) {
    /**
     * Které záložky mají být. Členové jsou první, pak jen ty druhy obsahu, které v chatu jsou —
     * prázdná záložka by byla slib, který chat nesplní. Dokud počty nedorazí, nejsou žádné:
     * záložka, která by po chvíli zmizela, vypadá jako chyba.
     */
    val tabs: List<ProfileTab>
        get() {
            val counts = counts ?: return emptyList()
            return buildList {
                if (profile?.canListMembers == true) add(ProfileTab.Members)
                SharedMediaKind.entries.forEach { kind ->
                    if (counts.of(kind) > 0) add(ProfileTab.Shared(kind))
                }
                // Na konci: dorazí samostatným dotazem a záložka přidaná doprostřed by posunula
                // tu, na které uživatel zrovna stojí.
                if (!commonGroups.isNullOrEmpty()) add(ProfileTab.CommonGroups)
                if (!similarChannels.isNullOrEmpty()) add(ProfileTab.Similar(bots = profile?.kind == ProfileKind.Bot))
                // Záložka zůstane i nad prázdnou nebo právě načítanou sbírkou — jinak by při
                // přepnutí sbírky zmizela a pager by uskočil jinam.
                // Ověřeno na emulátoru: s podmínkou jen na položky záložka při návratu na „Vše"
                // zmizela a pager uskočil na Příběhy. Se sbírkami je přepínání vždycky.
                if (gifts != null && (gifts.items.isNotEmpty() || gifts.collections.isNotEmpty() || gifts.collectionId != null)) add(ProfileTab.Gifts)
                if (profileStories?.available == true) add(ProfileTab.Stories)
            }
        }

    /**
     * Hledání členů se nabízí jen tam, kde se nevejdou na jednu stránku — u skupiny o pár lidech
     * by pole jen ubíralo místo. A ne ve skupině se skrytými členy: hledalo by jen mezi správci. Rozepsané hledání se ale neschová, ani kdyby se počet změnil.
     */
    val memberSearchAvailable: Boolean
        get() = profile?.membersHidden != true &&
            ((profile?.memberCount ?: 0) > MEMBERS_PAGE_SIZE || memberQuery.isNotEmpty())

    /** Nabídnout nad záložkou [kind] hledání? Viz [offersSharedSearch]. */
    fun sharedSearchAvailable(kind: SharedMediaKind): Boolean =
        offersSharedSearch(kind, counts?.of(kind) ?: 0, sharedQueries[kind].orEmpty())

    /** Zpráva z načtených záložek — pro otevření souboru nemusíme vědět, ze které je. */
    fun sharedMessage(messageId: Long): ChatMessage? =
        shared.values.firstNotNullOfOrNull { tab -> tab.messages.firstOrNull { it.id == messageId } }
}

/** Kam vede „Upravit práva": [admin] `true` = práva správce, `false` = omezení člena. */
data class MemberRightsTarget(val userId: Long, val admin: Boolean)

/** Záložka profilu. Členové jsou vlastní druh, ne sdílený obsah. */
sealed interface ProfileTab {
    data object Members : ProfileTab
    data class Shared(val kind: SharedMediaKind) : ProfileTab

    /** Skupiny, ve kterých je člověk i účet. Jen profil uživatele a bota. */
    data object CommonGroups : ProfileTab

    /** Kanály podobné kanálu, nebo boti podobní botovi ([bots]). */
    data class Similar(val bots: Boolean) : ProfileTab

    /** Dárky vystavené v profilu člověka nebo kanálu. */
    data object Gifts : ProfileTab

    /** Příběhy, které autor nechal v profilu (i vypršelé). */
    data object Stories : ProfileTab
}

/**
 * Načtené příběhy profilu — všechny, nebo jednoho alba ([albumId]); připnuté jdou první (tak je
 * vrací server, jen u všech).
 */
data class ProfileStories(
    val items: List<MessageMedia.Story> = emptyList(),
    val total: Int = 0,
    val pinnedIds: Set<Int> = emptySet(),
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    /** Alba autora; prázdné = čipy se nekreslí. */
    val albums: List<StoryAlbum> = emptyList(),
    /** Vybrané album; `null` = všechny příběhy. */
    val albumId: Int? = null,
    /** Autor má v profilu nějaké příběhy nebo alba — záložka se ukáže a už nezmizí. */
    val available: Boolean = false,
)

/** Načtené dárky profilu; [nextOffset] `null` = všechny. */
data class ProfileGifts(
    val items: List<ProfileGift> = emptyList(),
    val total: Int = 0,
    val nextOffset: String? = null,
    val loading: Boolean = false,
    /** Sbírky majitele; prázdné = čipy se nekreslí. Bez prázdných sbírek. */
    val collections: List<GiftCollection> = emptyList(),
    /** Vybraná sbírka; `null` = všechny dárky v profilu. */
    val collectionId: Int? = null,
)

/** Pauza v psaní, po které se hledání členů zeptá serveru. */
private const val MEMBER_SEARCH_DELAY = 300L

/** Pauza v psaní, po které se hledání v záložce sdíleného obsahu zeptá serveru. */
private const val SHARED_SEARCH_DELAY = 300L

/** Na rozběh stahování se čeká jen chvíli; bez toho by mrtvé spojení nechalo kolečko točit se napořád. */
private const val DOWNLOAD_START_TIMEOUT = 10_000L

/** Co otevřít v prohlížeči: zpráva, u článku i pořadí média (`NO_ARTICLE_MEDIA` = příloha zprávy). */
data class MediaTarget(val messageId: Long, val articleMedia: Int = NO_ARTICLE_MEDIA)
