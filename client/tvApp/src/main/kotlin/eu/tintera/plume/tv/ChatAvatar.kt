package eu.tintera.plume.tv

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bookmark_filled_24dp
import eu.tintera.plume.resources.saved_messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Fotka chatu v tvaru podle druhu chatu ([shape], [avatarShape]), dokud není stažená (nebo chat
 * žádnou nemá) iniciála. Uložené zprávy ([savedMessages]) mají místo fotky účtu záložku, jako na telefonu. Odběr [FileRepository.file]
 * stažení spustí sám; dekóduje se mimo hlavní vlákno rovnou zmenšené — levné televize mají
 * paměti málo.
 */
@Composable
fun ChatAvatar(
    fileId: Int?,
    title: String,
    size: Dp,
    modifier: Modifier = Modifier,
    savedMessages: Boolean = false,
    /** Výchozí kruh je člověk — i odesílatel ve skupině; chat dá tvar svého druhu ([avatarShape]). */
    shape: Shape = CircleShape,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val photo = if (savedMessages) null else rememberImage(fileId, sizePx)
    Box(
        modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
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

/**
 * Tvar avataru podle druhu chatu, stejný jako na telefonu (`avatarShape` v `core/ui/Avatar.kt`
 * modulu `:client:shared`, kam televize nesahá): člověk kruh, skupina čtyřlaločná „sušenka",
 * kanál zaoblený čtverec. Druh se pozná dřív, než se dočte název. Tvar nese DRUH, ne stav —
 * fokus má dál svůj rámeček a zvětšení, avatar se nemorfuje.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatKind.avatarShape(): Shape = when (this) {
    ChatKind.Private -> CircleShape
    ChatKind.Group -> MaterialShapes.Cookie4Sided.toShape()
    ChatKind.Channel -> MaterialShapes.Square.toShape()
}

/** Druh chatu z informací konverzace (hlavička): kanál se počítá i do `isGroup`. */
val ChatInfo.kind: ChatKind
    get() = when {
        isChannel -> ChatKind.Channel
        isGroup -> ChatKind.Group
        else -> ChatKind.Private
    }

/**
 * Obrázek souboru TDLib zmenšený na [targetPx] (kratší strana); `null`, dokud se nestáhne.
 *
 * **`produceState` hodnotu při změně klíče NEVYNULUJE** (stav je `remember` bez klíčů, znovu se
 * spustí jen producent). Náhled chatu a pozadí proto ukazovaly obrázek prvního zaostřeného chatu
 * u všech dalších (ověřeno na emulátoru). Producent ji vynuluje sám a čeká na hotový soubor.
 */
@Composable
fun rememberImage(fileId: Int?, targetPx: Int): ImageBitmap? {
    val files = koinInject<FileRepository>()
    val image by produceState<ImageBitmap?>(initialValue = null, fileId, targetPx) {
        value = null
        if (fileId == null) return@produceState
        val ready = files.file(fileId).first { it is FileState.Ready } as FileState.Ready
        value = withContext(Dispatchers.IO) { decodeScaled(ready.path, targetPx) }
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
