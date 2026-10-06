package eu.tintera.plume.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import eu.tintera.plume.core.video.exportVideo
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Media3 Transformer: výřez (`Crop`), zmenšení (`Presentation`), prvních 10 s (ořez
 * `MediaItem`) a bez zvuku (`setRemoveAudio`).
 *
 * **Výřez platí pro OTOČENÝ obraz.** Transformer nechá dekodér snímky otočit podle metadat
 * dřív, než na ně pustí efekty, takže výřez vybraný nad otočeným snímkem
 * (`getFrameAtTime` vrací otočený) sedí i na video natočené na výšku.
 */
class AndroidProfileVideo(private val context: Context) : ProfileVideo {

    override suspend fun frame(path: String): String = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Video nemá snímek.")
            val scale = minOf(1f, PROFILE_VIDEO_FRAME_EDGE.toFloat() / max(frame.width, frame.height))
            val scaled = if (scale == 1f) frame else Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).roundToInt().coerceAtLeast(1),
                (frame.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            val file = File(File(path).parentFile, "profile-frame.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
            file.absolutePath
        } finally {
            retriever.release()
        }
    }

    override suspend fun crop(path: String, region: CropRegion?): String {
        val source = File(path)
        val (width, height) = withContext(Dispatchers.IO) { orientedSize(path) }
        val (left, top, side) = squarePixels(region, width, height)
        // Sudá strana: kodéry H.264 lichou odmítají nebo ji tiše zaokrouhlí.
        val edge = (minOf(side, PROFILE_VIDEO_EDGE).coerceAtLeast(PROFILE_VIDEO_MIN_EDGE) / 2) * 2

        // Crop bere souřadnice od −1 do 1, svisle zdola nahoru.
        val crop = Crop(
            left * 2f / width - 1f,
            (left + side) * 2f / width - 1f,
            1f - (top + side) * 2f / height,
            1f - top * 2f / height,
        )
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(source))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(PROFILE_VIDEO_MAX_SECONDS * 1000L)
                    .build(),
            )
            .build()
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(crop, Presentation.createForWidthAndHeight(edge, edge, Presentation.LAYOUT_SCALE_TO_FIT)),
                ),
            )
            .build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(edited).build()).build()

        // Kodér datový tok přesně nedodrží; když soubor přesto přeteče strop, zkusí se polovina.
        var bitrate = PROFILE_VIDEO_BITRATE
        repeat(2) {
            val target = File(source.parentFile, "profile-$bitrate.mp4")
            exportVideo(context, composition, target, videoBitrate = bitrate)
            if (target.length() <= PROFILE_VIDEO_MAX_BYTES) return target.absolutePath
            target.delete()
            bitrate /= 2
        }
        error("Video se nevešlo do ${PROFILE_VIDEO_MAX_BYTES} B.")
    }

    /** Rozměry tak, jak se video zobrazí — u videa na výšku prohozené podle metadat. */
    private fun orientedSize(path: String): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            fun metadata(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            val width = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            check(width > 0 && height > 0) { "Video nemá rozměry." }
            val rotated = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
            return if (rotated) height to width else width to height
        } finally {
            retriever.release()
        }
    }
}
