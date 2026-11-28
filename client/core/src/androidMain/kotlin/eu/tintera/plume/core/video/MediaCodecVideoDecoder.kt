package eu.tintera.plume.core.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MimeTypes

/**
 * Ptá se `MediaCodecList` na dekodér pro MIME typ kodeku i na rozměry — softwarový dekodér
 * na slabším telefonu může mít strop nižší než 1080p. Neznámý kodek = žádný.
 */
fun mediaCodecVideoDecoder(codec: String, width: Int, height: Int): VideoDecoder {
    val mime = when (codec.lowercase()) {
        "h264", "avc" -> MimeTypes.VIDEO_H264
        "h265", "hevc" -> MimeTypes.VIDEO_H265
        "av1", "av01" -> MimeTypes.VIDEO_AV1
        "vp9" -> MimeTypes.VIDEO_VP9
        else -> return VideoDecoder.None
    }
    val usable = decoders.filter { info ->
        info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
            runCatching { info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(width, height) }
                .getOrNull() ?: true
    }
    return when {
        usable.isEmpty() -> VideoDecoder.None
        usable.any(::isHardware) -> VideoDecoder.Hardware
        else -> VideoDecoder.Software
    }
}

/**
 * Od Androidu 10 to dekodér řekne sám; starší systém jen jménem — softwarové dekodéry jsou
 * Googlu (`OMX.google.`, `c2.android.`).
 */
private fun isHardware(info: MediaCodecInfo): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        info.isHardwareAccelerated
    } else {
        !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
    }

/** Dekodéry zařízení; seznam se za běhu nemění a sestavit ho stojí desítky milisekund. */
private val decoders: List<MediaCodecInfo> by lazy {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
}
