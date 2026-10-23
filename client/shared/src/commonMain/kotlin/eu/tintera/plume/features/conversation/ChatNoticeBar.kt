package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.smartphone_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.InviteMembersBar
import eu.tintera.plume.core.telegram.JoinRequestBar
import eu.tintera.plume.core.telegram.SharePhoneBar
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_bar_join_request
import eu.tintera.plume.resources.chat_bar_invite_members
import eu.tintera.plume.resources.chat_bar_invite_members_hint
import eu.tintera.plume.resources.chat_bar_share_phone
import eu.tintera.plume.resources.chat_bar_share_phone_confirm
import eu.tintera.plume.resources.chat_bar_share_phone_text
import eu.tintera.plume.resources.chat_bar_share_phone_title
import eu.tintera.plume.resources.chat_bar_share_phone_hint
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.how_to_reg_24dp
import eu.tintera.plume.resources.person_add_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh nad konverzací, který nic nehlásí ani neblokuje: nasdílení čísla vzájemnému kontaktu
 * ([SharePhoneBar]), pozvání lidí do čerstvé skupiny ([InviteMembersBar]) a vysvětlení zprávy
 * od správce, u kterého uživatel žádal o vstup ([JoinRequestBar]).
 *
 * **Pilulka jako pruh připnutých ([BarPill]), ne karta s tlačítkem.** Dřív to byla karta
 * s větou a tónovým tlačítkem pod ní: o dva řádky vyšší než ostatní pruhy, s plochou tlačítka
 * na ploše skla — mezi pilulkami připnutých a videochatu vypadala jako cizí prvek. Akce je teď
 * celý pruh a nadpis ji pojmenovává („Pozvat členy"), jako u připnutých skok na zprávu.
 *
 * Křížek pruh zavře napořád (`removeChatActionBar`), jako u pruhu proti spamu.
 */
@Composable
internal fun ChatNoticeBarCard(
    bar: ChatBar,
    onSharePhone: () -> Unit,
    onInviteMembers: () -> Unit,
    onDismiss: () -> Unit,
    /** Mléčné sklo nad seznamem, jako u ostatních pruhů. */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val dismiss: @Composable () -> Unit = {
        IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
        }
    }
    when (bar) {
        SharePhoneBar -> BarPill(
            title = stringResource(Res.string.chat_bar_share_phone),
            subtitle = stringResource(Res.string.chat_bar_share_phone_hint),
            onClick = onSharePhone,
            glass = glass,
            modifier = modifier,
            leading = { BarBadge(Res.drawable.smartphone_24dp) },
            trailing = dismiss,
        )
        InviteMembersBar -> BarPill(
            title = stringResource(Res.string.chat_bar_invite_members),
            subtitle = stringResource(Res.string.chat_bar_invite_members_hint),
            onClick = onInviteMembers,
            glass = glass,
            modifier = modifier,
            leading = { BarBadge(Res.drawable.person_add_24dp) },
            trailing = dismiss,
        )
        // Název chatu a den za tečkou, ne uprostřed věty: `asDayLabel` je nadpisový („Včera").
        is JoinRequestBar -> BarPill(
            title = stringResource(Res.string.chat_bar_join_request),
            subtitle = listOf(bar.chatTitle, bar.requestDate.asDayLabel()).filter { it.isNotBlank() }.joinToString(" · "),
            onClick = null,
            glass = glass,
            modifier = modifier,
            leading = { BarBadge(Res.drawable.how_to_reg_24dp) },
            trailing = dismiss,
        )
        else -> Unit
    }
}

/**
 * Potvrzení nasdílení čísla z pruhu. Odchází osobní údaj a zpátky vzít nejde, takže ne na jedno
 * ťuknutí — stejně jako u tlačítka bota, které o číslo žádá.
 */
@Composable
internal fun ContactSharePhoneDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.chat_bar_share_phone_title),
        text = stringResource(Res.string.chat_bar_share_phone_text, name),
        confirmLabel = stringResource(Res.string.chat_bar_share_phone_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = Res.drawable.smartphone_24dp,
    )
}
