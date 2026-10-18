package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalHapticFeedback
import eu.tintera.plume.core.audio.skipSilenceSupported
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.rememberTelegramLinkHandling
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.settings_telegram_links
import eu.tintera.plume.resources.settings_telegram_links_off
import eu.tintera.plume.resources.settings_telegram_links_on
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.settings_message_context
import eu.tintera.plume.resources.settings_message_context_summary
import eu.tintera.plume.resources.settings_open_links_in_app
import eu.tintera.plume.resources.settings_open_links_in_app_summary
import eu.tintera.plume.resources.headphones_24dp
import eu.tintera.plume.resources.settings_skip_silence
import eu.tintera.plume.resources.settings_skip_silence_summary
import org.koin.compose.koinInject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import eu.tintera.plume.resources.emoji_sets_row_help
import eu.tintera.plume.resources.emoji_sets_title
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.sticker_sets_row_help
import eu.tintera.plume.resources.sticker_sets_title
import eu.tintera.plume.resources.proxy_row_direct
import eu.tintera.plume.resources.proxy_row_help
import eu.tintera.plume.resources.proxy_row_via
import eu.tintera.plume.resources.proxy_title
import eu.tintera.plume.resources.settings_connection_group
import eu.tintera.plume.resources.vpn_lock_24dp
import eu.tintera.plume.resources.settings_chats
import eu.tintera.plume.resources.settings_messages
import eu.tintera.plume.resources.settings_links
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sekce Chaty: jak se chová seznam chatů (složky, řazení, akce tahem, widget), práce se
 * zprávami (reakce, akce ze zpráv, sady samolepek a emoji) a kde se otevírají odkazy.
 */
@Composable
internal fun ChatsSettings(
    swipe: ChatSwipeSettingsViewModel,
    quickReaction: QuickReactionSettingsViewModel,
    appearance: AppearanceViewModel,
    onOpenFolders: () -> Unit,
    onOpenStickerSets: () -> Unit,
    onOpenEmojiSets: () -> Unit,
) {
    val actions by swipe.actions.collectAsStateWithLifecycle()
    // Která strana má otevřený výběr; `null` = žádná.
    var picking by rememberSaveable { mutableStateOf<SwipeSide?>(null) }

    SettingsGroup(Res.string.settings_chats) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.folder_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.chat_folders_title)) },
            supportingContent = { Text(stringResource(Res.string.chat_folders_row_help)) },
            modifier = Modifier.clickable(onClick = onOpenFolders),
        )
        SeparateUnreadRow(appearance)
        SwipeSide.entries.forEach { side ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(side.icon), contentDescription = null) },
                headlineContent = { Text(stringResource(side.title)) },
                supportingContent = { Text(stringResource(side.of(actions).label)) },
                modifier = Modifier.clickable { picking = side },
            )
        }
        ChatsWidgetRow(appearance)
    }

    SettingsGroup(Res.string.settings_messages) {
        QuickReactionSettings(quickReaction)
        MessageContextRow()
        if (skipSilenceSupported) SkipSilenceRow()
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.sticker_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.sticker_sets_title)) },
            supportingContent = { Text(stringResource(Res.string.sticker_sets_row_help)) },
            modifier = Modifier.clickable(onClick = onOpenStickerSets),
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.mood_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.emoji_sets_title)) },
            supportingContent = { Text(stringResource(Res.string.emoji_sets_row_help)) },
            modifier = Modifier.clickable(onClick = onOpenEmojiSets),
        )
    }

    SettingsGroup(Res.string.settings_links) {
        OpenLinksInAppRow()
        TelegramLinksRow()
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
 * Odkazy `t.me` z jiných aplikací a z fotoaparátu (naskenovaný „Můj QR kód"). Plume je přijímá,
 * ale od Androidu 12 je systém pošle do prohlížeče, dokud je uživatel Plume nepovolí — řádek
 * říká, jak to je, a vede do systémového nastavení. Přepínač to není: přepnout to aplikace
 * sama nesmí. Kde volba není ([rememberTelegramLinkHandling] `null`), řádek chybí.
 */
@Composable
private fun TelegramLinksRow() {
    val handling = rememberTelegramLinkHandling() ?: return
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.link_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_telegram_links)) },
        supportingContent = {
            Text(
                stringResource(
                    if (handling.enabled) Res.string.settings_telegram_links_on else Res.string.settings_telegram_links_off,
                ),
            )
        },
        modifier = Modifier.clickable(onClick = handling.openSettings),
    )
}

/**
 * Ikonka akcí u zprávy s termínem, účtem nebo zásilkou (`findMessageContext`). Tady, ne ve skupině
 * AI v zařízení: rozpoznávají pravidla, takže funguje i bez modelu a na iOS — a skupina AI se na
 * telefonu bez modelu vůbec neukáže.
 */
@Composable
private fun MessageContextRow() {
    val settings = koinInject<AppSettings>()
    val haptics = LocalHapticFeedback.current
    val enabled by settings.messageContextEnabled.collectAsStateWithLifecycle()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.auto_awesome_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_message_context)) },
        supportingContent = { Text(stringResource(Res.string.settings_message_context_summary)) },
        trailingContent = { IconSwitch(checked = enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = enabled,
            role = Role.Switch,
            onValueChange = { on ->
                haptics.toggle(on)
                settings.setMessageContextEnabled(on)
            },
        ),
    )
}

/**
 * Přeskakování ticha v hlasovkách a audiu (`AppSettings.skipSilence`). V Nastavení, ne u čipu
 * rychlosti v bublině: volí se jednou a platí pro všechno, jako rychlost — a čip je jen u hrající
 * hlasovky, takže druhý prvek vedle něj by v bublině jen překážel. Jen kde to platforma umí
 * ([skipSilenceSupported]); na iOS a desktopu řádek chybí.
 */
@Composable
private fun SkipSilenceRow() {
    val settings = koinInject<AppSettings>()
    val haptics = LocalHapticFeedback.current
    val enabled by settings.skipSilence.collectAsStateWithLifecycle()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.headphones_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_skip_silence)) },
        supportingContent = { Text(stringResource(Res.string.settings_skip_silence_summary)) },
        trailingContent = { IconSwitch(checked = enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = enabled,
            role = Role.Switch,
            onValueChange = { on ->
                haptics.toggle(on)
                settings.setSkipSilence(on)
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
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
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
 * Připojení: proxy. Skupina v sekci Úložiště, jako „Data a úložiště" v oficiálním Telegramu — řeší, jestli
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
