package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.compose.koinInject
import org.webrtc.RendererCommon
import org.webrtc.TextureViewRenderer

/**
 * Snímky kreslí `TextureViewRenderer` z ntgcalls (WebRTC nad EGL) — `TextureView`, ne
 * `SurfaceView`, aby šel oříznout do zaobleného rohu a překrýt ovládáním jako každý jiný prvek.
 *
 * **Výplň podle orientace**: obraz stejně orientovaný jako plocha ji vyplní (okraje se
 * oříznou), jinak orientovaný se vejde celý — z videa na šířku by na výšku zbyl úzký střed.
 */
@Composable
actual fun CallVideo(source: CallVideoSource, mirror: Boolean, modifier: Modifier, onAspectRatio: (Float) -> Unit) {
    val sinks = koinInject<CallMedia>() as? CallVideoSinks ?: return
    val aspectRatio by rememberUpdatedState(onAspectRatio)
    key(source) {
        AndroidView(
            factory = { context ->
                TextureViewRenderer(context).apply {
                    val renderer = this
                    init(
                        sinks.eglContext,
                        object : RendererCommon.RendererEvents {
                            override fun onFirstFrameRendered() = Unit

                            override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                                val rotated = rotation % 180 != 0
                                val frameLandscape = if (rotated) height > width else width > height
                                val shownWidth = if (rotated) height else width
                                val shownHeight = if (rotated) width else height
                                renderer.post {
                                    if (shownWidth > 0 && shownHeight > 0) aspectRatio(shownWidth.toFloat() / shownHeight)
                                    val viewLandscape = renderer.width > renderer.height
                                    renderer.setScalingType(
                                        if (frameLandscape == viewLandscape) {
                                            RendererCommon.ScalingType.SCALE_ASPECT_FILL
                                        } else {
                                            RendererCommon.ScalingType.SCALE_ASPECT_FIT
                                        },
                                    )
                                }
                            }
                        },
                    )
                    setEnableHardwareScaler(true)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    sinks.attach(source, this)
                }
            },
            update = { renderer -> renderer.setMirror(mirror) },
            onRelease = { renderer ->
                sinks.detach(source, renderer)
                renderer.release()
            },
            modifier = modifier,
        )
    }
}
