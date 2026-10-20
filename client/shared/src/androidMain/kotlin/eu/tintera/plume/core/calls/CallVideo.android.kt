package eu.tintera.plume.core.calls

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * oříznou), jinak orientovaný — nebo s [fit] vždycky (sdílená obrazovka) — se vejde celý.
 *
 * **„Vejde se celý" dělá velikost view, ne `ScalingType`.** Renderer obraz vždycky ořízne na
 * poměr stran svého view (`setLayoutAspectRatio`); `SCALE_ASPECT_FIT` jen v `onMeasure`
 * zmenší view, a to pevná velikost z Compose (`fillMaxSize`) nedovolí — „přizpůsobit" se tak
 * tiše chovalo jako „vyplnit" a sdílená obrazovka přišla o horní a dolní okraj. Proto view
 * dostane poměr stran snímku (`aspectRatio`) a vycentruje se.
 */
@Composable
actual fun CallVideo(source: CallVideoSource, mirror: Boolean, modifier: Modifier, fit: Boolean, onAspectRatio: (Float) -> Unit) {
    // Živý příběh má vlastní přenos (bez hovoru ve dvou); ostatní obraz patří hovoru.
    val sinks = (if (source == CallVideoSource.Live) koinInject<LiveStreamMedia>() else koinInject<CallMedia>()) as? CallVideoSinks ?: return
    val aspectRatio by rememberUpdatedState(onAspectRatio)
    // Poměr stran snímku po otočení (šířka / výška), `null` do prvního snímku.
    var frameAspect by remember(source) { mutableStateOf<Float?>(null) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = frameAspect
        val boxLandscape = maxWidth > maxHeight
        val whole = aspect != null && (fit || (aspect > 1f) != boxLandscape)
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
                                    val shownWidth = if (rotated) height else width
                                    val shownHeight = if (rotated) width else height
                                    if (shownWidth <= 0 || shownHeight <= 0) return
                                    renderer.post {
                                        frameAspect = shownWidth.toFloat() / shownHeight
                                        aspectRatio(shownWidth.toFloat() / shownHeight)
                                    }
                                }
                            },
                        )
                        setEnableHardwareScaler(true)
                        // Ořez na poměr view: s view podle snímku ([whole]) se neořízne nic.
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        sinks.attach(source, this)
                    }
                },
                update = { renderer -> renderer.setMirror(mirror) },
                onRelease = { renderer ->
                    sinks.detach(source, renderer)
                    renderer.release()
                },
                modifier = if (whole && aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize(),
            )
        }
    }
}
