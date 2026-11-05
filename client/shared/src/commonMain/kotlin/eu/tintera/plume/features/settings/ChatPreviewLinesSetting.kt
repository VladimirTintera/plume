package eu.tintera.plume.features.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.settings.CHAT_PREVIEW_LINES
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.highlightAnchor
import eu.tintera.plume.features.chats.CHAT_ROW_AVATAR_SIZE
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_chat_preview_lines
import eu.tintera.plume.resources.settings_chat_preview_lines_count
import eu.tintera.plume.resources.settings_chat_preview_sample_text
import eu.tintera.plume.resources.settings_chat_preview_sample_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Řádky náhledu zprávy v seznamu chatů: podobizna řádku a spojená přepínací tlačítka pod ní.
 *
 * Výběr bez tahu se ukládá rovnou; posuvníky čekají na puštění jen kvůli zápisu na každý dílek.
 */
@Composable
internal fun ChatPreviewLinesSetting(selected: Int, onSelect: (Int) -> Unit) {
    val title = stringResource(Res.string.settings_chat_preview_lines)
    Column(Modifier.highlightAnchor(title).padding(vertical = 12.dp)) {
        ChatRowLikeness(lines = selected, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        LinesSelector(selected = selected, onSelect = onSelect)
    }
}

/** Jen podobizna — skutečný `ChatRow` potřebuje chat a sdílený přechod do konverzace. */
@Composable
private fun ChatRowLikeness(lines: Int, modifier: Modifier = Modifier) {
    val name = stringResource(Res.string.settings_chat_preview_sample_title)
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, ShapeDefaults.Large)
            .padding(12.dp)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(chatId = 0L, title = name, avatarFileId = null, size = CHAT_ROW_AVATAR_SIZE)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                stringResource(Res.string.settings_chat_preview_sample_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinesSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        CHAT_PREVIEW_LINES.forEach { lines ->
            val checked = lines == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(lines) },
                shapes = when (lines) {
                    CHAT_PREVIEW_LINES.first -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    CHAT_PREVIEW_LINES.last -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Text(
                    pluralStringResource(Res.plurals.settings_chat_preview_lines_count, lines, lines),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
