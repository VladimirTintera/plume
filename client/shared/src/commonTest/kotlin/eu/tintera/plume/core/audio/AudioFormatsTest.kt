package eu.tintera.plume.core.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AudioFormatsTest {

    @Test
    fun waveformUnpacksFiveBitValues() {
        // Hodnoty 31, 0, 21, 10 → bity od nejnižšího: 11111 00000 10101 01010
        val packed = byteArrayOf(0x1F, 0x54, 0x55.toByte())
        assertEquals(listOf(31, 0, 21, 10), decodeWaveform(packed))
    }

    @Test
    fun waveformResamplesAndNormalizes() {
        assertEquals(listOf(0.5f, 1f), resampleWaveform(listOf(1, 3, 4, 4), bars = 2))
        assertEquals(listOf(0f, 0f, 0f), resampleWaveform(emptyList(), bars = 3))
    }

    @Test
    fun oggPacketsAcrossPagesAndLacing() {
        val head = "OpusHead".encodeToByteArray() + byteArrayOf(1, 1, 0x38, 0x01) + ByteArray(7)
        val tags = "OpusTags".encodeToByteArray() + ByteArray(8)
        val long = ByteArray(300) { it.toByte() } // 255 + 45 → dva segmenty
        val short = byteArrayOf(0x7C, 1, 2)

        val bytes = oggPage(listOf(head), serial = 7) +
            oggPage(listOf(tags), serial = 7) +
            oggPage(listOf(long, short), serial = 7) +
            oggPage(listOf(byteArrayOf(9, 9)), serial = 99) // cizí stream se ignoruje

        val stream = parseOggOpus(bytes)
        assertEquals(1, stream.channels)
        assertEquals(312, stream.preSkip)
        assertEquals(2, stream.packets.size)
        assertContentEquals(long, stream.packets[0])
        assertContentEquals(short, stream.packets[1])
    }

    @Test
    fun opusPacketDuration() {
        assertEquals(960, opusPacketSamples(byteArrayOf((1 shl 3).toByte()))) // SILK 20 ms
        assertEquals(960, opusPacketSamples(byteArrayOf(((31 shl 3) or 0).toByte()))) // CELT 20 ms
        assertEquals(1920, opusPacketSamples(byteArrayOf(((31 shl 3) or 1).toByte()))) // 2 rámce
        assertEquals(4800, opusPacketSamples(byteArrayOf(((31 shl 3) or 3).toByte(), 5))) // 5 rámců
    }

    @Test
    fun wavHeader() {
        val wav = wavFile(ByteArray(4), sampleRate = 48000, channels = 1)
        assertEquals(48, wav.size)
        assertEquals("RIFF", wav.decodeToString(0, 4))
        assertEquals("data", wav.decodeToString(36, 40))
    }

    /** Ogg stránka s daty jako jedním segmentem na paket (delší než 255 se rozdělí). */
    private fun oggPage(packets: List<ByteArray>, serial: Int): ByteArray {
        val lacing = mutableListOf<Byte>()
        packets.forEach { packet ->
            var remaining = packet.size
            while (remaining >= 255) { lacing += 255.toByte(); remaining -= 255 }
            lacing += remaining.toByte()
        }
        val header = ByteArray(27)
        "OggS".encodeToByteArray().copyInto(header)
        header[14] = serial.toByte(); header[15] = (serial shr 8).toByte()
        header[26] = lacing.size.toByte()
        return header + lacing.toByteArray() + packets.fold(ByteArray(0)) { acc, p -> acc + p }
    }
}
