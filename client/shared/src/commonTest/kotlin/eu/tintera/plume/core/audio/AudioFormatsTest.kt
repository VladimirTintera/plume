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
    fun waveformPrevzorkujeNaAbsolutniMeritko() {
        // Průměry dvojic jsou 2 a 4; strop je 31, ne špička té hlasovky.
        assertEquals(listOf(2f / 31f, 4f / 31f), resampleWaveform(listOf(1, 3, 4, 4), bars = 2))
        assertEquals(listOf(0f, 0f, 0f), resampleWaveform(emptyList(), bars = 3))
    }

    /**
     * Tichá a hlasitá hlasovka se NESMÍ vykreslit stejně.
     *
     * Dokud se škálovalo na špičku, vyšly oba tyhle waveformy shodně (obojí `[0.5, 1]`) a každá
     * hlasovka v konverzaci pak měla jinak vysoké sloupce podle toho, jak nahlas byla nahraná.
     */
    @Test
    fun tichaHlasovkaNeniRoztazenaNaStrop() {
        val hlasita = resampleWaveform(listOf(16, 31), bars = 2)
        val ticha = resampleWaveform(listOf(2, 4), bars = 2)
        assertEquals(1f, hlasita.last())
        assertEquals(4f / 31f, ticha.last())
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
    fun waveformRoundTrip() {
        val levels = listOf(0f, 0.5f, 1f, 0.25f)
        val decoded = decodeWaveform(encodeWaveform(levels))
        assertEquals(WAVEFORM_VALUES, decoded.size)
        assertEquals(0, decoded.first())
        assertEquals(31, decoded.max())
        assertEquals(31, decoded[50]) // třetí úroveň (1.0) pokrývá hodnoty 50–74
    }

    @Test
    fun oggCrcMatchesReferenceVector() {
        // CRC-32/POSIX „123456789" = 0x765E7680 (s výstupním XOR); Ogg XOR nedělá.
        assertEquals(0x765E7680.toInt() xor -1, oggCrc("123456789".encodeToByteArray()))
    }

    @Test
    fun oggWriterRoundTrip() {
        val packets = List(120) { index -> ByteArray(index % 300 + 1) { index.toByte() } }
        val file = oggOpusFile(packets, channels = 1, preSkip = 312, samplesPerPacket = 960)

        val stream = parseOggOpus(file)
        assertEquals(1, stream.channels)
        assertEquals(312, stream.preSkip)
        assertEquals(packets.size, stream.packets.size)
        packets.zip(stream.packets).forEach { (expected, actual) -> assertContentEquals(expected, actual) }

        // Každá stránka má platné CRC (počítané s vynulovaným polem).
        var offset = 0
        while (offset < file.size) {
            val segments = file[offset + 26].toInt() and 0xFF
            val length = 27 + segments + (0 until segments).sumOf { file[offset + 27 + it].toInt() and 0xFF }
            val page = file.copyOfRange(offset, offset + length)
            val stored = (0 until 4).sumOf { (page[22 + it].toInt() and 0xFF) shl (8 * it) }
            for (i in 22 until 26) page[i] = 0
            assertEquals(stored, oggCrc(page))
            offset += length
        }
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
