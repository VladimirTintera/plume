package eu.tintera.plume.features.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.QuickReaction
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
    private val repository: StoryRepository,
    private val appScope: CoroutineScope,
    /** Emoji, která jdou dát jako reakce (výběr přes „+"); bez něj jen srdce. */
    quickReaction: QuickReaction? = null,
) : ViewModel() {

    /** Emoji do výběru reakcí v pořadí od serveru. */
    val reactionChoices: StateFlow<List<String>> = quickReaction?.choices ?: MutableStateFlow(emptyList())

    private val _notice = MutableStateFlow<UiText?>(null)

    /** Krátké hlášení nad lištou (odpověď odeslána, chyba); UI ho po zobrazení sundá [noticeShown]. */
    val notice: StateFlow<UiText?> = _notice.asStateFlow()

    private val _state = MutableStateFlow(
        viewerPosters(repository.active.value, startChatId).let { posters ->
            StoriesState(
                posters = posters,
                poster = posters.indexOfFirst { it.chatId == startChatId }.coerceAtLeast(0),
                closed = posters.isEmpty(),
            )
        },
    )
    val state: StateFlow<StoriesState> = _state.asStateFlow()

    /** Příběh, o kterém TDLib ví, že je otevřený. */
    private var opened: StoryKey? = null

    private val loading = mutableSetOf<StoryKey>()

    init {
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

    fun noticeShown() {
        _notice.value = null
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
        }
    }

    override fun onCleared() {
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
