package eu.tintera.plume.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatNotificationOption
import eu.tintera.plume.core.telegram.ChatNotificationPrefs
import eu.tintera.plume.core.telegram.MuteState
import eu.tintera.plume.core.telegram.NotificationDefaults
import eu.tintera.plume.core.telegram.NotificationScope
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.toggle
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
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(prefs.summary()) },
            )

            if (prefs.mute != MuteState.Off) ActionRow(Res.string.profile_notif_unmute, onClick = onUnmute)
            ActionRow(Res.string.profile_notif_mute_1h) { onMute(1.hours) }
            ActionRow(Res.string.profile_notif_mute_8h) { onMute(8.hours) }
            ActionRow(Res.string.profile_notif_mute_2d) { onMute(2.days) }
            if (prefs.mute != MuteState.Forever) ActionRow(Res.string.profile_notif_mute_forever) { onMute(null) }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SwitchRow(
                title = Res.string.settings_notif_preview,
                summary = Res.string.settings_notif_preview_summary,
                checked = prefs.showPreview,
                onChange = { onOption(ChatNotificationOption.ShowPreview, it) },
            )
            SwitchRow(
                title = Res.string.settings_notif_pinned,
                summary = Res.string.settings_notif_pinned_summary,
                checked = prefs.pinnedMessages,
                onChange = { onOption(ChatNotificationOption.PinnedMessages, it) },
            )
            // Zmínit se dá jen ve skupině, stejně jako v Nastavení.
            if (prefs.scope == NotificationScope.Groups) {
                SwitchRow(
                    title = Res.string.settings_notif_mentions,
                    summary = Res.string.settings_notif_mentions_summary,
                    checked = prefs.mentions,
                    onChange = { onOption(ChatNotificationOption.Mentions, it) },
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
            if (!prefs.isDefault) {
                val reset = when (prefs.defaults) {
                    NotificationDefaults.Scope -> Res.string.profile_notif_reset
                    NotificationDefaults.Chat -> Res.string.profile_notif_reset_group
                }
                ActionRow(reset, color = MaterialTheme.colorScheme.primary, onClick = onReset)
            }
        }
    }
}

@Composable
private fun ActionRow(title: StringResource, color: Color = Color.Unspecified, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(title), color = color) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: StringResource, summary: StringResource, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { IconSwitch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { on ->
            haptics.toggle(on)
            onChange(on)
        }),
    )
}
