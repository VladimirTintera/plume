package eu.tintera.plume.core.ai

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Zvuk pro Gemmu: WAV, 16 kHz, mono, 16 bitů, po kusech nejvýš [MAX_CLIP_SECONDS] — tak ho modelu
 * dává Google AI Edge Gallery (`SAMPLE_RATE = 16000`, `MAX_AUDIO_CLIP_DURATION_SEC = 30`).
 *
 * - **Dekóduje systém** (`MediaExtractor` + `MediaCodec`): hlasovka je OGG/Opus, kolečko MP4 s AAC.
 *   Ogg/Opus `MediaExtractor` čte od Androidu 10; na starším selže a přepis se nenabídne
 *   (Gemma tam stejně neběží, chce Android 12).
 * - **Převzorkování lineární interpolací** na 16 kHz a smíchání kanálů průměrem. Na řeč to stačí;
 *   kvalitní filtr by stál víc kódu, než kolik přepisu přidá.
 */
internal object AudioClips {
    const val SAMPLE_RATE = 16_000
    const val MAX_CLIP_SECONDS = 30

    /** Celý zvuk jako PCM 16 kHz mono 16 bitů — pro systémové rozpoznávání řeči (`SystemSpeechToText`). */
    fun pcm16k(path: String): ShortArray = resample(decodeMono(path), SAMPLE_RATE)

    /** Kusy WAV připravené pro `Content.AudioBytes`; prázdné, když v souboru zvuk není. */
    fun wavClips(path: String): List<ByteArray> {
        val samples = pcm16k(path)
        val clip = SAMPLE_RATE * MAX_CLIP_SECONDS
        return (samples.indices step clip).map { from -> wav(samples.copyOfRange(from, minOf(from + clip, samples.size))) }
    }

    /** Mono PCM a jeho vzorkovací frekvence. */
    private fun decodeMono(path: String): Pair<ShortArray, Int> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track in $path")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return drain(extractor, codec, format)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, input: MediaFormat): Pair<ShortArray, Int> {
        var sampleRate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var float = false
        val out = ShortList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> codec.outputFormat.let { format ->
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    float = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    appendMono(buffer, channels, float, out)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        return out.toArray() to sampleRate
    }

    private fun appendMono(buffer: ByteBuffer, channels: Int, float: Boolean, out: ShortList) {
        val frameCount = buffer.remaining() / (channels * if (float) 4 else 2)
        repeat(frameCount) {
            var sum = 0f
            repeat(channels) { sum += if (float) buffer.getFloat() * Short.MAX_VALUE else buffer.getShort().toFloat() }
            out.add((sum / channels).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
    }

    private fun resample(input: Pair<ShortArray, Int>, target: Int): ShortArray {
        val (samples, rate) = input
        if (rate == target || samples.isEmpty()) return samples
        val length = (samples.size.toLong() * target / rate).toInt()
        val step = rate.toDouble() / target
        return ShortArray(length) { i ->
            val position = i * step
            val left = position.toInt().coerceAtMost(samples.lastIndex)
            val right = (left + 1).coerceAtMost(samples.lastIndex)
            val fraction = position - left
            (samples[left] * (1 - fraction) + samples[right] * fraction).toInt().toShort()
        }
    }

    /** Hlavička WAV (PCM, 16 kHz, mono, 16 bitů) a data. */
    private fun wav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataSize)
        samples.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    private const val TIMEOUT_US = 10_000L

    /** Rostoucí pole vzorků bez boxování. */
    private class ShortList {
        private var data = ShortArray(SAMPLE_RATE * 8)
        private var size = 0
        fun add(value: Short) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }
        fun toArray(): ShortArray = data.copyOf(size)
    }
}
