package eu.tintera.plume.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.VideoStoryboard
import eu.tintera.plume.core.video.StoryboardFrame
import eu.tintera.plume.core.video.frameAt
import eu.tintera.plume.core.video.parseStoryboardMap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Storyboard připravený ke kreslení: dlaždice z mapy a celý obrázek v původní velikosti. */
class TvStoryboard(val frames: List<StoryboardFrame>, val image: ImageBitmap, val tileWidth: Int, val tileHeight: Int)

/**
 * Stáhne a přečte storyboard videa (`MessageVideo.storyboards`, jen u delších videí): mapu
 * (`parseStoryboardMap` v `core`, sdílená s telefonem) a obrázek s dlaždicemi. Dokud nejsou oba,
 * je `null` a přetáčení je bez náhledu, jen s časem.
 *
 * Obrázek se dekóduje v PŮVODNÍ velikosti (`rememberImage` s nekonečným cílem nezmenšuje):
 * zmenšený by posunul souřadnice dlaždic z mapy. Je jeden pro celé video, takže přetáčení jen
 * posouvá výřez.
 */
@Composable
fun rememberTvStoryboard(storyboard: VideoStoryboard?): TvStoryboard? {
    val files = koinInject<FileRepository>()
    val frames by produceState<List<StoryboardFrame>?>(null, storyboard) {
        value = null
        if (storyboard == null) return@produceState
        val path = files.file(storyboard.mapFileId).filterIsInstance<FileState.Ready>().first().path
        value = withContext(Dispatchers.IO) {
            runCatching { parseStoryboardMap(File(path).readText()) }.getOrDefault(emptyList())
        }
    }
    val image = rememberImage(storyboard?.imageFileId, Int.MAX_VALUE)
    val loadedFrames = frames?.takeIf { it.isNotEmpty() } ?: return null
    if (image == null) return null
    return TvStoryboard(loadedFrames, image, storyboard!!.tileWidth, storyboard.tileHeight)
}

/**
 * Náhled nad posuvníkem při přetáčení: snímek ze storyboardu (je-li) a pod ním čas, kam se
 * skočí. Do posuvníku ho klade [seekPreviewAbove]: stojí nad místem, kam se míří, a nevyjede
 * z jeho šířky.
 */
@Composable
fun SeekPreview(storyboard: TvStoryboard?, positionMs: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (storyboard != null) {
            val frame = storyboard.frames.frameAt(positionMs)
            if (frame != null) {
                val height = SEEK_PREVIEW_WIDTH * storyboard.tileHeight / storyboard.tileWidth.coerceAtLeast(1)
                val shape = RoundedCornerShape(12.dp)
                Canvas(
                    Modifier
                        .size(SEEK_PREVIEW_WIDTH, height)
                        .clip(shape)
                        .background(Color.Black)
                        .border(2.dp, Color.White, shape),
                ) {
                    drawImage(
                        image = storyboard.image,
                        srcOffset = IntOffset(frame.left, frame.top),
                        srcSize = IntSize(storyboard.tileWidth, storyboard.tileHeight),
                        dstSize = size.roundToIntSize(),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = Color.Black,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * Položí obsah NAD prvek (posuvník) do místa [fraction] jeho šířky, vystředěný a sevřený do
 * jeho okrajů, s mezerou [gap]. Obsah se měří bez omezení a místo v rozvržení nebere — posuvník
 * kvůli náhledu nezmění výšku a řádek s časy neposkočí.
 */
fun Modifier.seekPreviewAbove(fraction: Float, gap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    val width = constraints.maxWidth
    layout(0, 0) {
        val x = (fraction * width - placeable.width / 2f).toInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
        placeable.place(x, -placeable.height - gap.roundToPx())
    }
}

/** Šířka snímku při přetáčení (na TV 540 dp výšky: snímek 16 : 9 kolem 135 dp). */
private val SEEK_PREVIEW_WIDTH = 240.dp
