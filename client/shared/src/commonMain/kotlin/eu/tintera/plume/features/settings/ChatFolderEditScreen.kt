package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.telegram.CHAT_FOLDER_TITLE_MAX
import eu.tintera.plume.core.telegram.ChatFolderProblem
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.chat_folder_add_chats
import eu.tintera.plume.resources.chat_folder_delete
import eu.tintera.plume.resources.chat_folder_delete_body
import eu.tintera.plume.resources.chat_folder_delete_title
import eu.tintera.plume.resources.chat_folder_done
import eu.tintera.plume.resources.chat_folder_edit_title
import eu.tintera.plume.resources.chat_folder_exclude_archived
import eu.tintera.plume.resources.chat_folder_exclude_chats
import eu.tintera.plume.resources.chat_folder_exclude_muted
import eu.tintera.plume.resources.chat_folder_exclude_read
import eu.tintera.plume.resources.chat_folder_excluded
import eu.tintera.plume.resources.chat_folder_excluded_help
import eu.tintera.plume.resources.chat_folder_include_bots
import eu.tintera.plume.resources.chat_folder_include_channels
import eu.tintera.plume.resources.chat_folder_include_contacts
import eu.tintera.plume.resources.chat_folder_include_groups
import eu.tintera.plume.resources.chat_folder_include_non_contacts
import eu.tintera.plume.resources.chat_folder_included
import eu.tintera.plume.resources.chat_folder_included_help
import eu.tintera.plume.resources.chat_folder_name
import eu.tintera.plume.resources.chat_folder_new_title
import eu.tintera.plume.resources.chat_folder_no_chats
import eu.tintera.plume.resources.chat_folder_pick_search
import eu.tintera.plume.resources.chat_folder_remove_chat
import eu.tintera.plume.resources.chat_folder_save
import eu.tintera.plume.resources.chat_folder_title_missing
import eu.tintera.plume.resources.chat_folder_title_too_long
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.person_search_24dp
import eu.tintera.plume.resources.smart_toy_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Editor složky: název, co do ní patří (vybrané chaty a druhy chatů) a co z ní vypadne
 * (vynechané chaty, ztlumené, přečtené, archivované).
 *
 * - **Druhy chatů jsou přepínače, vybrané chaty seznam** — tak to Telegram skládá a tak to
 *   uživatel zná z oficiálních klientů.
 * - **Výběr chatů je `ChatPicker` přes celou obrazovku**, tentýž jako přeposílání; zpět ho
 *   zavře bez změny, teprve další zpět odejde z editoru.
 * - **Chyba konceptu (prázdný název, žádný chat) se ukáže až po pokusu o uložení** — prázdný
 *   formulář v červeném by křičel na někoho, kdo ještě nic neudělal.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatFolderEditScreen(viewModel: ChatFolderEditViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        scope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }

    val pick = state.pick
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = pick != null,
        onBackCompleted = viewModel::closePick,
    )
    if (pick != null) {
        FolderChatPicker(viewModel, pick, modifier)
        return
    }

    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft
    val problem = state.problem.takeIf { state.showProblem }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) Res.string.chat_folder_new_title else Res.string.chat_folder_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.saving) {
                            Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.chat_folder_delete))
                        }
                    }
                    if (state.saving) {
                        LoadingIndicator(Modifier.padding(horizontal = 12.dp))
                    } else {
                        TextButton(onClick = viewModel::save, enabled = state.changed && !state.loading) {
                            Text(stringResource(Res.string.chat_folder_save))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(Res.string.chat_folder_name)) },
                singleLine = true,
                isError = problem == ChatFolderProblem.MissingTitle || draft.title.trim().length > CHAT_FOLDER_TITLE_MAX,
                supportingText = {
                    val message = when {
                        draft.title.trim().length > CHAT_FOLDER_TITLE_MAX -> Res.string.chat_folder_title_too_long
                        problem == ChatFolderProblem.MissingTitle -> Res.string.chat_folder_title_missing
                        else -> null
                    }
                    Text(
                        if (message != null) stringResource(message, CHAT_FOLDER_TITLE_MAX) else "${draft.title.trim().length}/$CHAT_FOLDER_TITLE_MAX",
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            FolderHeader(Res.string.chat_folder_included)
            val chosen = draft.chosenChatIds
            val kinds = listOf(
                KindToggle(Res.drawable.person_24dp, Res.string.chat_folder_include_contacts, draft.includeContacts, viewModel::setIncludeContacts),
                KindToggle(Res.drawable.person_search_24dp, Res.string.chat_folder_include_non_contacts, draft.includeNonContacts, viewModel::setIncludeNonContacts),
                KindToggle(Res.drawable.group_24dp, Res.string.chat_folder_include_groups, draft.includeGroups, viewModel::setIncludeGroups),
                KindToggle(Res.drawable.campaign_24dp, Res.string.chat_folder_include_channels, draft.includeChannels, viewModel::setIncludeChannels),
                KindToggle(Res.drawable.smart_toy_24dp, Res.string.chat_folder_include_bots, draft.includeBots, viewModel::setIncludeBots),
            )
            FolderBlock(
                addLabel = Res.string.chat_folder_add_chats,
                onAdd = { viewModel.openPick(FolderChatPick.Include) },
                toggles = kinds,
                chatIds = chosen,
                chats = state.chats,
                onRemove = viewModel::removeChosen,
            )
            Text(
                stringResource(if (problem == ChatFolderProblem.NoChats) Res.string.chat_folder_no_chats else Res.string.chat_folder_included_help),
                style = MaterialTheme.typography.bodySmall,
                color = if (problem == ChatFolderProblem.NoChats) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            FolderHeader(Res.string.chat_folder_excluded, Modifier.padding(top = 16.dp))
            FolderBlock(
                addLabel = Res.string.chat_folder_exclude_chats,
                onAdd = { viewModel.openPick(FolderChatPick.Exclude) },
                toggles = listOf(
                    KindToggle(Res.drawable.notifications_off_24dp, Res.string.chat_folder_exclude_muted, draft.excludeMuted, viewModel::setExcludeMuted),
                    KindToggle(Res.drawable.done_all_24dp, Res.string.chat_folder_exclude_read, draft.excludeRead, viewModel::setExcludeRead),
                    KindToggle(Res.drawable.archive_24dp, Res.string.chat_folder_exclude_archived, draft.excludeArchived, viewModel::setExcludeArchived),
                ),
                chatIds = draft.excludedChatIds,
                chats = state.chats,
                onRemove = viewModel::removeExcluded,
            )
            Text(
                stringResource(Res.string.chat_folder_excluded_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.chat_folder_delete_title)) },
            text = { Text(stringResource(Res.string.chat_folder_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) {
                    Text(stringResource(Res.string.chat_folder_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

private class KindToggle(val icon: DrawableResource, val label: StringResource, val checked: Boolean, val onChange: (Boolean) -> Unit)

/**
 * Blok zaoblených řádků: „Přidat chaty", přepínače druhů a pod nimi vybrané chaty s křížkem.
 * Jeden blok, ne tři — patří k jedné otázce („co do složky patří").
 */
@Composable
private fun FolderBlock(
    addLabel: StringResource,
    onAdd: () -> Unit,
    toggles: List<KindToggle>,
    chatIds: List<Long>,
    chats: Map<Long, ChatSummary>,
    onRemove: (Long) -> Unit,
) {
    val count = 1 + toggles.size + chatIds.size
    Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
        ListItem(
            headlineContent = { Text(stringResource(addLabel)) },
            leadingContent = { FolderIcon(Res.drawable.add_24dp, accent = true) },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                headlineColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.clip(groupItemShape(0, count)).clickable(onClick = onAdd),
        )
        toggles.forEachIndexed { index, toggle ->
            ListItem(
                headlineContent = { Text(stringResource(toggle.label)) },
                leadingContent = { FolderIcon(toggle.icon, accent = false) },
                // Klikání nese celý řádek, přepínač ne (viz „Obrazovka nastavení").
                trailingContent = { Switch(checked = toggle.checked, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier
                    .clip(groupItemShape(index + 1, count))
                    .toggleable(value = toggle.checked, role = Role.Switch, onValueChange = toggle.onChange),
            )
        }
        chatIds.forEachIndexed { index, chatId ->
            val chat = chats[chatId]
            val title = chat?.let { chatTitle(it.id, it.title) }.orEmpty()
            ListItem(
                headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    Avatar(
                        chatId = chatId,
                        title = title,
                        avatarFileId = chat?.avatarFileId,
                        size = 40.dp,
                        isSavedMessages = isSavedMessages(chatId),
                        shape = chat?.kind?.avatarShape() ?: androidx.compose.foundation.shape.CircleShape,
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onRemove(chatId) }) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.chat_folder_remove_chat, title))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.clip(groupItemShape(1 + toggles.size + index, count)),
            )
        }
    }
}

@Composable
private fun FolderHeader(title: StringResource, modifier: Modifier = Modifier) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, bottom = 6.dp),
    )
}

/** Výběr chatů do složky (nebo vynechaných): tentýž `ChatPicker` jako přeposílání, víc naráz. */
@Composable
private fun FolderChatPicker(viewModel: ChatFolderEditViewModel, pick: FolderChatPick, modifier: Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chats by viewModel.pickChats.collectAsStateWithLifecycle()
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    ChatPicker(
        title = stringResource(if (pick == FolderChatPick.Include) Res.string.chat_folder_add_chats else Res.string.chat_folder_exclude_chats),
        query = state.pickQuery,
        onQueryChange = viewModel::setPickQuery,
        searchPlaceholder = stringResource(Res.string.chat_folder_pick_search),
        loading = chats.loading,
        sections = listOf(PickerSection(key = "chats", chats = chats.chats)),
        enabled = true,
        onPick = viewModel::togglePicked,
        onBack = viewModel::closePick,
        savedMessagesChatId = savedMessagesChatId,
        selected = state.picked,
        onConfirm = viewModel::confirmPick,
        confirmWhenEmpty = true,
        confirmLabel = Res.string.chat_folder_done,
        confirmIcon = Res.drawable.check_24dp,
        modifier = modifier,
    )
}
