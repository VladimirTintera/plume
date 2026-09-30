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
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
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
     * Pozorovatel konce přehrávání pro smyčku.
     *
     * `AVPlayer` opakování neumí sám a `AVPlayerLooper` by znamenal `AVQueuePlayer`. Drží se
     * silnou referencí a odhlašuje v [release]: `NSNotificationCenter` si pozorovatele nedrží.
     */
    private var loopObserver: Any? = null

    /** `play()` rychlost vrací na 1×, takže se drží tady a zapisuje po každém spuštění. */
    private var speed = 1f

    actual fun load(path: String, loop: Boolean, muted: Boolean) {
        this.muted = muted
        player.muted = muted
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = NSURL.fileURLWithPath(path)))
        setLooping(loop)
    }

    private fun setLooping(loop: Boolean) {
        loopObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        loopObserver = null
        if (!loop) return
        loopObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player.currentItem,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            player.seekToTime(CMTimeMakeWithSeconds(0.0, preferredTimescale = 600))
            player.play()
        }
    }

    /** Odhlásí pozorovatele smyčky. Volá se při uvolnění přehrávače z kompozice. */
    internal fun release() {
        loopObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        loopObserver = null
        player.pause()
    }

    /**
     * iOS streamování zatím neumí ([videoStreamingSupported] je `false`), takže se sem volání
     * nedostane. Kdyby se přesto dostalo, přehraje se, co je na disku — to je pořád lepší než
     * pád, ale u rozestavěného souboru to skončí dřív.
     */
    actual fun loadStreaming(source: StreamingSource, loop: Boolean, muted: Boolean) {
        load(source.path, loop = loop, muted = muted)
    }

    actual fun play() {
        // Kategorie „přehrávání": zvuk videa hraje i s přepínačem ztlumení. U ztlumeného obsahu
        // se relace nepřepíná — GIF nemá co ztišit hudbu, která uživateli hraje.
        if (!muted) {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
            AVAudioSession.sharedInstance().setActive(true, error = null)
        }
        if (isEnded) seekTo(0)
        player.play()
        if (speed != 1f) player.rate = speed
    }

    actual fun pause() = player.pause()

    actual fun seekTo(positionMs: Long) {
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

    /** AVPlayer „dohráno" nehlásí stavem; pozná se podle pozice u konce při zastaveném přehrávání. */
    actual val isEnded: Boolean
        get() = !isPlaying && durationMs > 0 && positionMs >= durationMs - END_TOLERANCE_MS

    private fun seconds(value: Double): Long = if (value.isNaN() || value.isInfinite()) 0 else (value * 1000).toLong()
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

private const val END_TOLERANCE_MS = 100L

/**
 * iOS zatím rozestavěný soubor přehrát neumí.
 *
 * `AVPlayer` by na to potřeboval `AVAssetResourceLoaderDelegate` — vlastní zdroj dat, který
 * odpovídá na požadavky na rozsahy bajtů. Je to samostatný kus práce; do té doby se na iOS
 * čeká na celý soubor jako dosud.
 */
actual val videoStreamingSupported: Boolean = false
