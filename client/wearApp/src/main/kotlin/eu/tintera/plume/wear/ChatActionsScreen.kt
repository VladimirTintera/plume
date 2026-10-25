package eu.tintera.plume.wear

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_mark_read
import eu.tintera.plume.resources.chats_action_mark_unread
import eu.tintera.plume.resources.chats_action_mute
import eu.tintera.plume.resources.chats_action_unarchive
import eu.tintera.plume.resources.chats_action_unmute
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.unarchive_24dp
import eu.tintera.plume.resources.wear_confirm_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("WearChatActions")

/**
 * Akce nad chatem (podržení karty v seznamu): přečteno / nepřečteno, ztlumit / zrušit ztlumení
 * a archivovat / vrátit — bez otevírání chatu. Hlavně „přečteno" se na hodinkách hodí: otevřít
 * chat jen kvůli tomu by ho stejně přečetlo, ale s načítáním historie.
 *
 * Stav chatu (nepřečtené, ztlumení, archiv) se bere živě ze seznamu, takže nabídka nabízí vždycky
 * tu správnou z dvojice. Po akci se zavře a seznam se změní sám (updaty TDLib); nezdar ohlásí
 * potvrzovací dialog. Uložené zprávy archivovat ani ztlumit nejdou (TDLib archivaci odmítne
 * a ztlumení u nich nemá smysl), nabídka je proto nemá.
 */
@Composable
fun ChatActionsScreen(chatId: Long, onDone: () -> Unit) {
    val chats = koinInject<ChatRepository>()
    // Každý chat je buď v hlavním seznamu, nebo v archivu (složky Telegramu jsou jen výběr
    // z nich), takže stačí hledat v obou — podržení karty jde i v archivu a ve složce.
    val main by chats.chats(ChatFolder.Main).collectAsState()
    val archive by chats.chats(ChatFolder.Archive).collectAsState()
    val chat = listOf(main, archive).firstNotNullOfOrNull { list -> (list as? ChatListState.Ready)?.chats?.firstOrNull { it.id == chatId } }
    val savedMessagesId = savedMessagesChatId()
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val run: (suspend () -> Result<Unit>) -> Unit = { action ->
        scope.launch {
            action()
                .onSuccess { onDone() }
                .onFailure {
                    logger.w(it) { "Akce nad chatem $chatId z hodinek se nepovedla." }
                    failed = true
                }
        }
    }

    // Chat ze seznamu zmizí hned po archivaci (onDone už běží) nebo když ho archivuje jiné
    // zařízení. Zavírat odsud ne: po archivaci by se tak couvlo dvakrát, i ze seznamu chatů.
    if (chat == null) return
    val isSaved = chat.id == savedMessagesId
    val unread = chat.unreadCount > 0 || chat.isMarkedAsUnread
    val title = chatTitle(chat.id, chat.title, savedMessagesId)

    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    ChatAvatar(fileId = chat.avatarFileId, title = title, size = 20.dp, savedMessages = isSaved)
                    Spacer(Modifier.width(6.dp))
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (unread) {
                action(spec, Res.drawable.done_all_24dp, Res.string.chats_action_mark_read) { run { chats.readChat(chatId) } }
            } else {
                action(spec, Res.drawable.mark_chat_unread_24dp, Res.string.chats_action_mark_unread) { run { chats.setMarkedAsUnread(chatId, true) } }
            }
            if (!isSaved) {
                if (chat.isMuted) {
                    action(spec, Res.drawable.notifications_24dp, Res.string.chats_action_unmute) { run { chats.setMuted(chatId, false) } }
                } else {
                    action(spec, Res.drawable.notifications_off_24dp, Res.string.chats_action_mute) { run { chats.setMuted(chatId, true) } }
                }
                if (chat.isArchived) {
                    action(spec, Res.drawable.unarchive_24dp, Res.string.chats_action_unarchive) { run { chats.setArchived(chatId, false) } }
                } else {
                    action(spec, Res.drawable.archive_24dp, Res.string.chats_action_archive) { run { chats.setArchived(chatId, true) } }
                }
            }
        }
    }
    FailureConfirmation(visible = failed, text = stringResource(Res.string.wear_confirm_failed), onDismiss = { failed = false })
}

private fun TransformingLazyColumnScope.action(
    spec: TransformationSpec,
    icon: DrawableResource,
    label: StringResource,
    onClick: () -> Unit,
) {
    item {
        Button(
            onClick = onClick,
            icon = { Icon(painterResource(icon), contentDescription = null) },
            label = { Text(stringResource(label)) },
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
            transformation = SurfaceTransformation(spec),
        )
    }
}
