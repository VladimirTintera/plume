package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.dragHandle
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chat_folders_add
import eu.tintera.plume.resources.chat_folders_all
import eu.tintera.plume.resources.chat_folders_all_fixed
import eu.tintera.plume.resources.chat_folders_create
import eu.tintera.plume.resources.chat_folders_help
import eu.tintera.plume.resources.chat_folders_mine
import eu.tintera.plume.resources.chat_folders_recommended
import eu.tintera.plume.resources.chat_folders_reorder
import eu.tintera.plume.resources.chat_folders_title
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.drag_indicator_24dp
import eu.tintera.plume.resources.folder_24dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Složky chatů v Nastavení: seznam s pořadím tažením za úchyt, nová složka a doporučené.
 *
 * - **Ťuknutí na složku otevře editor**, pořadí se mění jen úchytem — podržení řádku je volné
 *   a tah po celém řádku by se pral se scrollem.
 * - **„Všechny chaty" se netáhne a nic se přes ně nepřesouvá.** Bez Premium je server drží na
 *   začátku a jinou polohu odmítne; kdo je má jinde (Premium z jiného klienta), tomu zůstanou.
 * - **Blok zaoblených řádků, jako seznam chatů a pozvánky** — tentýž jazyk pro „seznam věcí".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatFoldersScreen(
    viewModel: ChatFoldersViewModel,
    onBack: () -> Unit,
    onEdit: (folderId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Snackbar mimo tělo efektu: vynulování chyby mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        scope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }

    val listState = rememberLazyListState()
    val dragState = rememberDragReorderState(
        listState = listState,
        canMove = { key -> key is Int },
        onMove = viewModel::move,
    )
    // Puštěný úchyt = konec tahu; pořadí se pošle serveru jedním voláním.
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.draggedKey }.drop(1).filter { it == null }.collect { viewModel.commitOrder() }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.chat_folders_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        val entries = state.entries
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
                    stringResource(Res.string.chat_folders_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            item(key = "mine-header") { FoldersHeader(stringResource(Res.string.chat_folders_mine)) }
            // „Nová složka" je první řádek bloku, jako „Vytvořit odkaz" u pozvánek.
            val count = entries.size + 1
            item(key = "create") {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.chat_folders_create)) },
                    leadingContent = { FolderIcon(Res.drawable.add_24dp, accent = true) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        headlineColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.animateItem().clip(groupItemShape(0, count)).clickable { onEdit(0) },
                )
            }
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                val folder = entry.folder
                val shape = groupItemShape(index + 1, count)
                val dragged = dragState.draggedKey == entry.key
                ListItem(
                    headlineContent = {
                        if (folder == null) {
                            Text(stringResource(Res.string.chat_folders_all))
                        } else {
                            val (text, ids) = remember(folder.title, folder.titleEntities) {
                                var emoji: Set<Long> = emptySet()
                                buildAnnotatedString { emoji = appendWithCustomEmoji(folder.title, folder.titleEntities) } to emoji
                            }
                            Text(text, inlineContent = customEmojiInlineContent(ids))
                        }
                    },
                    supportingContent = if (folder == null) {
                        { Text(stringResource(Res.string.chat_folders_all_fixed)) }
                    } else {
                        null
                    },
                    leadingContent = { FolderIcon(Res.drawable.folder_24dp, accent = false) },
                    trailingContent = if (folder != null) {
                        {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(48.dp).dragHandle(dragState, entry.key),
                            ) {
                                Icon(
                                    painterResource(Res.drawable.drag_indicator_24dp),
                                    contentDescription = stringResource(Res.string.chat_folders_reorder),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (dragged) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                    ),
                    // Tažená položka bez `animateItem`: animace umístění by se s posunem pod
                    // prstem prala (viz `DragReorder`).
                    modifier = (if (dragState.draggedKey == null) Modifier.animateItem() else Modifier)
                        .draggedItem(dragState, entry.key)
                        .clip(shape)
                        .then(if (folder != null) Modifier.clickable { onEdit(folder.id) } else Modifier),
                )
            }

            val recommended = state.recommended
            if (recommended.isNotEmpty()) {
                item(key = "recommended-header") {
                    FoldersHeader(stringResource(Res.string.chat_folders_recommended), Modifier.padding(top = 16.dp))
                }
                itemsIndexed(recommended, key = { _, it -> "rec-${it.draft.title}" }) { index, folder ->
                    ListItem(
                        headlineContent = { Text(folder.draft.title) },
                        supportingContent = { Text(folder.description) },
                        leadingContent = { FolderIcon(Res.drawable.folder_24dp, accent = false) },
                        trailingContent = {
                            if (state.adding == folder.draft.title) {
                                LoadingIndicator(Modifier.size(40.dp))
                            } else {
                                FilledTonalButton(onClick = { viewModel.addRecommended(folder) }, enabled = state.adding == null) {
                                    Text(stringResource(Res.string.chat_folders_add))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        modifier = Modifier.animateItem().clip(groupItemShape(index, recommended.size)),
                    )
                }
            }
        }
    }
}

@Composable
private fun FoldersHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, bottom = 6.dp),
    )
}

/** Ikona v tónovaném kolečku, jako u pozvánek; akce v akcentu, složka tlumeně. */
@Composable
internal fun FolderIcon(icon: DrawableResource, accent: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
