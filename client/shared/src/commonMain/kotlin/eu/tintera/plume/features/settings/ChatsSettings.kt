package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalHapticFeedback
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.settings_open_links_in_app
import eu.tintera.plume.resources.settings_open_links_in_app_summary
import org.koin.compose.koinInject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.settings.ChatSwipeAction
import eu.tintera.plume.core.settings.ChatSwipeActions
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.arrow_forward_24dp
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_mark_read
import eu.tintera.plume.resources.chats_action_mute
import eu.tintera.plume.resources.chats_action_pin
import eu.tintera.plume.resources.chats_action_snooze
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.settings_swipe_body
import eu.tintera.plume.resources.settings_swipe_left
import eu.tintera.plume.resources.settings_swipe_none
import eu.tintera.plume.resources.settings_swipe_right
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.features.proxy.ProxyStatus
import eu.tintera.plume.features.proxy.ProxyStatusViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_folders_row_help
import eu.tintera.plume.resources.chat_folders_title
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.proxy_row_direct
import eu.tintera.plume.resources.proxy_row_help
import eu.tintera.plume.resources.proxy_row_via
import eu.tintera.plume.resources.proxy_title
import eu.tintera.plume.resources.settings_connection_group
import eu.tintera.plume.resources.vpn_lock_24dp
import eu.tintera.plume.resources.settings_chat_folders_group
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Skupina „Chaty" v Obecných: složky, akce tahem v seznamu chatů, rychlá reakce a otevírání odkazů.
 * Samostatná sekce by měla pár položek.
 */
@Composable
internal fun ChatsSettings(
    swipe: ChatSwipeSettingsViewModel,
    quickReaction: QuickReactionSettingsViewModel,
    onOpenFolders: () -> Unit,
) {
    val actions by swipe.actions.collectAsStateWithLifecycle()
    // Která strana má otevřený výběr; `null` = žádná.
    var picking by rememberSaveable { mutableStateOf<SwipeSide?>(null) }

    SettingsGroup(Res.string.settings_chat_folders_group) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.folder_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.chat_folders_title)) },
            supportingContent = { Text(stringResource(Res.string.chat_folders_row_help)) },
            modifier = Modifier.clickable(onClick = onOpenFolders),
        )
        SwipeSide.entries.forEach { side ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(side.icon), contentDescription = null) },
                headlineContent = { Text(stringResource(side.title)) },
                supportingContent = { Text(stringResource(side.of(actions).label)) },
                modifier = Modifier.clickable { picking = side },
            )
        }
        QuickReactionSettings(quickReaction)
        OpenLinksInAppRow()
    }

    picking?.let { side ->
        SwipeActionDialog(
            title = side.title,
            selected = side.of(actions),
            onSelect = { action ->
                picking = null
                when (side) {
                    SwipeSide.Right -> swipe.setRight(action)
                    SwipeSide.Left -> swipe.setLeft(action)
                }
            },
            onDismiss = { picking = null },
        )
    }
}

/**
 * Webové odkazy v aplikaci (Custom Tabs / Safari uvnitř Plume), nebo v prohlížeči. Odkaz, který
 * si vezme jiná aplikace, jde do ní v obou režimech — věta pod názvem to říká, aby vypnutí
 * nevypadalo jako jediná cesta do YouTube.
 */
@Composable
private fun OpenLinksInAppRow() {
    val settings = koinInject<AppSettings>()
    val haptics = LocalHapticFeedback.current
    val enabled by settings.openLinksInApp.collectAsStateWithLifecycle()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.open_in_new_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_open_links_in_app)) },
        supportingContent = { Text(stringResource(Res.string.settings_open_links_in_app_summary)) },
        trailingContent = { IconSwitch(checked = enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = enabled,
            role = Role.Switch,
            onValueChange = { on ->
                haptics.toggle(on)
                settings.setOpenLinksInApp(on)
            },
        ),
    )
}

/**
 * Výběr akce pro jednu stranu. Akce se nabízejí slovesem, jak je zná nabídka nad chatem; že se
 * u chatu, kde už platí, otočí, říká věta nad nimi — jinak by „Ztlumit" vypadalo, že tahem
 * odtlumit nejde.
 */
@Composable
private fun SwipeActionDialog(
    title: StringResource,
    selected: ChatSwipeAction,
    onSelect: (ChatSwipeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Text(stringResource(Res.string.settings_swipe_body), modifier = Modifier.padding(bottom = 8.dp))
                ChatSwipeAction.entries.forEach { action ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = action == selected, role = Role.RadioButton, onClick = { onSelect(action) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = action == selected, onClick = null)
                        Text(stringResource(action.label), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/** Strana tahu v Nastavení; fyzický směr, jako v [ChatSwipeActions]. */
private enum class SwipeSide { Right, Left }

private fun SwipeSide.of(actions: ChatSwipeActions): ChatSwipeAction = when (this) {
    SwipeSide.Right -> actions.right
    SwipeSide.Left -> actions.left
}

private val SwipeSide.title: StringResource
    get() = when (this) {
        SwipeSide.Right -> Res.string.settings_swipe_right
        SwipeSide.Left -> Res.string.settings_swipe_left
    }

private val SwipeSide.icon: DrawableResource
    get() = when (this) {
        SwipeSide.Right -> Res.drawable.arrow_forward_24dp
        SwipeSide.Left -> Res.drawable.arrow_back_24dp
    }

private val ChatSwipeAction.label: StringResource
    get() = when (this) {
        ChatSwipeAction.Archive -> Res.string.chats_action_archive
        ChatSwipeAction.Read -> Res.string.chats_action_mark_read
        ChatSwipeAction.Mute -> Res.string.chats_action_mute
        ChatSwipeAction.Pin -> Res.string.chats_action_pin
        ChatSwipeAction.Snooze -> Res.string.chats_action_snooze
        ChatSwipeAction.None -> Res.string.settings_swipe_none
    }

/**
 * Připojení: proxy. Vlastní skupina, protože nesouvisí s chaty ani se soukromím — řeší, jestli
 * se aplikace k Telegramu vůbec dostane.
 *
 * Podtitul říká, přes kterou proxy se aplikace připojuje. Zapnutá proxy, která nefunguje, je
 * nejčastější důvod „Připojování…", a bez stavu v řádku by se na ni přišlo až na její obrazovce.
 */
@Composable
internal fun ConnectionSettings(viewModel: ProxyStatusViewModel, onOpenProxy: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    SettingsGroup(Res.string.settings_connection_group) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.vpn_lock_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.proxy_title)) },
            supportingContent = {
                when (val current = status) {
                    // Dokud stav nedorazí, řádek nic netvrdí — „vypnutá" by mohla být lež.
                    null -> Unit
                    ProxyStatus.Direct -> Text(stringResource(Res.string.proxy_row_direct))
                    is ProxyStatus.Via -> Text(
                        stringResource(
                            Res.string.proxy_row_via,
                            "${current.config.server}:${current.config.port}",
                        ),
                    )
                    ProxyStatus.Unknown -> Text(stringResource(Res.string.proxy_row_help))
                }
            },
            modifier = Modifier.clickable(onClick = onOpenProxy),
        )
    }
}
