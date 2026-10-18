package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.LinkedChat
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.edit_chat_discussion
import eu.tintera.plume.resources.edit_chat_discussion_confirm
import eu.tintera.plume.resources.edit_chat_discussion_confirm_text
import eu.tintera.plume.resources.edit_chat_discussion_confirm_title
import eu.tintera.plume.resources.edit_chat_discussion_empty
import eu.tintera.plume.resources.edit_chat_discussion_explanation
import eu.tintera.plume.resources.edit_chat_discussion_none
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr diskusní skupiny kanálu: „Bez diskuse" a skupiny, které jde připojit
 * (`getSuitableDiscussionChats`). Tvarem jako výběr doby automatického mazání — kolečko
 * u vybrané volby, řádek nese klikání celý.
 *
 * **Připojení se potvrzuje**, odpojení ne: připojená skupina dostává všechny příspěvky kanálu
 * a její historie bude vidět i novým členům — to je následek, který se z názvu volby nepozná.
 * Odpojení jen vypne komentáře a jde vrátit stejnou cestou.
 */
@Composable
internal fun DiscussionSheet(
    current: LinkedChat?,
    candidates: List<ChatSummary>?,
    onSelect: (ChatSummary?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    var confirming by remember { mutableStateOf<ChatSummary?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.edit_chat_discussion),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Text(
            text = stringResource(Res.string.edit_chat_discussion_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (candidates == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@ModalBottomSheet
        }
        // Připojená skupina mezi vhodnými být nemusí — ukázat ji jde vždycky.
        val groups = (listOfNotNull(current?.let { linked -> candidates.firstOrNull { it.id == linked.chatId } ?: linked.toSummary() }) + candidates)
            .distinctBy { it.id }
        Column(
            Modifier
                .selectableGroup()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            DiscussionRow(
                selected = current == null,
                title = stringResource(Res.string.edit_chat_discussion_none),
                group = null,
                onClick = { if (current != null) onSelect(null) },
            )
            groups.forEach { group ->
                DiscussionRow(
                    selected = group.id == current?.chatId,
                    title = group.title,
                    group = group,
                    onClick = { if (group.id != current?.chatId) confirming = group },
                )
            }
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(Res.string.edit_chat_discussion_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }

    confirming?.let { group ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(Res.string.edit_chat_discussion_confirm_title)) },
            text = { Text(stringResource(Res.string.edit_chat_discussion_confirm_text, group.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onSelect(group)
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.edit_chat_discussion_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun DiscussionRow(selected: Boolean, title: String, group: ChatSummary?, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(title) },
        trailingContent = group?.let {
            {
                Avatar(
                    chatId = it.id,
                    title = it.title,
                    avatarFileId = it.avatarFileId,
                    size = 40.dp,
                    shape = ChatKind.Group.avatarShape(),
                )
            }
        },
        // Řádek nese klikání celý, kolečko má `onClick = null` — viz „Obrazovka nastavení".
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

private fun LinkedChat.toSummary() = ChatSummary(
    id = chatId,
    title = title,
    unreadCount = 0,
    lastMessagePreview = null,
    isPinned = false,
    order = 0,
    avatarFileId = photoFileId,
    isMuted = false,
    isMarkedAsUnread = false,
    kind = ChatKind.Group,
    lastMessageTimestamp = null,
    presence = null,
    activity = null,
    draft = null,
)
