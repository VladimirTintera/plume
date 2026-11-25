package eu.tintera.plume.core.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.resourceLoader
import platform.AVFoundation.asset
import platform.AVFoundation.status
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.presentationSize
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.muted
import platform.AVFoundation.setMuted
import platform.AVFoundation.setRate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/** iOS: `AVPlayer` s `AVPlayerLayer` v UIKit view. */
@OptIn(ExperimentalForeignApi::class)
actual class VideoPlayer {

    internal val player = AVPlayer()

    /**
     * Vrstva, do které se právě kreslí ([VideoSurface]); obraz v obraze se staví nad ní
     * ([rememberPictureInPicture]). Stav, protože povrch se skládá až po přehrávači.
     */
    internal var layer by mutableStateOf<AVPlayerLayer?>(null)

    /** Ztlumený obsah (GIF) nesmí sahat na zvukovou relaci — ztišil by hudbu uživatele pro nic. */
    private var muted = false

    /**
     * Pozorovatel konce přehrávání: smyčka (GIF) a [isEnded].
     *
     * `AVPlayer` opakování neumí sám a `AVPlayerLooper` by znamenal `AVQueuePlayer`. Drží se
     * silnou referencí a odhlašuje v [release]: `NSNotificationCenter` si pozorovatele nedrží.
     */
    private var endObserver: Any? = null

    /**
     * Položka dohrála (`AVPlayerItemDidPlayToEndTime`). Dřív se konec odhadoval z pozice u délky,
     * jenže délku AVPlayer u streamu zjistí až z indexu, který dorazí později: do té doby by
     * pozastavení u „konce" vypadalo jako dohrání a `play()` by skočil na začátek.
     */
    private var reachedEnd = false

    /** Dohrané se pustí znovu (GIF); pamatuje se kvůli obnově položky po chybě v [play]. */
    private var looping = false

    /** `play()` rychlost vrací na 1×, takže se drží tady a zapisuje po každém spuštění. */
    private var speed = 1f

    /** Stream, který právě hraje (rozestavěný soubor), nebo `null` u hotového souboru. */
    private var stream: ActiveStream? = null

    actual fun load(path: String, loop: Boolean, muted: Boolean) {
        replaceItem(AVPlayerItem(uRL = NSURL.fileURLWithPath(path)), loop, muted, stream = null)
    }

    /**
     * Streamování. Progresivní MP4: AVPlayer dostane URL s vlastním schématem a o data si říká
     * [StreamingResourceLoader]. Přepočítaná kvalita (fragmentované MP4) s HLS seznamem: místní
     * HTTP server ([HlsServer]) — přes vlastní schéma ji AVPlayer neumí. Oba čtou rozestavěný
     * soubor TDLib a na chybějící kus počkají ([StreamingReader]).
     */
    actual fun loadStreaming(source: StreamingSource, loop: Boolean, muted: Boolean) {
        val reader = StreamingReader(source)
        val hls = source.hlsPlaylist?.let { HlsServer.register(reader, it) }
        val hlsUrl = hls?.url()
        if (hlsUrl != null) {
            replaceItem(AVPlayerItem(uRL = hlsUrl), loop, muted, ActiveStream(reader, loader = null, hls = hls))
            return
        }
        hls?.close()
        val loader = StreamingResourceLoader(reader)
        val asset = AVURLAsset(uRL = streamingUrl, options = null)
        asset.resourceLoader.setDelegate(loader, queue = loader.queue)
        replaceItem(AVPlayerItem(asset = asset), loop, muted, ActiveStream(reader, loader, hls = null))
    }

    private fun replaceItem(item: AVPlayerItem, loop: Boolean, muted: Boolean, stream: ActiveStream?) {
        // Opuštěný zdroj streamu pustí čekající požadavky — jinak by vlákna čekala do limitu.
        this.stream?.release()
        this.stream = stream
        this.muted = muted
        player.muted = muted
        looping = loop
        player.replaceCurrentItemWithPlayerItem(item)
        observeEnd(item)
    }

    /** Konec se hlídá na KONKRÉTNÍ položce — nová (jiné video, obnova po chybě) chce nového pozorovatele. */
    private fun observeEnd(item: AVPlayerItem) {
        val loop = looping
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        reachedEnd = false
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (loop) {
                player.seekToTime(CMTimeMakeWithSeconds(0.0, preferredTimescale = 600))
                player.play()
            } else {
                reachedEnd = true
                // Dohrané video hudbu pustí zpátky stejně jako pauza.
                releaseSession()
            }
        }
    }

    /** Odhlásí pozorovatele konce. Volá se při uvolnění přehrávače z kompozice. */
    internal fun release() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        stream?.release()
        stream = null
        player.pause()
        releaseSession()
    }

    actual fun play() {
        // Kategorie „přehrávání": zvuk videa hraje i s přepínačem ztlumení. U ztlumeného obsahu
        // se relace nepřepíná — GIF nemá co ztišit hudbu, která uživateli hraje.
        if (!muted) {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
            AVAudioSession.sharedInstance().setActive(true, error = null)
        }
        // Selhaná položka se sama nevzpamatuje; připraví se znovu ze stejného souboru. U HLS
        // s novou adresou: server mohl o soket přijít (uspaná aplikace) a naslouchat jinde.
        if (isFailed) {
            val hlsUrl = stream?.hls?.url()
            val item = when {
                hlsUrl != null -> AVPlayerItem(uRL = hlsUrl)
                else -> player.currentItem?.asset?.let { AVPlayerItem(asset = it) }
            }
            if (item != null) {
                player.replaceCurrentItemWithPlayerItem(item)
                observeEnd(item)
            }
        }
        if (isEnded) seekTo(0)
        player.play()
        if (speed != 1f) player.rate = speed
    }

    actual fun pause() {
        player.pause()
        releaseSession()
    }

    /**
     * Vrátí zvukovou relaci a dá vědět ostatním (`NotifyOthersOnDeactivation`): hudba, kterou
     * video přerušilo (Hudba, Spotify), se pustí zpátky — jako na Androidu po vrácení fokusu.
     * Ztlumený obsah relaci nepřepínal, tak ji ani nevrací. Hraje-li v aplikaci jiný zvuk
     * (hlasovka), systém deaktivaci odmítne a nic se nestane.
     */
    private fun releaseSession() {
        if (muted) return
        AVAudioSession.sharedInstance().setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }

    actual fun seekTo(positionMs: Long) {
        reachedEnd = false
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, preferredTimescale = 600))
    }

    actual fun setSpeed(rate: Float) {
        speed = rate
        // Pozastavené video se změnou `rate` rozjede — zapisuje se jen do hrajícího.
        if (isPlaying) player.rate = rate
    }

    actual fun setMuted(muted: Boolean) {
        player.muted = muted
    }

    actual val positionMs: Long get() = seconds(CMTimeGetSeconds(player.currentTime()))

    actual val durationMs: Long
        get() = player.currentItem?.let { seconds(CMTimeGetSeconds(it.duration)) } ?: 0

    actual val isPlaying: Boolean get() = player.rate != 0f

    /** AVPlayer „dohráno" nehlásí stavem — nese ho oznámení o konci položky ([reachedEnd]). */
    actual val isEnded: Boolean
        get() = reachedEnd && !isPlaying

    actual val isFailed: Boolean
        get() = player.currentItem?.status == AVPlayerItemStatusFailed

    /** Velikost, ve které AVPlayer obraz ukazuje (i s poměrem pixelu); nulová, dokud ji nezná. */
    @OptIn(ExperimentalForeignApi::class)
    actual val aspectRatio: Float?
        get() = player.currentItem?.presentationSize?.useContents {
            if (width > 0 && height > 0) (width / height).toFloat() else null
        }

    private fun seconds(value: Double): Long = if (value.isNaN() || value.isInfinite()) 0 else (value * 1000).toLong()
}

/**
 * Stream, který přehrávač právě hraje. `AVAssetResourceLoader` si delegáta ([loader]) drží slabě,
 * takže silnou referenci musí mít přehrávač — jinak by zmizel uprostřed videa. [hls] je registrace
 * u místního serveru, odhlásí se s opuštěním streamu.
 */
private class ActiveStream(
    val reader: StreamingReader,
    val loader: StreamingResourceLoader?,
    val hls: HlsServer.Registration?,
) {
    fun release() {
        reader.released = true
        hls?.close()
    }
}

@Composable
actual fun rememberVideoPlayer(): VideoPlayer {
    val player = remember { VideoPlayer() }
    DisposableEffect(player) {
        onDispose {
            // `release` odhlásí pozorovatele smyčky — `NSNotificationCenter` si ho nedrží slabě
            // a bez odhlášení by přežil přehrávač.
            player.release()
            player.player.replaceCurrentItemWithPlayerItem(null)
        }
    }
    return player
}

/**
 * `AVPlayerLayer` v UIView. Interop je **bez interakce** (`interactionMode = null`), dotyky jdou
 * do Compose nad ním — ovládání a tah na zavření kreslí prohlížeč.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    UIKitView(
        factory = { PlayerLayerView(player.player).also { player.layer = it.playerLayer } },
        modifier = modifier,
        onRelease = { view -> if (player.layer === view.playerLayer) player.layer = null },
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

@OptIn(ExperimentalForeignApi::class)
private class PlayerLayerView(player: AVPlayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    val playerLayer: AVPlayerLayer = AVPlayerLayer.playerLayerWithPlayer(player).apply {
        videoGravity = AVLayerVideoGravityResizeAspect
    }

    init {
        backgroundColor = UIColor.clearColor
        layer.addSublayer(playerLayer)
    }

    /** Vrstva velikost s view nemění sama; bez implicitní animace, jinak by obraz „dojížděl". */
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

/** iOS streamuje přes `AVAssetResourceLoaderDelegate` ([StreamingResourceLoader]). */
actual val videoStreamingSupported: Boolean = true

/**
 * Fragmentované MP4 (přepočítané kvality) streamuje iOS jako HLS přes místní server ([HlsServer]).
 * Přes vlastní zdroj ([StreamingResourceLoader]) to nešlo: AVPlayer `mehd` nečte, délku skládá
 * z přečtených fragmentů (hodinové video hlásilo 0:45, rostla s přehráváním) a skok za ně skončí
 * koncem videa.
 */
actual val videoFragmentedStreamingSupported: Boolean = true

/** AVPlayer přepočítanou kvalitu přehraje jen jako HLS, viz [videoFragmentedStreamingSupported]. */
actual val videoFragmentedStreamingNeedsHls: Boolean = true

/**
 * AVPlayer umí h264 a HEVC na každém podporovaném iPhonu; AV1 jen od čipu A17 Pro / M3, a zjistit
 * se to dá jen přes VideoToolbox — přepočítaná AV1 se proto nenabízí vůbec.
 */
actual fun videoDecoder(codec: String, width: Int, height: Int): VideoDecoder = when (codec) {
    "h264", "avc", "h265", "hevc" -> VideoDecoder.Hardware
    else -> VideoDecoder.None
}
