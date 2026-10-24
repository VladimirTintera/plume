package eu.tintera.plume.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import eu.tintera.plume.core.audio.resampleWaveform
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.stop_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val WAVE_HEIGHT = 20.dp
private val BAR_WIDTH = 2.dp
private val BAR_GAP = 1.5.dp
private val BAR_MIN = 2.dp

/** Hodnota pod tímhle zlomkem se kreslí jako nejnižší sloupec — u ticha by jinak vlna zmizela. */
private const val SILENCE = 0.08f

/**
 * Hlasovka v bublině: přehrát/zastavit, vlna z dat zprávy (`waveform`, co Telegram ke hlasovce
 * posílá) a čas. Během přehrávání se vlna zleva vybarvuje a místo délky běží uplynulý čas — dřív
 * byla jen délka a nebylo poznat, kolik zbývá.
 *
 * Průběh se čte po snímcích z přehrávače ([progress], [elapsedSeconds]): `MediaPlayer` ho
 * neohlašuje. Smyčka běží jen u hrající hlasovky; ostatní bubliny nic nepřekreslují.
 */
@Composable
fun VoiceNoteRow(
    voice: MessageMedia.VoiceNote,
    outgoing: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    progress: () -> Float,
    elapsedSeconds: () -> Int,
) {
    var played by remember { mutableFloatStateOf(0f) }
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        played = 0f
        elapsed = 0
        while (playing) {
            withFrameMillis { }
            played = progress()
            elapsed = elapsedSeconds()
        }
    }
    // Barvy z popředí bubliny: v odchozí `onPrimaryContainer` (akcent na tónové ploše kontrast
    // nezaručuje, CLAUDE.md), v příchozí `primary`. Nepřehraná část je tatáž barva tlumeně.
    val playedColor = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    val restColor = (if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        .copy(alpha = 0.4f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Tónová výplň má barvu příchozí bubliny a tlačítko by v ní zmizelo — tam plná.
        FilledTonalIconButton(
            onClick = onPlay,
            shapes = IconButtonDefaults.animatedShapes(),
            colors = if (outgoing) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.filledIconButtonColors(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painterResource(if (playing) Res.drawable.stop_24dp else Res.drawable.play_arrow_24dp),
                contentDescription = stringResource(if (playing) Res.string.media_pause else Res.string.media_play),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Waveform(voice.waveform, played = if (playing) played else 0f, playedColor = playedColor, restColor = restColor)
            Text(
                formatDuration(if (playing) elapsed else voice.durationSeconds),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Sloupce vlny přes celou šířku: kolik se jich vejde, tolik se z dat převzorkuje
 * (`resampleWaveform` z `core`, totéž co na telefonu). Bez dat (stará hlasovka) rovná čára.
 */
@Composable
private fun Waveform(values: List<Int>, played: Float, playedColor: Color, restColor: Color) {
    Canvas(Modifier.fillMaxWidth().height(WAVE_HEIGHT)) {
        val step = (BAR_WIDTH + BAR_GAP).toPx()
        val bars = ((size.width + BAR_GAP.toPx()) / step).toInt().coerceAtLeast(1)
        val levels = if (values.isEmpty()) List(bars) { 0f } else resampleWaveform(values, bars)
        val barWidth = BAR_WIDTH.toPx()
        val minHeight = BAR_MIN.toPx()
        val radius = CornerRadius(barWidth / 2)
        levels.forEachIndexed { index, level ->
            val height = if (level < SILENCE) minHeight else maxOf(minHeight, level * size.height)
            val x = index * step
            drawRoundRect(
                color = if ((index + 0.5f) / bars <= played) playedColor else restColor,
                topLeft = Offset(x, (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}
