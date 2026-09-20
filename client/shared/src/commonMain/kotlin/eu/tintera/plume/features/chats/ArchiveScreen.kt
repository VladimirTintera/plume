package eu.tintera.plume.features.chats

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_filled_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.chats_archive_empty
import eu.tintera.plume.resources.chats_archive_empty_title
import eu.tintera.plume.resources.common_back
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
    onBack: () -> Unit,
    onChatArchived: ((ArchiveChange) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    ChatActionsHost(viewModel, snackbarHostState, onArchived = onChatArchived)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Obyčejný bar bez scroll behavior: nemění barvu, takže nevznikne tónovaný pruh
            // přes celou šířku, kvůli kterému hlavní seznam opustil `LargeFlexibleTopAppBar`.
            TopAppBar(
                title = { Text(stringResource(Res.string.chats_archive)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        ChatListContent(
            viewModel = viewModel,
            selectedChatId = selectedChatId,
            onChatClick = onChatClick,
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
