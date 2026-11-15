package eu.tintera.plume.features.calls

import eu.tintera.plume.core.accounts.AccountTopAppBar
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountBadge
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.telegram.AccountInfo
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
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.groupRow
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
import eu.tintera.plume.resources.calls_delete_one
import eu.tintera.plume.resources.calls_delete_one_confirm
import eu.tintera.plume.resources.calls_delete_one_text
import eu.tintera.plume.resources.calls_repeated
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Historie hovorů napříč chaty (menu účtu → Hovory). Řádek je chat s tím, jak hovor dopadl
 * (stejná šipka a slova jako zpráva o hovoru v konverzaci), délkou a časem; ťuknutí otevře chat,
 * sluchátko vpravo volá zpátky. Nahoře přepínač Všechny / Zmeškané, v ⋮ smazání celé historie.
 *
 * V režimu Všechny účty je to společná historie všech účtů ([sources], [mergeCallStates]) s odznakem
 * účtu u řádku; filtr i smazání celé historie platí pro všechny, řádek patří svému účtu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallsScreen(
    sources: List<CallSource>,
    onBack: () -> Unit,
    onOpenChat: (slot: AccountSlot, chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val states = sources.map { source -> key(source.account.slot) { source.viewModel.state.collectAsStateWithLifecycle().value } }
    val state = remember(states) { mergeCallStates(states) }
    val snackbarHostState = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteGroup by remember { mutableStateOf<CallHistoryGroup?>(null) }

    sources.forEachIndexed { index, source ->
        key(source.account.slot) {
            val errorText = states[index].error?.resolve()
            LaunchedEffect(errorText) {
                if (errorText != null) {
                    source.viewModel.consumeError()
                    snackbarHostState.showSnackbar(errorText)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AccountTopAppBar(
                showAccount = sources.size == 1,
                title = { Text(stringResource(Res.string.calls_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    // Smazat jde jen to, co je; prázdná historie ⋮ nemá.
                    if (!state.calls.isNullOrEmpty()) {
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
                sources = sources,
                onFilter = { onlyMissed -> sources.forEach { it.viewModel.setOnlyMissed(onlyMissed) } },
                onLoadMore = { state.needMore.forEach { sources[it].viewModel.loadMore() } },
                onOpenChat = { group -> onOpenChat(sources[group.source].account.slot, group.latest.chat.id) },
                onDelete = { deleteGroup = it },
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
                sources.forEach { it.viewModel.deleteAll(revoke) }
            },
            onDismiss = { confirmDelete = false },
        ) {
            DialogCheckRow(label = stringResource(Res.string.calls_delete_revoke), checked = revoke, onChange = { revoke = it })
        }
    }

    deleteGroup?.let { group ->
        var revoke by remember(group) { mutableStateOf(false) }
        ConfirmDialog(
            title = pluralStringResource(Res.plurals.calls_delete_one_confirm, group.count, group.count),
            text = stringResource(Res.string.calls_delete_one_text, group.latest.chat.title),
            confirmLabel = stringResource(Res.string.calls_delete_one),
            icon = Res.drawable.delete_24dp,
            destructive = true,
            onConfirm = {
                deleteGroup = null
                sources.getOrNull(group.source)?.viewModel?.delete(group, revoke)
            },
            onDismiss = { deleteGroup = null },
            // Ve skupině je záznam o videochatu zpráva všech; smazat ji ostatním smí jen správce.
            content = if (group.latest.isGroup) {
                null
            } else {
                { DialogCheckRow(label = stringResource(Res.string.calls_delete_revoke), checked = revoke, onChange = { revoke = it }) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CallsContent(
    state: MergedCallsState,
    sources: List<CallSource>,
    onFilter: (onlyMissed: Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onOpenChat: (CallHistoryGroup) -> Unit,
    onDelete: (CallHistoryGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calls = state.calls
    // Opakované hovory po sobě jsou jeden řádek; stránkování počítá řádky, ne hovory.
    val groups = remember(calls) { calls?.let(::groupSourcedCallHistory) }
    val merged = sources.size > 1
    val listState = rememberLazyListState()
    // Dojíždí se ke konci: další stránka s předstihem, ať seznam při rychlém scrollu nedojede.
    LaunchedEffect(listState, groups?.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (groups != null && last >= groups.size - LOAD_AHEAD) onLoadMore() }
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
            groups == null -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            groups.isEmpty() -> item(key = "empty") {
                IntroEmptyState(
                    icon = Res.drawable.call_24dp,
                    title = stringResource(Res.string.calls_empty_title),
                    text = stringResource(if (state.onlyMissed) Res.string.calls_empty_missed else Res.string.calls_empty),
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
            else -> {
                itemsIndexed(groups, key = { _, group -> group.key }) { index, group ->
                    val row = @Composable { account: AccountInfo? ->
                        CallHistoryRow(
                            group = group,
                            index = index,
                            count = groups.size,
                            account = account,
                            onClick = { onOpenChat(group) },
                            onDelete = { onDelete(group) },
                        )
                    }
                    // Ve společné historii se řádek skládá v účtu, kterému hovor patří: fotky jsou
                    // z jeho souborů a sluchátko volá z něj.
                    val source = sources.getOrNull(group.source)
                    if (merged && source != null) AccountScope(source.account) { row(source.info) } else row(null)
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

/**
 * Řádek historie: nejnovější hovor skupiny, u opakovaných s počtem za jménem („Jana (3)").
 * Podržení (pravé tlačítko myši) nabídne smazání řádku z historie.
 */
@Composable
private fun CallHistoryRow(
    group: CallHistoryGroup,
    index: Int,
    count: Int,
    account: AccountInfo?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val entry = group.latest
    val container = MaterialTheme.colorScheme.groupRow
    val failed = entry.outcome.isFailure
    val start = rememberStartCall(entry.callUserId)
    var menu by remember { mutableStateOf(false) }
    val deleteLabel = stringResource(Res.string.calls_delete_one)
    Box {
        ListItem(
            headlineContent = {
                Text(
                    if (group.count > 1) stringResource(Res.string.calls_repeated, entry.chat.title, group.count) else entry.chat.title,
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
                        // U sloučených by délka jednoho z nich mátla.
                        entry.durationSeconds.takeIf { it > 0 && group.count == 1 }?.let(::formatDuration),
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
                Box {
                    Avatar(
                        chatId = entry.chat.avatarColorId,
                        title = entry.chat.title,
                        avatarFileId = entry.chat.avatarFileId,
                        size = 40.dp,
                        shape = entry.chat.kind.avatarShape(),
                    )
                    // Komu hovor patří, jako u řádku společného seznamu chatů.
                    account?.let { AccountBadge(it, rim = container, modifier = Modifier.align(Alignment.BottomStart)) }
                }
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
            modifier = Modifier
                .groupedItem(index, count, container)
                .contextClickable(onLongClickLabel = deleteLabel, onLongClick = { menu = true }, onClick = onClick),
        )
        ExpressiveMenu(
            expanded = menu,
            onDismiss = { menu = false },
            groups = listOf(
                listOf(
                    MenuAction(label = deleteLabel, icon = Res.drawable.delete_24dp, destructive = true) {
                        menu = false
                        onDelete()
                    },
                ),
            ),
        )
    }
}

/** Kolik řádků před koncem se čte další stránka. */
private const val LOAD_AHEAD = 10
