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
import eu.tintera.plume.core.audio.AudioEngine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
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
 * přehrávače a snímky jako obrázky Skii kreslí [VideoSurface]. Zvuk hraje [AudioEngine] —
 * tentýž přehrávač jako u hlasovek, nad druhým otevřením téhož souboru.
 *
 * Časování:
 * - **Se zvukem jdou hodiny ze zvuku** ([AudioEngine.positionMs], tedy z odehraných vzorků):
 *   snímek se ukáže, až zvuk dojde k jeho PTS. Ucho pozná posun o pár desítek ms, oko zahozený
 *   snímek ne — proto se obraz řídí zvukem, ne naopak. Nestíhá-li obraz, snímky se zahazují.
 * - **Bez zvuku** (ztlumené načtení, video bez zvukové stopy, zvuk dohrál dřív) se snímek
 *   ukáže v čase podle svého PTS od „kotvy“ (snímek a okamžik, kdy se pustil). Pauza, skok
 *   a změna rychlosti kotvu zahodí a další snímek ji založí znovu; smyčka pokračuje na téže
 *   časové ose (`loopBase`), aby první snímek nepřeskočil délku posledního.
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

    /** Hraje v okénku obrazu v obraze ([DesktopPip]); jen hlavní vlákno. */
    internal var heldByPip = false

    /** Stránka, která přehrávač založila, odešla z kompozice; uvolní ho okénko. */
    internal var orphaned = false

    /** Velikost [VideoSurface] v pixelech; podle ní dekodér snímky zmenšuje. */
    @Volatile
    internal var surfaceSize: IntSize = IntSize.Zero

    /** Probudí dekódovací smyčku: pauza, přehrání, skok, rychlost. */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val pendingSeek = AtomicLong(NO_SEEK)

    /** Zrušení čtení rozestavěného souboru pro právě načtený zdroj ([FfmpegInput.Streaming]). */
    @Volatile private var cancelInput = AtomicBoolean(false)

    @Volatile private var playWhenReady = false
    @Volatile private var speed = 1f
    @Volatile private var position = 0L
    @Volatile private var duration = 0L
    @Volatile private var ended = false
    @Volatile private var failed = false
    @Volatile private var muted = false

    /**
     * Zvuk videa; `null` u ztlumeného načtení ([load] s `muted`) a u videa bez zvukové stopy.
     * Zakládá, pouští a zavírá ho smyčka [decode]; odsud se jen ztlumí a zastaví.
     */
    @Volatile private var audio: AudioEngine? = null

    /** Zvuk dohrál dřív než obraz (nebo skončil chybou); dál se časuje bez něj. */
    @Volatile private var audioEnded = false

    actual fun load(path: String, loop: Boolean, muted: Boolean) = start(Source(path, null, loop, muted))

    /** Rozestavěný soubor čte FFmpeg přes vlastní vstup s čekáním na data ([StreamingIo]). */
    actual fun loadStreaming(source: StreamingSource, loop: Boolean, muted: Boolean) =
        start(Source(null, source, loop, muted))

    actual fun play() {
        // „Zkusí připravit znovu“: soubor mohl mezitím dorazit celý.
        val retry = source?.takeIf { failed }
        if (retry != null) start(retry)
        if (ended) {
            pendingSeek.set(0)
            ended = false
        }
        playWhenReady = true
        wake.trySend(Unit)
    }

    actual fun pause() {
        // V okénku obrazu v obraze pauzu dává jen okénko; stránka prohlížeče, která se zrovna
        // zavírá, video pozastaví (přestane být vybraná), a to by okénko zastavilo.
        if (heldByPip) return
        pauseFromPip()
    }

    /** Pauza bez ohledu na okénko — z okénka obrazu v obraze a při jeho zavření. */
    internal fun pauseFromPip() {
        playWhenReady = false
        // Zvuk stojí hned, ne až se smyčka probudí: dozvuk po stisku pauzy je slyšet.
        audio?.pause()
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

    actual fun setMuted(muted: Boolean) {
        this.muted = muted
        audio?.muted = muted
    }

    actual val positionMs: Long get() = position

    actual val durationMs: Long get() = duration

    actual val isPlaying: Boolean get() = playWhenReady && !ended && !failed

    actual val isEnded: Boolean get() = ended

    actual val isFailed: Boolean get() = failed

    /** Hraje i zvuk (pro testy): načtené se zvukem, stopa v souboru a výstup v systému. */
    internal val playsSound: Boolean get() = audio != null

    /** Odchod z kompozice: zastaví dekodér a vrátí snímek. */
    internal fun release() {
        released = true
        cancelInput.set(true)
        scope.cancel()
        replaceImage(null)
    }

    private fun start(next: Source) {
        job?.cancel()
        // Čtení, které čeká na data předchozího souboru, skončí hned (korutinu ruší jen mezi kroky).
        cancelInput.set(true)
        cancelInput = AtomicBoolean(false)
        source = next
        generation++
        pendingSeek.set(NO_SEEK)
        position = 0
        duration = 0
        ended = false
        failed = false
        replaceImage(null)
        val token = generation
        val input = next.input(cancelInput)
        // Rozestavěný soubor blokuje vlákno čekáním na data — to patří do IO, ne do Default.
        job = scope.launch(if (next.streaming != null) Dispatchers.IO else Dispatchers.Default) { decode(next, input, token) }
    }

    private suspend fun decode(source: Source, input: () -> FfmpegInput, token: Int) {
        val decoder = try {
            // GIF a tichá smyčka kolečka jedním vláknem (hraje jich víc naráz), video podle jader.
            FfmpegVideoDecoder.open(input(), threads = if (source.loop && source.muted) 1 else 0)
        } catch (failure: Throwable) {
            // Mimo jiné UnsatisfiedLinkError na systému, pro který nativní FFmpeg v balíčku není.
            logger.w(failure) { "Video nejde otevřít" }
            failed = true
            return
        }
        val sound = if (source.muted) null else openAudio(input())
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
            var soundRunning = false
            var soundSpeed = 1f
            var shownNanos = System.nanoTime()
            while (true) {
                val seek = pendingSeek.getAndSet(NO_SEEK)
                if (seek != NO_SEEK) {
                    // Čtení mohlo mezitím skončit (rozestavěný soubor) a ohlásit konec; skok ho ruší.
                    ended = false
                    decoder.seek(seek)
                    if (sound != null) {
                        sound.seekTo(seek)
                        audioEnded = false
                        soundRunning = false
                    }
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
                    if (soundRunning) {
                        sound?.pause()
                        soundRunning = false
                    }
                    anchored = false
                    wake.receive()
                    continue
                }
                if (sound != null) {
                    if (speed != soundSpeed) {
                        sound.setSpeed(speed)
                        soundSpeed = speed
                    }
                    if (!soundRunning) {
                        sound.start()
                        soundRunning = true
                    }
                }

                val frame = pending ?: decoder.decodeNext()
                if (frame == null) {
                    if (source.loop) {
                        loopBase += max(duration, position + 1)
                        decoder.seek(0)
                        if (sound != null) {
                            sound.seekTo(0)
                            audioEnded = false
                            soundRunning = false
                        }
                    } else {
                        ended = true
                    }
                    continue
                }
                pending = frame

                val now = System.nanoTime()
                // Pozice zvuku je čas v nahrávce, rychlost už v ní je; čekat se ale musí ve
                // skutečném čase, proto dělení rychlostí.
                val clock = sound?.takeIf { !audioEnded }?.positionMs
                val dueMs = if (clock != null) {
                    // Až zvuk skončí, kotva se založí od snímku, na kterém skončil.
                    anchored = false
                    ((frame - clock) / speed).toLong()
                } else {
                    val timeline = loopBase + frame
                    if (!anchored) {
                        anchored = true
                        anchorNanos = now
                        anchorMs = timeline
                    }
                    ((timeline - anchorMs) / speed).toLong() - (now - anchorNanos) / NANOS_PER_MS
                }
                if (dueMs > 0) {
                    // Probuzení (pauza, skok, rychlost) se vyhodnotí znovu; snímek počká v `pending`.
                    if (withTimeoutOrNull(dueMs) { wake.receive() } != null) {
                        anchored = false
                        continue
                    }
                } else if (dueMs < -RESYNC_MS) {
                    if (clock != null) {
                        // Obraz nestíhá zvuk: snímek se zahodí bez převodu (ten je dražší než
                        // dekódování), dokud obraz zvuk nedožene. Aspoň jeden za MAX_DROP_MS se
                        // ale ukáže, jinak by pomalý dekodér obraz zastavil úplně.
                        if (now - shownNanos < MAX_DROP_MS * NANOS_PER_MS) {
                            pending = null
                            continue
                        }
                    } else {
                        // Dekodér nestíhá (nebo stál proces): dohánět by znamenalo přehrát zbytek
                        // zrychleně, takže se čas srovná od tohohle snímku.
                        anchored = false
                    }
                }
                show(decoder, frame, token)
                shownNanos = System.nanoTime()
                pending = null
            }
        } finally {
            decoder.close()
            if (sound != null) {
                if (audio === sound) audio = null
                sound.release()
            }
        }
    }

    /**
     * Zvuk [path] do reproduktoru, nebo `null`, když v souboru zvuková stopa není (GIF poslaný
     * jako video, němé video) nebo systém nemá zvukový výstup — obraz pak hraje sám.
     */
    private suspend fun openAudio(input: FfmpegInput): AudioEngine? {
        val engine = AudioEngine()
        try {
            engine.load(input)
        } catch (failure: Throwable) {
            // `load` mohl otevřít linku a pustit vlákno těsně před zrušením korutiny; uvolnění
            // je vrátí v obou případech.
            engine.release()
            if (failure is CancellationException) throw failure
            logger.i { "Video hraje bez zvuku: ${failure.message}" }
            return null
        }
        engine.muted = muted
        engine.onCompleted = { audioEnded = true }
        audioEnded = false
        audio = engine
        return engine
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

    /** Hotový soubor ([path]), nebo rozestavěný ([streaming]). */
    private class Source(val path: String?, val streaming: StreamingSource?, val loop: Boolean, val muted: Boolean) {
        /** Vstup pro každý dekodér zvlášť (obraz, zvuk); u streamování se společným zrušením. */
        fun input(cancelled: AtomicBoolean): () -> FfmpegInput = {
            if (streaming != null) FfmpegInput.Streaming(streaming, cancelled) else FfmpegInput.File(checkNotNull(path))
        }
    }
}

@Composable
actual fun rememberVideoPlayer(): VideoPlayer {
    val player = remember { VideoPlayer() }
    // Přehrávač v okénku obrazu v obraze uvolní až okénko (DesktopPip).
    DisposableEffect(player) { onDispose { if (player.heldByPip) player.orphaned = true else player.release() } }
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

/** Rozestavěný soubor čte FFmpeg přes vlastní `AVIOContext` ([StreamingIo]). */
actual val videoStreamingSupported: Boolean = true

/** Fragmentované MP4 (přepočítané kvality) FFmpeg přečte i rozestavěné, HLS nepotřebuje. */
actual val videoFragmentedStreamingSupported: Boolean = true

actual val videoFragmentedStreamingNeedsHls: Boolean = false

/**
 * Přepočítané kvality od serveru jsou h264 a AV1; FFmpeg z JavaCPP dekóduje h264 vždycky, AV1
 * jen s `libdav1d`, o kterém se tu bez nahrání nativních knihoven nedá nic říct (volá se
 * z kompozice). Nabízí se tedy h264 (a h265, kdyby přišel) a původní soubor.
 */
actual fun videoDecoder(codec: String, width: Int, height: Int): VideoDecoder =
    when (codec) {
        "h264", "avc", "h265", "hevc" -> VideoDecoder.Software
        else -> VideoDecoder.None
    }

private const val NO_SEEK = -1L
private const val NANOS_PER_MS = 1_000_000L

/** O kolik smí snímek zaostat, než se časování srovná od něj (nebo se se zvukem zahodí). */
private const val RESYNC_MS = 250L

/** Se zvukem: nejdelší doba bez nového snímku, když obraz nestíhá a snímky se zahazují. */
private const val MAX_DROP_MS = 250L
