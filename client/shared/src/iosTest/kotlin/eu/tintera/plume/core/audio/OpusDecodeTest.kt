package eu.tintera.plume.core.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioPCMBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Dekodér hlasovek na skutečném systémovém Opusu: sinus se zakóduje týmž `AVAudioConverter`
 * (opačným směrem) a musí se vrátit jako neztichlý zvuk správné délky. Ogg se tu neřeší — ten
 * pokrývá `AudioFormatsTest` v commonTest.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class OpusDecodeTest {

    @Test
    fun decodesSystemEncodedOpus() {
        val seconds = 1
        val packets = encodeSine(seconds)
        assertTrue(packets.size >= 40, "Kodér vydal ${packets.size} paketů.")

        val pcm = decodeOpus(OggOpusStream(channels = 1, preSkip = 0, packets = packets))
        val samples = pcm.size / 2
        assertTrue(samples >= 48000 * seconds * 9 / 10, "Dekódováno jen $samples vzorků.")

        var peak = 0
        for (i in 0 until samples) {
            val value = (pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)
            peak = maxOf(peak, abs(value.toShort().toInt()))
        }
        assertTrue(peak > 3000, "Dekódovaný zvuk je ticho (špička $peak).")
    }

    /** Sinus 440 Hz zakódovaný týmž kodérem, kterým se kódují nahrávky. */
    private fun encodeSine(seconds: Int): List<ByteArray> {
        val frames = 48000 * seconds
        val pcm = AVAudioPCMBuffer(pCMFormat = pcmFormat(), frameCapacity = frames.convert())
        pcm.frameLength = frames.convert()
        val channel = pcm.int16ChannelData!![0]!!
        for (i in 0 until frames) channel[i] = (sin(2 * PI * 440 * i / 48000) * 12000).toInt().toShort()
        return encodeOpus(pcm)
    }

    @Test
    fun recordedVoiceRoundTripThroughOgg() {
        val packets = encodeSine(2)
        val ogg = oggOpusFile(packets, channels = 1, preSkip = 312, samplesPerPacket = OPUS_ENCODE_SAMPLES_PER_PACKET)
        val stream = parseOggOpus(ogg)
        val pcm = decodeOpus(stream)
        assertTrue(pcm.size / 2 >= 48000 * 2 * 9 / 10, "Z Oggu se dekódovalo jen ${pcm.size / 2} vzorků.")
    }
}
