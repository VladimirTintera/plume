package eu.tintera.plume.features.settings

import org.jetbrains.compose.resources.pluralStringResource
import eu.tintera.plume.resources.chat_folders_ai_more
import eu.tintera.plume.resources.chat_folders_ai_failed
import eu.tintera.plume.resources.chat_folders_ai_model_missing
import eu.tintera.plume.resources.chat_folders_ai_nothing
import eu.tintera.plume.resources.chat_folders_ai_working
import eu.tintera.plume.resources.chat_folders_ai_suggest_summary
import eu.tintera.plume.resources.chat_folders_ai_suggest
import eu.tintera.plume.resources.chat_folders_ai
import eu.tintera.plume.resources.auto_awesome_24dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import eu.tintera.plume.core.ui.FolderIcon
import eu.tintera.plume.core.ui.folderIcon
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.dragHandle
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.chat_folders_add
import eu.tintera.plume.resources.chat_folders_all
import eu.tintera.plume.resources.chat_folders_all_fixed
import eu.tintera.plume.resources.chat_folders_create
import eu.tintera.plume.resources.chat_folders_help
import eu.tintera.plume.resources.chat_folders_mine
import eu.tintera.plume.resources.chat_folders_recommended
import eu.tintera.plume.resources.chat_folders_reorder
import eu.tintera.plume.resources.chat_folders_title
import eu.tintera.plume.resources.drag_indicator_24dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.GroupTitle

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

    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(title = stringResource(Res.string.chat_folders_title), onBack = onBack, scrollBehavior = scrollBehavior)
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
            item(key = "mine-header") { GroupTitle(stringResource(Res.string.chat_folders_mine), Modifier.padding(bottom = 6.dp)) }
            // „Nová složka" je první řádek bloku, jako „Vytvořit odkaz" u pozvánek.
            val count = entries.size + 1
            item(key = "create") {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.chat_folders_create)) },
                    leadingContent = { FolderIcon(Res.drawable.add_24dp, accent = true) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.groupRow,
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
                    leadingContent = { FolderIcon(if (folder == null) Res.drawable.chat_24dp else folderIcon(folder.iconName), accent = false) },
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
                            MaterialTheme.colorScheme.groupRow
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
                    GroupTitle(stringResource(Res.string.chat_folders_recommended), Modifier.padding(top = 16.dp, bottom = 6.dp))
                }
                itemsIndexed(recommended, key = { _, it -> "rec-${it.draft.title}" }) { index, folder ->
                    ListItem(
                        headlineContent = { Text(folder.draft.title) },
                        supportingContent = { Text(folder.description) },
                        leadingContent = { FolderIcon(folderIcon(folder.iconName), accent = false) },
                        trailingContent = {
                            if (state.adding == folder.draft.title) {
                                LoadingIndicator(Modifier.size(40.dp))
                            } else {
                                FilledTonalButton(onClick = { viewModel.addRecommended(folder) }, enabled = state.adding == null, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(Res.string.chat_folders_add))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow),
                        modifier = Modifier.animateItem().clip(groupItemShape(index, recommended.size)),
                    )
                }
            }

            /*
             * Návrh od modelu v zařízení: napřed jen řádek „Navrhnout složky" (model se pouští až
             * na ťuknutí, rozřazení čtyřiceti chatů trvá i desítky vteřin), pak navržené složky
             * se jmény chatů — uživatel vidí, co přidává, dřív než ťukne na „Přidat".
             */
            if (state.aiAvailable) {
                item(key = "ai-header") {
                    GroupTitle(stringResource(Res.string.chat_folders_ai), Modifier.padding(top = 16.dp, bottom = 6.dp))
                }
                when (val ai = state.ai) {
                    is AiFolders.Done -> if (ai.folders.isEmpty()) {
                        item(key = "ai-nothing") { AiFoldersNote(stringResource(Res.string.chat_folders_ai_nothing)) }
                    } else {
                        itemsIndexed(ai.folders, key = { _, it -> it.addingKey }) { index, folder ->
                            SuggestedFolderRow(
                                folder = folder,
                                adding = state.adding,
                                onAdd = { viewModel.addSuggested(folder) },
                                modifier = Modifier.animateItem().clip(groupItemShape(index, ai.folders.size)),
                            )
                        }
                    }
                    AiFolders.ModelMissing -> item(key = "ai-missing") { AiFoldersNote(stringResource(Res.string.chat_folders_ai_model_missing)) }
                    AiFolders.Idle, AiFolders.Working, AiFolders.Failed -> item(key = "ai-suggest") {
                        val working = ai == AiFolders.Working
                        ListItem(
                            headlineContent = {
                                Text(stringResource(if (working) Res.string.chat_folders_ai_working else Res.string.chat_folders_ai_suggest))
                            },
                            supportingContent = {
                                Text(stringResource(if (ai == AiFolders.Failed) Res.string.chat_folders_ai_failed else Res.string.chat_folders_ai_suggest_summary))
                            },
                            leadingContent = {
                                if (working) LoadingIndicator(Modifier.size(40.dp)) else FolderIcon(Res.drawable.auto_awesome_24dp, accent = true)
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.groupRow,
                                headlineColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.animateItem().clip(groupItemShape(0, 1))
                                .clickable(enabled = !working, onClick = viewModel::suggestWithAi),
                        )
                    }
                }
            }
        }
    }
}

/** Navržená složka: název kategorie, kterým chatům patří, a „Přidat". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestedFolderRow(folder: SuggestedFolder, adding: String?, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val shown = folder.names.take(SUGGESTED_NAMES_SHOWN).joinToString(", ")
    val more = folder.names.size - SUGGESTED_NAMES_SHOWN
    ListItem(
        headlineContent = { Text(folder.category.title?.let { stringResource(it) }.orEmpty()) },
        supportingContent = {
            Text(if (more > 0) pluralStringResource(Res.plurals.chat_folders_ai_more, more, shown, more) else shown)
        },
        leadingContent = { FolderIcon(Res.drawable.auto_awesome_24dp, accent = false) },
        trailingContent = {
            if (adding == folder.addingKey) {
                LoadingIndicator(Modifier.size(40.dp))
            } else {
                FilledTonalButton(onClick = onAdd, enabled = adding == null, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.chat_folders_add))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow),
        modifier = modifier,
    )
}

/** Věta místo návrhů (nic k navržení, model chybí) — v témže bloku jako řádky. */
@Composable
private fun AiFoldersNote(text: String) {
    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow),
        modifier = Modifier.clip(groupItemShape(0, 1)),
    )
}

/** Kolik názvů chatů se pod navrženou složkou vypíše; zbytek je „a další N". */
private const val SUGGESTED_NAMES_SHOWN = 3
