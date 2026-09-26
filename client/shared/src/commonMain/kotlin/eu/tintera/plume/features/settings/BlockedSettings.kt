package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.BlockedSender
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.chats_undo
import eu.tintera.plume.resources.chats_unblocked
import eu.tintera.plume.resources.profile_unblock
import eu.tintera.plume.resources.search_off_24dp
import eu.tintera.plume.resources.settings_blocked
import eu.tintera.plume.resources.settings_blocked_count
import eu.tintera.plume.resources.settings_blocked_deleted
import eu.tintera.plume.resources.settings_blocked_none
import eu.tintera.plume.resources.settings_blocked_search
import eu.tintera.plume.resources.settings_blocked_summary
import eu.tintera.plume.resources.settings_privacy
import eu.tintera.plume.resources.settings_sensitive_content
import eu.tintera.plume.resources.settings_sensitive_content_failed
import eu.tintera.plume.resources.settings_sensitive_content_summary
import eu.tintera.plume.resources.visibility_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Skupina „Soukromí" v Obecných: blokovaní a citlivý obsah. Samostatná sekce Soukromí by měla
 * dvě položky, proto tady.
 */
@Composable
internal fun PrivacySettings(
    blocked: BlockedSendersViewModel,
    sensitive: SensitiveContentViewModel,
    onOpenBlocked: () -> Unit,
) {
    SettingsGroup(Res.string.settings_privacy) {
        BlockedRow(blocked, onOpenBlocked)
        SensitiveContentRow(sensitive)
    }
}

/**
 * Řádek s počtem blokovaných, který otevře jejich seznam ([BlockedSendersScreen]).
 *
 * Počet se čte znovu při každém návratu na obrazovku — po odblokování v seznamu by jinak
 * zůstal starý.
 */
@Composable
private fun BlockedRow(viewModel: BlockedSendersViewModel, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        onPauseOrDispose {}
    }

    run {
        val count = state.senders?.size
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.block_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.settings_blocked)) },
            supportingContent = {
                // Dokud počet nedorazí, řádek nic netvrdí — „nikoho" by mohla být lež.
                when (count) {
                    null -> Unit
                    0 -> Text(stringResource(Res.string.settings_blocked_none))
                    else -> Text(pluralStringResource(Res.plurals.settings_blocked_count, count, count))
                }
            },
            modifier = Modifier.clickable(onClick = onOpen),
        )
    }
}

/**
 * „Zobrazovat citlivý obsah": fotky a videa označená jako citlivá se ukážou bez rozostření.
 * Kde volbu server změnit nedovolí, řádek není vůbec ([SensitiveContentState.show] `null`).
 *
 * Už načtené zprávy se nepřekreslí — příznak se čte při převodu zprávy; projeví se po návratu
 * do chatu.
 */
@Composable
private fun SensitiveContentRow(viewModel: SensitiveContentViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val show = state.show ?: return
    SwitchRow(
        title = Res.string.settings_sensitive_content,
        summary = if (state.failed) Res.string.settings_sensitive_content_failed else Res.string.settings_sensitive_content_summary,
        summaryIsError = state.failed,
        checked = show,
        onChange = viewModel::setShow,
        icon = Res.drawable.visibility_24dp,
    )
}

/**
 * Seznam blokovaných lidí a chatů.
 *
 * **Je to tentýž `ChatPicker` jako kontakty, přeposílání a sdílení** — hlavička, hledání, bloky
 * zaoblených řádků, prázdný stav. První verze byla sheet se seznamem `ListItem`ů a tlačítkem
 * „Odblokovat" u každého řádku: vedle kontaktů vypadala jako jiná aplikace a měla jiné ovládání.
 * Akce nad položkou se tu dělá jako všude jinde — **ťuknutí otevře nabídku**, vratná akce má
 * snackbar s „Vrátit".
 */
@Composable
fun BlockedSendersScreen(
    viewModel: BlockedSendersViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Snackbar běží mimo efekt: vynulování události mění klíč efektu a zrušilo by ho
    // (stejná past jako „Vrátit" u archivace v seznamu chatů).
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(state.unblocked) {
        val sender = state.unblocked ?: return@LaunchedEffect
        viewModel.consumeUnblocked()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(Res.string.chats_unblocked),
                actionLabel = getString(Res.string.chats_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoUnblock(sender)
        }
    }

    val deleted = stringResource(Res.string.settings_blocked_deleted)
    val blankQuery = state.query.isBlank()
    ChatPicker(
        title = stringResource(Res.string.settings_blocked),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.settings_blocked_search),
        loading = state.senders == null,
        sections = listOf(PickerSection(key = "blocked", chats = state.visible.map { it.toSummary(deleted) })),
        enabled = true,
        onPick = viewModel::openActions,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        // Bez dotazu je prázdno „nikoho nemáš zablokovaného", s dotazem výchozí „nic nenalezeno".
        emptyIcon = if (blankQuery) Res.drawable.block_24dp else Res.drawable.search_off_24dp,
        emptyTitle = if (blankQuery) stringResource(Res.string.settings_blocked_none) else null,
        emptyText = if (blankQuery) stringResource(Res.string.settings_blocked_summary) else null,
        error = state.error,
        onErrorShown = viewModel::consumeError,
        modifier = modifier,
    )

    state.target?.let { sender ->
        BlockedActionsSheet(
            sender = sender,
            title = sender.title.ifBlank { deleted },
            onUnblock = { viewModel.unblock(sender) },
            onDismiss = viewModel::dismissActions,
        )
    }
}

/**
 * Nabídka nad blokovaným: kdo to je a „Odblokovat". Tentýž tvar jako nabídka nad chatem
 * v seznamu (`ChatActionsSheet`) — jazyk akcí nad položkou je v aplikaci jeden.
 */
@Composable
private fun BlockedActionsSheet(
    sender: BlockedSender,
    title: String,
    onUnblock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Avatar(
                chatId = sender.id,
                title = title,
                avatarFileId = sender.avatarFileId,
                size = 40.dp,
                shape = sender.kind.avatarShape(),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Box(Modifier.padding(16.dp)) {
            SheetActionRow(
                icon = Res.drawable.block_24dp,
                label = stringResource(Res.string.profile_unblock),
                shape = RoundedCornerShape(20.dp),
                onClick = onUnblock,
            )
        }
    }
}

/**
 * Blokovaný jako položka výběru chatu. `ChatPicker` kreslí `ChatSummary`; použije se z něj
 * jméno, fotka a druh (tvar avataru), zbytek jsou výchozí hodnoty bez významu.
 */
private fun BlockedSender.toSummary(deletedTitle: String) = ChatSummary(
    id = id,
    title = title.ifBlank { deletedTitle },
    unreadCount = 0,
    lastMessagePreview = null,
    isPinned = false,
    order = 0,
    avatarFileId = avatarFileId,
    isMuted = false,
    isMarkedAsUnread = false,
    kind = kind,
    lastMessageTimestamp = null,
    presence = null,
    activity = null,
    draft = null,
)
