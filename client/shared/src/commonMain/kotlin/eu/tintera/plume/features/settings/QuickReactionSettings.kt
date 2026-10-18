package eu.tintera.plume.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.CustomEmojiIcon
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_reaction_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.settings_quick_reaction
import eu.tintera.plume.resources.settings_quick_reaction_choose
import eu.tintera.plume.resources.settings_quick_reaction_choose_body
import eu.tintera.plume.resources.settings_quick_reaction_summary
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Rychlá reakce: přepínač dvojitého ťuknutí a pod ním (jen zapnutý) výběr reakce.
 *
 * Dva řádky, ne přepínač v řádku, který otevírá výběr — řádek nese jedno klikání celý (viz
 * „Obrazovka nastavení" v poznatcích).
 */
@Composable
internal fun QuickReactionSettings(viewModel: QuickReactionSettingsViewModel) {
    val haptics = LocalHapticFeedback.current
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val reaction by viewModel.reaction.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.add_reaction_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_quick_reaction)) },
        supportingContent = { Text(stringResource(Res.string.settings_quick_reaction_summary)) },
        trailingContent = { IconSwitch(checked = enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = enabled,
            role = Role.Switch,
            onValueChange = { on ->
                haptics.toggle(on)
                viewModel.setEnabled(on)
            },
        ),
    )
    AnimatedVisibility(visible = enabled) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            // Odsazení pod ikonu řádku výš: patří k němu, vlastní ikonu nemá.
            leadingContent = { Box(Modifier.size(24.dp)) },
            headlineContent = { Text(stringResource(Res.string.settings_quick_reaction_choose)) },
            trailingContent = { reaction?.let { ReactionGlyph(it, size = 28) } },
            modifier = Modifier.clickable { picking = true },
        )
    }

    if (picking) {
        QuickReactionDialog(
            selected = reaction,
            choices = choices,
            onSelect = { chosen ->
                haptics.confirm()
                picking = false
                viewModel.choose(chosen)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Mřížka emoji, která jdou jako reakce použít. Vlastní emoji (Premium) v nabídce nejsou —
 * seznam od serveru nese jen obyčejná —, ale nastavené z jiného klienta se ukáže v řádku.
 */
@Composable
private fun QuickReactionDialog(
    selected: String?,
    choices: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_quick_reaction_choose)) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.settings_quick_reaction_choose_body),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp),
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(choices, key = { it }) { emoji ->
                        val isSelected = emoji == selected
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    CircleShape,
                                )
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(emoji) })
                                .semantics { contentDescription = emoji },
                            contentAlignment = Alignment.Center,
                        ) {
                            ReactionGlyph(emoji, size = 28)
                        }
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

/** Obyčejné emoji jako text, vlastní (klíč s id) jako obrázek. */
@Composable
private fun ReactionGlyph(reaction: String, size: Int) {
    val customId = customEmojiIdOf(reaction)
    if (customId != null) {
        CustomEmojiIcon(customEmojiId = customId, size = size.dp)
    } else {
        Text(reaction, fontSize = (size * 0.85f).sp, textAlign = TextAlign.Center)
    }
}
