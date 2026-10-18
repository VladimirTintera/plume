package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.core.ui.ChoiceListItem
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
        SheetTitle(stringResource(Res.string.edit_chat_discussion), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
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
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            DiscussionRow(
                selected = current == null,
                title = stringResource(Res.string.edit_chat_discussion_none),
                group = null,
                index = 0,
                count = groups.size + 1,
                onClick = { if (current != null) onSelect(null) },
            )
            groups.forEachIndexed { index, group ->
                DiscussionRow(
                    selected = group.id == current?.chatId,
                    title = group.title,
                    group = group,
                    index = index + 1,
                    count = groups.size + 1,
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
        ConfirmDialog(
            title = stringResource(Res.string.edit_chat_discussion_confirm_title),
            text = stringResource(Res.string.edit_chat_discussion_confirm_text, group.title),
            confirmLabel = stringResource(Res.string.edit_chat_discussion_confirm),
            onConfirm = {
                confirming = null
                onSelect(group)
            },
            onDismiss = { confirming = null },
            icon = Res.drawable.forum_24dp,
        )
    }
}

@Composable
private fun DiscussionRow(selected: Boolean, title: String, group: ChatSummary?, index: Int, count: Int, onClick: () -> Unit) {
    ChoiceListItem(
        label = title,
        selected = selected,
        index = index,
        count = count,
        onClick = onClick,
        // Fotka skupiny vlevo jako v každém řádku s chatem; „Bez diskuse" má na jejím místě
        // znak, ať názvy lícují.
        leading = {
            if (group != null) {
                Avatar(
                    chatId = group.id,
                    title = group.title,
                    avatarFileId = group.avatarFileId,
                    size = 40.dp,
                    shape = ChatKind.Group.avatarShape(),
                )
            } else {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.block_24dp), contentDescription = null)
                }
            }
        },
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
