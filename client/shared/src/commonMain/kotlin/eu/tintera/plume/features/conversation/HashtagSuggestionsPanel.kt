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
import eu.tintera.plume.core.telegram.hashtagText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.tag_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Napovídání hashtagu nad vstupním polem: dříve použité hashtagy k rozepsanému `#…` u kurzoru.
 *
 * Tentýž plovoucí panel jako zmínky ([MentionSuggestionsPanel]) — nad polem, se stropem výšky
 * a mění se s každým písmenem, takže konverzace pod ním neposkakuje ([FloatingSuggestion]).
 */
@Composable
internal fun HashtagSuggestionsPanel(
    hashtags: List<String>,
    visible: Boolean,
    onPick: (String) -> Unit,
    /** Mléčné sklo nad seznamem zpráv (viz [FloatingSuggestion]). */
    glass: Modifier = Modifier,
) {
    // Při odjezdu se kreslí poslední seznam, ne prázdno; ve stavu, viz [MentionSuggestionsPanel].
    val last = remember { mutableStateOf(hashtags) }
    if (hashtags.isNotEmpty()) last.value = hashtags

    FloatingSuggestion(
        visible = visible && hashtags.isNotEmpty(),
        shape = RoundedCornerShape(PANEL_CORNER),
        glass = glass,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(Modifier.heightIn(max = MAX_HEIGHT).padding(vertical = 4.dp)) {
            items(last.value, key = { it }) { hashtag ->
                ListItem(
                    colors = FloatingSuggestionColors.listItem(),
                    leadingContent = { Icon(painterResource(Res.drawable.tag_24dp), contentDescription = null) },
                    // Mřížka je v textu i vedle ikony: čte se tak, jak se vloží.
                    headlineContent = { Text(hashtagText(hashtag), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { onPick(hashtag) },
                )
            }
        }
    }
}

/** Nejvýš tolik zabere seznam; zbytek se roluje. Stejně jako zmínky a příkazy. */
private val MAX_HEIGHT = 280.dp

private val PANEL_CORNER = 20.dp
