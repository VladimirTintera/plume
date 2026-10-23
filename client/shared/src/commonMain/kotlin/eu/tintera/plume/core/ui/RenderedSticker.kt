package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import co.touchlab.kermit.Logger
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlin.concurrent.Volatile
import kotlin.coroutines.ContinuationInterceptor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private val logger = Logger.withTag("RenderedSticker")

/**
 * Samolepka `.tgs` kreslená MIMO hlavní vlákno.
 *
 * Compottie kreslí Lottie vektorově, každý snímek znovu. Kreslený přímo v kompozici
 * (`Image(rememberLottiePainter(…))`) stál ten výpočet hlavní vlákno: mřížka pětadvaceti samolepek
 * zabrala 60–90 ms `Record View#draw()` na snímek a výběr samolepek drhnul při vyjíždění, přepnutí
 * kategorie i scrollu. Tady se painter kreslí na pozadí do bitmapy ([StickerFrameRenderer])
 * a hlavní vlákno jen položí hotovou bitmapu — jako oficiální klient s rlottie.
 *
 * - **Všechny samolepky kreslí JEDNO vlákno** ([stickerRenderer]). Compottie není pro kreslení
 *   z víc vláken naráz stavěné: má globální pracovní objekty (`PathMeasure` a cesty u ořezu cesty,
 *   matici u přechodů na iOS a desktopu) a vrstvy animace sdílí každý painter téže samolepky.
 *   Se třemi vlákny aplikace během vteřiny spadla nativně v `PathMeasure.setPath`.
 *   Proto jde přes tohle vlákno i jednorázové přehrání a poslední snímek, nejen smyčka — painter
 *   kreslený na hlavním vlákně by se s ním potkal.
 * - **Další snímek až po vykreslení předchozího** ([StickerFrames.awaitDrawn]). Samolepka, která
 *   se nekreslí — položka přednačtená mřížkou mimo viewport, aplikace na pozadí, okno pod jiným —
 *   po jednom snímku stojí a nic nepočítá. Viewport a lifecycle tak není potřeba hlídat zvlášť.
 * - **Snímková frekvence nejvýš [maxFrameRate]**, nikdy víc, než má animace sama (samolepky mají
 *   60). Mřížka a emoji v textu jedou na 30.
 * - **Čas běží podle hodin, ne podle snímků**: nestíhá-li vlákno, snímky se přeskočí a animace se
 *   nezpomalí. Zastavená smyčka ([isPlaying]) stojí na posledním snímku a rozběhne se odtamtud.
 *   Jednorázové přehrání se nezastavuje.
 * - **Placeholder zůstává ve stejném místě stromu**, dokud není první snímek — u mřížky se tak
 *   náhled po načtení animace neskládá znovu (viz `StickerPreview`).
 */
@Composable
internal fun RenderedSticker(
    composition: LottieComposition?,
    playback: StickerPlayback,
    isPlaying: Boolean,
    maxFrameRate: Int,
    onFinished: () -> Unit,
    markerFrame: Int,
    onMarker: () -> Unit,
    contentDescription: String?,
    modifier: Modifier,
    placeholder: @Composable () -> Unit,
) {
    // Průběh čte painter (`derivedStateOf` uvnitř Compottie), proto stav a ne obyčejná proměnná.
    // Zapisuje se jen uvnitř snímku vykreslování (viz [StickerFrameRenderer.render]).
    val progress = remember { mutableFloatStateOf(0f) }
    val painter = rememberLottiePainter(composition = composition, progress = { progress.floatValue })
    val frames = remember(composition) { StickerFrames() }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val playing by rememberUpdatedState(isPlaying)
    val finished by rememberUpdatedState(onFinished)
    val marker by rememberUpdatedState(onMarker)
    val density = LocalDensity.current

    LaunchedEffect(composition, area, playback) {
        if (composition == null || area.width <= 0 || area.height <= 0) return@LaunchedEffect
        // Painter vzniká z kompozice až v korutině Compottie; do té doby by kreslil prázdno a místo
        // náhledu by se na okamžik ukázala prázdná bitmapa. Nepřipravený hlásí rozměr 1 × 1.
        snapshotFlow { painter.intrinsicSize }.first { it != UNREADY_PAINTER }
        // Zpětná volání patří na vlákno kompozice, smyčka běží na [stickerRenderer].
        val ui = checkNotNull(coroutineContext[ContinuationInterceptor])
        val target = fitInto(composition, area)
        val clip = StickerClip(composition, maxFrameRate, markerFrame)
        withContext(stickerRenderer) {
            val renderer = StickerFrameRenderer(painter, progress, target, density)
            try {
                when (playback) {
                    StickerPlayback.LastFrame -> frames.publish(renderer.render(1f))
                    StickerPlayback.Loop -> clip.play(
                        loop = true,
                        frame = { at -> frames.awaitDrawn(frames.publish(renderer.render(at))) },
                        hold = {
                            if (playing) {
                                false
                            } else {
                                snapshotFlow { playing }.first { it }
                                true
                            }
                        },
                    )
                    StickerPlayback.Once -> {
                        clip.play(
                            loop = false,
                            frame = { at -> frames.awaitDrawn(frames.publish(renderer.render(at))) },
                            onMarker = { withContext(ui) { marker() } },
                        )
                        withContext(ui) { finished() }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                // Samolepka zůstane stát na posledním snímku, nebo na náhledu.
                logger.w(failure) { "Samolepku se nepodařilo vykreslit." }
            }
        }
    }

    Box(
        modifier
            .onSizeChanged { if (area.isFarFrom(it)) area = it }
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                role = Role.Image
            }
            // Vlastní vrstva: nový snímek překreslí jen samolepku, ne celou mřížku kolem ní.
            .graphicsLayer()
            .drawWithContent {
                // Čtení stavu až při kreslení: nový snímek nesloží znovu kompozici.
                val tick = frames.tick
                val frame = frames.front
                if (frame == null) {
                    drawContent()
                    return@drawWithContent
                }
                val scale = min(size.width / frame.width, size.height / frame.height)
                val w = (frame.width * scale).roundToInt()
                val h = (frame.height * scale).roundToInt()
                drawImage(
                    image = frame,
                    dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
                    dstSize = IntSize(w, h),
                )
                frames.markDrawn(tick)
            },
    ) {
        if (!frames.ready) placeholder()
    }
}

/**
 * Hotové snímky jedné samolepky. Bitmapy se střídají po třech (viz [StickerFrameRenderer]),
 * kreslí se vždy [front].
 */
@Stable
private class StickerFrames {
    @Volatile
    var front: ImageBitmap? = null
        private set
    var tick by mutableIntStateOf(0)
        private set
    var ready by mutableStateOf(false)
        private set

    /** Poslední vykreslený [tick]. Zapisuje kreslení na hlavním vlákně, čte smyčka na pozadí. */
    private val drawn = MutableStateFlow(0)

    /** Vrátí [tick] nového snímku. Volá jen smyčka vykreslování, z jednoho vlákna naráz. */
    fun publish(frame: ImageBitmap): Int {
        front = frame
        val next = Snapshot.withoutReadObservation { tick } + 1
        tick = next
        if (!ready) ready = true
        return next
    }

    fun markDrawn(tick: Int) {
        if (drawn.value < tick) drawn.value = tick
    }

    /** Počká, až se snímek [tick] opravdu nakreslí. */
    suspend fun awaitDrawn(tick: Int) {
        drawn.first { it >= tick }
    }
}

/**
 * Časování přehrávání: který průběh (0..1) kreslit a kdy. Čas běží podle hodin, ne podle snímků.
 */
private class StickerClip(composition: LottieComposition, maxFrameRate: Int, markerFrame: Int) {
    private val duration = composition.duration.inWholeNanoseconds.coerceAtLeast(1)
    private val frameTime =
        (1_000_000_000L / min(composition.frameRate.roundToInt(), maxFrameRate).coerceAtLeast(1)).nanoseconds

    /** Průběh, od kterého se hlásí značka (`0` = nehlásí se nic). */
    private val markerProgress = if (markerFrame > 0) {
        markerFrame / (composition.endFrame - composition.startFrame).coerceAtLeast(1f)
    } else {
        Float.NaN
    }

    /**
     * Hraje od začátku. Smyčka ([loop]) dokola, jinak skončí po vykreslení posledního snímku.
     * [hold] smí smyčku zastavit (vrátí `true`, když čekal); čas zastavení se nepočítá.
     */
    suspend fun play(
        loop: Boolean,
        frame: suspend (Float) -> Unit,
        hold: suspend () -> Boolean = { false },
        onMarker: suspend () -> Unit = {},
    ) {
        var position = 0L
        var markerSent = markerProgress.isNaN()
        var last = TimeSource.Monotonic.markNow()
        while (true) {
            val started = TimeSource.Monotonic.markNow()
            val at = if (loop) position.toFloat() / duration else min(1f, position.toFloat() / duration)
            frame(at)
            // Značka za koncem animace se nahlásí na konci, jako dřív u `animateLottieCompositionAsState`.
            if (!markerSent && (at >= markerProgress || (!loop && at >= 1f))) {
                markerSent = true
                onMarker()
            }
            if (!loop && at >= 1f) return
            if (hold()) last = TimeSource.Monotonic.markNow()
            // Čekání na vykreslení už do snímku započítané je — pauza navíc by z 60 fps udělala
            // 30 (cyklus by přesáhl jeden vsync). Nezbývá-li čas, vlákno se jen pustí ostatním.
            val remaining = frameTime - started.elapsedNow()
            if (remaining.isPositive()) delay(remaining) else yield()
            val now = TimeSource.Monotonic.markNow()
            position += (now - last).inWholeNanoseconds
            if (loop) position %= duration
            last = now
        }
    }
}

/**
 * Kreslí painter samolepky do bitmap na pozadí.
 *
 * **Tři bitmapy na střídačku**: do jedné se kreslí, druhou kreslí UI a třetí může ještě
 * nahrávat `RenderThread` z minulého snímku. Smyčka navíc čeká na vykreslení, takže se nikdy
 * nepíše do bitmapy, kterou systém právě čte. Stejné pravidlo jako u videosamolepek.
 *
 * **Průběh se nastavuje uvnitř vlastního snímku stavu, který se zahodí** (`takeMutableSnapshot`
 * + `dispose`). Zápis do globálního snímku z pozadí by po každém snímku samolepky plánoval
 * na hlavním vlákně rozeslání změn, a nikdo jiný ten stav nečte.
 */
private class StickerFrameRenderer(
    private val painter: Painter,
    private val progress: MutableFloatState,
    size: IntSize,
    private val density: Density,
) {
    private val width = size.width
    private val height = size.height

    // Až při prvním použití: poslední snímek (kostka z historie) potřebuje jen jednu.
    private val bitmaps = arrayOfNulls<ImageBitmap>(FRAME_BUFFERS)
    private val canvases = arrayOfNulls<Canvas>(FRAME_BUFFERS)
    private val drawScope = CanvasDrawScope()
    private val area = Size(size.width.toFloat(), size.height.toFloat())
    private var written = 0

    fun render(at: Float): ImageBitmap {
        val index = written++ % FRAME_BUFFERS
        val bitmap = bitmaps[index] ?: ImageBitmap(width, height).also { bitmaps[index] = it }
        val canvas = canvases[index] ?: Canvas(bitmap).also { canvases[index] = it }
        val snapshot = Snapshot.takeMutableSnapshot()
        try {
            snapshot.enter {
                progress.floatValue = at
                drawScope.draw(density, LayoutDirection.Ltr, canvas, area) {
                    drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                    with(painter) { draw(this@draw.size) }
                }
            }
        } finally {
            snapshot.dispose()
        }
        return bitmap
    }
}

/** Rozměr bitmapy: animace vepsaná do plochy, se zachovaným poměrem stran. */
private fun fitInto(composition: LottieComposition, area: IntSize): IntSize {
    val scale = min(area.width / composition.width, area.height / composition.height)
    return IntSize(
        (composition.width * scale).roundToInt().coerceAtLeast(1),
        (composition.height * scale).roundToInt().coerceAtLeast(1),
    )
}

/**
 * Plocha se změnila natolik, že se vyplatí kreslit do nových bitmap. Buňky mřížky se přeměřují
 * o pixel (171 ↔ 172) a každá změna by smyčku zahodila a založila znovu; drobný rozdíl se jen
 * přeškáluje při kreslení.
 */
private fun IntSize.isFarFrom(other: IntSize): Boolean {
    if (width <= 0 || height <= 0) return other.width > 0 && other.height > 0
    return abs(width - other.width) * RESIZE_TOLERANCE > max(width, other.width) ||
        abs(height - other.height) * RESIZE_TOLERANCE > max(height, other.height)
}

/**
 * Jediné vlákno pro kreslení všech samolepek — Compottie víc vláken naráz nesnese (viz
 * [RenderedSticker]). Plnou mřížku tak taky nezabere celý `Dispatchers.Default`, na kterém běží
 * i čtení textů z prostředků a další práce aplikace.
 */
private val stickerRenderer = Dispatchers.Default.limitedParallelism(1)

private const val FRAME_BUFFERS = 3

/** Změna plochy o víc než 1/8 znamená nové bitmapy. */
private const val RESIZE_TOLERANCE = 8

private val UNREADY_PAINTER = Size(1f, 1f)
