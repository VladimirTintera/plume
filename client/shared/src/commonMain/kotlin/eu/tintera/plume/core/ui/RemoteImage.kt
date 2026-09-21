package eu.tintera.plume.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.Minithumbnail
import kotlinx.coroutines.flow.flowOf
import kotlin.math.min
import kotlin.math.roundToInt
import org.koin.compose.koinInject

/**
 * Obrázek ze souboru spravovaného TDLib.
 *
 * TDLib nedává URL — soubor se musí nejdřív stáhnout do jeho vlastního adresáře a teprve pak je
 * známá cesta. Odběr stavu souboru sám stahování spustí, takže volající nic neinicializuje.
 *
 * [thumbnailFileId] je menší varianta téhož obrázku. Stáhne se výrazně dřív a slouží jako náhled,
 * než dorazí ostrá verze — bez toho bliká prázdné místo i několik sekund.
 *
 * [placeholder] je ještě o krok dřív: rozmazaný náhled přímo ze zprávy, bez stahování. Kreslí
 * se místo holé podložky, dokud není ani [thumbnailFileId].
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
    placeholder: Minithumbnail? = null,
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
                    if (placeholder != null) BlurredPlaceholder(placeholder, contentScale)
                    // Průběh se schválně nezobrazuje číselně — u náhledů by to byl neklid.
                    if (state is FileState.Downloading) {
                        ContainedLoadingIndicator(Modifier.loadingIndicatorSize())
                    }
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
 * Rozmazaný náhled ze zprávy přes celou plochu.
 *
 * **`Modifier.blur`, ne Haze.** Obojí rozostří vlastní obsah na Androidu 12+ i na iOS, jenže na
 * starším Androidu Haze kreslí místo rozostření tónovanou plochu, kdežto `blur` se jen vypne —
 * a zbude náhled o čtyřiceti pixelech zvětšený bilineárně, který rozmazaně vypadá sám.
 * Okraj se ořezává (`Rectangle`), jinak by rozostření do stran vyprůhlednilo kraje.
 */
@Composable
private fun BlurredPlaceholder(placeholder: Minithumbnail, contentScale: ContentScale) {
    AsyncImage(
        model = placeholder.jpeg,
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier
            .fillMaxSize()
            .blur(PLACEHOLDER_BLUR, BlurredEdgeTreatment.Rectangle),
    )
}

private val PLACEHOLDER_BLUR = 16.dp

/**
 * Velikost indikátoru podle plochy obrázku: [INDICATOR_FRACTION] kratší strany, nejvýš
 * [MAX_INDICATOR]. Pevných 24 dp bylo uprostřed velké fotky tak malých, že to vypadalo jako
 * chyba vykreslení, ne načítání. Pod [MIN_INDICATOR] (malý avatar) se nekreslí vůbec — tam
 * stačí podložka a tečka by byla jen šum.
 */
private fun Modifier.loadingIndicatorSize() = layout { measurable, constraints ->
    val available = min(constraints.maxWidth, constraints.maxHeight)
    val side = (available * INDICATOR_FRACTION).roundToInt().coerceAtMost(MAX_INDICATOR.roundToPx())
    if (side < MIN_INDICATOR.roundToPx()) return@layout layout(0, 0) {}
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

private const val INDICATOR_FRACTION = 0.3f
private val MAX_INDICATOR = 64.dp
private val MIN_INDICATOR = 24.dp

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
