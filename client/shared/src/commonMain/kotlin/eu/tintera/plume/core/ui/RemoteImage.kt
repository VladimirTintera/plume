package eu.tintera.plume.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject

/**
 * Obrázek ze souboru spravovaného TDLib.
 *
 * TDLib nedává URL — soubor se musí nejdřív stáhnout do jeho vlastního adresáře a teprve pak je
 * známá cesta. Odběr stavu souboru sám stahování spustí, takže volající nic neinicializuje.
 *
 * [thumbnailFileId] je menší varianta téhož obrázku. Stáhne se výrazně dřív a slouží jako náhled,
 * než dorazí ostrá verze — bez toho bliká prázdné místo i několik sekund.
 */
@Composable
fun RemoteImage(
    fileId: Int,
    modifier: Modifier = Modifier,
    thumbnailFileId: Int? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val state = rememberFileState(fileId)
    val thumbnailState = rememberFileState(thumbnailFileId)

    // Dokud není ostrá verze, kreslí se náhled. Jakmile dorazí, Crossfade je prolne.
    val path = (state as? FileState.Ready)?.path ?: (thumbnailState as? FileState.Ready)?.path

    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = path, label = "remote-image") { current ->
            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Průběh se schválně nezobrazuje číselně — u náhledů by to byl neklid.
                    if (state is FileState.Downloading) LoadingIndicator(Modifier.size(24.dp))
                }
            } else {
                AsyncImage(
                    // Coil potřebuje schéma; holá cesta by se vyhodnotila jako relativní URI.
                    model = "file://$current",
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Stav souboru, nebo [FileState.Pending] když id není.
 *
 * Struktura kompozice musí zůstat stejná i pro `null` — proto se `remember` a `collectAsState`
 * volají vždycky a nulovost se řeší až uvnitř zapamatovaného Flow.
 */
@Composable
fun rememberFileState(fileId: Int?): FileState {
    val repository = koinInject<FileRepository>()
    val flow = remember(repository, fileId) {
        if (fileId == null) flowOf(FileState.Pending) else repository.file(fileId)
    }
    val state by flow.collectAsStateWithLifecycle(initialValue = FileState.Pending)
    return state
}
