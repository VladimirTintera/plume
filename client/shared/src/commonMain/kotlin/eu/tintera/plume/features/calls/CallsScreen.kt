package eu.tintera.plume.features.calls

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.telegram.CallHistoryEntry
import eu.tintera.plume.core.telegram.isFailure
import eu.tintera.plume.core.telegram.title
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.DialogCheckRow
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.features.conversation.items.icon
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.call_back
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.calls_all
import eu.tintera.plume.resources.calls_delete
import eu.tintera.plume.resources.calls_delete_confirm
import eu.tintera.plume.resources.calls_delete_revoke
import eu.tintera.plume.resources.calls_delete_text
import eu.tintera.plume.resources.calls_empty
import eu.tintera.plume.resources.calls_empty_missed
import eu.tintera.plume.resources.calls_empty_title
import eu.tintera.plume.resources.calls_missed
import eu.tintera.plume.resources.calls_title
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.videocam_24dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Historie hovorů napříč chaty (menu účtu → Hovory). Řádek je chat s tím, jak hovor dopadl
 * (stejná šipka a slova jako zpráva o hovoru v konverzaci), délkou a časem; ťuknutí otevře chat,
 * sluchátko vpravo volá zpátky. Nahoře přepínač Všechny / Zmeškané, v ⋮ smazání celé historie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    viewModel: CallsViewModel,
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(Res.string.calls_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    // Smazat jde jen to, co je; prázdná historie ⋮ nemá.
                    if (!state.entries.isNullOrEmpty()) {
                        Box {
                            IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.profile_more))
                            }
                            ExpressiveMenu(
                                expanded = menu,
                                onDismiss = { menu = false },
                                groups = listOf(
                                    listOf(
                                        MenuAction(
                                            label = stringResource(Res.string.calls_delete),
                                            icon = Res.drawable.delete_24dp,
                                            destructive = true,
                                        ) {
                                            menu = false
                                            confirmDelete = true
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            CallsContent(
                state = state,
                onFilter = viewModel::setOnlyMissed,
                onLoadMore = viewModel::loadMore,
                onOpenChat = onOpenChat,
                modifier = Modifier.widthIn(max = ContentWidth.Reading),
            )
        }
    }

    if (confirmDelete) {
        var revoke by remember { mutableStateOf(false) }
        ConfirmDialog(
            title = stringResource(Res.string.calls_delete_confirm),
            text = stringResource(Res.string.calls_delete_text),
            confirmLabel = stringResource(Res.string.calls_delete),
            icon = Res.drawable.delete_24dp,
            destructive = true,
            confirmEnabled = !state.deleting,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAll(revoke)
            },
            onDismiss = { confirmDelete = false },
        ) {
            DialogCheckRow(label = stringResource(Res.string.calls_delete_revoke), checked = revoke, onChange = { revoke = it })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CallsContent(
    state: CallsState,
    onFilter: (onlyMissed: Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onOpenChat: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = state.entries
    val listState = rememberLazyListState()
    // Dojíždí se ke konci: další stránka s předstihem, ať seznam při rychlém scrollu nedojede.
    LaunchedEffect(listState, entries?.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (entries != null && last >= entries.size - LOAD_AHEAD) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "filter") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FilterOption(Res.string.calls_all, selected = !state.onlyMissed) { onFilter(false) }
                FilterOption(Res.string.calls_missed, selected = state.onlyMissed) { onFilter(true) }
            }
        }
        when {
            entries == null -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            entries.isEmpty() -> item(key = "empty") {
                IntroEmptyState(
                    icon = Res.drawable.call_24dp,
                    title = stringResource(Res.string.calls_empty_title),
                    text = stringResource(if (state.onlyMissed) Res.string.calls_empty_missed else Res.string.calls_empty),
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
            else -> {
                itemsIndexed(entries, key = { _, entry -> "${entry.chat.id}-${entry.messageId}" }) { index, entry ->
                    CallHistoryRow(entry, index, entries.size, onClick = { onOpenChat(entry.chat.id) })
                }
                if (state.loadingMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                }
            }
        }
    }
}

/** Čip filtru; vybraný s fajfkou jako filtry v záznamu akcí — výběr nese i tvar, ne jen barva. */
@Composable
private fun FilterOption(label: StringResource, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = if (selected) {
            { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
private fun CallHistoryRow(entry: CallHistoryEntry, index: Int, count: Int, onClick: () -> Unit) {
    val container = MaterialTheme.colorScheme.surfaceContainerLowest
    val failed = entry.outcome.isFailure
    val start = rememberStartCall(entry.callUserId)
    ListItem(
        headlineContent = {
            Text(
                entry.chat.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Zmeškaný jménem v chybové barvě, jako v telefonu: po uživateli něco chce.
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painterResource(entry.outcome.icon()),
                    contentDescription = null,
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                val parts = listOfNotNull(
                    stringResource(entry.outcome.title(entry.isVideo, entry.isGroup)),
                    entry.durationSeconds.takeIf { it > 0 }?.let(::formatDuration),
                    entry.date.asRelativeTime(),
                )
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            Avatar(
                chatId = entry.chat.avatarColorId,
                title = entry.chat.title,
                avatarFileId = entry.chat.avatarFileId,
                size = 40.dp,
                shape = entry.chat.kind.avatarShape(),
            )
        },
        trailingContent = start?.let {
            {
                val label = stringResource(Res.string.call_back)
                WithTooltip(label) {
                    IconButton(onClick = { start(entry.isVideo) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painterResource(if (entry.isVideo) Res.drawable.videocam_24dp else Res.drawable.call_24dp),
                            contentDescription = label,
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = container),
        modifier = Modifier.groupedItem(index, count, container).clickable(onClick = onClick),
    )
}

/** Kolik řádků před koncem se čte další stránka. */
private const val LOAD_AHEAD = 10
