package eu.tintera.plume.core.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * iOS: `AVPictureInPictureController` nad vrstvou přehrávače ([VideoPlayer.layer]). Do okénka
 * jde jen video; aplikace se nemění, a okénko ovládá systém sám (přehrát, posun, zavřít).
 *
 * - **Automaticky při odchodu z aplikace** (`canStartPictureInPictureAutomaticallyFromInline`),
 *   jen dokud video hraje — stejné pravidlo jako na Androidu.
 * - **Hraje i na pozadí**, protože `UIBackgroundModes: audio` v Info.plist už aplikace má
 *   (hudba) a video přepíná relaci na Playback.
 * - **Delegát se drží silnou referencí**: kontroler ho má jen slabě.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPictureInPicture(
    player: VideoPlayer,
    aspectRatio: Float,
    playing: Boolean,
    onTogglePlay: () -> Unit,
): PictureInPicture {
    val state = remember(player) { IosPictureInPicture() }
    val layer = player.layer
    DisposableEffect(state, layer) {
        state.controller = layer
            ?.takeIf { AVPictureInPictureController.isPictureInPictureSupported() }
            ?.let { AVPictureInPictureController(playerLayer = it) }
            ?.also { it.delegate = state.delegate }
        onDispose {
            state.controller?.delegate = null
            state.controller = null
            state.active = false
        }
    }
    state.controller?.canStartPictureInPictureAutomaticallyFromInline = playing
    return state
}

@OptIn(ExperimentalForeignApi::class)
private class IosPictureInPicture : PictureInPicture {
    var controller: AVPictureInPictureController? by mutableStateOf(null)

    override val supported: Boolean get() = controller != null

    override var active by mutableStateOf(false)

    val delegate: AVPictureInPictureControllerDelegateProtocol = object : NSObject(), AVPictureInPictureControllerDelegateProtocol {
        override fun pictureInPictureControllerDidStartPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
            active = true
        }

        override fun pictureInPictureControllerDidStopPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
            active = false
        }
    }

    override fun enter() {
        controller?.startPictureInPicture()
    }
}
