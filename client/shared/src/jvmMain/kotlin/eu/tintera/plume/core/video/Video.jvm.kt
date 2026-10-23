package eu.tintera.plume.core.video

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import co.touchlab.kermit.Logger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect

/**
 * Přehrávač videa na desktopu: obraz dekóduje FFmpeg ([FfmpegVideoDecoder]) v korutině
 * přehrávače a snímky jako obrázky Skii kreslí [VideoSurface].
 *
 * **Jen ztlumené video** — GIF a tichá smyčka kolečka. Zvukový výstup desktop zatím nemá
 * (docs/roadmap.md, sekce Desktop), takže [load] se zvukem hlásí [isFailed] jako dřív:
 * video bez zvuku by vypadalo, že hraje, a přitom by mlčelo. Plné video proto dál otevírá
 * aplikace systému ([viewerUnsupportedFormats] v `core/files`).
 *
 * Časování: snímek se ukáže v čase podle svého PTS od „kotvy“ (snímek a okamžik, kdy se
 * pustil). Pauza, skok a změna rychlosti kotvu zahodí a další snímek ji založí znovu; smyčka
 * pokračuje na téže časové ose (`loopBase`), aby první snímek nepřeskočil délku posledního.
 */
actual class VideoPlayer internal constructor() {

    private val logger = Logger.withTag("VideoPlayer")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Jen hlavní vlákno.
    private var job: Job? = null
    private var source: Source? = null
    private var generation = 0
    private var released = false

    /** Snímek na ploše. Mění a zavírá se jen na hlavním vlákně, kde se i kreslí. */
    internal var image: Image? by mutableStateOf(null)
        private set

    /** Velikost [VideoSurface] v pixelech; podle ní dekodér snímky zmenšuje. */
    @Volatile
    internal var surfaceSize: IntSize = IntSize.Zero

    /** Probudí dekódovací smyčku: pauza, přehrání, skok, rychlost. */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val pendingSeek = AtomicLong(NO_SEEK)

    @Volatile private var playWhenReady = false
    @Volatile private var speed = 1f
    @Volatile private var position = 0L
    @Volatile private var duration = 0L
    @Volatile private var ended = false
    @Volatile private var failed = false

    actual fun load(path: String, loop: Boolean, muted: Boolean) = start(Source(path, loop, muted))

    /** Desktop rozestavěné soubory nečte ([videoStreamingSupported]), sem se nedostane. */
    actual fun loadStreaming(source: StreamingSource, loop: Boolean, muted: Boolean) {
        job?.cancel()
        this.source = null
        failed = true
    }

    actual fun play() {
        // „Zkusí připravit znovu“: soubor mohl mezitím dorazit celý.
        val retry = source?.takeIf { failed && it.muted }
        if (retry != null) start(retry)
        if (ended) {
            pendingSeek.set(0)
            ended = false
        }
        playWhenReady = true
        wake.trySend(Unit)
    }

    actual fun pause() {
        playWhenReady = false
        wake.trySend(Unit)
    }

    actual fun seekTo(positionMs: Long) {
        pendingSeek.set(positionMs.coerceAtLeast(0))
        ended = false
        wake.trySend(Unit)
    }

    actual fun setSpeed(rate: Float) {
        speed = rate
        wake.trySend(Unit)
    }

    /** Zvuk se nepřehrává vůbec (viz třída), takže není co ztlumit. */
    actual fun setMuted(muted: Boolean) = Unit

    actual val positionMs: Long get() = position

    actual val durationMs: Long get() = duration

    actual val isPlaying: Boolean get() = playWhenReady && !ended && !failed

    actual val isEnded: Boolean get() = ended

    actual val isFailed: Boolean get() = failed

    /** Odchod z kompozice: zastaví dekodér a vrátí snímek. */
    internal fun release() {
        released = true
        scope.cancel()
        replaceImage(null)
    }

    private fun start(next: Source) {
        job?.cancel()
        source = next
        generation++
        pendingSeek.set(NO_SEEK)
        position = 0
        duration = 0
        ended = false
        failed = false
        replaceImage(null)
        if (!next.muted) {
            logger.i { "Video se zvukem desktop zatím nepřehrává" }
            failed = true
            return
        }
        val token = generation
        job = scope.launch { decode(next, token) }
    }

    private suspend fun decode(source: Source, token: Int) {
        val decoder = try {
            FfmpegVideoDecoder.open(source.path)
        } catch (failure: Throwable) {
            // Mimo jiné UnsatisfiedLinkError na systému, pro který nativní FFmpeg v balíčku není.
            logger.w(failure) { "Video nejde otevřít" }
            failed = true
            return
        }
        try {
            duration = decoder.durationMs
            // Jako ExoPlayer: připravené video ukáže první snímek, i když ještě nehraje.
            val first = decoder.decodeNext()
            if (first == null) {
                failed = true
                return
            }
            show(decoder, first, token)

            var pending: Long? = null
            var loopBase = 0L
            var anchored = false
            var anchorNanos = 0L
            var anchorMs = 0L
            while (true) {
                val seek = pendingSeek.getAndSet(NO_SEEK)
                if (seek != NO_SEEK) {
                    decoder.seek(seek)
                    var frame = decoder.decodeNext()
                    while (frame != null && frame < seek) {
                        yield()
                        frame = decoder.decodeNext()
                    }
                    if (frame != null) show(decoder, frame, token) else if (!source.loop) ended = true
                    pending = null
                    loopBase = 0
                    anchored = false
                    continue
                }
                if (!playWhenReady || ended) {
                    anchored = false
                    wake.receive()
                    continue
                }

                val frame = pending ?: decoder.decodeNext()
                if (frame == null) {
                    if (source.loop) {
                        loopBase += max(duration, position + 1)
                        decoder.seek(0)
                    } else {
                        ended = true
                    }
                    continue
                }
                pending = frame

                val timeline = loopBase + frame
                val now = System.nanoTime()
                if (!anchored) {
                    anchored = true
                    anchorNanos = now
                    anchorMs = timeline
                }
                val dueMs = ((timeline - anchorMs) / speed).toLong() - (now - anchorNanos) / NANOS_PER_MS
                if (dueMs > 0) {
                    // Probuzení (pauza, skok, rychlost) se vyhodnotí znovu; snímek počká v `pending`.
                    if (withTimeoutOrNull(dueMs) { wake.receive() } != null) {
                        anchored = false
                        continue
                    }
                } else if (dueMs < -RESYNC_MS) {
                    // Dekodér nestíhá (nebo stál proces): dohánět by znamenalo přehrát zbytek
                    // zrychleně, takže se čas srovná od tohohle snímku.
                    anchored = false
                }
                show(decoder, frame, token)
                pending = null
            }
        } finally {
            decoder.close()
        }
    }

    /** Převede poslední dekódovaný snímek a vymění ho na ploše (na hlavním vlákně). */
    private suspend fun show(decoder: FfmpegVideoDecoder, frameMs: Long, token: Int) {
        val next = decoder.toImage(surfaceSize)
        position = frameMs
        var taken = false
        try {
            withContext(Dispatchers.Main) {
                // Snímek z předchozího souboru nebo po uvolnění už nikam nepatří.
                if (token == generation && !released) {
                    replaceImage(next)
                    taken = true
                }
            }
        } finally {
            if (!taken) next.close()
        }
    }

    /**
     * Starý snímek se zavírá hned: kreslí se na hlavním vlákně, takže teď ho nikdo nekreslí,
     * a co už je zaznamenané ke kreslení, drží si v Skie vlastní referenci. Spoléhat na GC by
     * znamenalo desítky MB nativní paměti za vteřinu, o kterých halda neví.
     */
    private fun replaceImage(next: Image?) {
        val old = image
        image = next
        old?.close()
    }

    private class Source(val path: String, val loop: Boolean, val muted: Boolean)
}

@Composable
actual fun rememberVideoPlayer(): VideoPlayer {
    val player = remember { VideoPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    Spacer(
        modifier
            .onSizeChanged { player.surfaceSize = it }
            .drawBehind {
                val image = player.image ?: return@drawBehind
                val scale = min(size.width / image.width, size.height / image.height)
                val width = image.width * scale
                val height = image.height * scale
                val target = Rect.makeXYWH((size.width - width) / 2, (size.height - height) / 2, width, height)
                drawIntoCanvas { canvas ->
                    canvas.skiaCanvas.drawImageRect(
                        image,
                        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                        target,
                        FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
                        null,
                        false,
                    )
                }
            },
    )
}

/** Desktop čte jen hotové soubory; streamování by chtělo vlastní vstup pro FFmpeg (`AVIOContext`). */
actual val videoStreamingSupported: Boolean = false

actual val videoFragmentedStreamingSupported: Boolean = false

actual val videoFragmentedStreamingNeedsHls: Boolean = false

private object NoPictureInPicture : PictureInPicture {
    override val supported = false
    override val active = false

    override fun enter() = Unit
}

@Composable
actual fun rememberPictureInPicture(
    player: VideoPlayer,
    aspectRatio: Float,
    playing: Boolean,
    onTogglePlay: () -> Unit,
): PictureInPicture = NoPictureInPicture

/** Kvalitu videa desktop nevybírá — plné video otevírá aplikace systému (viz [VideoPlayer]). */
actual fun videoDecoder(codec: String, width: Int, height: Int): VideoDecoder = VideoDecoder.None

private const val NO_SEEK = -1L
private const val NANOS_PER_MS = 1_000_000L

/** O kolik smí snímek zaostat, než se časování srovná od něj. */
private const val RESYNC_MS = 250L
