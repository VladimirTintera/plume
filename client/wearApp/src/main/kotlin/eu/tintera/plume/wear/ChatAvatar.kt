package eu.tintera.plume.wear

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Fotka chatu v kruhu, dokud není stažená (nebo chat žádnou nemá) iniciála. Stahuje se malá
 * varianta (`ChatSummary.avatarFileId`, odběr [FileRepository.file] stažení spustí sám)
 * a dekóduje se mimo hlavní vlákno rovnou zmenšená na velikost avataru — hodinky mají málo paměti.
 */
@Composable
fun ChatAvatar(fileId: Int?, title: String, size: Dp, modifier: Modifier = Modifier) {
    val files = koinInject<FileRepository>()
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val photo by produceState<ImageBitmap?>(initialValue = null, fileId) {
        if (fileId == null) return@produceState
        files.file(fileId).collect { state ->
            if (state is FileState.Ready) value = withContext(Dispatchers.IO) { decodeScaled(state.path, sizePx) }
        }
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        photo?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text(
                title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
    }
}

/** Dekóduje obrázek zmenšený (mocninou dvou) tak, aby kratší strana byla aspoň [targetPx]. */
private fun decodeScaled(path: String, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
