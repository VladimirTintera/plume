package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.shortcuts.TextShortcut
import eu.tintera.plume.core.shortcuts.shortcutText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bolt_filled_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Vlastní textové zkratky k rozepsanému `/…` u kurzoru: název a začátek textu. Tentýž plovoucí
 * panel jako hashtagy a příkazy ([FloatingSuggestion]); ťuknutí nahradí `/…` textem zkratky
 * a nic neodešle (na rozdíl od příkazu bota).
 */
@Composable
internal fun ShortcutSuggestionsPanel(
    shortcuts: List<TextShortcut>,
    visible: Boolean,
    onPick: (TextShortcut) -> Unit,
    glass: Modifier = Modifier,
) {
    // Při odjezdu se kreslí poslední seznam, ne prázdno (jako u hashtagů).
    val last = remember { mutableStateOf(shortcuts) }
    if (shortcuts.isNotEmpty()) last.value = shortcuts

    FloatingSuggestion(
        visible = visible && shortcuts.isNotEmpty(),
        shape = RoundedCornerShape(PANEL_CORNER),
        glass = glass,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(Modifier.heightIn(max = MAX_HEIGHT).padding(vertical = 4.dp)) {
            items(last.value, key = { it.id }) { shortcut ->
                ListItem(
                    colors = FloatingSuggestionColors.listItem(),
                    leadingContent = { Icon(painterResource(Res.drawable.bolt_filled_24dp), contentDescription = null) },
                    supportingContent = {
                        Text(shortcut.text.replace('\n', ' '), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.clickable { onPick(shortcut) },
                ) {
                    Text(shortcutText(shortcut.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Nejvýš tolik zabere seznam; zbytek se roluje. Stejně jako zmínky a hashtagy. */
private val MAX_HEIGHT = 280.dp

private val PANEL_CORNER = 20.dp
