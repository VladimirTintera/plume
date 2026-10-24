package eu.tintera.plume.wear

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Fotka, kterou hodinky v bublině ukážou. Spoiler, citlivý obsah, tajný chat a mizející fotka
 * zůstávají slovem („Fotka"): odkrývání ani časovač hodinky nemají a ukázat je rovnou by
 * obešlo, co odesílatel (nebo server) chtěl.
 */
fun MessageMedia.shownPhoto(): MessageMedia.Photo? =
    (this as? MessageMedia.Photo)?.takeUnless { it.hasSpoiler || it.isSensitive || it.isSecret || it.selfDestructType != null }

/**
 * Fotka v bublině přes celou šířku bubliny, v poměru stran fotky. Hned se ukáže minináhled
 * (malý JPEG přímo ve zprávě, rozmazaný), po stažení varianta pro bublinu (`fileId`) —
 * dekódovaná rovnou zmenšená na šířku, hodinky mají málo paměti. Plnou variantu (`fullFileId`)
 * hodinky nestahují: displej má pár set pixelů a stahovala by se megabajty.
 *
 * Vysoká fotka se zastropuje na [MAX_HEIGHT_RATIO] šířky a zúží se s ní (ne ořízne) — viz
 * „Výšku obrázku nepočítej `aspectRatio`" v CLAUDE.md.
 */
@Composable
fun MessagePhoto(photo: MessageMedia.Photo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val ratio = if (photo.width > 0 && photo.height > 0) photo.height.toFloat() / photo.width else 1f
        val width = if (ratio > MAX_HEIGHT_RATIO) maxWidth * (MAX_HEIGHT_RATIO / ratio) else maxWidth
        val height = width * ratio
        val widthPx = with(LocalDensity.current) { width.roundToPx() }
        val bitmap = rememberPhotoBitmap(photo.fileId, widthPx)
        val preview = remember(photo.minithumbnail) {
            photo.minithumbnail?.let { BitmapFactory.decodeByteArray(it.jpeg, 0, it.jpeg.size)?.asImageBitmap() }
        }
        Box(
            Modifier
                .size(width, height)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
        ) {
            (bitmap ?: preview)?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width, height))
            }
        }
    }
}

/** Stáhne soubor ([FileRepository.file] stažení spustí sám) a dekóduje ho zmenšený na [targetPx]. */
@Composable
fun rememberPhotoBitmap(fileId: Int, targetPx: Int): ImageBitmap? {
    val files = koinInject<FileRepository>()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, fileId, targetPx) {
        files.file(fileId).collect { state ->
            if (state is FileState.Ready && value == null) value = withContext(Dispatchers.IO) { decodeScaled(state.path, targetPx) }
        }
    }
    return bitmap
}

/** Dekóduje obrázek zmenšený (mocninou dvou) tak, aby kratší strana byla aspoň [targetPx]. */
fun decodeScaled(path: String, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private const val MAX_HEIGHT_RATIO = 1.25f
