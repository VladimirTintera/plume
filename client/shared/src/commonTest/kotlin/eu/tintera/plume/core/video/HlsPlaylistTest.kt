package eu.tintera.plume.core.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HlsPlaylistTest {

    /** Začátek skutečného seznamu přepočítané kvality (h264 478p, kanál s hodinovými videi). */
    private val playlist = """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXT-X-VERSION:6
        #EXT-X-MEDIA-SEQUENCE:1
        #EXT-X-INDEPENDENT-SEGMENTS
        #EXT-X-MAP:URI="mtproto:6212933475159253354",BYTERANGE="1280@0"
        #EXTINF:5.4966
        #EXT-X-BYTERANGE:1021055@11972
        mtproto:6212933475159253354
        #EXTINF:4.8
        #EXT-X-BYTERANGE:936105@1033027
        mtproto:6212933475159253354
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun segmentyIInitVedouNaMistniSoubor() {
        val rewritten = rewriteHlsPlaylist(playlist, "video.mp4")!!
        assertEquals(false, "mtproto:" in rewritten)
        assertEquals(true, "#EXT-X-MAP:URI=\"video.mp4\",BYTERANGE=\"1280@0\"" in rewritten)
        assertEquals(2, rewritten.lines().count { it == "video.mp4" })
        // Rozsahy a délky segmentů zůstávají, jak je poslal server.
        assertEquals(true, "#EXT-X-BYTERANGE:936105@1033027" in rewritten)
    }

    @Test
    fun seznamSViceSouboryNejde() {
        assertNull(rewriteHlsPlaylist(playlist.replaceFirst("mtproto:6212933475159253354\n", "mtproto:1\n"), "video.mp4"))
    }

    @Test
    fun bezSegmentuNeboHlavickyNejde() {
        assertNull(rewriteHlsPlaylist("#EXTM3U\n#EXT-X-ENDLIST", "video.mp4"))
        assertNull(rewriteHlsPlaylist(playlist.removePrefix("#EXTM3U"), "video.mp4"))
    }

    @Test
    fun rozsahOdDo() {
        assertEquals(0L..1L, parseByteRange("bytes=0-1", 1000))
        assertEquals(100L..199L, parseByteRange(" bytes=100-199", 1000))
    }

    @Test
    fun rozsahDoKonce() {
        assertEquals(900L..999L, parseByteRange("bytes=900-", 1000))
    }

    @Test
    fun konecZaSouboremSeOrizne() {
        assertEquals(900L..999L, parseByteRange("bytes=900-5000", 1000))
    }

    @Test
    fun posledniBajty() {
        assertEquals(990L..999L, parseByteRange("bytes=-10", 1000))
        assertEquals(0L..999L, parseByteRange("bytes=-5000", 1000))
    }

    @Test
    fun nesplnitelnyRozsah() {
        assertNull(parseByteRange("bytes=1000-", 1000))
        assertNull(parseByteRange("bytes=5-1", 1000))
        assertNull(parseByteRange("items=0-1", 1000))
        assertNull(parseByteRange("bytes=a-b", 1000))
    }
}
