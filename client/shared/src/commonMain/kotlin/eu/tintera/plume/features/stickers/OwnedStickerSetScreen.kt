package eu.tintera.plume.features.stickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.telegram.OwnedStickerSet
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.stickerSetLink
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.TextFieldSlot
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.dragHandle
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.conversation.StickerPreview
import eu.tintera.plume.features.settings.SettingsActionRow
import eu.tintera.plume.core.telegram.stickerKeywords
import eu.tintera.plume.core.telegram.stickerKeywordsFit
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.sticker_keywords
import eu.tintera.plume.resources.sticker_keywords_replace
import eu.tintera.plume.resources.sticker_keywords_too_long
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.drag_indicator_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.sticker_set_add_sticker
import eu.tintera.plume.resources.sticker_set_change_emoji
import eu.tintera.plume.resources.sticker_set_cover
import eu.tintera.plume.resources.sticker_set_cover_custom
import eu.tintera.plume.resources.sticker_set_cover_first
import eu.tintera.plume.resources.sticker_set_cover_first_hint
import eu.tintera.plume.resources.sticker_set_cover_reset
import eu.tintera.plume.resources.sticker_set_delete
import eu.tintera.plume.resources.sticker_set_delete_confirm
import eu.tintera.plume.resources.sticker_set_delete_text
import eu.tintera.plume.resources.sticker_set_delete_title
import eu.tintera.plume.resources.sticker_set_edit_title
import eu.tintera.plume.resources.sticker_set_emoji_cancel
import eu.tintera.plume.resources.sticker_set_emoji_save
import eu.tintera.plume.resources.sticker_set_full
import eu.tintera.plume.resources.sticker_set_reorder
import eu.tintera.plume.resources.sticker_set_reorder_hint
import eu.tintera.plume.resources.sticker_set_save_title
import eu.tintera.plume.resources.sticker_set_set_cover
import eu.tintera.plume.resources.sticker_set_sticker_menu
import eu.tintera.plume.resources.sticker_set_sticker_remove
import eu.tintera.plume.resources.sticker_set_sticker_remove_confirm
import eu.tintera.plume.resources.sticker_set_sticker_remove_text
import eu.tintera.plume.resources.sticker_set_sticker_remove_title
import eu.tintera.plume.resources.sticker_set_title
import eu.tintera.plume.resources.sticker_emoji
import eu.tintera.plume.resources.undo_24dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace
import eu.tintera.plume.core.adaptive.WindowControlsSpace

/**
 * Úprava vlastní sady samolepek: název, obálka, samolepky v pořadí (tažením za úchyt), u každé emoji
 * a odebrání, přidání další z fotky a smazání celé sady. Zavře se po smazání.
 *
 * [onAddSticker] dostane fotku z výběru a sadu, do které má nová samolepka jít; `null` =
 * platforma samolepky vyrábět neumí.
 */
@Composable
fun OwnedStickerSetScreen(
    viewModel: OwnedStickerSetViewModel,
    onBack: () -> Unit,
    onAddSticker: ((path: String, width: Int, height: Int, setId: Long) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var editingEmoji by remember { mutableStateOf<StickerItem?>(null) }
    var editingKeywords by remember { mutableStateOf<StickerItem?>(null) }
    var removing by remember { mutableStateOf<StickerItem?>(null) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.errorShown()
        snackbarScope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    val picker = rememberAttachmentPicker(
        onPicked = { picked ->
            val set = state.set ?: return@rememberAttachmentPicker
            (picked.firstOrNull { it is OutgoingAttachment.Photo } as? OutgoingAttachment.Photo)?.let { photo ->
                onAddSticker?.invoke(photo.path, photo.width, photo.height, set.id)
            }
        },
        onError = {},
    )

    val listState = rememberLazyListState()
    val dragState = rememberDragReorderState(listState = listState, canMove = { key -> key is Long }, onMove = viewModel::move)
    // Puštěný úchyt = konec tahu; přesun se pošle serveru.
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.draggedKey }.drop(1).filter { it == null }.collect { viewModel.commitMove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.sticker_set_edit_title)) },
                navigationIcon = {
                    WindowControlsSpace {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                        }
                    }
                },
                actions = { WindowControlsEndSpace() },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val set = state.set
        if (set == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                modifier = Modifier.widthIn(max = ContentWidth.Form).fillMaxSize(),
            ) {
                item(key = "title") {
                    FormTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitleDraft,
                        singleLine = true,
                        label = { Text(stringResource(Res.string.sticker_set_title)) },
                        supportingText = { Text(stickerSetLink(set.name)) },
                        trailingIcon = if (state.canSaveTitle) {
                            {
                                TextFieldSlot {
                                    IconButton(onClick = viewModel::saveTitle, shapes = IconButtonDefaults.shapes()) {
                                        Icon(painterResource(Res.drawable.check_24dp), contentDescription = stringResource(Res.string.sticker_set_save_title))
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.saveTitle() }),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
                item(key = "cover") {
                    val customCover = set.customCover != null
                    ListItem(
                        leadingContent = {
                            Box(Modifier.size(COVER_SIZE), contentAlignment = Alignment.Center) {
                                if (state.coverBusy) {
                                    LoadingIndicator()
                                } else {
                                    state.cover?.let { StickerPreview(it, playing = false, modifier = Modifier.size(COVER_SIZE)) }
                                }
                            }
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    when {
                                        customCover -> Res.string.sticker_set_cover_custom
                                        state.canSetCover -> Res.string.sticker_set_cover_first_hint
                                        else -> Res.string.sticker_set_cover_first
                                    },
                                ),
                            )
                        },
                        trailingContent = if (customCover) {
                            {
                                WithTooltip(stringResource(Res.string.sticker_set_cover_reset)) {
                                    IconButton(onClick = viewModel::resetCover, enabled = !state.coverBusy, shapes = IconButtonDefaults.shapes()) {
                                        Icon(painterResource(Res.drawable.undo_24dp), contentDescription = stringResource(Res.string.sticker_set_cover_reset))
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow),
                        modifier = Modifier.padding(bottom = 16.dp).clip(groupItemShape(0, 1)),
                    ) { Text(stringResource(Res.string.sticker_set_cover)) }
                }
                item(key = "hint") {
                    Text(
                        stringResource(Res.string.sticker_set_reorder_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
                val stickers = state.stickers
                itemsIndexed(stickers, key = { _, sticker -> sticker.id }) { index, sticker ->
                    val dragged = dragState.draggedKey == sticker.id
                    ListItem(
                        leadingContent = { StickerPreview(sticker.preview, playing = false, modifier = Modifier.size(STICKER_SIZE)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StickerMenu(
                                    onSetCover = if (state.canSetCover && sticker.preview.fileId != null && !state.coverBusy) {
                                        { viewModel.setCover(sticker) }
                                    } else {
                                        null
                                    },
                                    onChangeEmoji = { editingEmoji = sticker },
                                    onChangeKeywords = { editingKeywords = sticker },
                                    onRemove = { removing = sticker },
                                )
                                Box(Modifier.size(48.dp).dragHandle(dragState, sticker.id), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(Res.drawable.drag_indicator_24dp), contentDescription = stringResource(Res.string.sticker_set_reorder))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (dragged) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.groupRow,
                        ),
                        // Tažená položka bez `animateItem`: animace umístění by se s posunem pod prstem prala.
                        modifier = (if (dragState.draggedKey == null) Modifier.animateItem() else Modifier)
                            .draggedItem(dragState, sticker.id)
                            .clip(groupItemShape(index, stickers.size)),
                    ) {
                        Text(sticker.preview.emoji, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item(key = "actions") {
                    GroupedColumn(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        if (onAddSticker != null) {
                            val full = stickers.size >= OwnedStickerSet.MAX_SIZE
                            SettingsActionRow(
                                icon = Res.drawable.sticker_24dp,
                                title = stringResource(if (full) Res.string.sticker_set_full else Res.string.sticker_set_add_sticker),
                                onClick = picker::pickMedia,
                                enabled = !full,
                            )
                        }
                        SettingsActionRow(
                            icon = Res.drawable.delete_24dp,
                            title = stringResource(Res.string.sticker_set_delete),
                            onClick = { deleting = true },
                            destructive = true,
                        )
                    }
                }
            }
        }
    }

    editingEmoji?.let { sticker ->
        EmojiDialog(
            initial = sticker.preview.emoji,
            onSave = { emojis ->
                editingEmoji = null
                viewModel.changeEmoji(sticker, emojis)
            },
            onDismiss = { editingEmoji = null },
        )
    }
    editingKeywords?.let { sticker ->
        KeywordsDialog(
            onSave = { text ->
                editingKeywords = null
                viewModel.changeKeywords(sticker, text)
            },
            onDismiss = { editingKeywords = null },
        )
    }
    removing?.let { sticker ->
        ConfirmDialog(
            title = stringResource(Res.string.sticker_set_sticker_remove_title),
            text = stringResource(Res.string.sticker_set_sticker_remove_text),
            confirmLabel = stringResource(Res.string.sticker_set_sticker_remove_confirm),
            icon = Res.drawable.delete_24dp,
            destructive = true,
            onConfirm = {
                removing = null
                viewModel.remove(sticker)
            },
            onDismiss = { removing = null },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(Res.string.sticker_set_delete_title),
            text = stringResource(Res.string.sticker_set_delete_text),
            confirmLabel = stringResource(Res.string.sticker_set_delete_confirm),
            icon = Res.drawable.delete_24dp,
            destructive = true,
            onConfirm = {
                deleting = false
                viewModel.deleteSet()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun StickerMenu(onSetCover: (() -> Unit)?, onChangeEmoji: () -> Unit, onChangeKeywords: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.sticker_set_sticker_menu))
        }
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                listOfNotNull(
                    onSetCover?.let { setCover ->
                        MenuAction(stringResource(Res.string.sticker_set_set_cover), Res.drawable.image_24dp) {
                            open = false
                            setCover()
                        }
                    },
                    MenuAction(stringResource(Res.string.sticker_set_change_emoji), Res.drawable.mood_24dp) {
                        open = false
                        onChangeEmoji()
                    },
                    MenuAction(stringResource(Res.string.sticker_keywords), Res.drawable.search_24dp) {
                        open = false
                        onChangeKeywords()
                    },
                ),
                listOf(
                    MenuAction(stringResource(Res.string.sticker_set_sticker_remove), Res.drawable.delete_24dp, destructive = true) {
                        open = false
                        onRemove()
                    },
                ),
            ),
        )
    }
}

/** Nová emoji samolepky — pole pro klávesnici, předvyplněné tím, co samolepka má. */
@Composable
private fun EmojiDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sticker_set_change_emoji)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.sticker_emoji)) },
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.sticker_set_emoji_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.sticker_set_emoji_cancel))
            }
        },
    )
}

/**
 * Klíčová slova samolepky. Pole začíná PRÁZDNÉ: TDLib dosavadní slova vydat neumí, takže věta
 * pod polem říká, že uložení je nahradí.
 */
@Composable
private fun KeywordsDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    val fits = stickerKeywordsFit(stickerKeywords(value))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sticker_keywords)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(KEYWORDS_FIELD_MAX) },
                singleLine = true,
                label = { Text(stringResource(Res.string.sticker_keywords)) },
                supportingText = {
                    Text(stringResource(if (fits) Res.string.sticker_keywords_replace else Res.string.sticker_keywords_too_long))
                },
                isError = !fits,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = fits, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.sticker_set_emoji_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.sticker_set_emoji_cancel))
            }
        },
    )
}

/** Strop pole klíčových slov — limit hlídá [stickerKeywordsFit], tohle jen brzdí vložený román. */
private const val KEYWORDS_FIELD_MAX = 200

private val STICKER_SIZE = 56.dp
private val COVER_SIZE = 48.dp
