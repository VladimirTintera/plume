package eu.tintera.plume.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.download_24dp
import eu.tintera.plume.resources.media_download
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.Minithumbnail
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
 *
 * [placeholder] je ještě o krok dřív: rozmazaný náhled přímo ze zprávy, bez stahování. Kreslí
 * se místo holé podložky, dokud není ani [thumbnailFileId].
 *
 * [autoDownload] `false` (automatické stahování podle sítě, [LocalAutoDownload]): obrázek ani
 * zmenšenina se samy nestáhnou, jen se ukážou, když už na disku jsou. Jinak je vidět rozmazaný
 * náhled s tlačítkem stažení; ťuknutí stáhne obojí. Stahuje-li soubor někdo jiný (prohlížeč),
 * běží lesk jako obvykle.
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
    autoDownload: Boolean = true,
) {
    // Ťuknutí na stažení platí pro tenhle soubor; jiný soubor na tomtéž místě (recyklovaná
    // položka seznamu) začíná zase podle nastavení.
    var requested by remember(fileId) { mutableStateOf(false) }
    val download = autoDownload || requested
    val state = rememberFileState(fileId, download)
    val thumbnailState = rememberFileState(thumbnailFileId, download)

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
                // Průběh se schválně nezobrazuje číselně — u náhledů by to byl neklid.
                val shimmer = state is FileState.Downloading && (placeholder != null || background.alpha > 0f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (shimmer) Modifier.loadingShimmer(onPhoto = placeholder != null) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (placeholder != null) BlurredPlaceholder(placeholder, contentScale)
                    if (!download && state !is FileState.Downloading) {
                        DownloadButton(onClick = { requested = true })
                    }
                }
            } else {
                // Zmenšenina místo ostré verze, která se teprve stahuje (prohlížeč médií nad
                // velkou fotkou): bez lesku by stála, jako by víc nebylo. Náhled nese jen fotka,
                // takže pruh je vždycky ten nad fotkou.
                val sharpening = current != readyPath && state is FileState.Downloading
                AsyncImage(
                    // Coil potřebuje schéma; holá cesta by se vyhodnotila jako relativní URI.
                    model = localImage(current),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (sharpening) Modifier.loadingShimmer(onPhoto = true) else Modifier),
                )
            }
        }
    }
}

/**
 * Tlačítko stažení přes rozmazaný náhled: tmavý kruh s bílou šipkou, jako „přehrát" u videa —
 * leží přes fotku, takže barvy z motivu by na ní nebyly čitelné.
 */
@Composable
private fun DownloadButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.media_download)
    Box(
        Modifier
            .size(DOWNLOAD_BUTTON_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = DOWNLOAD_BUTTON_SCRIM))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(Res.drawable.download_24dp),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

private val DOWNLOAD_BUTTON_SIZE = 48.dp
private const val DOWNLOAD_BUTTON_SCRIM = 0.5f

/**
 * Jen rozmazaný náhled ze zprávy, BEZ stahování — pro obsah, který má zůstat skrytý (spoiler
 * v citaci). [RemoteImage] by si stáhl i ostrou verzi a po chvíli ji prolnul.
 */
@Composable
fun BlurredThumbnail(placeholder: Minithumbnail, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        BlurredPlaceholder(placeholder, ContentScale.Crop)
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
 * Světlý pruh, který šikmo přejíždí přes načítaný obrázek.
 *
 * **Místo indikátoru, ne vedle něj.** Nad rozmazaným náhledem byl `ContainedLoadingIndicator`
 * — barevný disk, který k obrázku nepatřil a vypadal jako nálepka. Rozmazání samo říká „tady
 * bude obrázek", jen nepozná, jestli se ještě něco děje; pruh to dodá, aniž by přes fotku
 * ležel cizí prvek. Kreslí se jen během stahování, takže stojící rozmazání = nic se nestahuje.
 *
 * Nad fotkou je pruh bílý (stejné pravidlo jako ovládání v prohlížeči médií), na holé podložce
 * v barvě popředí plochy — bílá by v tmavém motivu svítila. Na průhledné podložce bez náhledu
 * (samolepka) se nekreslí vůbec: pruh by tam přejížděl přes prázdno jako světlý obdélník.
 *
 * Průběh se čte až při kreslení, animace nic nerekomponuje. Pod [MIN_SHIMMER] (malý avatar)
 * se nekreslí — seznam plný blikajících teček by byl jen šum.
 */
@Composable
private fun Modifier.loadingShimmer(onPhoto: Boolean): Modifier {
    val highlight = if (onPhoto) {
        Color.White.copy(alpha = SHIMMER_ON_PHOTO_ALPHA)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = SHIMMER_ON_SURFACE_ALPHA)
    }
    val progress = rememberInfiniteTransition(label = "image-shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = SHIMMER_PERIOD_MS
                0f at 0
                1f at SHIMMER_SWEEP_MS using FastOutSlowInEasing
            },
        ),
        label = "image-shimmer-progress",
    )
    return drawWithContent {
        drawContent()
        if (size.minDimension < MIN_SHIMMER.toPx()) return@drawWithContent
        // Pás je široký podle kratší strany, sklon dává posun konce gradientu dolů. Dráha začíná
        // i končí celým pásem mimo plochu, jinak by na krajích naskakoval a mizel.
        val band = size.minDimension * SHIMMER_BAND_FRACTION
        val slant = size.height * SHIMMER_SLANT
        val x = -band - slant + progress.value * (size.width + 2 * band + slant)
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x, 0f),
                end = Offset(x + 2 * band, band * SHIMMER_SLANT * 2),
            ),
        )
    }
}

private const val SHIMMER_ON_PHOTO_ALPHA = 0.45f
private const val SHIMMER_ON_SURFACE_ALPHA = 0.1f
private const val SHIMMER_PERIOD_MS = 1600
private const val SHIMMER_SWEEP_MS = 1100
private const val SHIMMER_BAND_FRACTION = 0.5f
private const val SHIMMER_SLANT = 0.4f
private val MIN_SHIMMER = 24.dp

/**
 * Stav souboru, nebo [FileState.Pending] když id není. S [download] `false` se jen sleduje,
 * stahování se nespustí ([FileRepository.observe]).
 *
 * Struktura kompozice musí zůstat stejná i pro `null` — proto se `remember` a `collectAsState`
 * volají vždycky a nulovost se řeší až uvnitř zapamatovaného Flow.
 */
@Composable
fun rememberFileState(fileId: Int?, download: Boolean = true): FileState {
    val repository = koinInject<FileRepository>()
    val flow = remember(repository, fileId, download) {
        when {
            fileId == null -> flowOf(FileState.Pending)
            download -> repository.file(fileId)
            else -> repository.observe(fileId)
        }
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
