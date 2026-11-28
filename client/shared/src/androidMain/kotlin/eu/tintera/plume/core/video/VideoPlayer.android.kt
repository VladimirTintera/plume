package eu.tintera.plume.core.video

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.graphics.Color
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
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File

/**
 * Android: Media3 ExoPlayer, audio focus si drží sám ([VideoAudioFocus]) — video hudbu jiné
 * aplikace pozastaví jen na dobu přehrávání a po pauze ji pustí zpátky.
 */
actual class VideoPlayer(internal val exo: ExoPlayer, audioManager: AudioManager?) {

    private val focus = audioManager?.let { VideoAudioFocus(it, exo) }

    /** Ztlumený obsah (GIF, tichá smyčka) o fokus nežádá vůbec, viz [load]. */
    private var muted = false

    actual fun load(path: String, loop: Boolean, muted: Boolean) {
        exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.volume = if (muted) 0f else 1f
        /*
         * Ztlumený obsah o AUDIO FOCUS NEŽÁDÁ ([play]). Dokud fokus řešil ExoPlayer, řekl si o něj
         * i při nulové hlasitosti — a když ho nedostal, přehrávání POTLAČIL: `isPlaying` zůstal
         * `false` a pozice stála na nule. Přesně tak se GIF v bublině nehýbal. Ztlumený GIF navíc
         * nemá co ztišit hudbu uživatele.
         */
        this.muted = muted
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
        this.muted = muted
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
        // Fokus nedostaneme jen za hovoru; pak video nepustit, ozvalo by se přes telefonát.
        if (!muted && focus?.request() == false) return
        exo.play()
    }

    actual fun pause() = exo.pause()

    /** Uvolní přehrávač i fokus (odchod z kompozice). */
    internal fun release() {
        focus?.release()
        exo.release()
    }

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
                // Fokus drží [VideoAudioFocus], ne ExoPlayer — ten si říká o trvalý a drží ho i v pauze.
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
                .build(),
            context.getSystemService(AudioManager::class.java),
        )
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * Audio focus videa: na dobu přehrávání, ne natrvalo.
 *
 * - **`GAIN_TRANSIENT`, ne `GAIN`.** ExoPlayer (`handleAudioFocus`) si u médií bere trvalý fokus
 *   a drží ho i v pauze: hudební aplikace (Spotify, YouTube Music) dostala trvalou ztrátu, zastavila
 *   se a sama se už nerozjela. S přechodným fokusem hudbu jen pozastaví a po jeho vrácení ji pustí
 *   zpátky — jako přehrávač ve Files od Google.
 * - **Fokus se vrací, jakmile video nehraje** (pauza, konec, zhasnutý displej, chyba): hlídá se
 *   `playWhenReady` a stav přehrávače, ne jednotlivá místa, odkud se pauzuje (sluchátka pauzují
 *   uvnitř ExoPlayeru).
 * - **O fokus přijdeme hovorem nebo jinou aplikací**: video se pozastaví, a byla-li ztráta jen
 *   přechodná (hovor), po vrácení fokusu pokračuje. Při tom se fokus NEVRACÍ — jinak by o něm
 *   systém zapomněl a po hovoru by nepřišel zpátky.
 * - **Ztišení pod navigací dělá systém sám** (od API 26, `setWillPauseWhenDucked(false)`).
 */
private class VideoAudioFocus(private val audio: AudioManager, private val exo: ExoPlayer) {
    /** Žádost je v systému (je co vracet), i když fokus zrovna drží někdo jiný. */
    private var held = false

    /** Fokus máme teď my. Po přechodné ztrátě je žádost dál v systému, ale fokus ne. */
    private var owning = false
    private var pausedByLoss = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    owning = false
                    pausedByLoss = false
                    exo.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    owning = false
                    if (exo.playWhenReady) {
                        pausedByLoss = true
                        exo.pause()
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    owning = true
                    if (pausedByLoss) {
                        pausedByLoss = false
                        exo.play()
                    }
                }
            }
        }
        .build()

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = abandonIfIdle()

        override fun onPlaybackStateChanged(playbackState: Int) = abandonIfIdle()
    }

    init {
        exo.addListener(listener)
    }

    /** `false`, když fokus systém nedal (hovor). */
    fun request(): Boolean {
        pausedByLoss = false
        // Jen „žádost je v systému" nestačí: po přechodné ztrátě by video hrálo přes hudbu,
        // která fokus mezitím drží. Opakovaná žádost téhož `AudioFocusRequest` je v pořádku.
        if (owning) return true
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        held = held || granted
        owning = granted
        return granted
    }

    fun release() {
        exo.removeListener(listener)
        pausedByLoss = false
        abandon()
    }

    private fun abandonIfIdle() {
        // Pauza kvůli přechodné ztrátě fokus drží, jinak by po hovoru nepřišel zpátky.
        if (pausedByLoss) return
        val playing = exo.playWhenReady && exo.playbackState != Player.STATE_ENDED && exo.playbackState != Player.STATE_IDLE
        if (!playing) abandon()
    }

    private fun abandon() {
        owning = false
        if (!held) return
        held = false
        audio.abandonAudioFocusRequest(request)
    }
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

/** Android streamovat umí — čtení rozestavěného souboru řeší [PartialFileDataSource]. */
actual val videoStreamingSupported: Boolean = true

/** ExoPlayer fragmentované MP4 přehraje i rozestavěné (délku má z `mehd`). */
actual val videoFragmentedStreamingSupported: Boolean = true

/** ExoPlayer čte fragmentované MP4 sám, HLS seznam nepotřebuje. */
actual val videoFragmentedStreamingNeedsHls: Boolean = false

/** Dekodér podle `MediaCodecList` ([mediaCodecVideoDecoder] v `core`, sdílí ho televize). */
actual fun videoDecoder(codec: String, width: Int, height: Int): VideoDecoder = mediaCodecVideoDecoder(codec, width, height)
