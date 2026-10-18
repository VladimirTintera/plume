package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import eu.tintera.plume.core.ui.MediumButtonHeight
import eu.tintera.plume.core.ui.MediumButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_join
import eu.tintera.plume.resources.invite_members
import eu.tintera.plume.resources.invite_request
import eu.tintera.plume.resources.invite_subscribers
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Náhled chatu z pozvánky: fotka, název, počet členů, popis a tlačítko „Přidat se".
 *
 * **Přidává se až na potvrzení.** Ťuknutí na odkaz ve zprávě nesmí uživatele nikam přidat samo —
 * ze zprávy není poznat, kam pozvánka vede, a odejít ze skupiny je práce navíc. U pozvánky se
 * schválením je tlačítko „Požádat o přijetí": do chatu se nedostane hned, žádost čeká na správce.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InviteSheet(
    invite: InviteSheetState,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val preview = invite.preview

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            // Chat za pozvánkou ještě nemá id, barva iniciály se proto odvodí z názvu.
            Avatar(
                chatId = preview.title.hashCode().toLong(),
                title = preview.title,
                avatarFileId = preview.avatarFileId,
                size = 88.dp,
            )
            Text(
                text = preview.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = pluralStringResource(
                    if (preview.isChannel) Res.plurals.invite_subscribers else Res.plurals.invite_members,
                    preview.memberCount,
                    preview.memberCount.grouped(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.description.isNotBlank()) {
                Text(
                    text = preview.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            MediumButton(
                onClick = onJoin,
                enabled = !invite.joining,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(MediumButtonHeight),
            ) {
                if (invite.joining) {
                    LoadingIndicator(Modifier.size(ButtonDefaults.iconSizeFor(MediumButtonHeight)), color = LocalContentColor.current)
                } else {
                    Text(stringResource(if (preview.createsJoinRequest) Res.string.invite_request else Res.string.conversation_join))
                }
            }
        }
    }
}
