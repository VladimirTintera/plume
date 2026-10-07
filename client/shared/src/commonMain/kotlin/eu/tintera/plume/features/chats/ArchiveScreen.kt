package eu.tintera.plume.features.chats

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val archiveSettings by viewModel.archiveSettings.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.archiveSettingsOpen.collectAsStateWithLifecycle()
    val chatFolders by viewModel.chatFolders.collectAsStateWithLifecycle()
    val reorder by viewModel.reorder.collectAsStateWithLifecycle()

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
