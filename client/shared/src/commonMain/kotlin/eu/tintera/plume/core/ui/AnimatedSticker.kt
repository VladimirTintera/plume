package eu.tintera.plume.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import eu.tintera.plume.core.telegram.FileState
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionCache
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.flow.first
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
 * Animace hraje ve smyčce jako v Telegramu. Zastavit mimo obrazovku ji není potřeba: položka
 * `LazyColumn` mimo viewport se z kompozice odebere, a s ní i hodiny animace.
 */
@Composable
fun AnimatedSticker(
    fileId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /**
     * `false` = jen první snímek. Ve výběru je vidět desítky samolepek naráz a nechat je hrát
     * všechny by znamenalo desítky animací v jednom snímku; hraje se až tam, kde má smysl.
     */
    isPlaying: Boolean = true,
    /** Jak animace běží; výchozí je smyčka (viz [StickerPlayback]). */
    playback: StickerPlayback = StickerPlayback.Loop,
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

    Box(modifier, contentAlignment = Alignment.Center) {
        if (composition == null) {
            placeholder()
        } else {
            Image(
                painter = when (playback) {
                    StickerPlayback.Loop -> rememberLottiePainter(
                        composition = composition,
                        isPlaying = isPlaying,
                        iterations = Compottie.IterateForever,
                    )
                    StickerPlayback.Once -> {
                        val state = animateLottieCompositionAsState(composition, iterations = 1)
                        val finished by rememberUpdatedState(onFinished)
                        // Konec se hlídá mimo kompozici: průběh se mění každý snímek a čtený
                        // v kompozici by ji s každým snímkem skládal znovu.
                        LaunchedEffect(state) {
                            snapshotFlow { state.isAtEnd }.first { it }
                            finished()
                        }
                        val marker by rememberUpdatedState(onMarker)
                        if (markerFrame > 0) {
                            LaunchedEffect(state, markerFrame) {
                                snapshotFlow {
                                    val frame = composition.startFrame +
                                        (composition.endFrame - composition.startFrame) * state.progress
                                    frame >= composition.startFrame + markerFrame || state.isAtEnd
                                }.first { it }
                                marker()
                            }
                        }
                        rememberLottiePainter(composition = composition, progress = { state.progress })
                    }
                    StickerPlayback.LastFrame ->
                        rememberLottiePainter(composition = composition, progress = { 1f })
                },
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
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
        val json = FileSystem.SYSTEM.source(file.toPath()).gzip().buffer().use { it.readUtf8() }
        return LottieComposition.parse(json)
    }
}

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
