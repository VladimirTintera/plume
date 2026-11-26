package eu.tintera.plume.features.conversation.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [anchor] a nad ním [above], pod ním [below] — oba v PŘESNĚ jeho šířce: hlavička a popisek holého
 * snímku, přepis pod kolečkem.
 *
 * - **Šířku dává [anchor]**, díly se do ní zalomí; karta tak nemá roztřepený okraj a dlouhý
 *   popisek se neroztáhne vedle zúžené fotky.
 * - **Vlastní `Layout`, ne `IntrinsicSize`**: snímek i kolečko se kreslí v `BoxWithConstraints`
 *   (SubcomposeLayout), a ten intrinsické míry neumí — aplikace by spadla (viz „Gotchas"
 *   v CLAUDE.md). Kotva se změří normálně a díly dostanou její šířku napevno.
 * - [stretch] = díly na celou šířku kotvy (karta snímku); bez něj jen nejvýš na ni (přepis má
 *   vlastní kartu velkou podle textu).
 * - [spacing] stojí jen mezi díly, které něco nakreslily.
 *
 * Dřív to byly dvě kopie (`MediaWithCaption`, `BelowWithSameWidth`), každá trochu jinak.
 * `WithBelowMessage` (tlačítka bota) je jiné pravidlo — tlačítka smějí být širší než zpráva.
 */
@Composable
internal fun SameWidthStack(
    anchor: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    above: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
    stretch: Boolean = true,
    spacing: Dp = 0.dp,
) {
    Layout(contents = listOf(above, anchor, below), modifier = modifier) { (aboveMeasurables, anchorMeasurables, belowMeasurables), constraints ->
        val anchorPlaceables = anchorMeasurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = anchorPlaceables.maxOfOrNull { it.width } ?: 0
        val partConstraints = Constraints(minWidth = if (stretch) width else 0, maxWidth = width)
        val abovePlaceables = aboveMeasurables.map { it.measure(partConstraints) }
        val belowPlaceables = belowMeasurables.map { it.measure(partConstraints) }
        val gap = spacing.roundToPx()
        val parts = listOf(abovePlaceables, anchorPlaceables, belowPlaceables).filter { part -> part.any { it.height > 0 } }
        val height = parts.sumOf { part -> part.maxOf { it.height } } + gap * (parts.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            parts.forEach { part ->
                part.forEach { it.place(0, y) }
                y += part.maxOf { it.height } + gap
            }
        }
    }
}
