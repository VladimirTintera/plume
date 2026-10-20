package eu.tintera.plume.features.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.StoryRepository
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
) : ViewModel() {

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
