package eu.tintera.plume.core.audio

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable
import org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN

/** Zvuk zakódovaný do Opusu, ještě bez kontejneru — do Oggu ho zabalí [oggOpusFile]. */
internal class EncodedOpus(
    val packets: List<ByteArray>,
    /** Zpoždění kodéru na začátku, které přehrávač zahodí (vzorky při 48 kHz). */
    val preSkip: Int,
    val samplesPerPacket: Int,
)

/**
 * PCM z mikrofonu (48 kHz, mono, 16 bitů little endian) do Opusu přes `libopus` z FFmpegu
 * (JavaCPP; LGPL build ho má staticky v libavcodec). Tentýž kodér používá Telegram, takže
 * hlasovka se nastavením neliší od těch z mobilů: 20ms rámce, režim `voip`, 32 kbit/s.
 *
 * Poslední neúplný rámec se doplní tichem — `libopus` bere jen celé rámce; ticho na konci
 * jednotek ms nikdo nepozná a délku hlasovky počítá Telegram z granule poslední stránky.
 */
internal object FfmpegOpusEncoder {

    fun encode(pcm: ByteArray): EncodedOpus {
        val encoder = avcodec_find_encoder_by_name("libopus")
            ?: throw IllegalStateException("avcodec_find_encoder_by_name: libopus")
        val codec: AVCodecContext = avcodec_alloc_context3(encoder)
        val options = AVDictionary(null)
        val packet: AVPacket = av_packet_alloc()
        val frame: AVFrame = av_frame_alloc()
        try {
            codec.sample_rate(SAMPLE_RATE)
            codec.sample_fmt(AV_SAMPLE_FMT_S16)
            av_channel_layout_default(codec.ch_layout(), 1)
            codec.bit_rate(BIT_RATE)
            av_dict_set(options, "application", "voip", 0)
            av_dict_set(options, "frame_duration", "20", 0)
            avcodec_open2(codec, encoder, options).checked("avcodec_open2")

            val samplesPerFrame = codec.frame_size()
            val bytesPerFrame = samplesPerFrame * BYTES_PER_SAMPLE
            frame.nb_samples(samplesPerFrame)
            frame.format(AV_SAMPLE_FMT_S16)
            av_channel_layout_default(frame.ch_layout(), 1)
            frame.sample_rate(SAMPLE_RATE)
            av_frame_get_buffer(frame, 0).checked("av_frame_get_buffer")

            val packets = mutableListOf<ByteArray>()
            fun drain() {
                while (true) {
                    val received = avcodec_receive_packet(codec, packet)
                    if (received == AVERROR_EAGAIN() || received == AVERROR_EOF) return
                    received.checked("avcodec_receive_packet")
                    val bytes = ByteArray(packet.size())
                    packet.data().get(bytes)
                    packets += bytes
                    av_packet_unref(packet)
                }
            }

            var offset = 0
            var pts = 0L
            val chunk = ByteArray(bytesPerFrame)
            while (offset < pcm.size) {
                val count = minOf(bytesPerFrame, pcm.size - offset)
                pcm.copyInto(chunk, 0, offset, offset + count)
                if (count < bytesPerFrame) chunk.fill(0, count)
                av_frame_make_writable(frame).checked("av_frame_make_writable")
                frame.data(0).put(chunk, 0, bytesPerFrame)
                frame.pts(pts)
                avcodec_send_frame(codec, frame).checked("avcodec_send_frame")
                drain()
                offset += count
                pts += samplesPerFrame
            }
            avcodec_send_frame(codec, null as AVFrame?).checked("avcodec_send_frame")
            drain()
            return EncodedOpus(packets, preSkip = codec.initial_padding(), samplesPerPacket = samplesPerFrame)
        } finally {
            av_frame_free(frame)
            av_packet_free(packet)
            av_dict_free(options)
            avcodec_free_context(codec)
        }
    }

    private fun Int.checked(call: String): Int =
        if (this < 0) throw IllegalStateException("$call: $this") else this

    const val SAMPLE_RATE = 48_000
    const val BYTES_PER_SAMPLE = 2

    /** Hlas mono: 32 kbit/s je víc než dost a minuta má ~240 kB. */
    private const val BIT_RATE = 32_000L
}
