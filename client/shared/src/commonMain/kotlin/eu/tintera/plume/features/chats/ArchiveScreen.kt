package eu.tintera.plume.features.chats

import eu.tintera.plume.resources.chats_archive_read_all
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.core.ui.WithTooltip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import eu.tintera.plume.features.stories.StoryStack
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.ReorderTopBar
import eu.tintera.plume.resources.chats_reorder_title
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_filled_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.chats_archive_empty
import eu.tintera.plume.resources.chats_archive_empty_title
import eu.tintera.plume.resources.chats_archive_settings
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.tune_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Archivované chaty.
 *
 * Seznam i akce nad chatem jsou tytéž jako v hlavním seznamu ([ChatListContent],
 * [ChatActionsHost]); jiná je jen hlavička. Hledání ani karta s aktualizací sem nepatří — hledá
 * se z hlavního seznamu a archiv je obrazovka, na kterou se chodí jednou za čas.
 * Na širokém rozvržení je archiv LIST panel samostatné scény archivu: má vlastní detail
 * panel [ArchiveConversationRoute], takže nezasahuje do otevřené konverzace hlavního seznamu.
 * Šipka zpět je proto i tam — vede na hlavní seznam.
 */
@Composable
fun ArchiveScreen(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    onForumClick: (Long) -> Unit,
    onBack: () -> Unit,
    onChatArchived: ((ArchiveChange) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Ťuknutí na avatar s kroužkem příběhů. */
    onOpenStories: ((Long) -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val archiveSettings by viewModel.archiveSettings.collectAsStateWithLifecycle()
    val storyStack by viewModel.storyStack.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.archiveSettingsOpen.collectAsStateWithLifecycle()
    val chatFolders by viewModel.chatFolders.collectAsStateWithLifecycle()
    val reorder by viewModel.reorder.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionActions by viewModel.selectionActions.collectAsStateWithLifecycle()
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    val archiveUnread by viewModel.archiveUnread.collectAsStateWithLifecycle()

    ChatActionsHost(viewModel, snackbarHostState, onArchived = onChatArchived)

    // Sheet jen s načteným nastavením — tlačítko se bez něj stejně nenabízí.
    archiveSettings?.let { settings ->
        if (settingsOpen) {
            ArchiveSettingsSheet(
                settings = settings,
                onArchiveUnknownSenders = viewModel::setArchiveUnknownSenders,
                onKeepUnmutedArchived = viewModel::setKeepUnmutedArchived,
                hasFolders = chatFolders.folders.isNotEmpty(),
                onKeepFromFoldersArchived = viewModel::setKeepFromFoldersArchived,
                onDismiss = viewModel::closeArchiveSettings,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Lišta výběru víc chatů, stejná jako v hlavním seznamu. Ve slotu `bottomBar`, takže
        // snackbar stojí nad ní a seznam se o ni odsadí.
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                ChatSelectionToolbar(
                    actions = selectionActions,
                    callbacks = viewModel.selectionCallbacks,
                    count = selection?.count ?: 0,
                )
            }
        },
        topBar = {
            reorder?.let { current ->
                ReorderTopBar(
                    title = stringResource(Res.string.chats_reorder_title),
                    busy = current.busy,
                    onCancel = viewModel::cancelReorder,
                    onDone = viewModel::saveReorder,
                )
                return@Scaffold
            }
            selection?.let { current ->
                ChatSelectionTopBar(
                    count = current.count,
                    allSelected = allSelected,
                    onSelectAll = viewModel::selectAll,
                    onClose = viewModel::closeSelection,
                )
                return@Scaffold
            }
            // Obyčejný bar bez scroll behavior: nemění barvu, takže nevznikne tónovaný pruh
            // přes celou šířku, kvůli kterému hlavní seznam opustil `LargeFlexibleTopAppBar`.
            TopAppBar(
                title = { Text(stringResource(Res.string.chats_archive)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    // Skrytí autoři příběhů: v archivu jako v pilulce hlavního seznamu.
                    if (onOpenStories != null) {
                        StoryStack(
                            posters = storyStack,
                            rim = MaterialTheme.colorScheme.surface,
                            onOpen = onOpenStories,
                        )
                    }
                    // Jen když je co číst; jinak by ikona slibovala akci, která nic neudělá.
                    if (archiveUnread.chats > 0) {
                        WithTooltip(stringResource(Res.string.chats_archive_read_all)) {
                            IconButton(onClick = viewModel::readArchive, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    painterResource(Res.drawable.done_all_24dp),
                                    contentDescription = stringResource(Res.string.chats_archive_read_all),
                                )
                            }
                        }
                    }
                    /*
                     * Jedna ikona místo ⋮ s jedinou položkou: uvnitř archivu nemá posuvník co
                     * jiného znamenat než jeho nastavení. Nenabízí se, dokud (nebo když) ho
                     * TDLib nevydá — prázdný sheet by slíbil volby, které nejsou.
                     */
                    if (archiveSettings != null) {
                        IconButton(onClick = viewModel::openArchiveSettings, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painterResource(Res.drawable.tune_24dp),
                                contentDescription = stringResource(Res.string.chats_archive_settings),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        ChatListContent(
            viewModel = viewModel,
            selectedChatId = selectedChatId,
            onChatClick = onChatClick,
            onForumClick = onForumClick,
            onOpenStories = onOpenStories,
            padding = padding,
            // Stejný prázdný stav jako placeholder detailu vedle — na široké ploše stojí oba
            // vedle sebe a dva různé vzhledy pro „tady nic není" by se přely.
            empty = {
                IntroEmptyState(
                    icon = Res.drawable.archive_filled_24dp,
                    title = stringResource(Res.string.chats_archive_empty_title),
                    text = stringResource(Res.string.chats_archive_empty),
                )
            },
        )
    }
}
