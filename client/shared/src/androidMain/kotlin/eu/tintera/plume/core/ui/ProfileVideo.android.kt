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
 * Media3 Transformer: výřez (`Crop`), zmenšení (`Presentation`), 10 s od zvoleného začátku
 * (ořez `MediaItem`) a bez zvuku (`setRemoveAudio`).
 *
 * **Výřez platí pro OTOČENÝ obraz.** Transformer nechá dekodér snímky otočit podle metadat
 * dřív, než na ně pustí efekty, takže výřez vybraný nad otočeným snímkem
 * (`getFrameAtTime` vrací otočený) sedí i na video natočené na výšku.
 */
class AndroidProfileVideo(private val context: Context) : ProfileVideo {

    override suspend fun durationMs(path: String): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    override suspend fun frame(path: String, atMs: Long, edge: Int): String = withContext(Dispatchers.IO) {
        val file = File(File(path).parentFile, profileFrameName(atMs, edge))
        if (file.length() > 0) return@withContext file.absolutePath
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            // Snímek pro statickou fotku musí být PŘESNĚ ten (server ho vezme z času, ne ze
            // snímku, který jsme ukázali); dlaždice pásu stačí nejbližší klíčový — je rychlý.
            val option = if (edge > PROFILE_VIDEO_STRIP_EDGE) {
                MediaMetadataRetriever.OPTION_CLOSEST
            } else {
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            }
            val frame = retriever.getFrameAtTime(atMs * 1000, option)
                ?: retriever.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Video nemá snímek.")
            val scale = minOf(1f, edge.toFloat() / max(frame.width, frame.height))
            val scaled = if (scale == 1f) frame else Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).roundToInt().coerceAtLeast(1),
                (frame.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            val temp = File(file.parentFile, file.name + ".part")
            temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
            // Přes přejmenování: souběžné čtení (pás i ořez) nesmí dostat napůl zapsaný JPEG.
            check(temp.renameTo(file)) { "Snímek nejde uložit." }
            file.absolutePath
        } finally {
            retriever.release()
        }
    }

    override suspend fun crop(path: String, region: CropRegion?, startMs: Long): String {
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
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(startMs + PROFILE_VIDEO_MAX_SECONDS * 1000L)
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
