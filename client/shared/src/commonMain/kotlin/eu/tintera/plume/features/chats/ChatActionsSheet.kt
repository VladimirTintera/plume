package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_delete
import eu.tintera.plume.resources.chats_action_leave
import eu.tintera.plume.resources.chats_action_mark_read
import eu.tintera.plume.resources.chats_action_mark_unread
import eu.tintera.plume.resources.chats_action_mute
import eu.tintera.plume.resources.chats_action_pin
import eu.tintera.plume.resources.chats_action_unmute
import eu.tintera.plume.resources.chats_action_unpin
import eu.tintera.plume.resources.chats_delete_body
import eu.tintera.plume.resources.chats_delete_title
import eu.tintera.plume.resources.chats_leave_body
import eu.tintera.plume.resources.chats_leave_title
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_delete
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Akce nad chatem po dlouhém stisku.
 *
 * Bottom sheet, ne kontextové menu u prstu: na telefonu je spodní okraj v dosahu palce a nabídka
 * má místo na popisky, ze kterých je poznat, co se stane — u nevratné akce to není kosmetika.
 */
@Composable
fun ChatActionsSheet(
    chat: ChatSummary,
    onTogglePinned: () -> Unit,
    onToggleMuted: () -> Unit,
    onToggleUnread: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Modální sheet začíná skrytý a rozjede se sám. Náhrada za `rememberModalBottomSheetState`,
    // které je od Compose 1.12 zastaralé.
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = chatTitle(chat.id, chat.title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ActionRow(
                icon = Res.drawable.push_pin_filled_24dp,
                // Popisek říká, co se STANE, ne jaký je stav — jinak si uživatel musí domýšlet,
                // jestli „Připnuto" je tvrzení, nebo tlačítko.
                label = stringResource(if (chat.isPinned) Res.string.chats_action_unpin else Res.string.chats_action_pin),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                onClick = onTogglePinned,
            )

            ActionRow(
                icon = Res.drawable.notifications_off_24dp,
                label = stringResource(if (chat.isMuted) Res.string.chats_action_unmute else Res.string.chats_action_mute),
                shape = RoundedCornerShape(4.dp),
                onClick = onToggleMuted,
            )

            ActionRow(
                icon = Res.drawable.mark_chat_unread_24dp,
                label = stringResource(if (chat.hasUnread) Res.string.chats_action_mark_read else Res.string.chats_action_mark_unread),
                shape = RoundedCornerShape(4.dp),
                onClick = onToggleUnread,
            )

            ActionRow(
                icon = Res.drawable.delete_24dp,
                // Ze skupiny se odchází, soukromý chat se maže. Jeden popisek pro obojí by lhal.
                label = stringResource(if (chat.isPrivate) Res.string.chats_action_delete else Res.string.chats_action_leave),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                // Jediná nevratná akce v nabídce — dostane chybovou barvu, aby se nespletla
                // s ostatními.
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: DrawableResource,
    label: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = color,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Potvrzení nevratné akce.
 *
 * Text pojmenovává **konkrétní následek**, ne obecné „opravdu?". U soukromého chatu se maže
 * historie jen u nás, ze skupiny se odchází — to jsou dvě různé věci a uživatel musí vědět,
 * kterou zrovna dělá.
 */
@Composable
fun ChatDeleteDialog(
    chat: ChatSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (chat.isPrivate) Res.string.chats_delete_title else Res.string.chats_leave_title)) },
        text = {
            Text(
                stringResource(
                    if (chat.isPrivate) Res.string.chats_delete_body else Res.string.chats_leave_body,
                    chatTitle(chat.id, chat.title),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(if (chat.isPrivate) Res.string.common_delete else Res.string.chats_action_leave),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
