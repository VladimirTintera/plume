package eu.tintera.plume.core.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import eu.tintera.plume.core.video.exportVideo
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Media3 Transformer: plátno 720 × 1280 (`Presentation` — vyplnit s ořezem, nebo vepsat na
 * černou), prvních 60 s, H.265 s klíčovým snímkem každou sekundu. Zvuk zůstává (AAC).
 *
 * **H.265 jen když ho zařízení umí zakódovat**; jinak Transformer spadne na H.264. Telegram
 * ho podle dokumentace chce, ale oficiální klienti na zařízeních bez kodéru H.265 posílají
 * H.264 taky — na tom zařízení ověřené NENÍ.
 */
class AndroidStoryVideo(
    private val context: Context,
    private val profileVideo: ProfileVideo,
) : StoryVideo {

    override val supported: Boolean = true

    override suspend fun durationMs(path: String): Long = profileVideo.durationMs(path)

    override suspend fun preview(path: String): String = profileVideo.frame(path, atMs = 0)

    override suspend fun render(path: String, fill: Boolean): RenderedStoryVideo {
        val source = File(path)
        val (durationMs, hasAudio) = withContext(Dispatchers.IO) { probe(path) }
        val lengthMs = minOf(durationMs, STORY_VIDEO_MAX_SECONDS * 1000L).coerceAtLeast(1)
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(source))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(lengthMs).build(),
            )
            .build()
        val layout = if (fill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
        val edited = EditedMediaItem.Builder(item)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(Presentation.createForWidthAndHeight(STORY_VIDEO_WIDTH, STORY_VIDEO_HEIGHT, layout)),
                ),
            )
            .build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(edited).build()).build()
        val directory = File(context.cacheDir, "$OUTGOING_DIRECTORY/${UUID.randomUUID()}").apply { mkdirs() }
        val target = File(directory, "story.mp4")
        exportVideo(
            context,
            composition,
            target,
            audioMimeType = MimeTypes.AUDIO_AAC.takeIf { hasAudio },
            videoBitrate = STORY_VIDEO_BITRATE,
            videoMimeType = MimeTypes.VIDEO_H265,
            keyFrameIntervalSeconds = 1f,
        )
        return RenderedStoryVideo(path = target.absolutePath, durationSeconds = lengthMs / 1000.0, hasAudio = hasAudio)
    }

    private fun probe(path: String): Pair<Long, Boolean> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val audio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            return duration to audio
        } finally {
            retriever.release()
        }
    }
}
