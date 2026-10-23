package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.FileState
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionCache
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.gzip
import okio.use

/**
 * Animovaná samolepka ve formátu `.tgs`.
 *
 * `.tgs` je Lottie animace (JSON) zabalená gzipem. Compottie umí Lottie, ale gzip ne, takže se
 * soubor rozbalí sám přes Okio a do Compottie jde až JSON.
 *
 * [placeholder] se kreslí, dokud animace není připravená — soubor se stahuje, parsuje, nebo se
 * načíst nepodařil. Má to být **statický náhled** samolepky: ten odpovídá prvnímu snímku, takže
 * přechod na animaci není skok z prázdna. Rozbitá animace tak zůstane aspoň obrázkem.
 *
 * Kreslí se mimo hlavní vlákno do bitmap ([RenderedSticker]) a další snímek čeká, až se
 * předchozí vykreslí — samolepka mimo obrazovku ani v aplikaci na pozadí nic nepočítá.
 */
@Composable
fun AnimatedSticker(
    fileId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /**
     * `false` = jen první snímek. Kde je vedle sebe víc sad (obálky v záložkách výběru, ukázky
     * v trendech), by hrající samolepky stály víc, než přinesou; hraje se až tam, kde má smysl.
     */
    isPlaying: Boolean = true,
    /** Jak animace běží; výchozí je smyčka (viz [StickerPlayback]). */
    playback: StickerPlayback = StickerPlayback.Loop,
    /**
     * Nejvyšší snímková frekvence smyčky. Samolepky mají 60; mřížka výběru a emoji v textu,
     * kde jich hraje desítky naráz, berou [REDUCED_STICKER_FRAME_RATE].
     */
    maxFrameRate: Int = FULL_STICKER_FRAME_RATE,
    /** Doběhla animace hraná jednou ([StickerPlayback.Once]). */
    onFinished: () -> Unit = {},
    /**
     * Snímek, na kterém se u přehrání [StickerPlayback.Once] zavolá [onMarker] — třeba chvíle,
     * kdy hozený míč propadne košem. `0` = nic se nehlásí.
     */
    markerFrame: Int = 0,
    onMarker: () -> Unit = {},
    placeholder: @Composable () -> Unit,
) {
    val path = (rememberFileState(fileId) as? FileState.Ready)?.path

    // Spec se skládá i bez cesty, ať je struktura kompozice pořád stejná; bez ní se nic nenačítá.
    val composition = rememberLottieComposition(
        spec = TgsSpec(path),
        cache = stickerCompositions,
    ).value

    RenderedSticker(
        composition = composition,
        playback = playback,
        isPlaying = isPlaying,
        maxFrameRate = maxFrameRate,
        onFinished = onFinished,
        markerFrame = markerFrame,
        onMarker = onMarker,
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
    )
}

/** Jak se animace samolepky přehrává. */
enum class StickerPlayback {
    /** Pořád dokola, jako samolepka v konverzaci. */
    Loop,

    /** Jednou a zůstane stát na posledním snímku — hod kostkou, který padl právě teď. */
    Once,

    /** Rovnou poslední snímek — hod kostkou z historie, u kterého je vidět jen výsledek. */
    LastFrame,
}

/**
 * Lottie ze souboru `.tgs`.
 *
 * `data class` kvůli rovnosti: `rememberLottieComposition` si výsledek pamatuje podle specu, takže
 * nová instance se stejnou cestou nesmí znamenat nové načtení při každé rekompozici.
 *
 * Klíč cache je cesta, ne otisk obsahu (jako u `JsonString`) — počítat MD5 z rozbaleného JSONu
 * jen kvůli klíči by stálo víc než to, co cache šetří.
 */
@Immutable
private data class TgsSpec(private val path: String?) : LottieCompositionSpec {

    override val key: String? = path?.let { "tgs:$it" }

    override suspend fun load(): LottieComposition {
        // Bez cesty (soubor se ještě stahuje) se nenačítá nic; výjimka jen ponechá placeholder.
        val file = checkNotNull(path) { "Soubor samolepky ještě není stažený." }
        return parsing.withPermit {
            val json = FileSystem.SYSTEM.source(file.toPath()).gzip().buffer().use { it.readUtf8() }
            LottieComposition.parse(json)
        }
    }
}

/**
 * Kolik samolepek se smí parsovat naráz. Parsování JSONu animace alokuje megabajty a nová mřížka
 * (přepnutá kategorie, sada) jich chce desítky v jednom okamžiku: naměřeno ~100 MB během vteřiny,
 * GC to nestíhal a vlákna čekala na blokující GC („WaitForGcToComplete blocked Alloc" ~120 ms),
 * hlavní vlákno taky. Po dvou se to rozloží a samolepky naskakují postupně ze svých náhledů.
 * Samolepka, která mezitím odjede z obrazovky, z fronty vypadne zrušením.
 */
private val parsing = Semaphore(PARALLEL_PARSES)

/**
 * Rozparsované animace samolepek.
 *
 * Vlastní cache místo výchozí `LocalLottieCache`, která drží jen deset animací pro celou aplikaci.
 * V konverzaci se jedna samolepka opakuje a při scrollu tam a zpátky by se každá rozbalovala
 * a parsovala znovu. Rozparsovaná samolepka má desítky kilobajtů, takže padesát je pořád málo.
 */
private val stickerCompositions = LottieCompositionCache(STICKER_CACHE_SIZE)

/**
 * Zahodí rozparsované animace. Klíčem je cesta k souboru a nový účet má vlastní databázi TDLib,
 * kde pod stejnou cestou může ležet jiná samolepka — stejný důvod jako u [forgetKnownImagePaths].
 */
suspend fun forgetStickerAnimations() = stickerCompositions.clear()

private const val STICKER_CACHE_SIZE = 50
private const val PARALLEL_PARSES = 2

/** Snímková frekvence samolepky, která hraje sama (bublina, náhled). */
const val FULL_STICKER_FRAME_RATE = 60

/** Snímková frekvence tam, kde hrají desítky samolepek naráz (mřížka výběru, emoji v textu). */
const val REDUCED_STICKER_FRAME_RATE = 30
