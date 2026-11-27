package eu.tintera.plume.features.stories

import eu.tintera.plume.core.stories.StoriesState
import eu.tintera.plume.core.stories.StoryKey
import eu.tintera.plume.core.stories.next
import eu.tintera.plume.core.stories.previous
import eu.tintera.plume.core.stories.profileStoriesState
import eu.tintera.plume.core.stories.showPoster
import eu.tintera.plume.core.stories.viewerPosters
import eu.tintera.plume.core.stories.withoutStory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import eu.tintera.plume.resources.story_audience_pick_someone
import eu.tintera.plume.core.telegram.pinnedAfter
import eu.tintera.plume.core.telegram.StoryAudience
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.LiveStoryRepository
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.resources.story_caption_saved
import eu.tintera.plume.resources.story_audience_changed
import eu.tintera.plume.resources.story_unpinned
import eu.tintera.plume.resources.story_pinned
import eu.tintera.plume.resources.story_manage_failed
import eu.tintera.plume.resources.story_profile_removed
import eu.tintera.plume.resources.story_profile_added
import eu.tintera.plume.resources.story_albums_failed
import eu.tintera.plume.core.telegram.STORY_ALBUM_NAME_MAX
import eu.tintera.plume.core.telegram.StoryAlbum
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.features.report.ReportFlow
import eu.tintera.plume.core.telegram.StoryListKind
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.story_delete_failed
import eu.tintera.plume.resources.story_hidden
import eu.tintera.plume.resources.story_hide_failed
import eu.tintera.plume.resources.story_unhidden
import eu.tintera.plume.resources.story_deleted
import eu.tintera.plume.resources.story_reaction_failed
import eu.tintera.plume.resources.story_live_message_delete_failed
import eu.tintera.plume.resources.story_live_message_failed
import eu.tintera.plume.resources.story_reply_failed
import eu.tintera.plume.resources.story_reply_sent
import eu.tintera.plume.resources.story_viewers_failed
import eu.tintera.plume.core.telegram.StoryViewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Prohlížeč příběhů otevřený u autora [startChatId].
 *
 * Hlásí TDLib, který příběh je na obrazovce (`openStory` / `closeStory`) — bez toho se zhlédnutí
 * nepočítá a kroužek zůstane barevný. Zavření posledního jde přes [appScope]: `viewModelScope`
 * je v [onCleared] už zrušený.
 */
class StoriesViewModel(
    startChatId: Long,
    /** Příběhy z profilu autora ([startChatId]); prázdné = jeho aktivní příběhy a další autoři. */
    storyIds: List<Int> = emptyList(),
    startStoryId: Int = 0,
    private val repository: StoryRepository,
    private val appScope: CoroutineScope,
    /** Emoji, která jdou dát jako reakce (výběr přes „+"); bez něj jen srdce. */
    quickReaction: QuickReaction? = null,
    /** Přehrávání živých příběhů; `null` = platforma je neumí (prohlížeč to řekne větou). */
    liveRepository: LiveStoryRepository? = null,
    liveMedia: LiveStreamMedia? = null,
) : ViewModel() {

    private val liveWatch = if (liveRepository != null && liveMedia != null) {
        LiveStoryWatch(liveRepository, liveMedia, viewModelScope, appScope) {
            _notice.value = UiText(Res.string.story_live_message_failed)
        }
    } else {
        null
    }

    /** Jde živý příběh v tomhle prohlížeči sledovat. */
    val liveSupported: Boolean get() = liveWatch != null

    /** Sledovaný živý příběh (jen ten, na kterém prohlížeč stojí); `null` = žádný. */
    val live: StateFlow<LiveStoryState?> = liveWatch?.state ?: MutableStateFlow(null)

    /** Emoji do výběru reakcí v pořadí od serveru. */
    private val storyReactions = MutableStateFlow<List<String>?>(null)

    /**
     * Reakce v nabídce u příběhu: ty, které příběhy berou (`getStoryAvailableReactions`, i vlastní
     * emoji), a dokud nedorazí nebo když selžou, výběr rychlé reakce jako dřív.
     */
    val reactionChoices: StateFlow<List<String>> = combine(
        storyReactions,
        quickReaction?.choices ?: MutableStateFlow(emptyList()),
    ) { story, quick -> story?.takeIf { it.isNotEmpty() } ?: quick }
        .stateIn(viewModelScope, SharingStarted.Eagerly, quickReaction?.choices?.value.orEmpty())

    private val _notice = MutableStateFlow<UiText?>(null)

    /** Krátké hlášení nad lištou (odpověď odeslána, chyba); UI ho po zobrazení sundá [noticeShown]. */
    val notice: StateFlow<UiText?> = _notice.asStateFlow()

    private val _state = MutableStateFlow(
        if (storyIds.isNotEmpty()) {
            // Autora z profilu je potřeba dočíst (nemusí mít aktivní příběh); do té doby prázdno.
            StoriesState()
        } else {
            viewerPosters(repository.active.value, startChatId).let { posters ->
                StoriesState(
                    posters = posters,
                    poster = posters.indexOfFirst { it.chatId == startChatId }.coerceAtLeast(0),
                    closed = posters.isEmpty(),
                )
            }
        },
    )
    val state: StateFlow<StoriesState> = _state.asStateFlow()

    /** Hlášený příběh; průvodce se volá pro autora a tohle doplní, který příběh to je. */
    private var reporting: StoryKey? = null

    /**
     * Průvodce nahlášením — tentýž jako u chatu a zpráv ([ReportFlow]). Hotovo nebo chyba jde
     * do pilulky nad lištou ([notice]), prohlížeč snackbar nemá.
     */
    val report = ReportFlow(viewModelScope) { _, request ->
        val key = reporting ?: return@ReportFlow Result.failure(IllegalStateException("Žádný hlášený příběh"))
        repository.report(key.posterChatId, key.storyId, request)
    }

    /** Začne nahlášení příběhu [key]; prohlížeč pod průvodcem stojí. */
    fun reportStory(key: StoryKey) {
        reporting = key
        report.start(key.posterChatId)
    }

    /** Příběh, o kterém TDLib ví, že je otevřený. */
    private var opened: StoryKey? = null

    private val loading = mutableSetOf<StoryKey>()

    init {
        viewModelScope.launch {
            repository.availableReactions().onSuccess { storyReactions.value = it }
        }
        viewModelScope.launch {
            report.outcome.collect { outcome ->
                if (outcome != null) {
                    _notice.value = outcome
                    report.consumeOutcome()
                }
            }
        }
        if (storyIds.isNotEmpty()) {
            viewModelScope.launch {
                val author = repository.author(startChatId)
                _state.value = if (author == null) StoriesState(closed = true) else profileStoriesState(author, storyIds, startStoryId)
            }
        }
        viewModelScope.launch {
            state.map { it.current }.distinctUntilChanged().collect { key ->
                opened?.let { repository.close(it.posterChatId, it.storyId) }
                opened = key
                if (key != null) {
                    repository.open(key.posterChatId, key.storyId)
                    preload()
                }
            }
        }
        liveWatch?.let { watch ->
            viewModelScope.launch {
                // Živý příběh se sleduje, jen dokud na něm prohlížeč stojí — obsah se dočítá
                // až po přechodu, proto se hlídá i načtená stránka, ne jen klíč.
                state.map { s -> s.current?.let { key -> s.pages[key]?.live?.let { key to it } } }
                    .distinctUntilChanged()
                    .collect { current -> if (current == null) watch.stop() else watch.watch(current.first, current.second) }
            }
        }
    }

    fun next() = _state.update { it.next() }

    fun previous() = _state.update { it.previous() }

    fun showPoster(index: Int) = _state.update { it.showPoster(index) }

    private val _viewers = MutableStateFlow<StoryViewersState?>(null)

    /** Otevřený seznam diváků vlastního příběhu; `null` = zavřený. */
    val viewers: StateFlow<StoryViewersState?> = _viewers.asStateFlow()

    fun openViewers(key: StoryKey) {
        val expired = _state.value.pages[key]?.viewersExpired == true
        _viewers.value = StoryViewersState(storyId = key.storyId, expired = expired, nextOffset = "".takeUnless { expired })
        loadMoreViewers()
    }

    /** Další stránka diváků; volá ji konec seznamu. Souběžné volání se zahodí. */
    fun loadMoreViewers() {
        val current = _viewers.value ?: return
        val offset = current.nextOffset ?: return
        if (current.loading) return
        _viewers.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            repository.viewers(current.storyId, offset)
                .onSuccess { page ->
                    _viewers.update { state ->
                        state?.takeIf { it.storyId == current.storyId }?.copy(
                            // Server mezi stránkami nic neslibuje; téhož diváka podruhé neukazovat.
                            viewers = (state.viewers + page.viewers).distinctBy { it.chatId to it.action },
                            total = page.totalCount,
                            nextOffset = page.nextOffset,
                            loading = false,
                        )
                    }
                }
                .onFailure { failure ->
                    _viewers.update { state ->
                        state?.copy(loading = false, nextOffset = null, error = failure.userMessage(Res.string.story_viewers_failed))
                    }
                }
        }
    }

    fun closeViewers() {
        _viewers.value = null
    }

    /**
     * Skryje autora do archivu, nebo ho vrátí. Prohlížeč zůstane, kde je (autoři jsou zmražení),
     * jen se přepne položka v nabídce; seznam chatů a hromádky se přeskládají updatem.
     */
    fun toggleHidden(chatId: Long) {
        val author = _state.value.posters.firstOrNull { it.chatId == chatId } ?: return
        val hide = !author.isHidden
        viewModelScope.launch {
            repository.setHidden(chatId, hide)
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            posters = state.posters.map {
                                // Vrácený autor jde skrýt znovu: `canBeArchived` měl ze skrytého seznamu `false`.
                                if (it.chatId == chatId) {
                                    it.copy(list = if (hide) StoryListKind.Archive else StoryListKind.Main, canBeArchived = !hide || it.canBeArchived)
                                } else {
                                    it
                                }
                            },
                        )
                    }
                    _notice.value = UiText(if (hide) Res.string.story_hidden else Res.string.story_unhidden, author.title)
                }
                .onFailure { failure -> _notice.value = failure.userMessage(Res.string.story_hide_failed) }
        }
    }

    private val _albums = MutableStateFlow<StoryAlbumsState?>(null)

    /** Otevřený panel alb vlastního příběhu; `null` = zavřený. */
    val albums: StateFlow<StoryAlbumsState?> = _albums.asStateFlow()

    fun openAlbums(key: StoryKey) {
        val story = _state.value.pages[key] ?: return
        _albums.value = StoryAlbumsState(key = key, inAlbums = story.albumIds.toSet())
        viewModelScope.launch {
            repository.albums(key.posterChatId)
                .onSuccess { found -> _albums.update { it?.takeIf { s -> s.key == key }?.copy(albums = found) } }
                .onFailure { failure ->
                    _albums.value = null
                    _notice.value = failure.userMessage(Res.string.story_albums_failed)
                }
        }
    }

    fun closeAlbums() {
        _albums.value = null
    }

    /** Dá příběh do alba, nebo ho z něj vyndá. Přepne se hned, při chybě se vrátí. */
    fun toggleAlbum(albumId: Int) {
        val current = _albums.value ?: return
        val add = albumId !in current.inAlbums
        setInAlbums(current.key, if (add) current.inAlbums + albumId else current.inAlbums - albumId)
        viewModelScope.launch {
            repository.setInAlbum(current.key.posterChatId, albumId, current.key.storyId, add)
                .onFailure { failure ->
                    setInAlbums(current.key, current.inAlbums)
                    albumError(failure)
                }
        }
    }

    /** Nové album rovnou s tímhle příběhem. */
    fun createAlbum(name: String) {
        val current = _albums.value ?: return
        val trimmed = name.trim().take(STORY_ALBUM_NAME_MAX)
        if (trimmed.isEmpty() || current.busy) return
        _albums.update { it?.copy(busy = true) }
        viewModelScope.launch {
            repository.createAlbum(current.key.posterChatId, trimmed, listOf(current.key.storyId))
                .onSuccess { album ->
                    _albums.update { it?.copy(albums = it.albums.orEmpty() + album, busy = false) }
                    setInAlbums(current.key, (_albums.value?.inAlbums ?: current.inAlbums) + album.id)
                }
                .onFailure { failure ->
                    _albums.update { it?.copy(busy = false) }
                    albumError(failure)
                }
        }
    }

    fun renameAlbum(albumId: Int, name: String) {
        val current = _albums.value ?: return
        val trimmed = name.trim().take(STORY_ALBUM_NAME_MAX)
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameAlbum(current.key.posterChatId, albumId, trimmed)
                .onSuccess { renamed ->
                    _albums.update { state -> state?.copy(albums = state.albums?.map { if (it.id == albumId) renamed else it }) }
                }
                .onFailure(::albumError)
        }
    }

    /** Smaže album (příběhy zůstanou). */
    fun deleteAlbum(albumId: Int) {
        val current = _albums.value ?: return
        viewModelScope.launch {
            repository.deleteAlbum(current.key.posterChatId, albumId)
                .onSuccess {
                    _albums.update { state -> state?.copy(albums = state.albums?.filterNot { it.id == albumId }) }
                    setInAlbums(current.key, (_albums.value?.inAlbums ?: current.inAlbums) - albumId)
                }
                .onFailure(::albumError)
        }
    }

    /** Nové pořadí alb. Přeskládá se hned, při chybě se vrátí. */
    fun reorderAlbums(albumIds: List<Int>) {
        val current = _albums.value ?: return
        val before = current.albums ?: return
        val byId = before.associateBy { it.id }
        val reordered = albumIds.mapNotNull { byId[it] }
        if (reordered.map { it.id } == before.map { it.id }) return
        _albums.update { it?.copy(albums = reordered) }
        viewModelScope.launch {
            repository.reorderAlbums(current.key.posterChatId, reordered.map { it.id })
                .onFailure { failure ->
                    _albums.update { it?.copy(albums = before) }
                    albumError(failure)
                }
        }
    }

    /**
     * Otevře řazení příběhů alba [albumId]: načte je všechny (alba jsou malá, řadí se celé),
     * do té doby panel ukazuje čekání.
     */
    fun openAlbumStories(albumId: Int) {
        val current = _albums.value ?: return
        _albums.update { it?.copy(storiesOrder = AlbumStoriesOrder(albumId)) }
        viewModelScope.launch {
            val loaded = mutableListOf<MessageMedia.Story>()
            while (true) {
                val page = repository.albumStories(current.key.posterChatId, albumId, offset = loaded.size)
                    .getOrElse { failure ->
                        _albums.update { it?.copy(storiesOrder = null) }
                        albumError(failure)
                        return@launch
                    }
                val before = loaded.size
                page.stories.forEach { story -> if (loaded.none { it.storyId == story.storyId }) loaded += story }
                if (loaded.size == before || loaded.size >= page.totalCount) break
            }
            _albums.update { state ->
                state?.takeIf { it.storiesOrder?.albumId == albumId }?.copy(storiesOrder = AlbumStoriesOrder(albumId, loaded)) ?: state
            }
        }
    }

    fun closeAlbumStories() = _albums.update { it?.copy(storiesOrder = null) }

    /** Uloží nové pořadí příběhů v albu a řazení zavře; chyba se ukáže v panelu. */
    fun reorderAlbumStories(storyIds: List<Int>) {
        val current = _albums.value ?: return
        val order = current.storiesOrder ?: return
        _albums.update { it?.copy(storiesOrder = null) }
        if (storyIds == order.stories?.map { it.storyId }) return
        viewModelScope.launch {
            repository.reorderAlbumStories(current.key.posterChatId, order.albumId, storyIds).onFailure(::albumError)
        }
    }

    /**
     * Chyba patří DO panelu, ne do pilulky prohlížeče: panel je samostatné okno a pilulku pod
     * sebou zakryje (ověřeno — hláška nebyla vidět).
     */
    private fun albumError(failure: Throwable) {
        _albums.update { it?.copy(error = failure.userMessage(Res.string.story_albums_failed)) }
    }

    /** Členství v albech v panelu i u příběhu (ať se po zavření a otevření panelu nevrátí staré). */
    private fun setInAlbums(key: StoryKey, inAlbums: Set<Int>) {
        _albums.update { it?.takeIf { s -> s.key == key }?.copy(inAlbums = inAlbums) ?: it }
        _state.update { state ->
            val story = state.pages[key] ?: return@update state
            state.copy(pages = state.pages + (key to story.copy(albumIds = inAlbums.toList())))
        }
    }

    fun noticeShown() {
        _notice.value = null
    }

    /** Hláška z akce, kterou dělá obrazovka sama (zkopírovaný odkaz, nepovedené sdílení). */
    fun showNotice(text: UiText) {
        _notice.value = text
    }

    private val _pinned = MutableStateFlow<Map<Long, List<Int>>>(emptyMap())

    /**
     * Připnuté příběhy v profilu podle autora. Jen u vlastních (připnout jde jen tam) a načtou se,
     * až je vlastní příběh vidět — nabídka podle nich píše „Připnout" / „Odepnout".
     */
    val pinned: StateFlow<Map<Long, List<Int>>> = _pinned.asStateFlow()

    private fun loadPinned(posterChatId: Long) {
        if (posterChatId in _pinned.value) return
        _pinned.update { it + (posterChatId to emptyList()) }
        viewModelScope.launch {
            repository.pinnedStoryIds(posterChatId).onSuccess { ids -> _pinned.update { it + (posterChatId to ids) } }
        }
    }

    /**
     * Nechá příběh v profilu i po vypršení, nebo ho odtamtud vezme. Přepne se hned a při chybě
     * vrátí. Příběh vzatý z profilu přestane být připnutý — server ho z připnutých vyřadí sám.
     */
    fun setOnProfile(key: StoryKey, onProfile: Boolean) {
        val story = _state.value.pages[key] ?: return
        updatePage(key) { it.copy(isOnProfile = onProfile) }
        val pinnedBefore = _pinned.value[key.posterChatId]
        if (!onProfile) _pinned.update { it + (key.posterChatId to pinnedAfter(pinnedBefore.orEmpty(), key.storyId, pinned = false)) }
        viewModelScope.launch {
            repository.setOnProfile(key.posterChatId, key.storyId, onProfile)
                .onSuccess { _notice.value = UiText(if (onProfile) Res.string.story_profile_added else Res.string.story_profile_removed) }
                .onFailure { failure ->
                    updatePage(key) { it.copy(isOnProfile = story.isOnProfile) }
                    pinnedBefore?.let { before -> _pinned.update { it + (key.posterChatId to before) } }
                    _notice.value = failure.userMessage(Res.string.story_manage_failed)
                }
        }
    }

    /** Připne příběh v profilu nahoru, nebo ho odepne. Přepne se hned a při chybě vrátí. */
    fun setPinned(key: StoryKey, pinned: Boolean) {
        val before = _pinned.value[key.posterChatId].orEmpty()
        _pinned.update { it + (key.posterChatId to pinnedAfter(before, key.storyId, pinned)) }
        viewModelScope.launch {
            repository.setPinned(key.posterChatId, key.storyId, pinned)
                .onSuccess { _notice.value = UiText(if (pinned) Res.string.story_pinned else Res.string.story_unpinned) }
                .onFailure { failure ->
                    _pinned.update { it + (key.posterChatId to before) }
                    _notice.value = failure.userMessage(Res.string.story_manage_failed)
                }
        }
    }

    private val _audience = MutableStateFlow<StoryAudienceSheet?>(null)

    /** Otevřený výběr, kdo vlastní příběh uvidí; `null` = zavřený. */
    val audience: StateFlow<StoryAudienceSheet?> = _audience.asStateFlow()

    fun openAudience(key: StoryKey) {
        val current = _state.value.pages[key]?.audience ?: return
        _audience.value = StoryAudienceSheet(key, current)
        viewModelScope.launch {
            // Blízké přátele jde vybrat, jen když na seznamu někdo je — jinak by příběh nikdo neviděl.
            repository.closeFriends().onSuccess { friends ->
                _audience.update { it?.takeIf { s -> s.key == key }?.copy(closeFriends = friends.size) }
            }
        }
    }

    fun closeAudience() {
        _audience.value = null
    }

    private val _pickingPeople = MutableStateFlow<StoryAudienceSheet?>(null)

    /**
     * Otevřený výběr lidí pro „Vybraní lidé" (místo sheetu viditelnosti, přes celou plochu);
     * `null` = zavřený. Nese příběh a současnou viditelnost, kam se po potvrzení vrátí.
     */
    val pickingPeople: StateFlow<StoryAudienceSheet?> = _pickingPeople.asStateFlow()

    fun pickPeople() {
        _pickingPeople.value = _audience.value ?: return
        _audience.value = null
    }

    fun closePeoplePicker() {
        _pickingPeople.value = null
    }

    /** Vybraní lidé z výběru; prázdný výběr nic nemění — příběh by nikdo neviděl. */
    fun confirmPeople(userIds: Set<Long>) {
        val sheet = _pickingPeople.value ?: return
        _pickingPeople.value = null
        if (userIds.isEmpty()) {
            _notice.value = UiText(Res.string.story_audience_pick_someone)
            return
        }
        _audience.value = sheet
        setAudience(StoryAudience.Selected(userIds.toList()))
    }

    // Příběh, kterému se v editoru vyměňuje médium; po návratu se přečte znovu.
    private var replacing: StoryKey? = null

    fun replacingMedia(key: StoryKey) {
        replacing = key
    }

    /** Návrat z editoru: příběh s vyměněným médiem se dočte od TDLib (nové soubory). */
    fun reloadReplaced() {
        val key = replacing ?: return
        replacing = null
        viewModelScope.launch {
            val fresh = repository.story(key.posterChatId, key.storyId)
            _state.update { state ->
                if (key !in state.pages) state else state.copy(pages = state.pages + (key to fresh))
            }
        }
    }

    /** Nová viditelnost; přepne se hned a při chybě vrátí. */
    fun setAudience(audience: StoryAudience) {
        val sheet = _audience.value ?: return
        _audience.value = null
        if (audience == sheet.audience) return
        updatePage(sheet.key) { it.copy(audience = audience) }
        viewModelScope.launch {
            repository.setAudience(sheet.key.storyId, audience)
                .onSuccess { _notice.value = UiText(Res.string.story_audience_changed) }
                .onFailure { failure ->
                    updatePage(sheet.key) { it.copy(audience = sheet.audience) }
                    _notice.value = failure.userMessage(Res.string.story_manage_failed)
                }
        }
    }

    private val _captionEdit = MutableStateFlow<StoryKey?>(null)

    /** Příběh, kterému se upravuje popisek; `null` = dialog zavřený. */
    val captionEdit: StateFlow<StoryKey?> = _captionEdit.asStateFlow()

    fun openCaptionEdit(key: StoryKey) {
        if (_state.value.pages[key]?.canBeEdited == true) _captionEdit.value = key
    }

    fun closeCaptionEdit() {
        _captionEdit.value = null
    }

    /** Nový popisek (prázdný ho odebere); přepne se hned a při chybě vrátí. */
    fun editCaption(caption: String) {
        val key = _captionEdit.value ?: return
        _captionEdit.value = null
        val story = _state.value.pages[key] ?: return
        val trimmed = caption.trim()
        if (trimmed == story.caption) return
        updatePage(key) { it.copy(caption = trimmed) }
        viewModelScope.launch {
            repository.editCaption(key.posterChatId, key.storyId, trimmed)
                .onSuccess { _notice.value = UiText(Res.string.story_caption_saved) }
                .onFailure { failure ->
                    updatePage(key) { it.copy(caption = story.caption) }
                    _notice.value = failure.userMessage(Res.string.story_manage_failed)
                }
        }
    }

    private fun updatePage(key: StoryKey, change: (MessageMedia.Story) -> MessageMedia.Story) = _state.update { state ->
        val story = state.pages[key] ?: return@update state
        state.copy(pages = state.pages + (key to change(story)))
    }

    /**
     * Přepne reakci: táž reakce podruhé ji sundá, jiná ji vymění. Přepíná se hned (optimisticky)
     * a při chybě vrátí, jako reakce u zprávy.
     */
    fun react(key: StoryKey, reaction: String) {
        val story = _state.value.pages[key] ?: return
        val previous = story.chosenReaction
        val next = reaction.takeIf { it != previous }
        setReaction(key, next)
        viewModelScope.launch {
            repository.react(key.posterChatId, key.storyId, next).onFailure { failure ->
                setReaction(key, previous)
                _notice.value = failure.userMessage(Res.string.story_reaction_failed)
            }
        }
    }

    private fun setReaction(key: StoryKey, reaction: String?) = _state.update { state ->
        val story = state.pages[key] ?: return@update state
        state.copy(pages = state.pages + (key to story.copy(chosenReaction = reaction)))
    }

    /** Smaže vlastní příběh a prohlížeč přejde na další. */
    fun delete(key: StoryKey) {
        viewModelScope.launch {
            repository.delete(key.posterChatId, key.storyId)
                .onSuccess {
                    _state.update { it.withoutStory(key) }
                    _notice.value = UiText(Res.string.story_deleted)
                }
                .onFailure { failure -> _notice.value = failure.userMessage(Res.string.story_delete_failed) }
        }
    }

    /** Odešle odpověď; [onResult] dostane `true`, když odešla (pole se pak vyprázdní). */
    fun reply(key: StoryKey, text: String, onResult: (Boolean) -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.reply(key.posterChatId, key.storyId, trimmed)
                .onSuccess {
                    _notice.value = UiText(Res.string.story_reply_sent)
                    onResult(true)
                }
                .onFailure { failure ->
                    _notice.value = failure.userMessage(Res.string.story_reply_failed)
                    onResult(false)
                }
        }
    }

    /** Pošle zprávu do sledovaného živého vysílání; [onResult] jako u [reply]. */
    fun sendLiveMessage(text: String, onResult: (Boolean) -> Unit) {
        val watch = liveWatch ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            watch.send(trimmed)
                .onSuccess { onResult(true) }
                .onFailure { failure ->
                    _notice.value = failure.userMessage(Res.string.story_live_message_failed)
                    onResult(false)
                }
        }
    }

    /** Smaže zprávu z chatu vysílání (vlastní, nebo jako správce i s nahlášením spamu). */
    fun deleteLiveMessage(messageId: Int, reportSpam: Boolean) {
        val watch = liveWatch ?: return
        viewModelScope.launch {
            watch.delete(messageId, reportSpam).onFailure { failure ->
                _notice.value = failure.userMessage(Res.string.story_live_message_delete_failed)
            }
        }
    }

    /**
     * Načte aktuální příběh a ty, na které se nejspíš půjde dál: další u téhož autora a první
     * u sousedních autorů (tah do strany ukazuje sousední stránku hned).
     */
    private fun preload() {
        val current = _state.value
        val keys = buildList {
            add(current.current)
            current.posters.getOrNull(current.poster)?.let { author ->
                author.stories.getOrNull(current.storyIndex(current.poster) + 1)?.let { add(StoryKey(author.chatId, it.id)) }
            }
            add(current.keyAt(current.poster + 1))
            add(current.keyAt(current.poster - 1))
        }.filterNotNull()
        keys.forEach(::load)
    }

    private fun load(key: StoryKey) {
        if (key in _state.value.pages || !loading.add(key)) return
        viewModelScope.launch {
            val story = repository.story(key.posterChatId, key.storyId)
            loading.remove(key)
            _state.update { it.copy(pages = it.pages + (key to story)) }
            if (story.isOwn && story.canToggleOnProfile) loadPinned(key.posterChatId)
        }
    }

    override fun onCleared() {
        liveWatch?.stop()
        opened?.let { key -> appScope.launch { repository.close(key.posterChatId, key.storyId) } }
        opened = null
    }
}

/** Seznam diváků vlastního příběhu. [nextOffset] `""` = první stránka, `null` = víc jich není. */
data class StoryViewersState(
    val storyId: Int,
    val viewers: List<StoryViewer> = emptyList(),
    val total: Int = 0,
    val nextOffset: String? = "",
    val loading: Boolean = false,
    val error: UiText? = null,
    /** Seznam už server nedá (příběh je dávno pryč); ukáže se věta, ne prázdno. */
    val expired: Boolean = false,
)

/** Panel alb vlastního příběhu [key]: alba autora (`null` = načítají se) a ve kterých příběh je. */
/** Výběr viditelnosti vlastního příběhu; [closeFriends] = kolik lidí je na seznamu (`null`, dokud se nenačte). */
data class StoryAudienceSheet(val key: StoryKey, val audience: StoryAudience, val closeFriends: Int? = null)

data class StoryAlbumsState(
    val key: StoryKey,
    val albums: List<StoryAlbum>? = null,
    val inAlbums: Set<Int> = emptySet(),
    /** Zakládá se album; tlačítko je neaktivní, ať nevzniknou dvě. */
    val busy: Boolean = false,
    /** Poslední chyba; ukazuje se v panelu. */
    val error: UiText? = null,
    /** Řazení příběhů jednoho alba; `null` = panel ukazuje alba. */
    val storiesOrder: AlbumStoriesOrder? = null,
)

/** Příběhy alba [albumId] k seřazení; `null` = načítají se. */
data class AlbumStoriesOrder(val albumId: Int, val stories: List<MessageMedia.Story>? = null)
