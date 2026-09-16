package eu.tintera.plume.core.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioCompressedBuffer
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_EndOfStream
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterOutputStatus_EndOfStream
import platform.AVFAudio.AVAudioConverterOutputStatus_Error
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatOpus
import platform.Foundation.NSError
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

    private fun encodeSine(seconds: Int): List<ByteArray> = memScoped {
        val pcmFormat = AVAudioFormat(commonFormat = AVAudioPCMFormatInt16, sampleRate = 48000.0, channels = 1u, interleaved = true)
        val frames = 48000 * seconds
        val pcm = AVAudioPCMBuffer(pCMFormat = pcmFormat, frameCapacity = frames.convert())
        pcm.frameLength = frames.convert()
        val channel = pcm.int16ChannelData!![0]!!
        for (i in 0 until frames) channel[i] = (sin(2 * PI * 440 * i / 48000) * 12000).toInt().toShort()

        val description = alloc<AudioStreamBasicDescription>().apply {
            mSampleRate = 48000.0
            mFormatID = kAudioFormatOpus
            mFramesPerPacket = 960u
            mChannelsPerFrame = 1u
        }
        val opusFormat = AVAudioFormat(streamDescription = description.ptr)
        val converter = AVAudioConverter(fromFormat = pcmFormat, toFormat = opusFormat)
        val output = AVAudioCompressedBuffer(format = opusFormat, packetCapacity = 100u, maximumPacketSize = 4000)

        val packets = mutableListOf<ByteArray>()
        var given = false
        val error = alloc<ObjCObjectVar<NSError?>>()
        while (true) {
            output.packetCount = 0u
            output.byteLength = 0u
            val status = converter.convertToBuffer(output, error.ptr) { _, inputStatus ->
                if (given) {
                    inputStatus?.pointed?.value = AVAudioConverterInputStatus_EndOfStream
                    null
                } else {
                    given = true
                    inputStatus?.pointed?.value = AVAudioConverterInputStatus_HaveData
                    pcm
                }
            }
            check(status != AVAudioConverterOutputStatus_Error) { error.value?.localizedDescription ?: "kodér selhal" }
            val descriptions = output.packetDescriptions!!
            val data = output.data!!.rawValue
            for (i in 0 until output.packetCount.toInt()) {
                val d = descriptions[i]
                packets += interpretCPointer<ByteVar>(data + d.mStartOffset)!!.readBytes(d.mDataByteSize.toInt())
            }
            if (status == AVAudioConverterOutputStatus_EndOfStream || output.packetCount == 0u) break
        }
        packets
    }
}
