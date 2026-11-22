package eu.tintera.plume.features.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.calls.CallQuickReplies
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_decline_with_message
import eu.tintera.plume.resources.call_message
import eu.tintera.plume.resources.call_quick_reply_custom
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.edit_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("CallQuickReply")

/**
 * „Zpráva" mezi Odmítnout a Přijmout u zvonícího hovoru: nabídka hotových odpovědí, výběr hovor
 * odmítne a větu pošle volajícímu. „Napsat vlastní…" hovor odmítne a otevře chat s ním.
 *
 * Soukromý chat má id uživatele, takže se píše rovnou do [ActiveCall.userId]. Nepodaří-li se
 * zprávu odeslat, otevře se chat — uživatel tak vidí, že odpověď neodešla, a může ji napsat.
 */
@Composable
internal fun CallQuickReplyButton(call: ActiveCall, calls: CallRepository, modifier: Modifier = Modifier) {
    val conversations = koinInject<ConversationRepository>()
    val chatOpenRequests = koinInject<ChatOpenRequests>()
    val slot = LocalAccount.current.slot
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val replies = CallQuickReplies.map { stringResource(it) }
    val custom = stringResource(Res.string.call_quick_reply_custom)
    val decline = { text: String? ->
        open = false
        scope.launch {
            calls.hangUp()
            if (text == null) {
                chatOpenRequests.request(slot, call.userId)
            } else {
                conversations.send(chatId = call.userId, text = text, replyToMessageId = null).onFailure {
                    logger.w(it) { "Odpověď na odmítnutý hovor se nepodařilo odeslat." }
                    chatOpenRequests.request(slot, call.userId)
                }
            }
        }
    }
    LabeledButton(stringResource(Res.string.call_message), modifier) {
        Box {
            FilledTonalIconButton(
                onClick = { open = true },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
            ) {
                Icon(
                    painterResource(Res.drawable.chat_bubble_24dp),
                    contentDescription = stringResource(Res.string.call_decline_with_message),
                    modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                )
            }
            ExpressiveMenu(
                expanded = open,
                onDismiss = { open = false },
                groups = listOf(
                    replies.map { text -> MenuAction(label = text, onClick = { decline(text) }) },
                    listOf(MenuAction(label = custom, icon = Res.drawable.edit_24dp, onClick = { decline(null) })),
                ),
            )
        }
    }
}
