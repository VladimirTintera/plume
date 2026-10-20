package eu.tintera.plume.features.stories

import eu.tintera.plume.core.ui.resolve
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.STORY_ALBUM_NAME_MAX
import eu.tintera.plume.core.telegram.StoryAlbum
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.story_album_create
import eu.tintera.plume.resources.story_album_delete
import eu.tintera.plume.resources.story_album_delete_text
import eu.tintera.plume.resources.story_album_delete_title
import eu.tintera.plume.resources.story_album_name
import eu.tintera.plume.resources.story_album_name_counter
import eu.tintera.plume.resources.story_album_new
import eu.tintera.plume.resources.story_album_rename
import eu.tintera.plume.resources.story_album_save
import eu.tintera.plume.resources.story_albums_hint
import eu.tintera.plume.resources.story_albums_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Alba vlastního příběhu: zaškrtnutá jsou ta, ve kterých příběh je (ťuknutí přepne), nahoře
 * „Nové album" (rovnou s tímhle příběhem), u alba ⋮ s přejmenováním a smazáním. Alba patří
 * autorovi, ne příběhu — přejmenování a smazání platí pro celé album.
 */
@Composable
internal fun StoryAlbumsSheet(
    state: StoryAlbumsState,
    onToggle: (albumId: Int) -> Unit,
    onCreate: (name: String) -> Unit,
    onRename: (albumId: Int, name: String) -> Unit,
    onDelete: (albumId: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Dialog nad sheetem: nové album (`null` id), nebo přejmenování.
    var naming by remember { mutableStateOf<Pair<Int?, String>?>(null) }
    var deleting by remember { mutableStateOf<StoryAlbum?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(stringResource(Res.string.story_albums_title), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            Text(
                stringResource(Res.string.story_albums_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 16.dp),
            )
            state.error?.let { error ->
                Text(
                    error.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
                )
            }
            val albums = state.albums
            if (albums == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }
            GroupedColumn(Modifier.padding(horizontal = 16.dp)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(painterResource(Res.drawable.add_24dp), contentDescription = null) },
                    headlineContent = { Text(stringResource(Res.string.story_album_new)) },
                    modifier = Modifier.clickable(enabled = !state.busy) { naming = null to "" },
                )
                albums.forEach { album ->
                    AlbumRow(
                        album = album,
                        checked = album.id in state.inAlbums,
                        onToggle = { onToggle(album.id) },
                        onRename = { naming = album.id to album.name },
                        onDelete = { deleting = album },
                    )
                }
            }
        }
    }

    naming?.let { (albumId, initial) ->
        AlbumNameDialog(
            title = stringResource(if (albumId == null) Res.string.story_album_create else Res.string.story_album_rename),
            initial = initial,
            onConfirm = { name ->
                naming = null
                if (albumId == null) onCreate(name) else onRename(albumId, name)
            },
            onDismiss = { naming = null },
        )
    }
    deleting?.let { album ->
        ConfirmDialog(
            title = stringResource(Res.string.story_album_delete_title, album.name),
            text = stringResource(Res.string.story_album_delete_text),
            confirmLabel = stringResource(Res.string.story_album_delete),
            icon = Res.drawable.delete_24dp,
            destructive = true,
            onConfirm = {
                deleting = null
                onDelete(album.id)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun AlbumRow(album: StoryAlbum, checked: Boolean, onToggle: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // Zaškrtávátko je jen ukazatel; ťuká se celý řádek (čtečka ho čte jako zaškrtávací).
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        headlineContent = { Text(album.name, maxLines = 1) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.more_vert_24dp), stringResource(Res.string.profile_more))
                }
                ExpressiveMenu(
                    expanded = menu,
                    onDismiss = { menu = false },
                    groups = listOf(
                        listOf(
                            MenuAction(stringResource(Res.string.story_album_rename), Res.drawable.edit_24dp) {
                                menu = false
                                onRename()
                            },
                        ),
                        listOf(
                            MenuAction(stringResource(Res.string.story_album_delete), Res.drawable.delete_24dp, destructive = true) {
                                menu = false
                                onDelete()
                            },
                        ),
                    ),
                )
            }
        },
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    )
}

/** Název alba: 1–12 znaků (víc Telegram nevezme), potvrdit jde jen neprázdný. */
@Composable
private fun AlbumNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(STORY_ALBUM_NAME_MAX) },
                label = { Text(stringResource(Res.string.story_album_name)) },
                singleLine = true,
                supportingText = { Text(stringResource(Res.string.story_album_name_counter, name.length, STORY_ALBUM_NAME_MAX)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.story_album_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}
