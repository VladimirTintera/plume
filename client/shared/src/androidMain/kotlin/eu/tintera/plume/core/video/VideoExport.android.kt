package eu.tintera.plume.core.video

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Export [composition] do H.264 MP4 přes Media3 Transformer — sdílí ho kolečka (zmenšení,
 * spojení) i animovaná profilovka. [audioMimeType] vynutí kodér zvuku, [videoBitrate] datový tok
 * obrazu; `null` = podle Transformeru.
 */
internal suspend fun exportVideo(
    context: Context,
    composition: Composition,
    target: File,
    audioMimeType: String? = null,
    videoBitrate: Int? = null,
    /** Kodek obrazu; když ho zařízení neumí, Transformer sám spadne na podporovaný (H.264). */
    videoMimeType: String = MimeTypes.VIDEO_H264,
    /** Klíčový snímek po tolika sekundách; `null` = jak chce kodér. */
    keyFrameIntervalSeconds: Float? = null,
) {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            // Transformer chce vlákno s Looperem a ozývá se na něm; hlavní ho má.
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(videoMimeType)
                .apply { audioMimeType?.let(::setAudioMimeType) }
                .apply {
                    if (videoBitrate != null || keyFrameIntervalSeconds != null) {
                        val settings = VideoEncoderSettings.Builder()
                            .apply { videoBitrate?.let(::setBitrate) }
                            .apply { keyFrameIntervalSeconds?.let(::setiFrameIntervalSeconds) }
                            .build()
                        setEncoderFactory(
                            DefaultEncoderFactory.Builder(context)
                                .setRequestedVideoEncoderSettings(settings)
                                .build(),
                        )
                    }
                }
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        continuation.resumeWithException(exportException)
                    }
                })
                .build()
            transformer.start(composition, target.absolutePath)
            continuation.invokeOnCancellation { transformer.cancel() }
        }
    }
}
