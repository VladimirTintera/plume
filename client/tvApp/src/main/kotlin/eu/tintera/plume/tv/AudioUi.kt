package eu.tintera.plume.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.skip_next_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Obsah bubliny s hlasovkou nebo skladbou: tlačítko stavu, u hlasovky vlna (přehraná část
 * v barvě motivu), u skladby obal, název a interpret. Spouští se OK na bublině
 * ([mediaOpenAction]); tlačítko je jen ukazatel stavu, ne samostatný fokus — po zprávách se
 * chodí šipkami a druhý cíl uvnitř bubliny by každou zastávku zdvojil.
 */
@Composable
fun AudioBubbleContent(message: ChatMessage, contentColor: Color) {
    val audio = LocalAudioPlayer.current
    val current = audio?.current?.takeIf { it.messageId == message.id }
    val playing = current != null && audio.playing
    val progress = if (current != null && audio.durationMs > 0) (audio.positionMs.toFloat() / audio.durationMs).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayStateButton(playing = playing, loading = current != null && audio.loading)
        Spacer(Modifier.width(16.dp))
        when (val media = message.media) {
            is MessageMedia.VoiceNote -> {
                Waveform(media.waveform, progress, contentColor, Modifier.width(WAVEFORM_WIDTH).height(36.dp))
                Spacer(Modifier.width(16.dp))
                val seconds = if (current != null) (audio.positionMs / 1000).toInt() else media.durationSeconds
                Text(seconds.asDuration(), style = MaterialTheme.typography.labelLarge.tabular(), color = contentColor)
                // Nepřehraná příchozí hlasovka má tečku, jako na telefonu.
                if (!message.isOutgoing && !media.isListened && current == null) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            is MessageMedia.Audio -> {
                Cover(media.coverFileId, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.widthIn(max = 420.dp)) {
                    Text(
                        media.title.ifBlank { media.fileName },
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    media.performer?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.75f), maxLines = 1)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current != null) {
                            ProgressLine(progress, contentColor, Modifier.width(220.dp))
                            Spacer(Modifier.width(12.dp))
                            Text((audio.positionMs / 1000).toInt().asDuration() + " / ", style = MaterialTheme.typography.labelMedium.tabular(), color = contentColor)
                        }
                        // Délku nemusí odesílatel uvést (bot bez `duration`); pak ji ví až přehrávač.
                        val total = if (current != null && audio.durationMs > 0) (audio.durationMs / 1000).toInt() else media.durationSeconds
                        if (total > 0) {
                            Text(total.asDuration(), style = MaterialTheme.typography.labelMedium.tabular(), color = contentColor)
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

/**
 * Dock přehrávače dole uprostřed: co hraje, průběh a čas. Není zaostřitelný — ovládá se
 * tlačítky médií na ovladači (`MediaSession`) nebo OK na bublině té zprávy; na televizi by
 * další cíl fokusu přes obsah jen překážel. Vyjede, když něco hraje, a zmizí s koncem fronty.
 */
@Composable
fun AudioDock(audio: TvAudioPlayer, modifier: Modifier) {
    val item = audio.current
    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val shown = item ?: return@AnimatedVisibility
        val progress = if (audio.durationMs > 0) (audio.positionMs.toFloat() / audio.durationMs).coerceIn(0f, 1f) else 0f
        Row(
            Modifier
                .padding(bottom = SAFE_VERTICAL)
                .widthIn(max = 720.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlayStateButton(playing = audio.playing, loading = audio.loading)
            if (shown.voice) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.mic_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            } else {
                Cover(shown.coverFileId, 48.dp)
            }
            Column(Modifier.width(320.dp)) {
                Text(
                    shown.title.ifBlank { stringResource(Res.string.conversation_voice_message) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Pod jménem odesílatele hlasovky „Hlasová zpráva"; bez jména je to už nadpis.
                val subtitle = shown.performer?.takeIf { it.isNotBlank() }
                    ?: if (shown.voice && shown.title.isNotBlank()) stringResource(Res.string.conversation_voice_message) else null
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                ProgressLine(progress, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth())
            }
            Text(
                "${(audio.positionMs / 1000).toInt().asDuration()} / ${(audio.durationMs / 1000).toInt().asDuration()}",
                style = MaterialTheme.typography.labelLarge.tabular(),
            )
            // Že je ve frontě další, a že ho pustí tlačítko „další" na ovladači.
            if (audio.index < audio.queue.lastIndex) {
                Icon(painterResource(Res.drawable.skip_next_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Kolik místa nechat dole v seznamech, dokud je vidět dock: jinak by zakryl poslední řádky
 * a zaostřený řádek u spodního okraje by byl pod ním.
 */
@Composable
fun audioDockInset(): androidx.compose.ui.unit.Dp = if (LocalAudioPlayer.current?.current != null) DOCK_INSET else 0.dp

private val DOCK_INSET = 96.dp

/** Kruh s přehrát / pozastavit; dokud se soubor stahuje, měnící se tvar čekání. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayStateButton(playing: Boolean, loading: Boolean) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            LoadingIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
        } else {
            Icon(
                painterResource(if (playing) Res.drawable.pause_24dp else Res.drawable.play_arrow_24dp),
                contentDescription = stringResource(if (playing) Res.string.media_pause else Res.string.media_play),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Obal skladby (z ID3 / od serveru), jinak nota. */
@Composable
private fun Cover(fileId: Int?, size: androidx.compose.ui.unit.Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val image = rememberImage(fileId, px)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size)) }
            ?: Icon(painterResource(Res.drawable.music_note_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/**
 * Vlna hlasovky: sloupce z `waveform` od TDLib (hodnoty 0–31) sesypané na [BARS] sloupců,
 * přehraná část ([progress]) v barvě motivu, zbytek v barvě textu bubliny.
 */
@Composable
private fun Waveform(waveform: List<Int>, progress: Float, color: Color, modifier: Modifier) {
    val played = MaterialTheme.colorScheme.primary
    val bars = remember(waveform) { downsample(waveform, BARS) }
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (BARS - 1)) / BARS
        bars.forEachIndexed { i, value ->
            val height = (size.height * (0.15f + 0.85f * value)).coerceAtLeast(barWidth)
            drawRoundRect(
                color = if ((i + 0.5f) / BARS <= progress) played else color.copy(alpha = 0.5f),
                topLeft = Offset(i * (barWidth + gap), (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/** Průběh jako tenká čára s vyplněnou částí. */
@Composable
private fun ProgressLine(progress: Float, color: Color, modifier: Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.3f))) {
        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(MaterialTheme.colorScheme.primary))
    }
}

/** Hodnoty vlny (0–31) do [count] sloupců 0f–1f, průměrem přes úseky. Prázdná vlna = nízké sloupce. */
private fun downsample(waveform: List<Int>, count: Int): List<Float> {
    if (waveform.isEmpty()) return List(count) { 0.1f }
    return List(count) { i ->
        val from = i * waveform.size / count
        val to = ((i + 1) * waveform.size / count).coerceAtLeast(from + 1).coerceAtMost(waveform.size)
        waveform.subList(from, to).average().toFloat() / 31f
    }
}

private const val BARS = 40
private val WAVEFORM_WIDTH = 260.dp
