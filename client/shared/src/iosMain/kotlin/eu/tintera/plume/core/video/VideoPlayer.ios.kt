package eu.tintera.plume.core.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/** iOS: `AVPlayer` s `AVPlayerLayer` v UIKit view. */
@OptIn(ExperimentalForeignApi::class)
actual class VideoPlayer {

    internal val player = AVPlayer()

    actual fun load(path: String) {
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = NSURL.fileURLWithPath(path)))
    }

    actual fun play() {
        // Kategorie „přehrávání": zvuk videa hraje i s přepínačem ztlumení.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        if (isEnded) seekTo(0)
        player.play()
    }

    actual fun pause() = player.pause()

    actual fun seekTo(positionMs: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, preferredTimescale = 600))
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
            player.pause()
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
        factory = { PlayerLayerView(player.player) },
        modifier = modifier,
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

@OptIn(ExperimentalForeignApi::class)
private class PlayerLayerView(player: AVPlayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player).apply {
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
