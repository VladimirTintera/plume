package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.sticker_set_edit
import eu.tintera.plume.resources.sticker_create_from_photo
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import androidx.compose.foundation.clickable
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.core.ui.GroupEmptyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.StickerSetKind
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.ui.CustomEmojiImage
import eu.tintera.plume.resources.emoji_count
import eu.tintera.plume.resources.emoji_sets_empty
import eu.tintera.plume.resources.emoji_sets_help
import eu.tintera.plume.resources.emoji_sets_title
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.dragHandle
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.conversation.StickerPreview
import eu.tintera.plume.features.conversation.StickerSetSheet
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_undo
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.drag_indicator_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.sticker_sets_archive
import eu.tintera.plume.resources.sticker_sets_archived
import eu.tintera.plume.resources.sticker_sets_archived_notice
import eu.tintera.plume.resources.sticker_sets_empty
import eu.tintera.plume.resources.sticker_sets_help
import eu.tintera.plume.resources.sticker_sets_load_more
import eu.tintera.plume.resources.sticker_sets_mine
import eu.tintera.plume.resources.sticker_sets_more
import eu.tintera.plume.resources.sticker_sets_remove
import eu.tintera.plume.resources.sticker_sets_removed_notice
import eu.tintera.plume.resources.sticker_sets_reorder
import eu.tintera.plume.resources.sticker_sets_title
import eu.tintera.plume.resources.stickers_add
import eu.tintera.plume.resources.stickers_count
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.GroupTitle

/**
 * Sady samolepek v Nastavení: pořadí tažením za úchyt, archivace a odebrání, archivované sady.
 *
 * - **Pořadí je pořadí záložek ve výběru samolepek.** Při desítkách sad je to jediná cesta, jak
 *   mít oblíbené sady na začátku, bez dojíždění přes celý řádek záložek.
 * - **Ťuknutí na sadu otevře její detail jen na prohlížení** (`StickerSetSheet` bez `onPick`) —
 *   v Nastavení není kam poslat. Pořadí se mění jen úchytem, jako u složek chatů: tah po celém
 *   řádku by se pral se scrollem.
 * - **Archivovat a Odebrat jsou v nabídce řádku**, ne jako tlačítka: v seznamu desítek sad by
 *   dvě tlačítka na řádek byla šum. Odebrat je ve vlastní skupině chybovou barvou a u oficiálních
 *   sad chybí (server je odebrat nedovolí). Ani jedno se neptá — snackbar nabídne „Vrátit".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StickerSetsScreen(
    /** Samolepky, nebo sady vlastních emoji ([StickerSetKind.Emoji]) — táž obrazovka, jiné texty. */
    kind: StickerSetKind,
    viewModel: StickerSetsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Samolepka z fotky z galerie; `null` = platforma ji vyrobit neumí (jen samolepky, ne emoji). */
    onCreateSticker: ((path: String, width: Int, height: Int) -> Unit)? = null,
    /** Úprava vlastní sady; nabízí se jen u sad, které uživatel vytvořil. */
    onEditSet: ((setId: Long) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Z výběru se bere první fotka; video samolepkou z fotky být nemůže.
    val photoPicker = rememberAttachmentPicker(
        onPicked = { picked ->
            (picked.firstOrNull { it is OutgoingAttachment.Photo } as? OutgoingAttachment.Photo)?.let { photo ->
                onCreateSticker?.invoke(photo.path, photo.width, photo.height)
            }
        },
        onError = viewModel::showError,
    )
    // Snackbar mimo tělo efektu: vynulování události mění klíč a zrušilo by ho (viz „Vrátit").
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        scope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        scope.launch {
            val message = when (notice) {
                is StickerSetNotice.Archived -> readString(Res.string.sticker_sets_archived_notice, notice.set.title)
                is StickerSetNotice.Removed -> readString(Res.string.sticker_sets_removed_notice, notice.set.title)
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = readString(Res.string.chats_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(notice)
        }
    }

    // Sada otevřená v detailu. Přežije otočení — prohlížení se tím nepřeruší.
    var openSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    openSetId?.let { setId -> StickerSetSheet(setId = setId, onPick = null, onDismiss = { openSetId = null }) }

    val listState = rememberLazyListState()
    val dragState = rememberDragReorderState(
        listState = listState,
        // Táhnout jde jen nainstalované (klíč = id sady); archivované mají klíč s předponou.
        canMove = { key -> key is Long },
        onMove = viewModel::move,
    )
    // Puštěný úchyt = konec tahu; pořadí se pošle serveru jedním voláním.
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.draggedKey }.drop(1).filter { it == null }.collect { viewModel.commitOrder() }
    }

    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(
                title = stringResource(if (kind == StickerSetKind.Emoji) Res.string.emoji_sets_title else Res.string.sticker_sets_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val installed = state.installed
        if (installed == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "help") {
                Text(
                    stringResource(if (kind == StickerSetKind.Emoji) Res.string.emoji_sets_help else Res.string.sticker_sets_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            if (onCreateSticker != null && kind == StickerSetKind.Stickers) {
                item(key = "create") {
                    GroupedColumn(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        SettingsActionRow(
                            icon = Res.drawable.sticker_24dp,
                            title = stringResource(Res.string.sticker_create_from_photo),
                            onClick = photoPicker::pickMedia,
                        )
                    }
                }
            }
            item(key = "mine-header") { GroupTitle(stringResource(Res.string.sticker_sets_mine), Modifier.padding(bottom = 6.dp)) }
            if (installed.isEmpty()) {
                item(key = "empty") {
                    GroupEmptyRow(
                        stringResource(if (kind == StickerSetKind.Emoji) Res.string.emoji_sets_empty else Res.string.sticker_sets_empty),
                        icon = Res.drawable.sticker_24dp,
                    )
                }
            }
            itemsIndexed(installed, key = { _, set -> set.id }) { index, set ->
                val dragged = dragState.draggedKey == set.id
                SetRow(
                    set = set,
                    containerColor = if (dragged) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SetMenu(
                                set = set,
                                onEdit = onEditSet?.takeIf { set.isOwned }?.let { edit -> { edit(set.id) } },
                                onArchive = { viewModel.archive(set) },
                                onRemove = { viewModel.remove(set) },
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(48.dp).dragHandle(dragState, set.id),
                            ) {
                                Icon(
                                    painterResource(Res.drawable.drag_indicator_24dp),
                                    contentDescription = stringResource(Res.string.sticker_sets_reorder),
                                )
                            }
                        }
                    },
                    // Tažená položka bez `animateItem`: animace umístění by se s posunem pod
                    // prstem prala (viz `DragReorder`).
                    modifier = (if (dragState.draggedKey == null) Modifier.animateItem() else Modifier)
                        .draggedItem(dragState, set.id)
                        .clip(groupItemShape(index, installed.size))
                        .clickable { openSetId = set.id },
                )
            }

            val archived = state.archived
            if (archived.isNotEmpty()) {
                item(key = "archived-header") {
                    GroupTitle(stringResource(Res.string.sticker_sets_archived), Modifier.padding(top = 16.dp, bottom = 6.dp))
                }
                itemsIndexed(archived, key = { _, set -> "archived-${set.id}" }) { index, set ->
                    SetRow(
                        set = set,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        trailing = {
                            FilledTonalButton(onClick = { viewModel.unarchive(set) }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(Res.string.stickers_add))
                            }
                        },
                        modifier = Modifier.animateItem()
                            .clip(groupItemShape(index, archived.size))
                            .clickable { openSetId = set.id },
                    )
                }
                if (state.hasMoreArchived) {
                    item(key = "archived-more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (state.loadingMoreArchived) {
                                LoadingIndicator(Modifier.size(40.dp))
                            } else {
                                TextButton(onClick = viewModel::loadMoreArchived, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(Res.string.sticker_sets_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Řádek sady: obálka, název, počet samolepek a ovládání vpravo. */
@Composable
private fun SetRow(
    set: StickerSetSummary,
    containerColor: Color,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(set.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val count = if (set.coverCustomEmojiId != null) Res.plurals.emoji_count else Res.plurals.stickers_count
            Text(pluralStringResource(count, set.size, set.size))
        },
        leadingContent = {
            // Obálka nehraje: v seznamu desítek sad by se hýbalo všechno naráz (jako záložky výběru).
            // Sada emoji přes `CustomEmojiImage` — jednobarevné emoji obarví, samolepka by byla bílá.
            val cover = set.cover
            val emoji = set.coverCustomEmojiId
            if (emoji != null) {
                CustomEmojiImage(id = emoji, fallback = "", modifier = Modifier.size(COVER_SIZE))
            } else if (cover != null) {
                StickerPreview(cover, playing = false, modifier = Modifier.size(COVER_SIZE))
            } else {
                Box(Modifier.size(COVER_SIZE), contentAlignment = Alignment.Center) {
                    Text(set.title.take(1), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = modifier,
    )
}

/**
 * Nabídka řádku: u vlastní sady Upravit, pak Archivovat, a ve vlastní skupině Odebrat
 * (u oficiálních sad chybí).
 */
@Composable
private fun SetMenu(
    set: StickerSetSummary,
    onEdit: (() -> Unit)?,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.sticker_sets_more, set.title))
        }
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                listOfNotNull(
                    onEdit?.let { edit ->
                        MenuAction(stringResource(Res.string.sticker_set_edit), Res.drawable.edit_24dp) {
                            open = false
                            edit()
                        }
                    },
                    MenuAction(stringResource(Res.string.sticker_sets_archive), Res.drawable.archive_24dp) {
                        open = false
                        onArchive()
                    },
                ),
                listOfNotNull(
                    MenuAction(stringResource(Res.string.sticker_sets_remove), Res.drawable.delete_24dp, destructive = true) {
                        open = false
                        onRemove()
                    }.takeIf { !set.isOfficial },
                ),
            ),
        )
    }
}

/** Obálka sady v řádku — jako avatar v seznamu chatů menší, ať řádek nevyroste. */
private val COVER_SIZE = 40.dp
