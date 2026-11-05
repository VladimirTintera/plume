package eu.tintera.plume.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Vybledne okraj vodorovně posouvané řady tam, kde obsah pokračuje ([state]).
 *
 * Bez toho se položka na okraji usekla natvrdo a vypadala jako jiná: „+" na konci lišty složek
 * byl naležato useknutý přesně uprostřed a četl se jako „−", poslední emoji v pilulce návrhů
 * končilo půlkou. Vyblednutí říká „pokračuje to", useknutí ne.
 *
 * Patří PŘED posun (`horizontalScroll`) nebo na samotný `LazyRow`, tedy na výřez, ne na obsah.
 * Vrstva je `Offscreen`, jinak by `DstIn` vymazal i to, co leží pod řadou (sklo, plochu lišty).
 */
fun Modifier.horizontalFadingEdges(state: ScrollableState, width: Dp = FADING_EDGE_WIDTH): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = width.toPx().coerceAtMost(size.width / 2)
            val rtl = layoutDirection == LayoutDirection.Rtl
            val fadeLeft = if (rtl) state.canScrollForward else state.canScrollBackward
            val fadeRight = if (rtl) state.canScrollBackward else state.canScrollForward
            if (fadeLeft) {
                drawRect(
                    brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = edge),
                    size = Size(edge, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (fadeRight) {
                drawRect(
                    brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - edge, endX = size.width),
                    topLeft = Offset(size.width - edge, 0f),
                    size = Size(edge, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

private val FADING_EDGE_WIDTH = 24.dp
