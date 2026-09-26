package eu.tintera.plume.features.conversation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.MorphShape
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.animateMorph
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_audio_progress
import eu.tintera.plume.resources.conversation_play_audio
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Skladba v bublině: obal alba s tlačítkem, název, interpret, lišta průběhu a čas.
 *
 * Hraje tentýž přehrávač jako hlasovka ([playback] je `VoicePlayback` téže zprávy), takže
 * i ovládání se chová stejně: tvar tlačítka nese stav (sušenka / kruh), po liště se dá ťuknout
 * i táhnout ([seekGestures]) a u hrající je čip rychlosti. Místo waveformu je rovná lišta —
 * skladba waveform od serveru nemá.
 *
 * **Obal alba JE tlačítko** (ořezaný do téhož tvaru, se závojem pod ikonou), bez obalu je
 * tlačítko plné v barvě akcentu jako u hlasovky.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioContent(
    audio: MessageMedia.Audio,
    playback: () -> VoicePlayback?,
    speed: () -> VoiceSpeed,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    accent: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
) {
    val current = playback()
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val rest = animateMorph(current?.playing != true && current?.loading != true, "audio-button-shape")
        val cover = audio.coverFileId
        // Na obalu je ikona bílá na závoji — barva akcentu by na fotce zanikla.
        val iconColor = if (cover != null) Color.White else onAccent
        val playLabel = stringResource(Res.string.conversation_play_audio)
        Box(
            modifier = Modifier
                .size(AUDIO_BUTTON_SIZE)
                .clip(MorphShape(ShapeMorphs.Rest, rest, rotation = ShapeMorphs.REST_ROTATION))
                .background(if (cover != null) Color.Black else accent)
                .clickable(role = Role.Button, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                RemoteImage(
                    fileId = cover,
                    placeholder = audio.coverMinithumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_SCRIM_ALPHA)))
            }
            when {
                current?.loading == true -> CircularWavyProgressIndicator(color = iconColor, modifier = Modifier.size(28.dp))
                current?.playing == true -> Icon(
                    painterResource(Res.drawable.pause_24dp),
                    contentDescription = stringResource(Res.string.media_pause),
                    tint = iconColor,
                )
                else -> Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = playLabel, tint = iconColor)
            }
        }

        Column(Modifier.width(AUDIO_TEXT_WIDTH), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val active = current != null && !current.loading
            val progressLabel = stringResource(Res.string.conversation_audio_progress)
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val progress = dragFraction ?: current?.progress ?: 0f

            Text(
                text = audio.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            audio.performer?.let { performer ->
                Text(
                    text = performer,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Lišta je tenká, ale plocha pro prst ne — ťuká se do celé výšky.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_TOUCH_HEIGHT)
                    .semantics { contentDescription = progressLabel }
                    .seekGestures(active = active, steps = SEEK_STEPS, onDrag = { dragFraction = it }, onSeek = onSeek),
                contentAlignment = Alignment.Center,
            ) {
                ProgressBar(progress = progress, played = accent, unplayed = accent.copy(alpha = 0.35f))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val total = if (current != null && current.durationMs > 0) {
                    (current.durationMs / 1000).toInt()
                } else {
                    audio.durationSeconds
                }
                // Délku server u skladby nemusí znát (Bot API ji bez metadat pošle jako nulu);
                // „0:00" by lhalo, takže do spuštění se čas neukazuje. Přehrávač ji pak zjistí.
                val text = when {
                    active -> {
                        val position = ((dragFraction?.let { it * current.durationMs } ?: current.positionMs.toFloat()) / 1000).toInt()
                        "${formatDuration(position)} / ${formatDuration(total)}"
                    }
                    total > 0 -> formatDuration(total)
                    else -> ""
                }
                Text(text = text, style = MaterialTheme.typography.labelMedium.tabularNumbers())
                Spacer(Modifier.weight(1f))
                if (active) {
                    SpeedChip(speed = speed(), accent = accent, onAccent = onAccent, onClick = onCycleSpeed)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, played: Color, unplayed: Color) {
    Canvas(Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(unplayed, cornerRadius = radius)
        val playedWidth = size.width * progress
        if (playedWidth > 0f) {
            drawRoundRect(played, topLeft = Offset.Zero, size = Size(playedWidth, size.height), cornerRadius = radius)
        }
    }
}

private val AUDIO_BUTTON_SIZE = 48.dp
private val AUDIO_TEXT_WIDTH = 180.dp
private val BAR_HEIGHT = 4.dp
private val BAR_TOUCH_HEIGHT = 20.dp
private const val SEEK_STEPS = 40
private const val COVER_SCRIM_ALPHA = 0.35f
