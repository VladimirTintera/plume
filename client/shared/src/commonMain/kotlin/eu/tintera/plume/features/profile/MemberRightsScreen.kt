package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.AdminRights
import eu.tintera.plume.core.telegram.GroupPermissions
import eu.tintera.plume.core.telegram.MemberDetail
import eu.tintera.plume.core.telegram.MemberGroupKind
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.edit_chat_perm_change_info
import eu.tintera.plume.resources.edit_chat_perm_invite
import eu.tintera.plume.resources.edit_chat_perm_pin
import eu.tintera.plume.resources.edit_chat_perm_react
import eu.tintera.plume.resources.edit_chat_perm_send_documents
import eu.tintera.plume.resources.edit_chat_perm_send_messages
import eu.tintera.plume.resources.edit_chat_perm_send_photos
import eu.tintera.plume.resources.edit_chat_perm_send_polls
import eu.tintera.plume.resources.edit_chat_perm_send_stickers
import eu.tintera.plume.resources.edit_chat_perm_send_video_notes
import eu.tintera.plume.resources.edit_chat_perm_send_videos
import eu.tintera.plume.resources.edit_chat_perm_send_voice
import eu.tintera.plume.resources.edit_profile_save
import eu.tintera.plume.resources.member_restrict_duration
import eu.tintera.plume.resources.member_restricted_until
import eu.tintera.plume.resources.member_right_anonymous
import eu.tintera.plume.resources.member_right_change_info
import eu.tintera.plume.resources.member_right_change_info_channel
import eu.tintera.plume.resources.member_right_delete
import eu.tintera.plume.resources.member_right_edit
import eu.tintera.plume.resources.member_right_invite
import eu.tintera.plume.resources.member_right_invite_channel
import eu.tintera.plume.resources.member_right_manage_topics
import eu.tintera.plume.resources.member_right_pin
import eu.tintera.plume.resources.member_right_post
import eu.tintera.plume.resources.member_right_promote
import eu.tintera.plume.resources.member_right_restrict
import eu.tintera.plume.resources.member_rights_admin_help
import eu.tintera.plume.resources.member_rights_admin_title
import eu.tintera.plume.resources.member_rights_restrict_help
import eu.tintera.plume.resources.member_rights_restrict_title
import kotlin.time.Duration
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Práva jednoho člena: správcovská, nebo jeho omezení. Otevírá se z nabídky nad členem
 * v záložce Členové a vrší se nad profil jako úprava skupiny.
 *
 * **Nabízí se jen práva, která v tomhle druhu chatu dávají smysl.** V kanálu se publikuje
 * a upravují cizí příspěvky, ve skupině se připíná a omezuje; anonymita je jen v supergroupě.
 * Právo, které by server ignoroval, by slibovalo vliv, který nemá.
 */
@Composable
fun MemberRightsScreen(viewModel: MemberRightsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)

    val notice = viewModel.notice
    val noticeText = notice?.resolve()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) currentOnBack()
    }

    val member = viewModel.member

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.admin) {
                                Res.string.member_rights_admin_title
                            } else {
                                Res.string.member_rights_restrict_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painterResource(Res.drawable.arrow_back_24dp),
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = viewModel.canSave) {
                        Text(stringResource(Res.string.edit_profile_save))
                    }
                },
            )
        },
    ) { padding ->
        if (member == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (!viewModel.loadFailed) LoadingIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MemberHeader(member)

            if (viewModel.admin) {
                AdminRightsCard(member.kind, member.isForum, viewModel.rights, viewModel::setRights)
                Text(
                    stringResource(Res.string.member_rights_admin_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                viewModel.permissions?.let { permissions ->
                    RestrictionsCard(permissions, viewModel::setPermissions)
                }
                DurationCard(
                    restrictedUntil = member.restrictedUntil.takeIf { viewModel.remaining != null },
                    keepsCurrentEnd = viewModel.keepsCurrentEnd,
                    selected = viewModel.duration,
                    onKeep = viewModel::keepCurrentEnd,
                    onSelect = viewModel::chooseDuration,
                )
                Text(
                    stringResource(Res.string.member_rights_restrict_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** Komu se práva mění. Bez jména a tváře je obrazovka jen anonymní seznam přepínačů. */
@Composable
private fun MemberHeader(member: MemberDetail) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(
            chatId = member.userId,
            title = member.name,
            avatarFileId = member.avatarFileId,
            size = HEADER_AVATAR_SIZE,
        )
        Text(member.name, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * Práva správce. Skupina a kanál ukazují každý jinou podmnožinu: v kanálu nikdo nepíše, takže
 * se nepřipíná ani neomezuje, zato se publikuje a upravují cizí příspěvky.
 */
@Composable
private fun AdminRightsCard(
    kind: MemberGroupKind,
    isForum: Boolean,
    rights: AdminRights,
    onChange: ((AdminRights) -> AdminRights) -> Unit,
) {
    val channel = kind == MemberGroupKind.Channel
    Card(Modifier.fillMaxWidth()) {
        Column {
            SwitchRow(
                if (channel) Res.string.member_right_change_info_channel else Res.string.member_right_change_info,
                rights.changeInfo,
            ) { value -> onChange { it.copy(changeInfo = value) } }

            if (channel) {
                SwitchRow(Res.string.member_right_post, rights.postMessages) { value ->
                    onChange { it.copy(postMessages = value) }
                }
                SwitchRow(Res.string.member_right_edit, rights.editMessages) { value ->
                    onChange { it.copy(editMessages = value) }
                }
            }

            SwitchRow(Res.string.member_right_delete, rights.deleteMessages) { value ->
                onChange { it.copy(deleteMessages = value) }
            }

            if (!channel) {
                SwitchRow(Res.string.member_right_restrict, rights.restrictMembers) { value ->
                    onChange { it.copy(restrictMembers = value) }
                }
            }

            SwitchRow(
                if (channel) Res.string.member_right_invite_channel else Res.string.member_right_invite,
                rights.inviteUsers,
            ) { value -> onChange { it.copy(inviteUsers = value) } }

            if (!channel) {
                SwitchRow(Res.string.member_right_pin, rights.pinMessages) { value ->
                    onChange { it.copy(pinMessages = value) }
                }
            }

            // Jen ve skupině s tématy: jinde by přepínač sliboval vliv, který nemá.
            if (isForum) {
                SwitchRow(Res.string.member_right_manage_topics, rights.manageTopics) { value ->
                    onChange { it.copy(manageTopics = value) }
                }
            }

            SwitchRow(Res.string.member_right_promote, rights.promoteMembers) { value ->
                onChange { it.copy(promoteMembers = value) }
            }

            // Anonymitu má jen supergroup; v základní skupině ani v kanálu se nenabízí.
            if (kind == MemberGroupKind.Supergroup) {
                SwitchRow(Res.string.member_right_anonymous, rights.anonymous) { value ->
                    onChange { it.copy(anonymous = value) }
                }
            }
        }
    }
}

/**
 * Co smí omezený člen posílat — tytéž přepínače jako výchozí práva skupiny, protože je to tentýž
 * seznam práv, jen platí pro jednoho člověka.
 */
@Composable
private fun RestrictionsCard(
    permissions: GroupPermissions,
    onChange: ((GroupPermissions) -> GroupPermissions) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            SwitchRow(Res.string.edit_chat_perm_send_messages, permissions.sendMessages) { value ->
                onChange { it.copy(sendMessages = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_photos, permissions.sendPhotos) { value ->
                onChange { it.copy(sendPhotos = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_videos, permissions.sendVideos) { value ->
                onChange { it.copy(sendVideos = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_documents, permissions.sendDocuments) { value ->
                onChange { it.copy(sendDocuments = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_voice, permissions.sendVoiceNotes) { value ->
                onChange { it.copy(sendVoiceNotes = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_video_notes, permissions.sendVideoNotes) { value ->
                onChange { it.copy(sendVideoNotes = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_stickers, permissions.sendStickers) { value ->
                onChange { it.copy(sendStickers = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_send_polls, permissions.sendPolls) { value ->
                onChange { it.copy(sendPolls = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_react, permissions.react) { value ->
                onChange { it.copy(react = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_invite, permissions.inviteUsers) { value ->
                onChange { it.copy(inviteUsers = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_pin, permissions.pinMessages) { value ->
                onChange { it.copy(pinMessages = value) }
            }
            SwitchRow(Res.string.edit_chat_perm_change_info, permissions.changeInfo) { value ->
                onChange { it.copy(changeInfo = value) }
            }
        }
    }
}

/**
 * Do kdy omezení platí.
 *
 * **U už omezeného člena je první volba „ponechat"** se zbytkem běžícího omezení. Bez ní by
 * změna jediného přepínače u člověka omezeného na týden přepsala konec na „navždy" — a nebylo
 * by z čeho to poznat.
 */
@Composable
private fun DurationCard(
    /** `null` = člen omezení zatím nemá, takže není co ponechat. */
    restrictedUntil: Int?,
    keepsCurrentEnd: Boolean,
    selected: Duration?,
    onKeep: () -> Unit,
    onSelect: (Duration?) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.member_restrict_duration),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                if (restrictedUntil != null) {
                    DurationOption(
                        label = stringResource(Res.string.member_restricted_until, restrictedUntil.toLong().asUpcomingTime()),
                        selected = keepsCurrentEnd,
                        onSelect = onKeep,
                    )
                }
                MEMBER_DURATIONS.forEach { (label, value) ->
                    DurationOption(
                        label = stringResource(label),
                        selected = !keepsCurrentEnd && selected == value,
                        onSelect = { onSelect(value) },
                    )
                }
            }
        }
    }
}

/** Řádek nese klikání celý, kolečko má `onClick = null` — jako volby v Nastavení. */
@Composable
private fun DurationOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private val HEADER_AVATAR_SIZE = 56.dp
