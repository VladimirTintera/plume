package eu.tintera.plume.core.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Rohy letícího prvku: z karty prohlížeče (16) do tvaru bubliny, i se staženým nárožím (4).
 */
class FlightShapeTest {
    private val density = Density(1f)
    private val size = Size(400f, 300f)
    private val bubble = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)

    private fun corners(shape: CornerBasedShape) = listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart)
        .map { it.toPx(size, density) }

    @Test
    fun poDosednutiJeTvarCilovy() {
        assertSame(bubble, flightShape(bubble, fromCornerPx = 16f, progress = 1f, size = size, density = density))
    }

    @Test
    fun naZacatkuLetuMaRohyDruheStrany() {
        val start = flightShape(bubble, fromCornerPx = 16f, progress = 0f, size = size, density = density)
        assertEquals(listOf(16f, 16f, 16f, 16f), corners(start as CornerBasedShape))
    }

    @Test
    fun vPulceLetuSeRohyPrelevaji() {
        val half = flightShape(bubble, fromCornerPx = 16f, progress = 0.5f, size = size, density = density)
        assertEquals(listOf(17f, 17f, 17f, 10f), corners(half as CornerBasedShape))
    }
}
