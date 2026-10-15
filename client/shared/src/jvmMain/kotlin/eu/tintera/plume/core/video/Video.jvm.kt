package eu.tintera.plume.core.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Přehrávač videa desktop zatím nemá (viz docs/roadmap.md) — JVM vlastní dekodér nemá a vlcj by
 * chtěl VLC v systému. Video se proto otevírá v aplikaci systému ([viewerUnsupportedFormats]
 * v `core/files`), a kde se ho přesto někdo pokusí přehrát (GIF jako MP4, náhled v bublině),
 * přehrávač hlásí chybu a zůstane stát.
 */
actual class VideoPlayer {

    actual fun load(path: String, loop: Boolean, muted: Boolean) = Unit

    actual fun loadStreaming(source: StreamingSource, loop: Boolean, muted: Boolean) = Unit

    actual fun play() = Unit

    actual fun pause() = Unit

    actual fun seekTo(positionMs: Long) = Unit

    actual fun setSpeed(rate: Float) = Unit

    actual fun setMuted(muted: Boolean) = Unit

    actual val positionMs: Long = 0

    actual val durationMs: Long = 0

    actual val isPlaying: Boolean = false

    actual val isEnded: Boolean = false

    actual val isFailed: Boolean = true
}

@Composable
actual fun rememberVideoPlayer(): VideoPlayer = remember { VideoPlayer() }

@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) = Unit

actual val videoStreamingSupported: Boolean = false

actual val videoFragmentedStreamingSupported: Boolean = false

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

/** Desktop video nepřehrává (viz [VideoPlayer]); kvalitu stejně nikdo nevybírá. */
actual fun videoDecoder(codec: String, width: Int, height: Int): VideoDecoder = VideoDecoder.None
