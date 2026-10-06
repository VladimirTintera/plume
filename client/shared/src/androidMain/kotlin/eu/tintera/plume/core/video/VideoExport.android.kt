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
) {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            // Transformer chce vlákno s Looperem a ozývá se na něm; hlavní ho má.
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .apply { audioMimeType?.let(::setAudioMimeType) }
                .apply {
                    if (videoBitrate != null) {
                        setEncoderFactory(
                            DefaultEncoderFactory.Builder(context)
                                .setRequestedVideoEncoderSettings(
                                    VideoEncoderSettings.Builder().setBitrate(videoBitrate).build(),
                                )
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
