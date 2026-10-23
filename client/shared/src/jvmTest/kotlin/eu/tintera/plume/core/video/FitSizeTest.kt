package eu.tintera.plume.core.video

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

/** Do jaké velikosti dekodér na desktopu převádí snímek ([fitSize]). */
class FitSizeTest {

    @Test
    fun shrinksIntoSurfaceKeepingAspectRatio() {
        assertEquals(320 to 180, fitSize(1280, 720, IntSize(320, 400)))
        assertEquals(201 to 113, fitSize(1280, 720, IntSize(400, 113)))
    }

    @Test
    fun neverEnlargesSource() {
        assertEquals(480 to 270, fitSize(480, 270, IntSize(1920, 1080)))
    }

    @Test
    fun keepsSourceUntilSurfaceIsMeasured() {
        assertEquals(640 to 360, fitSize(640, 360, IntSize.Zero))
    }

    @Test
    fun neverCollapsesToZero() {
        assertEquals(100 to 1, fitSize(4000, 10, IntSize(100, 100)))
    }
}
