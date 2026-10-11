package eu.tintera.plume.core.video

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Syntetické soubory WebM ve tvaru, jaký mají videosamolepky Telegramu (ověřeno rozborem
 * skutečných samolepek): neprůhledná má `SimpleBlock`, průhledná `AlphaMode = 1` a snímky
 * v `BlockGroup` s průhledností v `BlockAdditional`.
 */
class WebmDemuxerTest {

    private fun id(id: Long): ByteArray {
        var n = 0
        while (id shr (8 * n) != 0L) n++
        return ByteArray(n) { k -> (id shr (8 * (n - 1 - k))).toByte() }
    }

    /** Velikost vždy na 8 bajtů — jednoduché a platné EBML. */
    private fun size(size: Long): ByteArray = byteArrayOf(0x01) + ByteArray(7) { k -> (size shr (8 * (6 - k))).toByte() }

    private fun el(id: Long, vararg payload: ByteArray): ByteArray {
        val body = payload.fold(ByteArray(0)) { acc, p -> acc + p }
        return id(id) + size(body.size.toLong()) + body
    }

    /** Prvek s velikostí „neznámá" (samé jedničky). */
    private fun unknownSize(id: Long, vararg payload: ByteArray): ByteArray =
        id(id) + byteArrayOf(0x01, -1, -1, -1, -1, -1, -1, -1) + payload.fold(ByteArray(0)) { acc, p -> acc + p }

    private fun uint(value: Long, bytes: Int = 2): ByteArray = ByteArray(bytes) { k -> (value shr (8 * (bytes - 1 - k))).toByte() }

    private fun block(relative: Int, flags: Int, data: ByteArray): ByteArray =
        byteArrayOf(0x81.toByte()) + uint(relative.toLong(), 2) + byteArrayOf(flags.toByte()) + data

    private fun header(alpha: Boolean, matrix: Long = 1, range: Long = 1): ByteArray {
        val video = el(
            0xE0,
            el(0xB0, uint(512)),
            el(0xBA, uint(512)),
            *(if (alpha) arrayOf(el(0x53C0, uint(1, 1))) else emptyArray()),
            el(0x55B0, el(0x55B1, uint(matrix, 1)), el(0x55B9, uint(range, 1))),
        )
        val track = el(
            0xAE,
            el(0xD7, uint(1, 1)),
            el(0x86, "V_VP9".encodeToByteArray()),
            el(0x23E383, uint(33_333_333, 4)),
            video,
        )
        return el(0x1549A966, el(0x2AD7B1, uint(1_000_000, 3))) + el(0x1654AE6B, track)
    }

    private val ebmlHeader = el(0x1A45DFA3, el(0x4282, "webm".encodeToByteArray()))

    @Test
    fun neprusvitnaSamolepka() {
        val cluster = el(
            0x1F43B675,
            el(0xE7, uint(0, 1)),
            el(0xA3, block(0, 0x80, byteArrayOf(1, 2, 3))),
            el(0xA3, block(33, 0x00, byteArrayOf(4, 5))),
        )
        val video = WebmDemuxer.parse(ebmlHeader + el(0x18538067, header(alpha = false), cluster))!!

        assertEquals(WebmCodec.Vp9, video.codec)
        assertEquals(512, video.width)
        assertEquals(33L, video.frameDurationMs)
        assertFalse(video.hasAlpha)
        assertEquals(WebmColour(bt709 = true, fullRange = false), video.colour)
        assertEquals(listOf(0L, 33L), video.frames.map { it.timeMs })
        assertEquals(listOf(true, false), video.frames.map { it.isKey })
        assertContentEquals(byteArrayOf(4, 5), video.frames[1].data)
        assertTrue(video.frames.all { it.alpha == null })
    }

    @Test
    fun pruhlednostVBlockAdditional() {
        fun group(relative: Int, data: ByteArray, alpha: ByteArray, referenced: Boolean) = el(
            0xA0,
            el(0xA1, block(relative, 0x00, data)),
            *(if (referenced) arrayOf(el(0xFB, uint(-33L and 0xFFFF, 2))) else emptyArray()),
            el(0x75A1, el(0xA6, el(0xEE, uint(1, 1)), el(0xA5, alpha))),
        )
        val cluster = el(
            0x1F43B675,
            el(0xE7, uint(1000, 2)),
            group(0, byteArrayOf(10), byteArrayOf(20), referenced = false),
            group(33, byteArrayOf(11), byteArrayOf(21), referenced = true),
        )
        val video = WebmDemuxer.parse(ebmlHeader + el(0x18538067, header(alpha = true), cluster))!!

        assertTrue(video.hasAlpha)
        assertEquals(listOf(1000L, 1033L), video.frames.map { it.timeMs })
        assertEquals(listOf(true, false), video.frames.map { it.isKey })
        assertContentEquals(byteArrayOf(21), video.frames[1].alpha)
        assertContentEquals(byteArrayOf(11), video.frames[1].data)
    }

    @Test
    fun segmentAClusterNeznameDelky() {
        val cluster = unknownSize(
            0x1F43B675,
            el(0xE7, uint(0, 1)),
            el(0xA3, block(0, 0x80, byteArrayOf(1))),
        )
        val cues = el(0x1C53BB6B, byteArrayOf(0x00))
        val video = WebmDemuxer.parse(ebmlHeader + unknownSize(0x18538067, header(alpha = false), cluster))
        assertEquals(1, video?.frames?.size)
        // Cues za clusterem neznámé délky snímkem není.
        val withCues = WebmDemuxer.parse(ebmlHeader + unknownSize(0x18538067, header(alpha = false), cluster, cues))
        assertEquals(1, withCues?.frames?.size)
    }

    @Test
    fun barvyBt601APlnyRozsah() {
        val cluster = el(0x1F43B675, el(0xE7, uint(0, 1)), el(0xA3, block(0, 0x80, byteArrayOf(1))))
        val video = WebmDemuxer.parse(ebmlHeader + el(0x18538067, header(alpha = false, matrix = 6, range = 2), cluster))!!
        assertEquals(WebmColour(bt709 = false, fullRange = true), video.colour)
    }

    @Test
    fun blokSLacingemSePreskoci() {
        val cluster = el(
            0x1F43B675,
            el(0xE7, uint(0, 1)),
            el(0xA3, block(0, 0x80, byteArrayOf(1))),
            el(0xA3, block(33, 0x02, byteArrayOf(2))),
        )
        val video = WebmDemuxer.parse(ebmlHeader + el(0x18538067, header(alpha = false), cluster))!!
        assertEquals(1, video.frames.size)
    }

    @Test
    fun poskozenyNeboCiziSouborJeNull() {
        assertNull(WebmDemuxer.parse(ByteArray(0)))
        assertNull(WebmDemuxer.parse(byteArrayOf(0x1A, 0x45)))
        assertNull(WebmDemuxer.parse(ByteArray(64) { 0 }))
        assertNull(WebmDemuxer.parse("GIF89a…".encodeToByteArray()))
    }
}
