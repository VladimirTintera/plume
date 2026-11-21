package eu.tintera.plume.features.profile

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.foundation.text.input.TextFieldState
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.crown_24dp
import eu.tintera.plume.resources.person_remove_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MemberDetail
import eu.tintera.plume.core.telegram.MemberGroupKind
import eu.tintera.plume.core.telegram.MemberRole
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.sheetActionShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.edit_profile_save
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.logout_24dp
import eu.tintera.plume.resources.member_action_ban_channel
import eu.tintera.plume.resources.member_action_ban_group
import eu.tintera.plume.resources.member_action_dismiss_admin
import eu.tintera.plume.resources.member_action_edit_admin
import eu.tintera.plume.resources.member_action_edit_restrictions
import eu.tintera.plume.resources.member_action_message
import eu.tintera.plume.resources.member_action_promote
import eu.tintera.plume.resources.member_action_restrict
import eu.tintera.plume.resources.member_action_tag
import eu.tintera.plume.resources.member_action_transfer
import eu.tintera.plume.resources.member_action_unrestrict
import eu.tintera.plume.resources.member_ban_body
import eu.tintera.plume.resources.member_ban_body_basic
import eu.tintera.plume.resources.member_ban_confirm
import eu.tintera.plume.resources.member_ban_duration
import eu.tintera.plume.resources.member_ban_title
import eu.tintera.plume.resources.member_duration_day
import eu.tintera.plume.resources.member_duration_forever
import eu.tintera.plume.resources.member_duration_hour
import eu.tintera.plume.resources.member_duration_week
import eu.tintera.plume.resources.member_tag_help
import eu.tintera.plume.resources.member_tag_label
import eu.tintera.plume.resources.member_restricted_forever
import eu.tintera.plume.resources.member_restricted_until
import eu.tintera.plume.resources.member_transfer_body
import eu.tintera.plume.resources.member_transfer_confirm
import eu.tintera.plume.resources.member_transfer_password
import eu.tintera.plume.resources.member_transfer_password_help
import eu.tintera.plume.resources.member_transfer_title
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.profile_bot
import eu.tintera.plume.resources.profile_role_admin
import eu.tintera.plume.resources.profile_role_owner
import eu.tintera.plume.resources.shield_person_filled_24dp
import eu.tintera.plume.resources.star_filled_24dp
import eu.tintera.plume.resources.sticky_note_2_24dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Akce nad jedním členem skupiny po dlouhém stisku — bottom sheet jako akce nad chatem
 * v seznamu. Ťuknutí na člena dál otevírá konverzaci s ním; správa je vědomé gesto navíc.
 *
 * **Ukazuje se jen to, co server dovolí** ([MemberDetail.actions]): položka, která by skončila
 * chybou, je horší než položka, která tam není. Proto se člen načítá až při otevření nabídky —
 * `getChatMember` je dotaz na server a ptát se na každého ze seznamu dopředu by znamenalo
 * dvě stě dotazů za otevření profilu.
 */
@Composable
fun MemberActionsSheet(
    member: MemberDetail,
    onMessage: () -> Unit,
    onEditAdminRights: () -> Unit,
    onDismissAdmin: () -> Unit,
    onEditRestrictions: () -> Unit,
    onUnrestrict: () -> Unit,
    onEditTag: () -> Unit,
    onBan: () -> Unit,
    onTransferOwnership: () -> Unit,
    onDismiss: () -> Unit,
) {
    /*
     * Celý, bez napůl rozbaleného stavu, a obsah ještě scrollovatelný. S výchozím sheetem
     * skončilo „Vyhodit" pod spodní hranou obrazovky — u nabídky, kde je tahle položka
     * poslední a nejzávažnější, to vypadá, že vyhodit člena nejde. Ověřeno na emulátoru:
     * osm položek se do napůl rozbaleného sheetu nevejde.
     */
    val sheetState = rememberExpandedSheetState()
    val actions = member.actions

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SheetTitle(member.name)
                member.subtitle()?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            /*
             * Položky se nejdřív posbírají a teprve pak vykreslí: tvar krajních položek se počítá
             * z jejich POČTU, a ten je podle práv pokaždé jiný. Se skládáním za běhu by se zaoblení
             * rozešlo s tím, co je vidět.
             */
            val items = buildList {
                add(MemberSheetItem(Res.drawable.forum_24dp, Res.string.member_action_message, onMessage))
                if (actions.canEditTag) {
                    add(MemberSheetItem(Res.drawable.sticky_note_2_24dp, Res.string.member_action_tag, onEditTag))
                }
                if (actions.canPromote) {
                    add(
                        MemberSheetItem(
                            icon = Res.drawable.shield_person_filled_24dp,
                            label = if (member.role == MemberRole.Admin) {
                                Res.string.member_action_edit_admin
                            } else {
                                Res.string.member_action_promote
                            },
                            onClick = onEditAdminRights,
                        ),
                    )
                    if (member.role == MemberRole.Admin) {
                        add(MemberSheetItem(Res.drawable.person_24dp, Res.string.member_action_dismiss_admin, onDismissAdmin))
                    }
                }
                if (actions.canRestrict) {
                    add(
                        MemberSheetItem(
                            icon = Res.drawable.block_24dp,
                            label = if (member.isRestricted) {
                                Res.string.member_action_edit_restrictions
                            } else {
                                Res.string.member_action_restrict
                            },
                            onClick = onEditRestrictions,
                        ),
                    )
                    if (member.isRestricted) {
                        add(MemberSheetItem(Res.drawable.check_24dp, Res.string.member_action_unrestrict, onUnrestrict))
                    }
                }
                if (actions.canTransferOwnership) {
                    add(MemberSheetItem(Res.drawable.star_filled_24dp, Res.string.member_action_transfer, onTransferOwnership))
                }
                if (actions.canBan) {
                    add(
                        MemberSheetItem(
                            icon = Res.drawable.logout_24dp,
                            label = if (member.kind == MemberGroupKind.Channel) {
                                Res.string.member_action_ban_channel
                            } else {
                                Res.string.member_action_ban_group
                            },
                            onClick = onBan,
                            // Vyhození je ta jediná akce, kterou uživatel sám nevrátí — dostane
                            // chybovou barvu, aby se nespletla s ostatními.
                            destructive = true,
                        ),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEachIndexed { index, item ->
                    SheetActionRow(
                        icon = item.icon,
                        label = stringResource(item.label),
                        shape = sheetActionShape(index, items.size),
                        color = if (item.destructive) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (item.destructive) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        onClick = item.onClick,
                    )
                }
            }
        }
    }
}

/** Co o členovi stojí pod jménem: role, omezení a jeho vlastní štítek — to, co se právě teď mění. */
@Composable
private fun MemberDetail.subtitle(): String? {
    val roleLabel = when {
        role == MemberRole.Owner -> stringResource(Res.string.profile_role_owner)
        role == MemberRole.Admin -> stringResource(Res.string.profile_role_admin)
        isBot -> stringResource(Res.string.profile_bot)
        else -> null
    }
    val until = restrictedUntil
    val restriction = when {
        !isRestricted -> null
        until == null -> stringResource(Res.string.member_restricted_forever)
        else -> stringResource(Res.string.member_restricted_until, until.toLong().asUpcomingTime())
    }
    return listOfNotNull(tag, roleLabel, restriction).joinToString(SUBTITLE_SEPARATOR).ifEmpty { null }
}

private data class MemberSheetItem(
    val icon: DrawableResource,
    val label: StringResource,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/**
 * Potvrzení vyhození, i s dobou.
 *
 * Doba je v dialogu, ne rozbalená v nabídce jako u ztlumení: vyhození se potvrzuje tak jako tak,
 * takže by výběr před potvrzením znamenal dvě rozhodnutí po sobě na dvou místech.
 *
 * **Text pojmenovává následek a v základní skupině je jiný.** Ze supergroupy a kanálu se vyhozený
 * sám nevrátí, v základní skupině ho může kdokoli přidat zpátky — jeden popis pro obojí by lhal.
 */
@Composable
fun MemberBanDialog(
    member: MemberDetail,
    onConfirm: (Duration?) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by rememberSaveable { mutableStateOf<Duration?>(null) }

    ConfirmDialog(
        title = stringResource(Res.string.member_ban_title, member.name),
        text = stringResource(
            if (member.kind == MemberGroupKind.BasicGroup) Res.string.member_ban_body_basic else Res.string.member_ban_body,
        ),
        confirmLabel = stringResource(Res.string.member_ban_confirm),
        onConfirm = { onConfirm(duration) },
        onDismiss = onDismiss,
        icon = Res.drawable.person_remove_24dp,
        destructive = true,
    ) {
        Text(
            stringResource(Res.string.member_ban_duration),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        MEMBER_DURATIONS.forEach { (label, value) ->
            DurationRow(
                label = stringResource(label),
                selected = duration == value,
                onSelect = { duration = value },
            )
        }
    }
}

/**
 * Předání vlastnictví: nevratné, a proto s heslem dvoufázového ověření.
 *
 * Jestli to účet vůbec smí, se ptáme PŘED otevřením dialogu (`canTransferOwnership`) — bez
 * dvoufázového ověření by uživatel psal heslo zbytečně. Heslo se drží jen v tomhle dialogu
 * a odchází rovnou do TDLib.
 */
@Composable
fun TransferOwnershipDialog(
    member: MemberDetail,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Heslo vědomě NENÍ `rememberSaveable` (ani `rememberTextFieldState`, ten je saveable):
    // uložený stav se serializuje a přežil by i na disk.
    val passwordState = remember { TextFieldState() }
    val password = passwordState.text.toString()

    ConfirmDialog(
        title = stringResource(Res.string.member_transfer_title),
        text = stringResource(Res.string.member_transfer_body, member.name),
        confirmLabel = stringResource(Res.string.member_transfer_confirm),
        onConfirm = { onConfirm(password) },
        onDismiss = onDismiss,
        icon = Res.drawable.crown_24dp,
        destructive = true,
        confirmEnabled = password.isNotEmpty() && !busy,
    ) {
        OutlinedSecureTextField(
            state = passwordState,
            label = { Text(stringResource(Res.string.member_transfer_password)) },
            supportingText = { Text(stringResource(Res.string.member_transfer_password_help)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Vlastní štítek člena („Moderátor"). Dialog, ne obrazovka: je to jedno pole a jedno tlačítko.
 *
 * Prázdný štítek ho odebere — proto tu není zvláštní položka „smazat štítek".
 */
@Composable
fun MemberTagDialog(
    member: MemberDetail,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tag by rememberSaveable(member.userId) { mutableStateOf(member.tag.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.member_action_tag)) },
        text = {
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it.take(MEMBER_TAG_MAX_LENGTH) },
                label = { Text(stringResource(Res.string.member_tag_label)) },
                supportingText = { Text(stringResource(Res.string.member_tag_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tag) }, enabled = tag.trim() != member.tag.orEmpty(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.edit_profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/** Řádek nese klikání celý, kolečko má `onClick = null` — jako volby v Nastavení. */
@Composable
private fun DurationRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Doby omezení a vyhození; `null` = navždy.
 *
 * Drží se pod 366 dny — všechno delší bere TDLib stejně jako navždy, takže delší volba by jen
 * slibovala konec, který nenastane.
 */
internal val MEMBER_DURATIONS: List<Pair<StringResource, Duration?>> = listOf(
    Res.string.member_duration_hour to 1.hours,
    Res.string.member_duration_day to 1.days,
    Res.string.member_duration_week to 7.days,
    Res.string.member_duration_forever to null,
)

/** Telegram bere u štítku 16 znaků; delší by server stejně odmítl. */
private const val MEMBER_TAG_MAX_LENGTH = 16

private const val SUBTITLE_SEPARATOR = " · "
