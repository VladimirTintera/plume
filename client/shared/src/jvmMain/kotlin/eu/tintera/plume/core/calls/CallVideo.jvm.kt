package eu.tintera.plume.core.calls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import org.koin.compose.getKoin

/**
 * Snímky z [VideoFrames] jako bitmapa v Compose — na desktopu není renderer WebRTC, převod
 * z I420 a otočení dělá [DesktopCallMedia].
 *
 * **Výplň podle orientace** jako na Androidu: obraz stejně orientovaný jako plocha ji vyplní
 * (okraje se oříznou), jinak orientovaný se vejde celý — z telefonu na výšku by v okně na
 * šířku zbyl jen pruh přes střed obličeje.
 */
@Composable
actual fun CallVideo(source: CallVideoSource, mirror: Boolean, modifier: Modifier, fit: Boolean, onAspectRatio: (Float) -> Unit) {
    // Bez přibalené knihovny CallMedia v Koinu není (viz AppModule.jvm).
    val media = getKoin().getOrNull<CallMedia>() as? DesktopCallMedia ?: return
    val frame by media.video.frames(source).collectAsState()
    val aspectRatio by rememberUpdatedState(onAspectRatio)
    val image = frame ?: return
    val frameAspect = image.width.toFloat() / image.height
    LaunchedEffect(frameAspect) { aspectRatio(frameAspect) }
    BoxWithConstraints(modifier) {
        val boxLandscape = constraints.maxWidth > constraints.maxHeight
        val fill = !fit && (image.width > image.height) == boxLandscape
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val scale = if (fill) {
                maxOf(size.width / image.width, size.height / image.height)
            } else {
                minOf(size.width / image.width, size.height / image.height)
            }
            val width = (image.width * scale).roundToInt()
            val height = (image.height * scale).roundToInt()
            val offset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt())
            // Náhled vlastní kamery jako zrcadlo, ať se pohyb nepřevrací.
            scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f) {
                drawImage(image, dstOffset = offset, dstSize = IntSize(width, height))
            }
        }
    }
}
