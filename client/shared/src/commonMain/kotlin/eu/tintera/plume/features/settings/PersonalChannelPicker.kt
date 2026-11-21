package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.highlightAnchor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.PersonalChannel
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.invite_subscribers
import eu.tintera.plume.resources.personal_channel_empty
import eu.tintera.plume.resources.personal_channel_load_failed
import eu.tintera.plume.resources.personal_channel_none
import eu.tintera.plume.resources.personal_channel_pick_text
import eu.tintera.plume.resources.personal_channel_retry
import eu.tintera.plume.resources.personal_channel_row
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Osobní kanál v úpravě profilu: řádek s vybraným kanálem, ťuknutí otevře výběr. Ukládá se
 * tlačítkem Uložit jako ostatní údaje.
 *
 * @param channels kanály na výběr; `null` = ještě se načítají.
 * @param onOpen výběr se otevřel — načíst kanály.
 */
@Composable
internal fun PersonalChannelRow(
    value: PersonalChannel?,
    channels: List<PersonalChannel>?,
    failed: Boolean,
    onOpen: () -> Unit,
    onChange: (PersonalChannel?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.campaign_24dp), contentDescription = null) },
        supportingContent = { Text(value?.title ?: stringResource(Res.string.personal_channel_none)) },
        modifier = Modifier
            .highlightAnchor(stringResource(Res.string.personal_channel_row))
            .clickable {
                onOpen()
                picking = true
            },
    ) { Text(stringResource(Res.string.personal_channel_row)) }
    if (picking) {
        PersonalChannelDialog(
            selected = value,
            channels = channels,
            failed = failed,
            onRetry = onOpen,
            onSelect = {
                picking = false
                onChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Výběr kanálu: „Žádný" a kanály, které server nabídne. Vybraný kanál zůstává v nabídce, i když
 * mezi vhodnými už není (přestal být veřejný) — jinak by výběr nic nevybraného neukazoval.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonalChannelDialog(
    selected: PersonalChannel?,
    channels: List<PersonalChannel>?,
    failed: Boolean,
    onRetry: () -> Unit,
    onSelect: (PersonalChannel?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.personal_channel_row)) },
        text = {
            Column(Modifier.heightIn(max = DIALOG_LIST_MAX).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(Res.string.personal_channel_pick_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                @Composable
                fun option(channel: PersonalChannel?) {
                    val isSelected = channel?.chatId == selected?.chatId
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                        supportingContent = channel?.subscribers?.let { count ->
                            { Text(pluralStringResource(Res.plurals.invite_subscribers, count, count.grouped())) }
                        },
                        trailingContent = channel?.let {
                            {
                                Avatar(
                                    chatId = it.chatId,
                                    title = it.title,
                                    avatarFileId = it.photoFileId,
                                    size = 40.dp,
                                    shape = ChatKind.Channel.avatarShape(),
                                )
                            }
                        },
                        modifier = Modifier.selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSelected) haptics.toggle(true)
                                onSelect(channel)
                            },
                        ),
                    ) {
                        Text(channel?.title ?: stringResource(Res.string.personal_channel_none), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                option(null)
                when {
                    channels != null -> {
                        val shown = if (selected != null && channels.none { it.chatId == selected.chatId }) listOf(selected) + channels else channels
                        shown.forEach { option(it) }
                        if (shown.isEmpty()) {
                            Text(
                                stringResource(Res.string.personal_channel_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    failed -> {
                        selected?.let { option(it) }
                        Text(
                            stringResource(Res.string.personal_channel_load_failed),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.personal_channel_retry)) }
                    }
                    else -> {
                        selected?.let { option(it) }
                        LoadingIndicator(Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

private val DIALOG_LIST_MAX = 480.dp
