package eu.tintera.plume.tv

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bookmark_filled_24dp
import eu.tintera.plume.resources.saved_messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Fotka chatu v kruhu, dokud není stažená (nebo chat žádnou nemá) iniciála. Uložené zprávy
 * ([savedMessages]) mají místo fotky účtu záložku, jako na telefonu. Odběr [FileRepository.file]
 * stažení spustí sám; dekóduje se mimo hlavní vlákno rovnou zmenšené — levné televize mají
 * paměti málo.
 */
@Composable
fun ChatAvatar(fileId: Int?, title: String, size: Dp, modifier: Modifier = Modifier, savedMessages: Boolean = false) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val photo = if (savedMessages) null else rememberImage(fileId, sizePx)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (savedMessages) {
            Icon(
                painterResource(Res.drawable.bookmark_filled_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxSize(0.55f),
            )
            return@Box
        }
        photo?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text(
                title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
    }
}

/** Obrázek souboru TDLib zmenšený na [targetPx] (kratší strana); `null`, dokud se nestáhne. */
@Composable
fun rememberImage(fileId: Int?, targetPx: Int): ImageBitmap? {
    val files = koinInject<FileRepository>()
    val image by produceState<ImageBitmap?>(initialValue = null, fileId, targetPx) {
        if (fileId == null) return@produceState
        files.file(fileId).collect { state ->
            if (state is FileState.Ready && value == null) value = withContext(Dispatchers.IO) { decodeScaled(state.path, targetPx) }
        }
    }
    return image
}

/** Dekóduje obrázek zmenšený (mocninou dvou) tak, aby kratší strana byla aspoň [targetPx]. */
private fun decodeScaled(path: String, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/**
 * Id chatu Uložené zprávy (= id přihlášeného účtu), `null` dokud ho TDLib neohlásil. V TDLib
 * nesou jméno a fotku vlastního účtu; všichni klienti je ukazují jako „Uložené zprávy".
 */
@Composable
fun savedMessagesChatId(): Long? {
    val id by koinInject<ChatRepository>().savedMessagesChatId.collectAsState()
    return id
}

/** Název chatu, jak ho má vidět uživatel: Uložené zprávy svým jménem, ne jménem účtu. */
@Composable
fun chatTitle(chatId: Long, title: String, savedMessagesId: Long?): String =
    if (chatId == savedMessagesId) stringResource(Res.string.saved_messages) else title
