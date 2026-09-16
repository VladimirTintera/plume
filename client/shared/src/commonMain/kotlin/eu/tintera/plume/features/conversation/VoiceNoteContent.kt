package eu.tintera.plume.features.conversation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.resampleWaveform
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Hlasovka v bublině: tlačítko, waveform s průběhem a čas.
 *
 * - **[playback] je lambda**, ne hodnota. Pozice se mění dvacetkrát za vteřinu a čte se až tady,
 *   takže se překresluje jen tahle hlasovka, ne celá konverzace. `null` = tahle nehraje.
 * - **Ťuknutí nebo tažení po waveformu posouvá** — jen u hlasovky, která je vybraná. Tažení
 *   pointerInput spotřebuje, takže se na waveformu nerozjede swipe na odpověď.
 * - **Tečka u času = neposlechnutá.** Telegram ji ukazuje u příchozí, dokud ji nepustíme, a u odchozí,
 *   dokud si ji nepustí protějšek.
 * - Barvy přicházejí zvenku ([accent] / [onAccent]) ze stejného důvodu jako u citace: v odchozí
 *   `primary` bublině by `primary` tlačítko zmizelo.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceNoteContent(
    voice: MessageMedia.VoiceNote,
    playback: () -> VoicePlayback?,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    accent: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
) {
    val current = playback()
    val bars = remember(voice.waveform) { resampleWaveform(voice.waveform, VOICE_BARS) }

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilledIconButton(
            onClick = onToggle,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = onAccent),
            modifier = Modifier.size(VOICE_BUTTON_SIZE),
        ) {
            when {
                current?.loading == true -> CircularWavyProgressIndicator(color = onAccent, modifier = Modifier.size(28.dp))
                current?.playing == true -> Icon(painterResource(Res.drawable.pause_24dp), contentDescription = "Pozastavit")
                else -> Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = "Přehrát hlasovou zprávu")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val active = current != null && !current.loading
            // Během tažení se ukazuje místo, kam prst míří, ne kde přehrávač zrovna je.
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val progress = dragFraction ?: current?.progress ?: 0f

            Waveform(
                bars = bars,
                progress = progress,
                played = accent,
                unplayed = accent.copy(alpha = 0.35f),
                modifier = Modifier
                    .width(VOICE_WAVEFORM_WIDTH)
                    .height(VOICE_WAVEFORM_HEIGHT)
                    .semantics { contentDescription = "Průběh hlasové zprávy" }
                    .pointerInput(active) {
                        if (!active) return@pointerInput
                        detectTapGestures { offset -> onSeek(offset.x / size.width) }
                    }
                    .pointerInput(active) {
                        if (!active) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = {
                                dragFraction?.let(onSeek)
                                dragFraction = null
                            },
                            onDragCancel = { dragFraction = null },
                        ) { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    },
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val seconds = if (active) {
                    ((dragFraction?.let { it * current.durationMs } ?: current.positionMs.toFloat()) / 1000).toInt()
                } else {
                    voice.durationSeconds
                }
                Text(
                    text = formatDuration(seconds),
                    style = MaterialTheme.typography.labelMedium,
                )
                if (!voice.isListened) {
                    Box(Modifier.size(6.dp).background(accent, CircleShape))
                }
            }
        }
    }
}

/** Sloupce waveformu; přehraná část plnou barvou akcentu, zbytek průsvitnou. */
@Composable
private fun Waveform(
    bars: List<Float>,
    progress: Float,
    played: Color,
    unplayed: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val step = size.width / bars.size
        val barWidth = (step * 0.6f).coerceAtLeast(1f)
        val minHeight = barWidth
        bars.forEachIndexed { index, value ->
            val height = (minHeight + (size.height - minHeight) * value)
            val left = index * step
            drawRoundRect(
                color = if ((index + 0.5f) / bars.size <= progress) played else unplayed,
                topLeft = Offset(left, (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private const val VOICE_BARS = 40
private val VOICE_BUTTON_SIZE = 44.dp
private val VOICE_WAVEFORM_WIDTH = 160.dp
private val VOICE_WAVEFORM_HEIGHT = 28.dp
