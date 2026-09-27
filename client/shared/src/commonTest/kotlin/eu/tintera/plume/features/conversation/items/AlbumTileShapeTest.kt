package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dlaždice alba si berou jen ty rohy mřížky, do kterých sahají. */
class AlbumTileShapeTest {
    private val grid = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    private val size = Size(100f, 100f)
    private val density = Density(1f)

    private fun corners(top: Boolean, bottom: Boolean, start: Boolean, end: Boolean): List<Float> {
        val shape = albumTileShape(grid, top, bottom, start, end) as CornerBasedShape
        return listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).map { it.toPx(size, density) }
    }

    @Test
    fun jedinaDlazdiceMaVsechnyRohyMrizky() {
        assertEquals(listOf(18f, 4f, 18f, 18f), corners(top = true, bottom = true, start = true, end = true))
    }

    @Test
    fun levaHorniMaJenSvujRoh() {
        assertEquals(listOf(18f, 0f, 0f, 0f), corners(top = true, bottom = false, start = true, end = false))
    }

    @Test
    fun dlazdiceUprostredJeHranata() {
        assertEquals(listOf(0f, 0f, 0f, 0f), corners(top = false, bottom = false, start = false, end = false))
    }

    @Test
    fun mrizkaBezRohuDaHranateDlazdice() {
        assertEquals(RectangleShape, albumTileShape(RectangleShape, top = true, bottom = true, start = true, end = true))
    }
}
