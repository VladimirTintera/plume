package eu.tintera.plume.core.audio

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Dekodér zvuku na desktopu nad skutečnými soubory: hlasovka (Ogg/Opus 48 kHz mono) a skladba
 * (MP3 44,1 kHz stereo), obě 2 s sinus s amplitudou 1/8 (`sine` z FFmpegu, ~4000). Výstup je
 * vždycky 48 kHz stereo 16 bitů.
 */
class FfmpegAudioDecoderTest {

    @Test
    fun voiceDecodesToTwoSecondsOfStereo48k() = decode("voice.ogg") { decoder ->
        assertNear(2000, decoder.durationMs, 60, "délka hlasovky")
        val frames = readAll(decoder)
        assertNear(96_000, frames, 2_000, "vzorků za 2 s")
    }

    @Test
    fun musicIsResampledTo48k() = decode("music.mp3") { decoder ->
        assertNear(2000, decoder.durationMs, 100, "délka skladby")
        assertNear(96_000, readAll(decoder), 3_000, "vzorků po převzorkování ze 44,1 kHz")
    }

    @Test
    fun fasterTempoShortensOutput() = decode("voice.ogg") { decoder ->
        decoder.setTempo(1.5f)
        assertNear(64_000, readAll(decoder), 2_500, "vzorků při rychlosti 1,5")
    }

    @Test
    fun seekSkipsTheBeginning() = decode("voice.ogg") { decoder ->
        decoder.read()
        decoder.seek(1500)
        assertNear(24_000, readAll(decoder), 2_000, "vzorků od 1,5 s do konce")
    }

    @Test
    fun signalIsNotSilence() = decode("voice.ogg") { decoder ->
        val chunk = generateSequence { decoder.read() }.take(20).fold(ByteArray(0)) { all, next -> all + next }
        var peak = 0
        for (i in 0 until chunk.size / 2) {
            val sample = (chunk[2 * i].toInt() and 0xFF) or (chunk[2 * i + 1].toInt() shl 8)
            peak = maxOf(peak, abs(sample.toShort().toInt()))
        }
        assertTrue(peak > 1500, "zvuk je ticho (špička $peak)")
    }

    private fun decode(name: String, block: (FfmpegAudioDecoder) -> Unit) {
        val file = File.createTempFile("audio", ".$name")
        file.writeBytes(checkNotNull(javaClass.getResourceAsStream("/audio/$name")).readBytes())
        FfmpegAudioDecoder.open(file.path).use(block)
    }

    /** Všechny zbylé vzorky (stereo rámce) do konce souboru. */
    private fun readAll(decoder: FfmpegAudioDecoder): Int =
        generateSequence { decoder.read() }.sumOf { it.size } / FfmpegAudioDecoder.OUTPUT_FRAME_BYTES

    private fun assertNear(expected: Long, actual: Long, tolerance: Long, what: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: čekám $expected ± $tolerance, je $actual")

    private fun assertNear(expected: Int, actual: Int, tolerance: Int, what: String) =
        assertNear(expected.toLong(), actual.toLong(), tolerance.toLong(), what)
}
