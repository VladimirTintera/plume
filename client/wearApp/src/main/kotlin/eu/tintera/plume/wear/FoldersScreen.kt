package eu.tintera.plume.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.wear_folder_unread
import eu.tintera.plume.resources.wear_folders_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Výběr složky: složky Telegramu v pořadí, které si uživatel nastavil, a na konci Archiv, každá
 * s počtem nepřečtených chatů (počítá TDLib, i pro složku, která se ještě neotevřela). Ťuknutí
 * otevře seznam té složky ([ChatListScreen] s [ChatFolder]), zpět tahem.
 *
 * Dřív hodinky ukazovaly jen hlavní seznam: archivovaný chat (i ten archivovaný z hodinek
 * podržením karty) se z nich nedal najít ani vrátit.
 */
@Composable
fun FoldersScreen(onOpen: (ChatFolder) -> Unit) {
    val chats = koinInject<ChatRepository>()
    val folders by chats.chatFolders.collectAsState()
    val unread by chats.folderUnread.collectAsState()
    val archiveUnread by chats.archiveUnread.collectAsState()
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(Res.string.wear_folders_title)) }
            }
            items(folders.folders) { folder ->
                FolderButton(
                    title = folder.title,
                    icon = Res.drawable.folder_24dp,
                    unread = unread[folder.id]?.chats ?: 0,
                    onClick = { onOpen(ChatFolder.Custom(folder.id)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item {
                FolderButton(
                    title = stringResource(Res.string.chats_archive),
                    icon = Res.drawable.archive_24dp,
                    unread = archiveUnread.chats,
                    onClick = { onOpen(ChatFolder.Archive) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

@Composable
private fun FolderButton(
    title: String,
    icon: DrawableResource,
    unread: Int,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Button(
        onClick = onClick,
        icon = { Icon(painterResource(icon), contentDescription = null) },
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (unread > 0) {
            { Text(pluralStringResource(Res.plurals.wear_folder_unread, unread, unread)) }
        } else {
            null
        },
        colors = ButtonDefaults.filledTonalButtonColors(),
        modifier = modifier,
        transformation = transformation,
    )
}
