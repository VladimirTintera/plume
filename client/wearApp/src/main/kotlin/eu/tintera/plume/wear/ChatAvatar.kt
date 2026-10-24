package eu.tintera.plume.wear

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bookmark_filled_24dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Fotka chatu v kruhu, dokud není stažená (nebo chat žádnou nemá) iniciála. Uložené zprávy
 * ([savedMessages]) mají místo fotky účtu záložku, jako na telefonu. Stahuje se malá
 * varianta (`ChatSummary.avatarFileId`, odběr [FileRepository.file] stažení spustí sám)
 * a dekóduje se mimo hlavní vlákno rovnou zmenšená na velikost avataru — hodinky mají málo paměti.
 */
@Composable
fun ChatAvatar(fileId: Int?, title: String, size: Dp, modifier: Modifier = Modifier, savedMessages: Boolean = false) {
    val files = koinInject<FileRepository>()
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val photo by produceState<ImageBitmap?>(initialValue = null, fileId, savedMessages) {
        if (fileId == null || savedMessages) return@produceState
        files.file(fileId).collect { state ->
            if (state is FileState.Ready) value = withContext(Dispatchers.IO) { decodeScaled(state.path, sizePx) }
        }
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (savedMessages) {
            Icon(
                painterResource(Res.drawable.bookmark_filled_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxSize(0.6f),
            )
            return@Box
        }
        photo?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text(
                title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
    }
}
