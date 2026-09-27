package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Návrhy emoji k rozepsanému slovu, nad vstupním polem.
 *
 * **Plovoucí pilulka, ne panel přes celou šířku.** Emoji je pár a řádek pod prstem nemá
 * zabírat místo zpráv; tvarem i plochou patří k ostatnímu napovídání nad polem (zmínky,
 * příkazy bota), jen je nízký. Zarovnaný k začátku, kde se píše. Ťuknutí emoji dosadí MÍSTO
 * slova — kdo chtěl slovo i emoji, napíše slovo znovu; oficiální klienti to dělají stejně.
 */
@Composable
internal fun EmojiSuggestionsPanel(
    emojis: List<String>,
    visible: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Při odjezdu se kreslí poslední sada, ne prázdno (stejně jako napovídání zmínek).
    // Stav, ne obyčejné pole: zápis do pole kompozici nevyvolá a obsah uvnitř
    // `AnimatedVisibility` by zůstal u první sady (po „hear" → „heart" dál 👂 🙉).
    val last = remember { mutableStateOf(emojis) }
    if (emojis.isNotEmpty()) last.value = emojis

    AnimatedVisibility(
        visible = visible && emojis.isNotEmpty(),
        enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        modifier = modifier,
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Surface(
                shape = RoundedCornerShape(PANEL_CORNER),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 2.dp,
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(last.value, key = { it }) { emoji ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(EMOJI_CELL)
                                .clip(CircleShape)
                                .clickable { onPick(emoji) },
                        ) {
                            Text(emoji, fontSize = EMOJI_SIZE)
                        }
                    }
                }
            }
        }
    }
}

private val EMOJI_CELL = 48.dp

private val EMOJI_SIZE = 28.sp

private val PANEL_CORNER = 28.dp
