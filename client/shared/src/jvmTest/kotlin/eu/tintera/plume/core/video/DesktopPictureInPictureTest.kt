package eu.tintera.plume.core.video

import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Předání přehrávače okénku obrazu v obraze ([DesktopPip]) a velikost okénka. */
class DesktopPictureInPictureTest {

    @AfterTest
    fun closePip() = DesktopPip.close()

    @Test
    fun pageCannotPauseVideoInPip() {
        val player = VideoPlayer()
        DesktopPip.show(player, 16f / 9f)
        // Zavírající se prohlížeč stránku pozastaví; okénko musí hrát dál.
        assertTrue(player.heldByPip)
        player.pause()
        assertTrue(player.heldByPip, "okénko pustilo přehrávač po pauze od stránky")
        assertSame(player, DesktopPip.current?.player)
    }

    @Test
    fun anotherVideoReplacesThePip() {
        val first = VideoPlayer()
        val second = VideoPlayer()
        DesktopPip.show(first, 1f)
        first.orphaned = true
        DesktopPip.show(second, 1f)
        assertSame(second, DesktopPip.current?.player)
        assertEquals(false, first.heldByPip, "první video zůstalo v okénku")
    }

    @Test
    fun closingReturnsThePlayer() {
        val player = VideoPlayer()
        DesktopPip.show(player, 1f)
        DesktopPip.close()
        assertNull(DesktopPip.current)
        assertEquals(false, player.heldByPip)
    }

    @Test
    fun sizeFollowsAspectRatio() {
        assertEquals(400.dp to 225.dp, pipSize(16f / 9f))
        assertEquals(225.dp to 400.dp, pipSize(9f / 16f))
        assertEquals(400.dp to 400.dp, pipSize(1f))
        // Neznámý poměr stran (0, NaN) jako 16 : 9.
        assertEquals(400.dp to 225.dp, pipSize(0f))
        assertEquals(400.dp to 225.dp, pipSize(Float.NaN))
    }

    @Test
    fun returnReopensViewerAtPosition() {
        val player = VideoPlayer()
        var reopenedAt: Long? = null
        DesktopPip.show(player, 1f) { reopenedAt = it }
        DesktopPip.returnToViewer()
        assertNull(DesktopPip.current, "okénko po návratu zůstalo")
        assertEquals(0L, reopenedAt, "prohlížeč se neotevřel")
    }

    @Test
    fun returnWithoutViewerKeepsThePip() {
        val player = VideoPlayer()
        DesktopPip.show(player, 1f)
        DesktopPip.returnToViewer()
        assertSame(player, DesktopPip.current?.player)
    }

    /** Tah za levý horní roh: pravý dolní stojí, poměr stran zůstává. */
    @Test
    fun resizeFromTopStartKeepsOppositeCorner() {
        val start = PipBounds(x = 1000f, y = 600f, width = 400f, height = 225f)
        val bigger = resizePip(PipCorner.TopStart, start, dx = -160f, dy = -20f, ratio = 16f / 9f, minLong = 240f, maxLong = 960f)
        assertEquals(560f, bigger.width)
        assertEquals(315f, bigger.height)
        assertEquals(start.x + start.width, bigger.x + bigger.width, "pravý okraj se pohnul")
        assertEquals(start.y + start.height, bigger.y + bigger.height, "spodní okraj se pohnul")
    }

    /** Řídí osa, po které se táhlo víc; tah za pravý dolní roh nechá stát levý horní. */
    @Test
    fun resizeFollowsDominantAxis() {
        val start = PipBounds(x = 100f, y = 100f, width = 400f, height = 225f)
        val taller = resizePip(PipCorner.BottomEnd, start, dx = 10f, dy = 90f, ratio = 16f / 9f, minLong = 240f, maxLong = 960f)
        assertEquals(315f, taller.height, 0.01f)
        assertEquals(start.x, taller.x)
        assertEquals(start.y, taller.y)
    }

    @Test
    fun resizeStaysWithinLimits() {
        val start = PipBounds(x = 0f, y = 0f, width = 400f, height = 225f)
        val tiny = resizePip(PipCorner.BottomEnd, start, dx = -1000f, dy = 0f, ratio = 16f / 9f, minLong = 240f, maxLong = 960f)
        assertEquals(240f, tiny.width)
        val huge = resizePip(PipCorner.BottomEnd, start, dx = 5000f, dy = 0f, ratio = 16f / 9f, minLong = 240f, maxLong = 960f)
        assertEquals(960f, huge.width)
        // Video na výšku: limity platí pro delší stranu, tedy výšku.
        val portrait = PipBounds(x = 0f, y = 0f, width = 225f, height = 400f)
        val tall = resizePip(PipCorner.BottomEnd, portrait, dx = 0f, dy = 5000f, ratio = 9f / 16f, minLong = 240f, maxLong = 960f)
        assertEquals(960f, tall.height, 0.01f)
    }
}
