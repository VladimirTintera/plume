package eu.tintera.plume.features.media

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewerKeyTest {

    private fun key(
        key: Key,
        char: Char? = null,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        rtl: Boolean = false,
        video: Boolean = false,
        seek: Boolean = false,
    ) = viewerKey(key, char, ctrl, alt, shift, meta, rtl, video, seek)

    @Test
    fun sipkyListuji() {
        assertEquals(ViewerKey.Previous, key(Key.DirectionLeft))
        assertEquals(ViewerKey.Next, key(Key.DirectionRight))
    }

    /** V RTL jde pager obráceně, takže šipka vlevo je další. */
    @Test
    fun sipkyVRtlObracene() {
        assertEquals(ViewerKey.Next, key(Key.DirectionLeft, rtl = true))
        assertEquals(ViewerKey.Previous, key(Key.DirectionRight, rtl = true))
    }

    /** Alt+šipka přepíná chaty, Shift/Ctrl+šipka patří textu. */
    @Test
    fun sipkySModifikatoremNic() {
        assertNull(key(Key.DirectionLeft, alt = true))
        assertNull(key(Key.DirectionRight, ctrl = true))
        assertNull(key(Key.DirectionRight, shift = true))
        assertNull(key(Key.DirectionRight, meta = true))
    }

    @Test
    fun priblizeni() {
        assertEquals(ViewerKey.ZoomIn, key(Key.NumPadAdd))
        assertEquals(ViewerKey.ZoomIn, key(Key.Equals))
        assertEquals(ViewerKey.ZoomIn, key(Key.Equals, char = '+', shift = true))
        assertEquals(ViewerKey.ZoomOut, key(Key.Minus))
        assertEquals(ViewerKey.ZoomOut, key(Key.NumPadSubtract))
        assertEquals(ViewerKey.ZoomReset, key(Key.Zero))
        assertEquals(ViewerKey.ZoomReset, key(Key.NumPad0))
    }

    /** Ctrl+= / Ctrl+− / Ctrl+0 jako v prohlížeči webu. */
    @Test
    fun priblizeniSCtrl() {
        assertEquals(ViewerKey.ZoomIn, key(Key.Equals, ctrl = true))
        assertEquals(ViewerKey.ZoomOut, key(Key.Minus, ctrl = true))
        assertEquals(ViewerKey.ZoomReset, key(Key.Zero, ctrl = true))
    }

    /** Česká klávesnice: „+" je na klávese s anglickou „1", rozhoduje znak. */
    @Test
    fun ceskaKlavesnicePodleZnaku() {
        assertEquals(ViewerKey.ZoomIn, key(Key.One, char = '+'))
        assertEquals(ViewerKey.ZoomReset, key(Key.Zero, char = '0', shift = true))
    }

    @Test
    fun ostatniKlavesyNic() {
        assertNull(key(Key.A, char = 'a'))
        assertNull(key(Key.Spacebar, char = ' '))
        assertNull(key(Key.Equals, alt = true))
        assertNull(key(Key.Escape))
    }

    /** U videa jako na YouTube; písmena podle klávesy, takže i s jiným znakem (rozložení). */
    @Test
    fun klavesyVidea() {
        assertEquals(ViewerKey.PlayPause, key(Key.Spacebar, char = ' ', video = true))
        assertEquals(ViewerKey.ToggleFullscreen, key(Key.F, char = 'f', video = true))
        assertEquals(ViewerKey.ToggleMute, key(Key.M, char = 'm', video = true))
        assertNull(key(Key.F, char = 'f'))
        assertNull(key(Key.F, ctrl = true, video = true))
        assertNull(key(Key.Spacebar, meta = true, video = true))
    }

    /** Hrající video na celé obrazovce: šipky posouvají, doleva zpět i v RTL. */
    @Test
    fun sipkyPosouvajiVideo() {
        assertEquals(ViewerKey.SeekBack, key(Key.DirectionLeft, video = true, seek = true))
        assertEquals(ViewerKey.SeekForward, key(Key.DirectionRight, video = true, seek = true))
        assertEquals(ViewerKey.SeekBack, key(Key.DirectionLeft, rtl = true, video = true, seek = true))
        assertEquals(ViewerKey.Previous, key(Key.DirectionLeft, video = true))
        assertNull(key(Key.DirectionLeft, alt = true, video = true, seek = true))
    }

    /** Ctrl+C i ⌘C kopírují, podle klávesy; s Alt, Shift nebo oběma modifikátory nic. */
    @Test
    fun ctrlCKopiruje() {
        assertEquals(ViewerKey.Copy, key(Key.C, char = 'c', ctrl = true))
        assertEquals(ViewerKey.Copy, key(Key.C, char = 'c', meta = true))
        assertEquals(ViewerKey.Copy, key(Key.C, char = 'с', ctrl = true))
        assertNull(key(Key.C, char = 'c'))
        assertNull(key(Key.C, ctrl = true, shift = true))
        assertNull(key(Key.C, ctrl = true, alt = true))
        assertNull(key(Key.C, ctrl = true, meta = true))
    }
}
