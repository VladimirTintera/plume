package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.BlockedSender
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.profile_block
import eu.tintera.plume.resources.profile_bot
import eu.tintera.plume.resources.profile_unblock
import eu.tintera.plume.resources.settings_blocked
import eu.tintera.plume.resources.settings_blocked_count
import eu.tintera.plume.resources.settings_blocked_deleted
import eu.tintera.plume.resources.settings_blocked_none
import eu.tintera.plume.resources.settings_blocked_summary
import eu.tintera.plume.resources.settings_blocked_unblocked
import eu.tintera.plume.resources.settings_privacy
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Skupina „Soukromí" v Obecných: řádek s počtem blokovaných, který otevře jejich seznam.
 *
 * **Seznam je sheet, ne další obrazovka** — stejně jako výjimky upozornění v sekci Notifikace.
 * Je to pohled na jednu věc s jedinou akcí na řádek; obrazovka navíc by znamenala routu,
 * chování zpět v dvoupanelu a vlastní hlavičku kvůli seznamu, ze kterého se jen odblokovává.
 */
@Composable
internal fun BlockedSettings(viewModel: BlockedSendersViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }

    SettingsGroup(Res.string.settings_privacy) {
        val count = state.blockedCount
        val error = state.error
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.block_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.settings_blocked)) },
            supportingContent = {
                // Dokud počet nedorazí, řádek nic netvrdí — „nikoho" by mohla být lež.
                when {
                    error != null && count == null -> ErrorText(error)
                    count == null -> Unit
                    count == 0 -> Text(stringResource(Res.string.settings_blocked_none))
                    else -> Text(pluralStringResource(Res.plurals.settings_blocked_count, count, count))
                }
            },
            modifier = Modifier.clickable {
                viewModel.dismissError()
                open = true
            },
        )
    }

    if (open) {
        BlockedSheet(
            state = state,
            onSetBlocked = viewModel::setBlocked,
            onLoadMore = viewModel::loadMore,
            onDismiss = {
                open = false
                // Odblokovaní v seznamu zůstávali kvůli „Zablokovat"; po zavření se čte, co platí.
                viewModel.reload()
            },
        )
    }
}

@Composable
private fun BlockedSheet(
    state: BlockedSendersState,
    onSetBlocked: (BlockedSender, Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Rovnou celý: seznam bývá delší než půl obrazovky (stejně jako výjimky upozornění).
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Text(stringResource(Res.string.settings_blocked), style = MaterialTheme.typography.titleLarge)
                },
                supportingContent = { Text(stringResource(Res.string.settings_blocked_summary)) },
            )
            // Chyba je v sheetu, ne ve snackbaru: ten by pod modálním sheetem nebyl vidět.
            state.error?.let { ErrorText(it, Modifier.padding(horizontal = 16.dp)) }

            when {
                state.totalCount == null && state.loadingMore -> Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
                state.senders.isEmpty() && state.totalCount != null -> Text(
                    stringResource(Res.string.settings_blocked_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
                else -> LazyColumn(Modifier.navigationBarsPadding()) {
                    items(state.senders, key = { it.id }) { sender ->
                        BlockedRow(
                            sender = sender,
                            unblocked = sender.id in state.unblocked,
                            busy = sender.id in state.busy,
                            onSetBlocked = { blocked -> onSetBlocked(sender, blocked) },
                        )
                    }
                    // Po chybě se nedotahuje: položka by stála s kolečkem, které se nikdy nezastaví.
                    if (state.totalCount != null && !state.endReached && state.error == null) {
                        item(key = "more") {
                            // Dotažení spouští kompozice téhle položky, ne `layoutInfo` — tak to
                            // dělají i záložky sdíleného obsahu v profilu (viz `LoadMoreAtEnd`).
                            LaunchedEffect(state.nextOffset) { onLoadMore() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(
    sender: BlockedSender,
    unblocked: Boolean,
    busy: Boolean,
    onSetBlocked: (Boolean) -> Unit,
) {
    val title = sender.title.ifBlank { stringResource(Res.string.settings_blocked_deleted) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            // Smazaný účet dostane prázdný název: iniciály ze „Smazaný účet" by vypadaly jako jméno.
            Avatar(
                chatId = sender.id,
                title = sender.title,
                avatarFileId = sender.avatarFileId,
                size = 40.dp,
                shape = sender.kind.avatarShape(),
            )
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val subtitle = when {
                unblocked -> stringResource(Res.string.settings_blocked_unblocked)
                sender.isBot -> stringResource(Res.string.profile_bot)
                else -> sender.username?.let { "@$it" }
            }
            subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            // Odblokovaný řádek zůstává a nabízí vrátit to zpátky — „Vrátit" bez snackbaru.
            TextButton(onClick = { onSetBlocked(unblocked) }, enabled = !busy) {
                Text(stringResource(if (unblocked) Res.string.profile_block else Res.string.profile_unblock))
            }
        },
    )
}

@Composable
private fun ErrorText(text: UiText, modifier: Modifier = Modifier) {
    Text(
        stringResource(text.resource, *text.args.toTypedArray()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}
