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
import eu.tintera.plume.core.telegram.OnDeviceFix
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.resources.speech_to_text_24dp
import eu.tintera.plume.resources.transcript_failed
import eu.tintera.plume.resources.transcript_pending
import eu.tintera.plume.resources.transcript_show
import eu.tintera.plume.resources.wear_transcript_download_gemma
import eu.tintera.plume.resources.wear_transcript_enable_ai
import eu.tintera.plume.resources.wear_transcript_no_answer
import eu.tintera.plume.resources.wear_transcript_no_phone
import eu.tintera.plume.resources.wear_transcript_phone_unavailable
import eu.tintera.plume.resources.wear_transcript_switch_gemma
import eu.tintera.plume.resources.wear_transcript_wrong_account
import androidx.compose.ui.text.font.FontStyle
import androidx.wear.compose.material3.IconButton
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.stop_24dp
import org.jetbrains.compose.resources.StringResource
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
    transcript: SpeechTranscript?,
    failure: TranscribeFailure?,
    onTranscribe: () -> Unit,
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
        // Přepsat jen dokud přepis není nebo se nepovedl; rozpracovaný a hotový mají text pod řádkem.
        if (transcript == null || transcript is SpeechTranscript.Failed) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onTranscribe, shapes = IconButtonDefaults.animatedShapes(), modifier = Modifier.size(TRANSCRIBE_SIZE)) {
                Icon(
                    painterResource(Res.drawable.speech_to_text_24dp),
                    contentDescription = stringResource(Res.string.transcript_show),
                    tint = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    TranscriptText(transcript, failure, outgoing)
}

private val TRANSCRIBE_SIZE = 32.dp

/**
 * Přepis pod hlasovkou: hotový text, rozpracovaný „Přepisuji…" s dosavadním textem, nepovedený
 * hláška v chybové barvě — s důvodem ([failure]), když ho známe (tlačítko Přepsat zůstává, jde
 * to zkusit znovu).
 */
@Composable
private fun TranscriptText(transcript: SpeechTranscript?, failure: TranscribeFailure?, outgoing: Boolean) {
    val color = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val muted = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    when (transcript) {
        null -> Unit
        is SpeechTranscript.Done -> Text(transcript.text, style = MaterialTheme.typography.bodySmall, color = color)
        is SpeechTranscript.Pending -> Text(
            transcript.partial.ifBlank { stringResource(Res.string.transcript_pending) },
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = muted,
        )
        is SpeechTranscript.Failed -> Text(
            stringResource(failure.message()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
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

/** Hláška k důvodu nezdaru; bez důvodu (přepis ze serveru) obecná. */
private fun TranscribeFailure?.message(): StringResource = when (this) {
    TranscribeFailure.NoPhone -> Res.string.wear_transcript_no_phone
    TranscribeFailure.NoAnswer -> Res.string.wear_transcript_no_answer
    TranscribeFailure.WrongAccount -> Res.string.wear_transcript_wrong_account
    is TranscribeFailure.PhoneCantTranscribe -> when (fix) {
        OnDeviceFix.EnableAi -> Res.string.wear_transcript_enable_ai
        OnDeviceFix.SwitchToGemma -> Res.string.wear_transcript_switch_gemma
        OnDeviceFix.DownloadGemma -> Res.string.wear_transcript_download_gemma
        null -> Res.string.wear_transcript_phone_unavailable
    }
    TranscribeFailure.Other, null -> Res.string.transcript_failed
}
