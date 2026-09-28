package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Návrhy emoji k rozepsanému slovu, PLOVOUCÍ nad vstupním polem.
 *
 * **Místo v rozvržení nezabírá** ([floatAbove]). Pole je `bottomBar` a seznam zpráv se odsazuje
 * o jeho výšku; dokud pilulka stála v jeho sloupci, každé objevení a zmizení (tedy skoro každé
 * slovo) zvedlo a spustilo celou konverzaci — oči při psaní skákaly nahoru za zprávami. Teď leží
 * přes konec seznamu a pod ní je mléčné sklo ([glass]) jako u pruhu připnutých: je vidět, že
 * zprávy pokračují, a emoji zůstávají čitelná.
 *
 * Zarovnaná k začátku, kde se píše. Ťuknutí emoji dosadí MÍSTO slova — kdo chtěl slovo i emoji,
 * napíše slovo znovu; oficiální klienti to dělají stejně.
 */
@Composable
internal fun EmojiSuggestionsPanel(
    emojis: List<String>,
    visible: Boolean,
    onPick: (String) -> Unit,
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    // Při odjezdu se kreslí poslední sada, ne prázdno (stejně jako napovídání zmínek).
    // Stav, ne obyčejné pole: zápis do pole kompozici nevyvolá a obsah uvnitř
    // `AnimatedVisibility` by zůstal u první sady (po „hear" → „heart" dál 👂 🙉).
    val last = remember { mutableStateOf(emojis) }
    if (emojis.isNotEmpty()) last.value = emojis

    // Vyroste z rohu u pole, jako nabídka: rozbalení do výšky by u plovoucího prvku vypadalo,
    // jako by se pod ním něco odsouvalo.
    val origin = TransformOrigin(0f, 1f)
    AnimatedVisibility(
        visible = visible && emojis.isNotEmpty(),
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.8f, transformOrigin = origin) +
            fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f, transformOrigin = origin) +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier.floatAbove(),
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Surface(
                shape = RoundedCornerShape(PANEL_CORNER),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 3.dp,
            ) {
                LazyRow(
                    modifier = Modifier.clip(RoundedCornerShape(PANEL_CORNER)).then(glass),
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

/**
 * Nahlásí nulovou výšku a obsah posadí NAD své místo — spodní hranou na něj.
 *
 * Rodič (sloupec nad polem v `bottomBar`) se tím nezvětší, takže se nezmění ani odsazení seznamu
 * zpráv. Obsah mimo hranice rodiče Compose kreslí i nechá ťukat, dokud nic neořezává.
 */
private fun Modifier.floatAbove(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
}

private val EMOJI_CELL = 48.dp

private val EMOJI_SIZE = 28.sp

private val PANEL_CORNER = 28.dp
