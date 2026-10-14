package eu.tintera.plume.core.calls

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoFramesTest {

    /** Obraz 2 × 1: vlevo černá (Y 16), vpravo bílá (Y 235); barevnost neutrální. */
    private val blackWhite = byteArrayOf(16, 235.toByte(), 128.toByte(), 128.toByte())

    private fun gray(out: ByteArray, pixel: Int): Int = out[pixel * 4 + 1].toInt() and 0xFF

    @Test
    fun limitedRangeMapsToFullRange() {
        val out = ByteArray(2 * 4)
        i420ToBgra(blackWhite, width = 2, height = 1, rotation = 0, out)
        assertEquals(listOf(0, 0, 0, 255), out.take(4).map { it.toInt() and 0xFF })
        assertEquals(listOf(255, 255, 255, 255), out.drop(4).map { it.toInt() and 0xFF })
    }

    @Test
    fun rotationTurnsClockwise() {
        // Po 90° je ze šířky 2 × 1 výška 1 × 2 a levý pixel je nahoře.
        assertEquals(1 to 2, rotatedSize(2, 1, 90))
        val out = ByteArray(2 * 4)
        i420ToBgra(blackWhite, width = 2, height = 1, rotation = 90, out)
        assertEquals(listOf(0, 255), listOf(gray(out, 0), gray(out, 1)))
        // Po 270° je levý pixel dole, po 180° vpravo.
        i420ToBgra(blackWhite, width = 2, height = 1, rotation = 270, out)
        assertEquals(listOf(255, 0), listOf(gray(out, 0), gray(out, 1)))
        i420ToBgra(blackWhite, width = 2, height = 1, rotation = 180, out)
        assertEquals(listOf(255, 0), listOf(gray(out, 0), gray(out, 1)))
    }

    @Test
    fun portraitFrameFromPhoneTurnsUpright() {
        // 2 × 2, horní řádek černý, dolní bílý; otočeno o 90° je černý pravý sloupec.
        val frame = byteArrayOf(16, 16, 235.toByte(), 235.toByte(), 128.toByte(), 128.toByte())
        val out = ByteArray(4 * 4)
        i420ToBgra(frame, width = 2, height = 2, rotation = 90, out)
        assertEquals(listOf(255, 0, 255, 0), (0 until 4).map { gray(out, it) })
    }
}
