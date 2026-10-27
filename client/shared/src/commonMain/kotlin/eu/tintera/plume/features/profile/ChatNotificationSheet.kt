package eu.tintera.plume.features.profile

import eu.tintera.plume.resources.settings_notif_stories_chat_summary
import eu.tintera.plume.resources.settings_notif_stories_chat
import eu.tintera.plume.resources.undo_24dp
import eu.tintera.plume.resources.snooze_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.notifications_24dp
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatNotificationOption
import eu.tintera.plume.core.telegram.ChatNotificationPrefs
import eu.tintera.plume.core.telegram.MuteState
import eu.tintera.plume.core.telegram.NotificationDefaults
import eu.tintera.plume.core.telegram.NotificationScope
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notif_sound_row
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.profile_notif_default
import eu.tintera.plume.resources.profile_notif_default_group
import eu.tintera.plume.resources.profile_notif_mute_1h
import eu.tintera.plume.resources.profile_notif_mute_2d
import eu.tintera.plume.resources.profile_notif_mute_8h
import eu.tintera.plume.resources.profile_notif_mute_forever
import eu.tintera.plume.resources.profile_notif_muted
import eu.tintera.plume.resources.profile_notif_muted_until
import eu.tintera.plume.resources.profile_notif_on
import eu.tintera.plume.resources.profile_notif_reset
import eu.tintera.plume.resources.profile_notif_reset_group
import eu.tintera.plume.resources.profile_notif_sound_summary
import eu.tintera.plume.resources.profile_notif_title
import eu.tintera.plume.resources.profile_notif_unmute
import eu.tintera.plume.resources.settings_notif_mentions
import eu.tintera.plume.resources.settings_notif_mentions_summary
import eu.tintera.plume.resources.settings_notif_pinned
import eu.tintera.plume.resources.settings_notif_pinned_summary
import eu.tintera.plume.resources.settings_notif_preview
import eu.tintera.plume.resources.settings_notif_preview_summary
import eu.tintera.plume.resources.settings_notif_sound
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import eu.tintera.plume.core.ui.SwitchListItem

/** Stav upozornění chatu jednou větou — do řádku v profilu i do hlavičky sheetu. */
@Composable
internal fun ChatNotificationPrefs.summary(): String {
    val state = when (val mute = mute) {
        MuteState.Off -> stringResource(Res.string.profile_notif_on)
        MuteState.Forever -> stringResource(Res.string.profile_notif_muted)
        is MuteState.Until -> stringResource(Res.string.profile_notif_muted_until, mute.epochSeconds.asUpcomingTime())
    }
    // Že chat jede podle rozsahu, je potřeba říct: změna výchozího nastavení skupin ho změní taky.
    // Téma se takhle řídí skupinou.
    if (!muteIsDefault) return state
    return when (defaults) {
        NotificationDefaults.Scope -> stringResource(Res.string.profile_notif_default, state)
        NotificationDefaults.Chat -> stringResource(Res.string.profile_notif_default_group, state)
    }
}

/**
 * Upozornění jednoho chatu (nebo tématu): doba ztlumení, co upozornění ukáže, zvuk konverzace
 * v systému a návrat k výchozímu nastavení.
 *
 * Doby ztlumení jsou akce (sheet zavřou), přepínače ne — ty se přepínají po několika.
 * [title] je u tématu jeho název: sheet se otevírá z nabídky nad tématem, a kterého se týká,
 * má být vidět i tady.
 */
@Composable
internal fun ChatNotificationSheet(
    prefs: ChatNotificationPrefs,
    title: String = stringResource(Res.string.profile_notif_title),
    supportsConversationSettings: Boolean,
    onMute: (Duration?) -> Unit,
    onUnmute: () -> Unit,
    onOption: (ChatNotificationOption, Boolean) -> Unit,
    onOpenConversationSettings: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    /** Zvuk z Telegramu („Výchozí", název tónu…); `null` = výběr se nenabízí (téma). */
    soundLabel: String? = null,
    onPickSound: () -> Unit = {},
) {
    // Celý, bez napůl rozbaleného stavu: přepínače a obnovení by jinak zůstaly pod okrajem.
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            SheetTitle(title, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            Text(
                prefs.summary(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 16.dp),
            )

            // Tři bloky: ztlumení (akce, sheet zavřou), volby (přepínají se po několika)
            // a návrat k výchozímu. Dřív holé řádky s dělicí čárou uprostřed.
            GroupedColumn(Modifier.padding(horizontal = 16.dp)) {
                if (prefs.mute != MuteState.Off) {
                    ActionRow(Res.string.profile_notif_unmute, Res.drawable.notifications_24dp, onClick = onUnmute)
                }
                ActionRow(Res.string.profile_notif_mute_1h, Res.drawable.snooze_24dp) { onMute(1.hours) }
                ActionRow(Res.string.profile_notif_mute_8h, Res.drawable.snooze_24dp) { onMute(8.hours) }
                ActionRow(Res.string.profile_notif_mute_2d, Res.drawable.snooze_24dp) { onMute(2.days) }
                if (prefs.mute != MuteState.Forever) {
                    ActionRow(Res.string.profile_notif_mute_forever, Res.drawable.notifications_off_24dp) { onMute(null) }
                }
            }

            Spacer(Modifier.height(GROUP_SPACING))
            GroupedColumn(Modifier.padding(horizontal = 16.dp)) {
                SwitchListItem(
                    headline = stringResource(Res.string.settings_notif_preview),
                    supporting = stringResource(Res.string.settings_notif_preview_summary),
                    checked = prefs.showPreview,
                    onCheckedChange = { onOption(ChatNotificationOption.ShowPreview, it) },
                )
                SwitchListItem(
                    headline = stringResource(Res.string.settings_notif_pinned),
                    supporting = stringResource(Res.string.settings_notif_pinned_summary),
                    checked = prefs.pinnedMessages,
                    onCheckedChange = { onOption(ChatNotificationOption.PinnedMessages, it) },
                )
                // Zmínit se dá jen ve skupině, stejně jako v Nastavení.
                if (prefs.scope == NotificationScope.Groups) {
                    SwitchListItem(
                        headline = stringResource(Res.string.settings_notif_mentions),
                        supporting = stringResource(Res.string.settings_notif_mentions_summary),
                        checked = prefs.mentions,
                        onCheckedChange = { onOption(ChatNotificationOption.Mentions, it) },
                    )
                }
                // Příběhy zveřejňují lidé a kanály; téma ve skupině je nemá.
                if (prefs.defaults == NotificationDefaults.Scope &&
                    (prefs.scope == NotificationScope.Private || prefs.scope == NotificationScope.Channels)
                ) {
                    SwitchListItem(
                        headline = stringResource(Res.string.settings_notif_stories_chat),
                        supporting = stringResource(Res.string.settings_notif_stories_chat_summary),
                        checked = prefs.stories,
                        onCheckedChange = { onOption(ChatNotificationOption.Stories, it) },
                    )
                }
                // Zvuk z Telegramu — nastavení účtu, platí i v oficiálních aplikacích (jako u rozsahu
                // v Nastavení). Na Androidu vybírá kanál, kterým chat zní.
                soundLabel?.let { label ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.notif_sound_row)) },
                        supportingContent = { Text(label) },
                        modifier = Modifier.clickable(onClick = onPickSound),
                    )
                }
                // Vibrace, důležitost a systémový zvuk jednoho chatu nastavuje systém (kanál
                // konverzace), aplikace je nepřepíše.
                if (supportsConversationSettings) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.settings_notif_sound)) },
                        supportingContent = { Text(stringResource(Res.string.profile_notif_sound_summary)) },
                        trailingContent = { Icon(painterResource(Res.drawable.open_in_new_24dp), contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenConversationSettings),
                    )
                }
            }

            if (!prefs.isDefault) {
                val reset = when (prefs.defaults) {
                    NotificationDefaults.Scope -> Res.string.profile_notif_reset
                    NotificationDefaults.Chat -> Res.string.profile_notif_reset_group
                }
                Spacer(Modifier.height(GROUP_SPACING))
                GroupedColumn(Modifier.padding(horizontal = 16.dp)) {
                    ActionRow(reset, Res.drawable.undo_24dp, color = MaterialTheme.colorScheme.primary, onClick = onReset)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(title: StringResource, icon: DrawableResource, color: Color = Color.Unspecified, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = color.takeOrElse { MaterialTheme.colorScheme.onSurface },
            leadingIconColor = color.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
        ),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
