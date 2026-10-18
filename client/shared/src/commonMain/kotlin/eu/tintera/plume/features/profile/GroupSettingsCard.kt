package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatReactions
import eu.tintera.plume.core.telegram.GroupSettings
import eu.tintera.plume.core.telegram.GroupToggle
import eu.tintera.plume.core.telegram.SLOW_MODE_DELAYS
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.durationText
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_reaction_24dp
import eu.tintera.plume.resources.edit_chat_reactions_save
import eu.tintera.plume.resources.edit_chat_reactions
import eu.tintera.plume.resources.edit_chat_reactions_all
import eu.tintera.plume.resources.edit_chat_reactions_help
import eu.tintera.plume.resources.edit_chat_reactions_none
import eu.tintera.plume.resources.edit_chat_reactions_some
import eu.tintera.plume.resources.edit_chat_reactions_some_count
import eu.tintera.plume.resources.edit_chat_settings_channel
import eu.tintera.plume.resources.edit_chat_settings_group
import eu.tintera.plume.resources.edit_chat_slow_mode
import eu.tintera.plume.resources.edit_chat_slow_mode_help
import eu.tintera.plume.resources.edit_chat_slow_mode_off
import eu.tintera.plume.resources.edit_chat_toggle_aggressive_anti_spam
import eu.tintera.plume.resources.edit_chat_toggle_aggressive_anti_spam_help
import eu.tintera.plume.resources.edit_chat_toggle_all_history
import eu.tintera.plume.resources.edit_chat_toggle_all_history_help
import eu.tintera.plume.resources.edit_chat_toggle_hidden_members
import eu.tintera.plume.resources.edit_chat_toggle_hidden_members_help
import eu.tintera.plume.resources.edit_chat_toggle_join_by_request
import eu.tintera.plume.resources.edit_chat_toggle_join_by_request_help
import eu.tintera.plume.resources.edit_chat_toggle_join_to_send
import eu.tintera.plume.resources.edit_chat_toggle_join_to_send_help
import eu.tintera.plume.resources.edit_chat_toggle_protected_content
import eu.tintera.plume.resources.edit_chat_toggle_direct_messages
import eu.tintera.plume.resources.edit_chat_toggle_direct_messages_help
import eu.tintera.plume.resources.edit_chat_toggle_protected_content_help
import eu.tintera.plume.resources.edit_chat_toggle_show_message_sender
import eu.tintera.plume.resources.edit_chat_toggle_show_message_sender_help
import eu.tintera.plume.resources.edit_chat_toggle_sign_messages
import eu.tintera.plume.resources.edit_chat_toggle_sign_messages_help
import eu.tintera.plume.resources.timer_24dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Nastavení skupiny nebo kanálu: pomalý režim a reakce jako řádky s výběrem, pod nimi přepínače.
 * Ukazuje se jen to, co [GroupSettings] nese — o tom rozhoduje `groupSettingsOffer` podle práv.
 *
 * Přepínače mají pod názvem větu s následkem: „Skrýt členy" ani „Agresivní antispam" samy
 * neřeknou, co se stane, a ukládají se hned, bez potvrzení.
 */
@Composable
internal fun GroupSettingsCard(
    channel: Boolean,
    settings: GroupSettings,
    onOpenSlowMode: () -> Unit,
    onOpenReactions: () -> Unit,
    onToggle: (GroupToggle, Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (channel) Res.string.edit_chat_settings_channel else Res.string.edit_chat_settings_group),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column {
                settings.slowModeDelay?.let { delay ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.edit_chat_slow_mode)) },
                        supportingContent = { Text(slowModeLabel(delay)) },
                        leadingContent = { Icon(painterResource(Res.drawable.timer_24dp), contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenSlowMode),
                    )
                }
                settings.reactions?.let { reactions ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.edit_chat_reactions)) },
                        supportingContent = { Text(reactionsLabel(reactions)) },
                        leadingContent = { Icon(painterResource(Res.drawable.add_reaction_24dp), contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenReactions),
                    )
                }
                GroupToggle.entries.forEach { toggle ->
                    val checked = settings.toggles[toggle] ?: return@forEach
                    // Profily autorů bez podpisů nedávají smysl — jako u oficiálních klientů.
                    if (toggle == GroupToggle.ShowMessageSender && settings.toggles[GroupToggle.SignMessages] != true) return@forEach
                    val (title, help) = toggle.texts()
                    ToggleRow(title, help, checked, haptics) { onToggle(toggle, it) }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: StringResource,
    help: StringResource,
    checked: Boolean,
    haptics: HapticFeedback,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(help)) },
        trailingContent = { IconSwitch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { on ->
            haptics.toggle(on)
            onChange(on)
        }),
    )
}

private fun GroupToggle.texts(): Pair<StringResource, StringResource> = when (this) {
    GroupToggle.AllHistory -> Res.string.edit_chat_toggle_all_history to Res.string.edit_chat_toggle_all_history_help
    GroupToggle.JoinToSend -> Res.string.edit_chat_toggle_join_to_send to Res.string.edit_chat_toggle_join_to_send_help
    GroupToggle.JoinByRequest -> Res.string.edit_chat_toggle_join_by_request to Res.string.edit_chat_toggle_join_by_request_help
    GroupToggle.HiddenMembers -> Res.string.edit_chat_toggle_hidden_members to Res.string.edit_chat_toggle_hidden_members_help
    GroupToggle.AggressiveAntiSpam ->
        Res.string.edit_chat_toggle_aggressive_anti_spam to Res.string.edit_chat_toggle_aggressive_anti_spam_help
    GroupToggle.SignMessages -> Res.string.edit_chat_toggle_sign_messages to Res.string.edit_chat_toggle_sign_messages_help
    GroupToggle.ShowMessageSender ->
        Res.string.edit_chat_toggle_show_message_sender to Res.string.edit_chat_toggle_show_message_sender_help
    GroupToggle.DirectMessages ->
        Res.string.edit_chat_toggle_direct_messages to Res.string.edit_chat_toggle_direct_messages_help
    GroupToggle.ProtectedContent ->
        Res.string.edit_chat_toggle_protected_content to Res.string.edit_chat_toggle_protected_content_help
}

@Composable
private fun slowModeLabel(seconds: Int): String =
    if (seconds == 0) stringResource(Res.string.edit_chat_slow_mode_off) else durationText(seconds)

@Composable
private fun reactionsLabel(reactions: ChatReactions): String = when {
    reactions.all -> stringResource(Res.string.edit_chat_reactions_all)
    reactions.none -> stringResource(Res.string.edit_chat_reactions_none)
    else -> pluralStringResource(Res.plurals.edit_chat_reactions_some_count, reactions.emojis.size, reactions.emojis.size)
}

/**
 * Výběr pomalého režimu: tytéž doby, které TDLib přijme ([SLOW_MODE_DELAYS]). Ukládá se
 * ťuknutím, jako automatické mazání.
 */
@Composable
internal fun SlowModeSheet(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    // Celý rozbalený: napůl otevřený list schoval 15 minut a hodinu pod spodní hranu.
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTitle(stringResource(Res.string.edit_chat_slow_mode), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Text(
            stringResource(Res.string.edit_chat_slow_mode_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(Modifier.selectableGroup().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SLOW_MODE_DELAYS.forEach { seconds ->
                val selected = seconds == current
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    headlineContent = { Text(slowModeLabel(seconds)) },
                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onSelect(seconds) },
                )
            }
        }
    }
}

/**
 * Výběr reakcí: všechny, jen vybrané, nebo žádné. „Jen vybrané" ukáže emoji, které jdou jako
 * reakce použít, a ukládá se tlačítkem — po jednom ťuknutí na každé emoji by šla na server řada
 * změn. Když chat dosud povoloval všechny, začíná výběr se všemi zaškrtnutými: odebírat je
 * častější než skládat od nuly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatReactionsSheet(
    current: ChatReactions,
    choices: List<String>,
    onSave: (ChatReactions) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    // 0 = všechny, 1 = vybrané, 2 = žádné.
    var mode by rememberSaveable { mutableStateOf(if (current.all) 0 else if (current.none) 2 else 1) }
    // `remember`, ne `rememberSaveable`: seznam řetězců se mimo Android uložit neumí jistě.
    var picked by remember { mutableStateOf(if (current.all || current.none) choices else current.emojis) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Posouvá se jen výběr; Uložit stojí pod ním vždycky vidět — emoji je přes sedmdesát
        // a tlačítko na konci seznamu se dalo najít jen dlouhým rolováním.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            SheetTitle(stringResource(Res.string.edit_chat_reactions), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            Text(
                stringResource(Res.string.edit_chat_reactions_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Column(Modifier.selectableGroup()) {
                listOf(
                    Res.string.edit_chat_reactions_all,
                    Res.string.edit_chat_reactions_some,
                    Res.string.edit_chat_reactions_none,
                ).forEachIndexed { index, label ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { RadioButton(selected = mode == index, onClick = null) },
                        headlineContent = { Text(stringResource(label)) },
                        modifier = Modifier.selectable(selected = mode == index, role = Role.RadioButton) { mode = index },
                    )
                }
            }
            if (mode == 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    // Co chat povoluje a server už jako reakci nenabízí, zůstává ve výběru taky.
                    (choices + current.emojis.filterNot { it in choices }).forEach { emoji ->
                        val selected = emoji in picked
                        FilterChip(
                            selected = selected,
                            onClick = { picked = if (selected) picked - emoji else picked + emoji },
                            label = { Text(emoji, style = MaterialTheme.typography.titleLarge) },
                        )
                    }
                }
            }
        }
        Button(
            onClick = {
                onSave(
                    when (mode) {
                        0 -> ChatReactions(all = true)
                        2 -> ChatReactions(all = false)
                        // V pořadí od serveru, ne podle toho, kdy se na emoji ťuklo.
                        else -> ChatReactions(all = false, emojis = (choices + current.emojis).distinct().filter { it in picked })
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(Res.string.edit_chat_reactions_save))
        }
    }
}
