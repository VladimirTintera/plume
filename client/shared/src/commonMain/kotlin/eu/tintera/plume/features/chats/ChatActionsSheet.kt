package eu.tintera.plume.features.chats

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

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
            text = chat.title,
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
                label = if (chat.isPinned) "Odepnout" else "Připnout",
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                onClick = onTogglePinned,
            )

            ActionRow(
                icon = Res.drawable.notifications_off_24dp,
                label = if (chat.isMuted) "Zrušit ztlumení" else "Ztlumit",
                shape = RoundedCornerShape(4.dp),
                onClick = onToggleMuted,
            )

            ActionRow(
                icon = Res.drawable.mark_chat_unread_24dp,
                label = if (chat.hasUnread) "Označit jako přečtené" else "Označit jako nepřečtené",
                shape = RoundedCornerShape(4.dp),
                onClick = onToggleUnread,
            )

            ActionRow(
                icon = Res.drawable.delete_24dp,
                // Ze skupiny se odchází, soukromý chat se maže. Jeden popisek pro obojí by lhal.
                label = if (chat.isPrivate) "Smazat chat" else "Opustit",
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
        title = { Text(if (chat.isPrivate) "Smazat chat?" else "Opustit chat?") },
        text = {
            Text(
                if (chat.isPrivate) {
                    "Historie chatu ${chat.title} se smaže u tebe. Protějšku zůstane."
                } else {
                    "Odejdeš z ${chat.title} a přestaneš dostávat zprávy. " +
                        "Zpátky se dá jen novou pozvánkou."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = if (chat.isPrivate) "Smazat" else "Opustit",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušit") }
        },
    )
}
