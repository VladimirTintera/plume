package eu.tintera.plume.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatProfile
import eu.tintera.plume.core.telegram.MemberRole
import eu.tintera.plume.core.telegram.ProfileKind
import eu.tintera.plume.core.telegram.ProfileMember
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.features.settings.SettingsGroup
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.invite_members
import eu.tintera.plume.resources.invite_subscribers
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.profile_bio
import eu.tintera.plume.resources.profile_bot
import eu.tintera.plume.resources.profile_description
import eu.tintera.plume.resources.profile_info
import eu.tintera.plume.resources.profile_members
import eu.tintera.plume.resources.profile_mute
import eu.tintera.plume.resources.profile_phone
import eu.tintera.plume.resources.profile_role_admin
import eu.tintera.plume.resources.profile_role_owner
import eu.tintera.plume.resources.profile_search
import eu.tintera.plume.resources.profile_unmute
import eu.tintera.plume.resources.profile_username
import eu.tintera.plume.resources.profile_username_value
import eu.tintera.plume.resources.search_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Profil chatu: velká fotka a jméno, řada akcí, údaje a u skupiny členové.
 *
 * - **Avatar a název přiletí z hlavičky konverzace** — tytéž sdílené klíče (`chatAvatarKey`,
 *   `chatTitleKey`) jako ze seznamu chatů do konverzace.
 * - **Údaje a členové jsou skupiny jako v Nastavení** (`SettingsGroup`): nadpis a karta s řádky.
 *   Aplikace tak má pro „seznam vlastností" jeden vzhled.
 * - **Akce jsou tónované dlaždice** s ikonou nad popiskem — velký cíl, a popisek řekne, co se stane
 *   (ztlumit / zapnout zvuk), ne jen stav.
 * - **Hledat zavře profil a otevře hledání v konverzaci** ([onSearch]); hledání patří konverzaci,
 *   profil by ho jen zdvojil.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatProfileScreen(
    viewModel: ChatProfileViewModel,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)

    LaunchedEffect(state.openChat) {
        val target = state.openChat ?: return@LaunchedEffect
        viewModel.consumeOpenChat()
        currentOnOpenChat(target)
    }
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        snackbarHostState.showSnackbar(getString(error.resource, *error.args.toTypedArray()))
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        val profile = state.profile
        if (profile == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) LoadingIndicator()
            }
            return@Scaffold
        }

        // Spodek (navigační lišta) jde do obsahu: poslední člen se nad ni doroluje.
        val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = 24.dp + bottom,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") { ProfileHeader(state, profile) }
            item(key = "actions") {
                ProfileActions(muted = state.muted, onToggleMute = viewModel::toggleMute, onSearch = onSearch)
            }
            if (profile.usernames.isNotEmpty() || profile.phone != null || profile.about.isNotBlank()) {
                item(key = "info") { ProfileInfo(profile, onLinkClick = viewModel::openLink) }
            }
            if (profile.canListMembers) {
                item(key = "members") {
                    ProfileMembers(state.members, onOpen = viewModel::openMember)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: ChatProfileState, profile: ChatProfile) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Avatar(
            chatId = profile.chatId,
            title = profile.title,
            avatarFileId = profile.photoFileId ?: profile.photoThumbnailFileId,
            size = HEADER_AVATAR_SIZE,
            modifier = Modifier.sharedNavElement(chatAvatarKey(profile.chatId)),
        )
        Text(
            text = profile.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).sharedNavText(chatTitleKey(profile.chatId)),
        )
        val subtitle = when (profile.kind) {
            ProfileKind.User -> state.presence?.describe()
            ProfileKind.Bot -> stringResource(Res.string.profile_bot)
            ProfileKind.Group -> profile.memberCount?.let { pluralStringResource(Res.plurals.invite_members, it, it.grouped()) }
            ProfileKind.Channel -> profile.memberCount?.let { pluralStringResource(Res.plurals.invite_subscribers, it, it.grouped()) }
            ProfileKind.SavedMessages -> null
        }
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileActions(muted: Boolean?, onToggleMute: () -> Unit, onSearch: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (muted != null) {
            ProfileAction(
                icon = if (muted) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp,
                label = stringResource(if (muted) Res.string.profile_unmute else Res.string.profile_mute),
                onClick = onToggleMute,
                modifier = Modifier.weight(1f),
            )
        }
        ProfileAction(
            icon = Res.drawable.search_24dp,
            label = stringResource(Res.string.profile_search),
            onClick = onSearch,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Akce jako tónovaná dlaždice: ikona nad popiskem. */
@Composable
private fun ProfileAction(icon: DrawableResource, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 14.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProfileInfo(profile: ChatProfile, onLinkClick: (String) -> Unit) {
    SettingsGroup(title = Res.string.profile_info) {
        profile.usernames.firstOrNull()?.let { username ->
            InfoRow(label = stringResource(Res.string.profile_username)) {
                Text(stringResource(Res.string.profile_username_value, username), style = MaterialTheme.typography.bodyLarge)
            }
        }
        profile.phone?.let { phone ->
            InfoRow(label = stringResource(Res.string.profile_phone)) {
                Text(phone, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (profile.about.isNotBlank()) {
            val isPerson = profile.kind == ProfileKind.User || profile.kind == ProfileKind.Bot
            InfoRow(label = stringResource(if (isPerson) Res.string.profile_bio else Res.string.profile_description)) {
                RichText(
                    text = profile.about,
                    entities = profile.aboutEntities,
                    style = MaterialTheme.typography.bodyLarge,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

/** Údaj: hodnota a pod ní, co to je — jako v kontaktech. */
@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    ListItem(
        headlineContent = value,
        supportingContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileMembers(members: List<ProfileMember>?, onOpen: (userId: Long) -> Unit) {
    SettingsGroup(title = Res.string.profile_members) {
        when {
            members == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            else -> members.forEach { member ->
                ListItem(
                    headlineContent = { Text(member.sender.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Avatar(
                            chatId = member.sender.id,
                            title = member.sender.title,
                            avatarFileId = member.sender.avatarFileId,
                            size = 40.dp,
                        )
                    },
                    trailingContent = when (member.role) {
                        MemberRole.Owner -> { { RoleLabel(stringResource(Res.string.profile_role_owner)) } }
                        MemberRole.Admin -> { { RoleLabel(stringResource(Res.string.profile_role_admin)) } }
                        MemberRole.Member -> null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(member.sender.id) },
                )
            }
        }
    }
}

@Composable
private fun RoleLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

private val HEADER_AVATAR_SIZE = 112.dp
