package eu.tintera.plume.features.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.VideoStoryboard
import eu.tintera.plume.core.ui.localImage
import eu.tintera.plume.core.video.StoryboardFrame
import eu.tintera.plume.core.video.frameAt
import eu.tintera.plume.core.video.parseStoryboardMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.compose.koinInject

/** Storyboard připravený ke kreslení: dlaždice z mapy a obrázek na disku. */
internal class LoadedStoryboard(
    val frames: List<StoryboardFrame>,
    val imagePath: String,
    val tileWidth: Int,
    val tileHeight: Int,
)

/**
 * Stáhne a přečte storyboard, až má smysl ([load] — video hraje na celé obrazovce). Sousední
 * stránky a galerie nic nestahují. Mapa a obrázek jsou dva soubory; dokud nejsou oba, je `null`
 * a posuvník je bez náhledu jako dřív.
 */
@Composable
internal fun rememberStoryboard(storyboard: VideoStoryboard?, load: Boolean): LoadedStoryboard? {
    val repository = koinInject<FileRepository>()
    val loaded by produceState<LoadedStoryboard?>(null, storyboard, load) {
        if (storyboard == null || !load || value != null) return@produceState
        val mapPath = repository.file(storyboard.mapFileId).filterIsInstance<FileState.Ready>().first().path
        val frames = withContext(Dispatchers.Default) {
            runCatching { FileSystem.SYSTEM.read(mapPath.toPath()) { readUtf8() } }
                .map(::parseStoryboardMap)
                .getOrDefault(emptyList())
        }
        if (frames.isEmpty()) return@produceState
        val imagePath = repository.file(storyboard.imageFileId).filterIsInstance<FileState.Ready>().first().path
        value = LoadedStoryboard(frames, imagePath, storyboard.tileWidth, storyboard.tileHeight)
    }
    return loaded
}

/**
 * Snímek videa v čase [positionMs] — výřez dlaždice ze storyboardu, nad posuvníkem při tahu.
 *
 * Kreslí se celý obrázek posunutý a ořezaný na dlaždici, ne výřez dekódovaný zvlášť: obrázek
 * je jeden pro celé video a Coil ho drží v paměti, takže tah po posuvníku jen posouvá kreslení.
 */
@Composable
internal fun StoryboardThumb(storyboard: LoadedStoryboard, positionMs: Long, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(storyboard.imagePath) {
        // Původní velikost: zmenšený obrázek by posunul souřadnice dlaždic z mapy.
        ImageRequest.Builder(context).data(localImage(storyboard.imagePath)).size(coil3.size.Size.ORIGINAL).build()
    }
    val painter = rememberAsyncImagePainter(request)
    val frame = storyboard.frames.frameAt(positionMs) ?: return
    val shape = RoundedCornerShape(8.dp)
    Canvas(
        modifier
            .clip(shape)
            // Dokud se obrázek nenačte, je vidět aspoň tmavý rámeček, ne díra.
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), shape),
    ) {
        val intrinsic = painter.intrinsicSize
        if (intrinsic.isUnspecified() || intrinsic.width <= 0f) return@Canvas
        val scale = size.width / storyboard.tileWidth
        clipRect {
            translate(-frame.left * scale, -frame.top * scale) {
                with(painter) { draw(Size(intrinsic.width * scale, intrinsic.height * scale)) }
            }
        }
    }
}

private fun Size.isUnspecified(): Boolean = this == Size.Unspecified
