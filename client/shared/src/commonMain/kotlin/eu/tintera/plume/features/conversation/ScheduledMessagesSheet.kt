package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.features.conversation.items.summaryLine
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_scheduled_delete
import eu.tintera.plume.resources.conversation_scheduled_reschedule
import eu.tintera.plume.resources.conversation_scheduled_send_now
import eu.tintera.plume.resources.conversation_scheduled_title
import eu.tintera.plume.resources.conversation_scheduled_when_online
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_calendar_24dp
import eu.tintera.plume.resources.send_24dp
import org.jetbrains.compose.resources.stringResource

/**
 * Naplánované zprávy chatu: kdy odejdou a co v nich je. Ťuknutí na řádek nabídne odeslat hned,
 * změnit čas a smazat.
 *
 * Seznam drží ViewModel a po každé akci ho čte znovu — co akce udělala (odeslaná zpráva
 * odešla, přeplánovaná se přesunula), ví server. Prázdný seznam sheet zavře sám.
 * [messages] prázdný = ještě se načítá.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScheduledMessagesSheet(
    messages: List<ChatMessage>,
    onSendNow: (ChatMessage) -> Unit,
    onReschedule: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.conversation_scheduled_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
        )
        if (messages.isEmpty()) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))
            return@ModalBottomSheet
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        ) {
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                ScheduledRow(
                    message = message,
                    onSendNow = { onSendNow(message) },
                    onReschedule = { onReschedule(message) },
                    onDelete = { onDelete(message) },
                    modifier = Modifier.animateItem().clip(groupItemShape(index, messages.size)),
                )
            }
        }
    }
}

@Composable
private fun ScheduledRow(
    message: ChatMessage,
    onSendNow: () -> Unit,
    onReschedule: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = message.summaryLine(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            // Kdy odejde — to je u naplánované zprávy to hlavní, co se tu hledá.
            overlineContent = { Text(message.schedule.label(), maxLines = 1) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            modifier = Modifier.clickable { menuOpen = true },
        )
        ExpressiveMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.conversation_scheduled_send_now), Res.drawable.send_24dp) {
                        menuOpen = false
                        onSendNow()
                    },
                    MenuAction(stringResource(Res.string.conversation_scheduled_reschedule), Res.drawable.edit_calendar_24dp) {
                        menuOpen = false
                        onReschedule()
                    },
                ),
                listOf(
                    MenuAction(
                        stringResource(Res.string.conversation_scheduled_delete),
                        Res.drawable.delete_24dp,
                        destructive = true,
                    ) {
                        menuOpen = false
                        onDelete()
                    },
                ),
            ),
        )
    }
}

@Composable
private fun MessageSchedule?.label(): String = when (this) {
    is MessageSchedule.At -> epochSeconds.asUpcomingTime()
    MessageSchedule.WhenOnline -> stringResource(Res.string.conversation_scheduled_when_online)
    null -> ""
}
