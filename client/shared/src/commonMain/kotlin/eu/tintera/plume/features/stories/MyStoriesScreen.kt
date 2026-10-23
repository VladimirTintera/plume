package eu.tintera.plume.features.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.profile.ProfileStories
import eu.tintera.plume.features.profile.ProfileStoriesGrid
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.my_stories_archive
import eu.tintera.plume.resources.my_stories_archive_empty
import eu.tintera.plume.resources.my_stories_failed
import eu.tintera.plume.resources.my_stories_profile
import eu.tintera.plume.resources.my_stories_profile_empty
import eu.tintera.plume.resources.my_stories_title
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Co obrazovka Moje příběhy ukazuje: příběhy v profilu, nebo celý archiv i s vypršelými. */
enum class MyStoriesTab(val label: StringResource, val empty: StringResource) {
    Profile(Res.string.my_stories_profile, Res.string.my_stories_profile_empty),
    Archive(Res.string.my_stories_archive, Res.string.my_stories_archive_empty),
}

data class MyStoriesState(
    val tab: MyStoriesTab = MyStoriesTab.Profile,
    val stories: ProfileStories = ProfileStories(),
    /** Vlastní účet — autor příběhů pro prohlížeč; `null`, dokud se nezjistí. */
    val chatId: Long? = null,
    val error: UiText? = null,
)

/**
 * Moje příběhy (menu účtu): v profilu (`getChatPostedToChatPageStories`) a archiv
 * (`getChatArchivedStories`, i vypršelé). Vlastní profil s příběhy Plume nemá, takže tohle je
 * jediné místo, kde se k vypršelým příběhům dostat. Spravují se v prohlížeči (⋮).
 *
 * **Po návratu z prohlížeče se načte znovu** ([refresh]): příběh odebraný z profilu nebo nově
 * připnutý se má v mřížce hned projevit.
 */
class MyStoriesViewModel(private val repository: StoryRepository) : ViewModel() {

    private val _state = MutableStateFlow(MyStoriesState())
    val state: StateFlow<MyStoriesState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun select(tab: MyStoriesTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab, stories = ProfileStories()) }
        load(reset = true)
    }

    fun refresh() = load(reset = true)

    fun loadMore() = load(reset = false)

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (!reset && (current.stories.loading || !current.stories.hasMore)) return
        val tab = current.tab
        loadJob?.cancel()
        _state.update { it.copy(stories = it.stories.copy(loading = true)) }
        loadJob = viewModelScope.launch {
            val chatId = _state.value.chatId ?: repository.myChatId()
            if (chatId == null) {
                _state.update { it.copy(stories = it.stories.copy(loading = false, hasMore = false)) }
                return@launch
            }
            val from = if (reset) 0 else _state.value.stories.items.lastOrNull()?.storyId ?: 0
            val page = when (tab) {
                MyStoriesTab.Profile -> repository.profileStories(chatId, from)
                MyStoriesTab.Archive -> repository.archivedStories(chatId, from)
            }
            page
                .onSuccess { found ->
                    _state.update { state ->
                        if (state.tab != tab) return@update state
                        val items = (if (reset) emptyList() else state.stories.items) + found.stories
                        val distinct = items.distinctBy { it.storyId }
                        val added = distinct.size - (if (reset) 0 else state.stories.items.size)
                        state.copy(
                            chatId = chatId,
                            stories = state.stories.copy(
                                items = distinct,
                                total = found.totalCount,
                                // Připnuté vydá jen první stránka; u archivu jsou prázdné.
                                pinnedIds = if (reset) found.pinnedIds else state.stories.pinnedIds,
                                hasMore = added > 0 && distinct.size < found.totalCount,
                                loading = false,
                            ),
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            chatId = chatId,
                            stories = it.stories.copy(loading = false, hasMore = false),
                            error = failure.userMessage(Res.string.my_stories_failed),
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyStoriesScreen(
    viewModel: MyStoriesViewModel,
    onBack: () -> Unit,
    onOpenStories: (chatId: Long, storyIds: List<Int>, startStoryId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val errorText = state.error?.resolve()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            viewModel.consumeError()
            snackbarHostState.showSnackbar(errorText)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.my_stories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                MyStoriesTab.entries.forEachIndexed { index, tab ->
                    val checked = tab == state.tab
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { if (!checked) viewModel.select(tab) },
                        shapes = if (index == 0) {
                            ButtonGroupDefaults.connectedLeadingButtonShapes()
                        } else {
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (checked) {
                            Icon(
                                painterResource(Res.drawable.check_24dp),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp).size(ButtonDefaults.IconSize),
                            )
                        }
                        Text(stringResource(tab.label))
                    }
                }
            }
            val stories = state.stories
            ProfileStoriesGrid(
                stories = stories,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                onOpen = { storyId ->
                    state.chatId?.let { onOpenStories(it, stories.items.map { story -> story.storyId }, storyId) }
                },
                onLoadMore = viewModel::loadMore,
                onSelectAlbum = {},
                emptyText = stringResource(state.tab.empty),
            )
        }
    }
}
