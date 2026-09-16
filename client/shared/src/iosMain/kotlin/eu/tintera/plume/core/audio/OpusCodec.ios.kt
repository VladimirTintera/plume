package eu.tintera.plume.core.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
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
import platform.posix.memcpy

/*
 * Systémový Opus (AudioToolbox přes AVAudioConverter), oběma směry. iOS Opus kóduje i dekóduje,
 * jen ho neumí číst ani psát v Oggu — kontejner řeší commonMain (`parseOggOpus`, `oggOpusFile`).
 */

/** Opus se kóduje i dekóduje vždycky při 48 kHz, bez ohledu na původní vzorkování nahrávky. */
internal const val OPUS_SAMPLE_RATE = 48000

/** Délka paketu, do kterých kódujeme: 20 ms, jako oficiální klienti. */
internal const val OPUS_ENCODE_SAMPLES_PER_PACKET = 960

/** Formát Opusu pro AVAudioFormat. Počet vzorků v paketu musí být znám předem. */
@OptIn(ExperimentalForeignApi::class)
private fun MemScope.opusFormat(channels: Int, samplesPerPacket: Int): AVAudioFormat {
    val description = alloc<AudioStreamBasicDescription>().apply {
        mSampleRate = OPUS_SAMPLE_RATE.toDouble()
        mFormatID = kAudioFormatOpus
        mFormatFlags = 0u
        mBytesPerPacket = 0u
        mFramesPerPacket = samplesPerPacket.convert()
        mBytesPerFrame = 0u
        mChannelsPerFrame = channels.convert()
        mBitsPerChannel = 0u
    }
    return AVAudioFormat(streamDescription = description.ptr)
}

/**
 * Dekóduje Opus pakety do PCM 16 bit.
 *
 * `AVAudioConverter` si pakety bere přes blok, po jednom v `AVAudioCompressedBuffer` s popisem
 * paketu — velikosti se liší, takže bez popisu by dekodér nevěděl, kde paket končí. Kolik vzorků
 * paket nese, říká jeho TOC bajt ([opusPacketSamples]); formát potřebuje hodnotu předem.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun decodeOpus(stream: OggOpusStream): ByteArray {
    if (stream.packets.isEmpty()) return ByteArray(0)
    val channels = stream.channels.coerceIn(1, 2)
    val framesPerPacket = stream.packets.firstNotNullOfOrNull { opusPacketSamples(it).takeIf { n -> n > 0 } } ?: 960

    return memScoped {
        val inputFormat = opusFormat(channels, framesPerPacket)
        val outputFormat = AVAudioFormat(
            commonFormat = AVAudioPCMFormatInt16,
            sampleRate = OPUS_SAMPLE_RATE.toDouble(),
            channels = channels.convert(),
            interleaved = true,
        )
        val converter = AVAudioConverter(fromFormat = inputFormat, toFormat = outputFormat)

        val maxPacket = stream.packets.maxOf { it.size }
        val input = AVAudioCompressedBuffer(format = inputFormat, packetCapacity = 1u, maximumPacketSize = maxPacket.convert())
        val output = AVAudioPCMBuffer(pCMFormat = outputFormat, frameCapacity = (framesPerPacket * 50).convert())

        var next = 0
        val result = ArrayList<ByteArray>()
        val error = alloc<ObjCObjectVar<NSError?>>()

        while (true) {
            output.frameLength = 0u
            val status = converter.convertToBuffer(output, error.ptr) { _, inputStatus ->
                if (next >= stream.packets.size) {
                    inputStatus?.pointed?.value = AVAudioConverterInputStatus_EndOfStream
                    return@convertToBuffer null
                }
                val packet = stream.packets[next++]
                packet.usePinned { pinned ->
                    memcpy(input.data, pinned.addressOf(0), packet.size.convert())
                }
                input.packetDescriptions?.get(0)?.apply {
                    mStartOffset = 0
                    mVariableFramesInPacket = 0u
                    mDataByteSize = packet.size.convert()
                }
                input.byteLength = packet.size.convert()
                input.packetCount = 1u
                inputStatus?.pointed?.value = AVAudioConverterInputStatus_HaveData
                input
            }
            check(status != AVAudioConverterOutputStatus_Error) {
                error.value?.localizedDescription ?: "Opus nejde dekódovat."
            }

            val frames = output.frameLength.toInt()
            if (frames > 0) {
                val samples = output.int16ChannelData?.get(0) ?: error("Opus nejde dekódovat.")
                result += samples.reinterpret<ByteVar>().readBytes(frames * channels * 2)
            }
            if (status == AVAudioConverterOutputStatus_EndOfStream || (frames == 0 && next >= stream.packets.size)) break
        }

        val total = ByteArray(result.sumOf { it.size })
        var offset = 0
        result.forEach { chunk ->
            chunk.copyInto(total, offset)
            offset += chunk.size
        }
        total
    }
}

/**
 * Zakóduje PCM 16 bit (48 kHz, mono) do Opus paketů po 20 ms.
 *
 * Kodér vydává pakety do `AVAudioCompressedBuffer` po dávkách; každý paket se podle popisu
 * (posun a délka) vykopíruje zvlášť, protože Ogg je balí jednotlivě.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun encodeOpus(pcm: AVAudioPCMBuffer): List<ByteArray> = memScoped {
    val opus = opusFormat(channels = 1, samplesPerPacket = OPUS_ENCODE_SAMPLES_PER_PACKET)
    val converter = AVAudioConverter(fromFormat = pcm.format, toFormat = opus)
    val output = AVAudioCompressedBuffer(format = opus, packetCapacity = 100u, maximumPacketSize = MAX_OPUS_PACKET.convert())

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
        check(status != AVAudioConverterOutputStatus_Error) { error.value?.localizedDescription ?: "Opus nejde zakódovat." }

        val descriptions = output.packetDescriptions ?: error("Opus nejde zakódovat.")
        val data = (output.data ?: error("Opus nejde zakódovat.")).rawValue
        for (i in 0 until output.packetCount.toInt()) {
            val description = descriptions[i]
            packets += interpretCPointer<ByteVar>(data + description.mStartOffset)!!
                .readBytes(description.mDataByteSize.toInt())
        }
        if (status == AVAudioConverterOutputStatus_EndOfStream || output.packetCount == 0u) break
    }
    packets
}

/** PCM formát, do kterého se nahrává a ze kterého se kóduje. */
@OptIn(ExperimentalForeignApi::class)
internal fun pcmFormat(): AVAudioFormat = AVAudioFormat(
    commonFormat = AVAudioPCMFormatInt16,
    sampleRate = OPUS_SAMPLE_RATE.toDouble(),
    channels = 1u,
    interleaved = true,
)

/** Opus paket má nejvýš 1 275 bajtů (RFC 6716); rezerva pro kodér. */
private const val MAX_OPUS_PACKET = 4000
