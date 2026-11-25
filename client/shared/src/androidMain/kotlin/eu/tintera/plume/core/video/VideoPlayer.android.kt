package eu.tintera.plume.core.video

import android.graphics.Color
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MimeTypes
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.media3.common.VideoSize
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.IOException
import java.io.RandomAccessFile
import java.io.File

/**
 * Android: Media3 ExoPlayer. Audio focus řeší sám (`handleAudioFocus`) — video ztlumí hudbu
 * a zastaví se při hovoru.
 */
actual class VideoPlayer(internal val exo: ExoPlayer) {

    actual fun load(path: String, loop: Boolean, muted: Boolean) {
        exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.volume = if (muted) 0f else 1f
        /*
         * Ztlumený obsah o AUDIO FOCUS NEŽÁDÁ.
         *
         * `handleAudioFocus = true` je správně pro video se zvukem, jenže ExoPlayer si o fokus
         * řekne i při nulové hlasitosti — a když ho nedostane, přehrávání POTLAČÍ: `isPlaying`
         * zůstane `false` a pozice stojí na nule, přestože je soubor načtený a délka známá.
         * Přesně tak se GIF v bublině nehýbal. Ztlumený GIF navíc nemá co ztišit hudbu uživatele.
         */
        exo.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ !muted)
        /*
         * Vytažená sluchátka video POZASTAVÍ (`ACTION_AUDIO_BECOMING_NOISY`), jinak by hrálo dál
         * nahlas z reproduktoru. Ztlumený obsah ne: GIF v bublině by se po vytažení sluchátek
         * zastavil, přestože žádný zvuk nemá.
         */
        exo.setHandleAudioBecomingNoisy(!muted)
        exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
        exo.prepare()
    }

    /**
     * Streamování: místo souboru dostane ExoPlayer zdroj, který **čeká na data**.
     *
     * `FileDataSource` by na konci rozestavěného souboru ohlásil konec vstupu a přehrávání by
     * skončilo po pár vteřinách. [PartialFileDataSource] místo toho počká, až `downloadedPrefixSize`
     * povyroste.
     */
    actual fun loadStreaming(source: StreamingSource, loop: Boolean, muted: Boolean) {
        exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.volume = if (muted) 0f else 1f
        exo.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ !muted)
        exo.setHandleAudioBecomingNoisy(!muted)
        val factory = DataSource.Factory { PartialFileDataSource(source) }
        exo.setMediaSource(
            ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(android.net.Uri.parse("tdlib://${source.path()}"))),
        )
        exo.prepare()
    }

    actual fun play() {
        // Po chybě přehrávač stojí v `STATE_IDLE` a `play()` sám nic nezmění; `prepare()` zdroj
        // otevře znovu (u streamu z aktuální cesty) a pokračuje z místa, kde chyba nastala.
        if (isFailed) exo.prepare()
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        exo.play()
    }

    actual fun pause() = exo.pause()

    actual fun seekTo(positionMs: Long) = exo.seekTo(positionMs)

    actual fun setSpeed(rate: Float) = exo.setPlaybackSpeed(rate)

    actual fun setMuted(muted: Boolean) {
        exo.volume = if (muted) 0f else 1f
    }

    actual val positionMs: Long get() = exo.currentPosition

    actual val durationMs: Long get() = exo.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0

    actual val isPlaying: Boolean get() = exo.isPlaying

    actual val isEnded: Boolean get() = exo.playbackState == Player.STATE_ENDED

    actual val isFailed: Boolean get() = exo.playerError != null

    actual val aspectRatio: Float? get() = exo.videoSize.displayAspectRatio()
}

/** Poměr stran obrazu i s poměrem pixelu; `null`, dokud ExoPlayer rozměry nezná. */
private fun VideoSize.displayAspectRatio(): Float? =
    if (width > 0 && height > 0) width * pixelWidthHeightRatio / height else null

/** Zvuk videa: běžné médium, takže ztlumí hudbu a ustoupí hovoru. */
private val audioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

@Composable
actual fun rememberVideoPlayer(): VideoPlayer {
    val context = LocalContext.current
    val player = remember {
        VideoPlayer(
            ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                .build(),
        )
    }
    DisposableEffect(player) { onDispose { player.exo.release() } }
    return player
}

/**
 * `TextureView` v `AspectRatioFrameLayout`, ne `PlayerView`.
 *
 * `PlayerView` kreslí do `SurfaceView`, a ten je samostatné okno pod aplikací: nepohne se
 * s `graphicsLayer` posunem, takže by video při tahu na zavření zůstalo stát a odjelo by jen
 * ovládání. `TextureView` je obyčejná vrstva a posouvá se s ostatními. Ovládání kreslí Compose
 * nad ním; dotyky view nebere, jdou do gest prohlížeče.
 */
@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    val listener = remember { arrayOfNulls<Player.Listener>(1) }
    AndroidView(
        factory = { context ->
            val texture = TextureView(context)
            AspectRatioFrameLayout(context).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(Color.TRANSPARENT)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                addView(texture, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                val frame = this
                fun applySize(size: VideoSize) {
                    size.displayAspectRatio()?.let(frame::setAspectRatio)
                }
                applySize(player.exo.videoSize)
                listener[0] = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) = applySize(videoSize)
                }.also(player.exo::addListener)
                player.exo.setVideoTextureView(texture)
            }
        },
        onRelease = { frame ->
            listener[0]?.let(player.exo::removeListener)
            (frame.getChildAt(0) as? TextureView)?.let(player.exo::clearVideoTextureView)
        },
        modifier = modifier,
    )
}

/**
 * Čtení rozestavěného souboru TDLib.
 *
 * Soubor na disku roste, ale číst se z něj smí jen po `available()`. Když přehrávač chce data,
 * která ještě nedorazila, **čeká** — `FileDataSource` by místo toho ohlásil konec vstupu
 * a přehrávání by skončilo uprostřed.
 *
 * Čekání blokuje vlákno ExoPlayeru, a tak to má být: je to jeho vlastní vlákno pro načítání.
 * Po [READ_TIMEOUT_MS] bez nových dat se vzdá — stahování mohlo spadnout a věčné čekání by
 * vypadalo jako zamrzlý přehrávač.
 */
private class PartialFileDataSource(private val source: StreamingSource) : BaseDataSource(/* isNetwork = */ false) {

    private var file: RandomAccessFile? = null
    private var uri: android.net.Uri? = null
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        // Cesta se čte při KAŽDÉM otevření: dostažený soubor TDLib přesune z `temp/` jinam.
        val opened = RandomAccessFile(source.path(), "r")
        opened.seek(dataSpec.position)
        file = opened
        position = dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            (source.totalSize - dataSpec.position).coerceAtLeast(0)
        } else {
            dataSpec.length
        }
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT

        val ready = awaitBytes()
        if (ready <= 0) throw IOException("Data se nestihla stáhnout (pozice $position).")

        val toRead = minOf(length.toLong(), remaining, ready).toInt()
        val read = file?.read(buffer, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        if (read <= 0) return C.RESULT_END_OF_INPUT

        position += read
        remaining -= read
        bytesTransferred(read)
        return read
    }

    /**
     * Počká, až bude na [position] co číst, a vrátí počet souvisle dostupných bajtů.
     *
     * Když pozice leží mimo stažený úsek, **přesune stahování na ni** — jinak by se čekalo na
     * data, která TDLib vůbec nestahuje. Požádá se jen jednou za skok: `downloadFile` s týmž
     * offsetem sice nevadí, ale volat ho dvacetkrát za vteřinu je zbytečné.
     */
    private fun awaitBytes(): Long {
        var waited = 0L
        var requested = false
        while (waited < READ_TIMEOUT_MS) {
            val range = source.downloaded()
            if (position in range) return range.last - position + 1

            if (!requested) {
                source.requestFrom(position)
                requested = true
            }
            Thread.sleep(POLL_MS)
            waited += POLL_MS
        }
        return 0
    }

    override fun getUri(): android.net.Uri? = uri

    override fun close() {
        file?.close()
        file = null
        uri?.let { transferEnded() }
        uri = null
    }
}

/** Jak dlouho se čeká na data, než se přehrávání vzdá. */
private const val READ_TIMEOUT_MS = 30_000L

/** Jak často se kouká, jestli data dorazila. */
private const val POLL_MS = 50L

/** Android streamovat umí — čtení rozestavěného souboru řeší [PartialFileDataSource]. */
actual val videoStreamingSupported: Boolean = true

/** ExoPlayer fragmentované MP4 přehraje i rozestavěné (délku má z `mehd`). */
actual val videoFragmentedStreamingSupported: Boolean = true

/** ExoPlayer čte fragmentované MP4 sám, HLS seznam nepotřebuje. */
actual val videoFragmentedStreamingNeedsHls: Boolean = false

/**
 * Ptá se `MediaCodecList` na dekodér pro MIME typ kodeku i na rozměry — softwarový dekodér
 * na slabším telefonu může mít strop nižší než 1080p. Neznámý kodek = žádný.
 */
actual fun videoDecoder(codec: String, width: Int, height: Int): VideoDecoder {
    val mime = when (codec.lowercase()) {
        "h264", "avc" -> MimeTypes.VIDEO_H264
        "h265", "hevc" -> MimeTypes.VIDEO_H265
        "av1", "av01" -> MimeTypes.VIDEO_AV1
        "vp9" -> MimeTypes.VIDEO_VP9
        else -> return VideoDecoder.None
    }
    val usable = decoders.filter { info ->
        info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
            runCatching { info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(width, height) }
                .getOrNull() ?: true
    }
    return when {
        usable.isEmpty() -> VideoDecoder.None
        usable.any(::isHardware) -> VideoDecoder.Hardware
        else -> VideoDecoder.Software
    }
}

/**
 * Od Androidu 10 to dekodér řekne sám; starší systém jen jménem — softwarové dekodéry jsou
 * Googlu (`OMX.google.`, `c2.android.`).
 */
private fun isHardware(info: MediaCodecInfo): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        info.isHardwareAccelerated
    } else {
        !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
    }

/** Dekodéry zařízení; seznam se za běhu nemění a sestavit ho stojí desítky milisekund. */
private val decoders: List<MediaCodecInfo> by lazy {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
}
