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
}
