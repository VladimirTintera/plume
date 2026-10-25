package eu.tintera.plume.core.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hlasovka z desktopu bez mikrofonu: PCM sinus → `libopus` → Ogg ([oggOpusFile]) a zpátky
 * dekodérem přehrávače. Ověřuje, že soubor přečte FFmpeg (tentýž rozbor jako u hlasovek od
 * Telegramu), délku, signál a spojení dvou nahrávek ([joinOggOpus]).
 */
class DesktopVoiceRecordingTest {

    @Test
    fun encodedVoiceDecodesBackWithTheSameLength() {
        val ogg = record(seconds = 2.0)
        FfmpegAudioDecoder.open(write(ogg).path).use { decoder ->
            assertNear(2000, decoder.durationMs, 60, "délka hlasovky")
            val decoded = readAll(decoder)
            assertNear(96_000, decoded.size / FfmpegAudioDecoder.OUTPUT_FRAME_BYTES, 2_000, "vzorků za 2 s")
            assertTrue(peak(decoded) > 8_000, "zvuk je ticho (špička ${peak(decoded)})")
        }
    }

    @Test
    fun twoRecordingsJoinIntoOne() {
        val joined = joinOggOpus(listOf(record(seconds = 1.0), record(seconds = 1.5)))
        FfmpegAudioDecoder.open(write(joined).path).use { decoder ->
            assertNear(2500, decoder.durationMs, 100, "délka spojené hlasovky")
        }
    }

    @Test
    fun levelFollowsLoudness() {
        val silence = ByteArray(9600)
        assertEquals(0f, levelOf(silence, silence.size))
        val loud = sine(0.1, amplitude = 0.7)
        // Sinus 0,7 má efektivní hodnotu 0,7/√2, tedy −6 dB: (50 − 6) / 50 ≈ 0,88.
        assertTrue(levelOf(loud, loud.size) in 0.85f..0.9f, "hlasitý tón")
        val quiet = sine(0.1, amplitude = 0.01)
        val level = levelOf(quiet, quiet.size)
        assertTrue(level in 0.05f..0.3f, "tichý tón (−43 dB) je $level")
    }

    private fun record(seconds: Double): ByteArray {
        val encoded = FfmpegOpusEncoder.encode(sine(seconds, amplitude = 0.5))
        assertEquals(960, encoded.samplesPerPacket, "20ms rámce")
        return oggOpusFile(encoded.packets, channels = 1, preSkip = encoded.preSkip, samplesPerPacket = encoded.samplesPerPacket)
    }

    /** 440 Hz, 48 kHz mono, 16 bitů little endian. */
    private fun sine(seconds: Double, amplitude: Double): ByteArray {
        val samples = (seconds * 48_000).roundToInt()
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = (sin(2 * PI * 440 * i / 48_000) * amplitude * Short.MAX_VALUE).roundToInt()
            bytes[2 * i] = value.toByte()
            bytes[2 * i + 1] = (value shr 8).toByte()
        }
        return bytes
    }

    private fun write(bytes: ByteArray): File = File.createTempFile("voice", ".ogg").apply { writeBytes(bytes) }

    private fun readAll(decoder: FfmpegAudioDecoder): ByteArray =
        generateSequence { decoder.read() }.fold(ByteArray(0)) { all, next -> all + next }

    private fun peak(pcm: ByteArray): Int {
        var peak = 0
        for (i in 0 until pcm.size / 2) {
            val sample = ((pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)).toShort().toInt()
            peak = maxOf(peak, abs(sample))
        }
        return peak
    }

    private fun assertNear(expected: Long, actual: Long, tolerance: Long, what: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: čekám $expected ± $tolerance, je $actual")

    private fun assertNear(expected: Int, actual: Int, tolerance: Int, what: String) =
        assertNear(expected.toLong(), actual.toLong(), tolerance.toLong(), what)
}
