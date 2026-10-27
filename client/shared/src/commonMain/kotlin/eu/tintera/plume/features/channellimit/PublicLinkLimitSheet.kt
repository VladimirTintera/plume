package eu.tintera.plume.features.channellimit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.PublicChat
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.create_chat_link_prefix
import eu.tintera.plume.resources.link_off_24dp
import eu.tintera.plume.resources.public_limit_chats
import eu.tintera.plume.resources.public_limit_empty
import eu.tintera.plume.resources.public_limit_premium
import eu.tintera.plume.resources.public_limit_release
import eu.tintera.plume.resources.public_limit_release_text
import eu.tintera.plume.resources.public_limit_release_title
import eu.tintera.plume.resources.public_limit_text
import eu.tintera.plume.resources.public_limit_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Limit vlastních veřejných skupin a kanálů při založení nebo úpravě chatu: proč veřejný odkaz
 * nejde a vlastní veřejné chaty, kterým jde odkaz odebrat. [onReleased] = jeden se uvolnil
 * a veřejný chat už jde; [onDismiss] = zavřeno bez odebrání.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PublicLinkLimitSheet(
    onDismiss: () -> Unit,
    onReleased: () -> Unit,
    viewModel: PublicLinkLimitViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val premium by viewModel.isPremium.collectAsStateWithLifecycle()
    LaunchedEffect(state.released) { if (state.released) onReleased() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            SheetTitle(stringResource(Res.string.public_limit_title))
            Text(
                text = stringResource(Res.string.public_limit_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Premium limit zvedne; koupit ho Plume neumí, ale vědět se to má.
            if (!premium) {
                Text(
                    text = stringResource(Res.string.public_limit_premium),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(Res.string.public_limit_chats),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp).semantics { heading() },
            )
            val chats = state.chats
            when {
                chats == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                chats.isEmpty() -> GroupEmptyRow(stringResource(Res.string.public_limit_empty))
                else -> GroupedColumn(Modifier.fillMaxWidth()) {
                    chats.forEach { chat ->
                        PublicChatRow(
                            chat = chat,
                            releasing = state.releasing == chat.chat.id,
                            enabled = state.releasing == null,
                            onRelease = { viewModel.askRelease(chat) },
                        )
                    }
                }
            }
            state.error?.let { error ->
                Text(
                    text = error.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    state.confirming?.let { chat ->
        ConfirmDialog(
            title = stringResource(Res.string.public_limit_release_title),
            text = stringResource(Res.string.public_limit_release_text, chat.chat.title, chat.username),
            confirmLabel = stringResource(Res.string.public_limit_release),
            onConfirm = viewModel::confirmRelease,
            onDismiss = viewModel::cancelRelease,
            icon = Res.drawable.link_off_24dp,
            destructive = true,
        )
    }
}

/**
 * Celý řádek je „Odebrat odkaz" (potvrzuje se dialogem), vpravo jen ikona v chybové barvě.
 * Textové tlačítko vedle avataru vzalo názvu i odkazu tolik místa, že se na telefonu zalomily
 * a usekly („Plume test", „t.me/") — a právě podle odkazu se vybírá, co uvolnit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PublicChatRow(
    chat: PublicChat,
    releasing: Boolean,
    enabled: Boolean,
    onRelease: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(chat.chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                stringResource(Res.string.create_chat_link_prefix) + chat.username,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Avatar(
                chatId = chat.chat.avatarColorId,
                title = chat.chat.title,
                avatarFileId = chat.chat.avatarFileId,
                size = 40.dp,
                shape = chat.chat.kind.avatarShape(),
            )
        },
        trailingContent = {
            if (releasing) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                // Odebrat odkaz je nevratné (vzít si ho může kdokoli), proto chybová barva.
                Icon(
                    painterResource(Res.drawable.link_off_24dp),
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(
            enabled = enabled,
            onClickLabel = stringResource(Res.string.public_limit_release),
            role = Role.Button,
            onClick = onRelease,
        ),
    )
}
