package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.hashtagText
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_search_remove_recent
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.tag_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Napovídání hashtagu nad vstupním polem: dříve použité hashtagy k rozepsanému `#…` u kurzoru.
 *
 * Tentýž plovoucí panel jako zmínky ([MentionSuggestionsPanel]) — nad polem, se stropem výšky
 * a mění se s každým písmenem, takže konverzace pod ním neposkakuje ([FloatingSuggestion]).
 *
 * Podržení hashtagu nabídne „Odebrat z historie" ([onRemove]) — stejně jako podržení člověka
 * v nejčastějších při hledání chatů: nabídka bez potvrzení a bez „Vrátit", hashtag se do
 * historie vrátí sám, až ho uživatel zase pošle.
 */
@Composable
internal fun HashtagSuggestionsPanel(
    hashtags: List<String>,
    visible: Boolean,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
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
                HashtagRow(hashtag, onPick = { onPick(hashtag) }, onRemove = { onRemove(hashtag) })
            }
        }
    }
}

@Composable
private fun HashtagRow(hashtag: String, onPick: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        ListItem(
            colors = FloatingSuggestionColors.listItem(),
            leadingContent = { Icon(painterResource(Res.drawable.tag_24dp), contentDescription = null) },
            // Mřížka je v textu i vedle ikony: čte se tak, jak se vloží.
            headlineContent = { Text(hashtagText(hashtag), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            // Odezvu na podržení dělá `combinedClickable` sám.
            modifier = Modifier.contextClickable(
                onLongClickLabel = stringResource(Res.string.chats_search_remove_recent),
                onLongClick = { menuOpen = true },
                onClick = onPick,
            ),
        )
        // Nabídka si nebere klávesnici (výchozí v [ExpressiveMenu]), takže pole s rozepsaným
        // hashtagem zůstane otevřené a panel se po odebrání jen přečte znovu.
        ExpressiveMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.chats_search_remove_recent), Res.drawable.close_24dp) {
                        menuOpen = false
                        onRemove()
                    },
                ),
            ),
        )
    }
}

/** Nejvýš tolik zabere seznam; zbytek se roluje. Stejně jako zmínky a příkazy. */
private val MAX_HEIGHT = 280.dp

private val PANEL_CORNER = 20.dp
