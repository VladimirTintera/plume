package eu.tintera.plume.wear

import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.core.telegram.Minithumbnail
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
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
    MessageImage(photo.fileId, photo.minithumbnail, photo.width, photo.height, onClick, modifier)
}

/**
 * Video jako náhled (`thumbnailFileId`, do 320 px) se značkou přehrát a délkou. Přehrávání na
 * hodinkách není — dekodér, zvuk do reproduktoru hodinek a stahování desítek megabajtů by
 * vydaly za samostatnou funkci; náhled aspoň řekne, co ve zprávě je. Ťuknutí patří bublině
 * (reakce), ne náhledu.
 */
@Composable
fun MessageVideo(video: MessageMedia.Video, modifier: Modifier = Modifier) {
    MessageImage(video.thumbnailFileId, video.minithumbnail, video.width, video.height, onClick = null, modifier = modifier) {
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, modifier = Modifier.size(14.dp))
            Text(formatDuration(video.durationSeconds), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Obrázek v poměru stran přes šířku bubliny: minináhled hned, soubor [fileId] po stažení. */
@Composable
private fun MessageImage(
    fileId: Int?,
    minithumbnail: Minithumbnail?,
    sourceWidth: Int,
    sourceHeight: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val ratio = if (sourceWidth > 0 && sourceHeight > 0) sourceHeight.toFloat() / sourceWidth else 1f
        val width = if (ratio > MAX_HEIGHT_RATIO) maxWidth * (MAX_HEIGHT_RATIO / ratio) else maxWidth
        val height = width * ratio
        val widthPx = with(LocalDensity.current) { width.roundToPx() }
        val bitmap = fileId?.let { rememberPhotoBitmap(it, widthPx) }
        val preview = remember(minithumbnail) {
            minithumbnail?.let { BitmapFactory.decodeByteArray(it.jpeg, 0, it.jpeg.size)?.asImageBitmap() }
        }
        Box(
            Modifier
                .size(width, height)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            (bitmap ?: preview)?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width, height))
            }
            overlay()
        }
    }
}

/**
 * Samolepka bez bubliny: statická (`fileId`, WebP) rovnou, animovaná (TGS) a videosamolepka
 * (WebM) svým náhledem (`thumbnailFileId`) — přehrávání animací by na hodinkách stálo baterii
 * i výkon. Bez stažení emoji samolepky, ať místo prázdna něco je.
 */
@Composable
fun MessageSticker(sticker: MessageMedia.Sticker, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { STICKER_SIZE.roundToPx() }
    val bitmap = (sticker.fileId ?: sticker.thumbnailFileId)?.let { rememberPhotoBitmap(it, sizePx) }
    Box(modifier.size(STICKER_SIZE), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = sticker.emoji, contentScale = ContentScale.Fit, modifier = Modifier.size(STICKER_SIZE))
        } else {
            Text(sticker.emoji, style = MaterialTheme.typography.displayMedium)
        }
    }
}

/** Video, které hodinky ukážou náhledem; skryté (spoiler, citlivé, tajné, mizející) zůstává slovem. */
fun MessageMedia.shownVideo(): MessageMedia.Video? =
    (this as? MessageMedia.Video)?.takeUnless {
        it.hasSpoiler || it.isSensitive || it.isSecret || it.selfDestructType != null ||
            (it.thumbnailFileId == null && it.minithumbnail == null)
    }

/** Samolepka, kterou hodinky ukážou; citlivá zůstává slovem (odkrytí ťuknutím hodinky nemají). */
fun MessageMedia.shownSticker(): MessageMedia.Sticker? = (this as? MessageMedia.Sticker)?.takeUnless { it.isSensitive }

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

private val STICKER_SIZE = 96.dp
