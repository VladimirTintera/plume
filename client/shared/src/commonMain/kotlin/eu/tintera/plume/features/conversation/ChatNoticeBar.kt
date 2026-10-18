package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.InviteMembersBar
import eu.tintera.plume.core.telegram.JoinRequestBar
import eu.tintera.plume.core.telegram.SharePhoneBar
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_bar_join_request_channel
import eu.tintera.plume.resources.chat_bar_join_request_date
import eu.tintera.plume.resources.chat_bar_invite_members
import eu.tintera.plume.resources.chat_bar_invite_members_hint
import eu.tintera.plume.resources.chat_bar_join_request_group
import eu.tintera.plume.resources.chat_bar_share_phone
import eu.tintera.plume.resources.chat_bar_share_phone_confirm
import eu.tintera.plume.resources.chat_bar_share_phone_text
import eu.tintera.plume.resources.chat_bar_share_phone_title
import eu.tintera.plume.resources.chat_bar_share_phone_hint
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_close
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh nad konverzací, který nic nehlásí ani neblokuje: nasdílení čísla vzájemnému kontaktu
 * ([SharePhoneBar]), pozvání lidí do čerstvé skupiny ([InviteMembersBar]) a vysvětlení zprávy
 * od správce, u kterého uživatel žádal o vstup ([JoinRequestBar]). Tvarem a okrajem je to tentýž pruh jako [SpamBarCard] — jedno místo
 * nad konverzací, kam server dává nabídky, jeden vzhled.
 *
 * Křížek pruh zavře napořád (`removeChatActionBar`), jako u pruhu proti spamu.
 */
@Composable
internal fun ChatNoticeBarCard(
    bar: ChatBar,
    onSharePhone: () -> Unit,
    onInviteMembers: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp)) {
                when (bar) {
                    SharePhoneBar -> {
                        Text(
                            text = stringResource(Res.string.chat_bar_share_phone_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // Posun o vnitřní odsazení `TextButton`, aby popisek lícoval s textem.
                        TextButton(onClick = onSharePhone, modifier = Modifier.offset(x = (-12).dp), shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.chat_bar_share_phone))
                        }
                    }
                    InviteMembersBar -> {
                        Text(
                            text = stringResource(Res.string.chat_bar_invite_members_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onInviteMembers, modifier = Modifier.offset(x = (-12).dp), shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.chat_bar_invite_members))
                        }
                    }
                    is JoinRequestBar -> {
                        Text(
                            text = stringResource(
                                if (bar.isChannel) Res.string.chat_bar_join_request_channel
                                else Res.string.chat_bar_join_request_group,
                                bar.chatTitle,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // Datum na vlastním řádku za dvojtečkou: `asDayLabel` je nadpisový
                        // („Včera") a uprostřed věty by mělo velké písmeno.
                        Text(
                            text = stringResource(Res.string.chat_bar_join_request_date, bar.requestDate.asDayLabel()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    else -> Unit
                }
            }
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
            }
        }
    }
}

/**
 * Potvrzení nasdílení čísla z pruhu. Odchází osobní údaj a zpátky vzít nejde, takže ne na jedno
 * ťuknutí — stejně jako u tlačítka bota, které o číslo žádá.
 */
@Composable
internal fun ContactSharePhoneDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_bar_share_phone_title)) },
        text = { Text(stringResource(Res.string.chat_bar_share_phone_text, name)) },
        confirmButton = { TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.chat_bar_share_phone_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
