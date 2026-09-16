package eu.tintera.plume.core.video

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
import java.io.File

/**
 * Android: Media3 ExoPlayer. Audio focus řeší sám (`handleAudioFocus`) — video ztlumí hudbu
 * a zastaví se při hovoru.
 */
actual class VideoPlayer(internal val exo: ExoPlayer) {

    actual fun load(path: String) {
        exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
        exo.prepare()
    }

    actual fun play() {
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        exo.play()
    }

    actual fun pause() = exo.pause()

    actual fun seekTo(positionMs: Long) = exo.seekTo(positionMs)

    actual val positionMs: Long get() = exo.currentPosition

    actual val durationMs: Long get() = exo.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0

    actual val isPlaying: Boolean get() = exo.isPlaying

    actual val isEnded: Boolean get() = exo.playbackState == Player.STATE_ENDED
}

@Composable
actual fun rememberVideoPlayer(): VideoPlayer {
    val context = LocalContext.current
    val player = remember {
        VideoPlayer(
            ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
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
                    if (size.width > 0 && size.height > 0) {
                        frame.setAspectRatio(size.width * size.pixelWidthHeightRatio / size.height)
                    }
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
