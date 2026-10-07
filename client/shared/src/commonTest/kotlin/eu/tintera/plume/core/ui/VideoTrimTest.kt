package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoTrimTest {

    @Test
    fun usekMaNejvysDesetSekund() {
        assertEquals(10_000, VideoTrim(25_000).segmentMs)
        assertTrue(VideoTrim(25_000).canMove)
        // Kratší video jde celé a posouvat není kam.
        assertEquals(6_000, VideoTrim(6_000).segmentMs)
        assertFalse(VideoTrim(6_000).canMove)
        assertFalse(VideoTrim(10_000).canMove)
    }

    @Test
    fun usekNevyjedeZaKonecVidea() {
        assertEquals(15_000, VideoTrim(25_000).movedTo(20_000).startMs)
        assertEquals(0, VideoTrim(25_000, startMs = 5_000).movedTo(-3_000).startMs)
        assertEquals(0, VideoTrim(6_000).movedTo(2_000).startMs)
    }

    @Test
    fun snimekJedeSUsekem() {
        val moved = VideoTrim(25_000, startMs = 2_000, coverMs = 5_000).movedTo(8_000)
        // Byl 3 s do úseku, zůstane 3 s do úseku.
        assertEquals(11_000, moved.coverMs)
        assertEquals(3.0, moved.mainFrameSeconds)
    }

    @Test
    fun usekZastavenyKoncemVidea() {
        // Úsek narazí na konec videa; snímek se posune jen o to, o co se posunul úsek.
        val moved = VideoTrim(25_000, startMs = 10_000, coverMs = 19_800).movedTo(20_000)
        assertEquals(15_000, moved.startMs)
        assertEquals(24_800, moved.coverMs)
    }

    @Test
    fun snimekSeSevreDoUseku() {
        val trim = VideoTrim(25_000, startMs = 5_000, coverMs = 5_000)
        assertEquals(5_000, trim.withCover(1_000).coverMs)
        // Kousek před koncem úseku: přesně na konci už v animaci snímek být nemusí.
        assertEquals(14_900, trim.withCover(20_000).coverMs)
        assertEquals(9_000, trim.withCover(9_000).coverMs)
        assertEquals(4.0, trim.withCover(9_000).mainFrameSeconds)
    }

    @Test
    fun kratickeVideoMaSnimekNaZacatku() {
        // Video kratší než okraj u konce: snímek nesmí vyjít před začátek.
        assertEquals(0, VideoTrim(50).withCover(40).coverMs)
    }
}
