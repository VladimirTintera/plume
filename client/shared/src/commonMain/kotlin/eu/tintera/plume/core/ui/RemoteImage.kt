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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    // Podložka, než dorazí obrázek. Průhlednou chce obsah s vlastním tvarem — samolepka
    // v rámečku vypadá jako obrázek, ne jako samolepka.
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val state = rememberFileState(fileId)
    val thumbnailState = rememberFileState(thumbnailFileId)

    val readyPath = (state as? FileState.Ready)?.path
    val thumbnailPath = (thumbnailState as? FileState.Ready)?.path

    SideEffect {
        readyPath?.let { knownPaths[fileId] = it }
        if (thumbnailFileId != null) thumbnailPath?.let { knownPaths[thumbnailFileId] = it }
    }

    // Dokud není ostrá verze, kreslí se náhled. Jakmile dorazí, Crossfade je prolne.
    // Poslední známá cesta je až poslední záloha — pokryje ten jeden snímek, než odpoví flow.
    val path = readyPath
        ?: thumbnailPath
        ?: knownPaths[fileId]
        ?: thumbnailFileId?.let { knownPaths[it] }

    Box(
        modifier.background(background),
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

/**
 * Poslední známá cesta k souboru.
 *
 * [rememberFileState] startuje vždycky na [FileState.Pending] — flow odpovídá až z korutiny, takže
 * každý nově složený [RemoteImage] proletí aspoň jedním snímkem bez obrázku. U sdíleného prvku ze
 * seznamu do konverzace je to vidět: avatar odletí jako prázdný kruh a obrázek naskočí až na místě.
 *
 * Mapa se čte i zapisuje výhradně z kompozice (hlavní vlákno), takže nepotřebuje zámek. Cesty
 * v TDLib jednou hotovému souboru nemizí, takže se za běhu nic nezneplatňuje; roste to o jeden
 * krátký řetězec na zobrazený soubor. Výjimka je odhlášení, viz [forgetKnownImagePaths].
 */
private val knownPaths = mutableMapOf<Int, String>()

/**
 * Zahodí známé cesty. `fileId` platí jen v rámci jedné databáze TDLib — po odhlášení a přihlášení
 * by pod stejným id visel soubor předchozího účtu. Volat z kompozice (kvůli vláknu).
 */
fun forgetKnownImagePaths() = knownPaths.clear()
