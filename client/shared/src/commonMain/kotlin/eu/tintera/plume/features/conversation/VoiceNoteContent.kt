package eu.tintera.plume.features.conversation

import androidx.compose.foundation.Canvas
import androidx.compose.material3.ShapeDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import eu.tintera.plume.core.ui.MorphShape
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.animateMorph
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.resampleWaveform
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.features.conversation.items.BelowWithSameWidth
import eu.tintera.plume.features.conversation.items.TranscribeButton
import eu.tintera.plume.features.conversation.items.TranscriptState
import eu.tintera.plume.features.conversation.items.TranscriptText
import androidx.compose.material3.LocalContentColor
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_play_voice
import eu.tintera.plume.resources.conversation_voice_progress
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.voice_speed_double
import eu.tintera.plume.resources.voice_speed_fast
import eu.tintera.plume.resources.voice_speed_normal
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hlasovka v bublině: tlačítko, waveform s průběhem a čas.
 *
 * - **[playback] je lambda**, ne hodnota. Pozice se mění dvacetkrát za vteřinu a čte se až tady,
 *   takže se překresluje jen tahle hlasovka, ne celá konverzace. `null` = tahle nehraje.
 * - **Ťuknutí nebo tažení po waveformu posouvá** — jen u hlasovky, která je vybraná. Tažení
 *   pointerInput spotřebuje, takže se na waveformu nerozjede swipe na odpověď.
 * - **Tečka u času = neposlechnutá.** Telegram ji ukazuje u příchozí, dokud ji nepustíme, a u odchozí,
 *   dokud si ji nepustí protějšek.
 * - **Čip rychlosti je jen u hrající hlasovky.** U ostatních by z něj byl další ovládací prvek
 *   v každé bublině, a přitom se rychlost mění jednou za čas. Místo si drží i tak (šířka řádku
 *   je daná waveformem), takže se bublina jeho objevením nepřeskládá.
 * - Barvy přicházejí zvenku ([accent] / [onAccent]) ze stejného důvodu jako u citace: v odchozí
 *   `primary` bublině by `primary` tlačítko zmizelo.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VoiceNoteContent(
    voice: MessageMedia.VoiceNote,
    playback: () -> VoicePlayback?,
    speed: () -> VoiceSpeed,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    accent: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
    transcript: TranscriptState? = null,
) {
    BelowWithSameWidth(
        modifier = modifier,
        top = {
            VoiceRow(voice, playback, speed, onToggle, onSeek, onCycleSpeed, accent, onAccent, transcript)
        },
        below = {
            if (transcript != null) TranscriptText(transcript, color = LocalContentColor.current)
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoiceRow(
    voice: MessageMedia.VoiceNote,
    playback: () -> VoicePlayback?,
    speed: () -> VoiceSpeed,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    accent: Color,
    onAccent: Color,
    transcript: TranscriptState?,
) {
    val current = playback()
    // Dlouhá hlasovka, kterou uživatel nedoposlouchal: průběh a čas tam, kde přestal.
    val remembered = rememberedAudioPosition(voice.fileKey).takeIf { current == null }
    val bars = remember(voice.waveform) { resampleWaveform(voice.waveform, VOICE_BARS) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Tvar nese stav jako tlačítko odeslání: připraveno / pozastaveno = sušenka, hraje (nebo se
        // stahuje) = kruh. Mění se morfem, takže je start i zastavení vidět i koutkem oka.
        val rest = animateMorph(current?.playing != true && current?.loading != true, "voice-button-shape")
        FilledIconButton(
            onClick = onToggle,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = onAccent),
            shape = MorphShape(ShapeMorphs.Rest, rest, rotation = ShapeMorphs.REST_ROTATION),
            modifier = Modifier.size(VOICE_BUTTON_SIZE),
        ) {
            when {
                current?.loading == true -> CircularWavyProgressIndicator(color = onAccent, modifier = Modifier.size(28.dp))
                current?.playing == true -> Icon(painterResource(Res.drawable.pause_24dp), contentDescription = stringResource(Res.string.media_pause))
                else -> Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = stringResource(Res.string.conversation_play_voice))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val active = current != null && !current.loading
            // `semantics` není composable blok, takže se popisek musí přečíst tady.
            val progressLabel = stringResource(Res.string.conversation_voice_progress)
            // Během tažení se ukazuje místo, kam prst míří, ne kde přehrávač zrovna je.
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val progress = dragFraction ?: current?.progress ?: remembered?.fraction ?: 0f

            Waveform(
                bars = bars,
                progress = progress,
                played = accent,
                unplayed = accent.copy(alpha = 0.35f),
                modifier = Modifier
                    .width(VOICE_WAVEFORM_WIDTH)
                    .height(VOICE_WAVEFORM_HEIGHT)
                    .semantics { contentDescription = progressLabel }
                    .seekGestures(
                        active = active,
                        steps = VOICE_BARS,
                        onDrag = { dragFraction = it },
                        onSeek = onSeek,
                    ),
            )

            Row(
                Modifier.width(VOICE_WAVEFORM_WIDTH),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val text = when {
                    active ->
                        formatDuration(((dragFraction?.let { it * current.durationMs } ?: current.positionMs.toFloat()) / 1000).toInt())
                    remembered != null ->
                        "${formatDuration((remembered.positionMs / 1000).toInt())} / ${formatDuration(voice.durationSeconds)}"
                    else -> formatDuration(voice.durationSeconds)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                )
                if (!voice.isListened) {
                    Box(Modifier.size(6.dp).background(accent, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                if (active) {
                    SpeedChip(speed = speed(), accent = accent, onAccent = onAccent, onClick = onCycleSpeed)
                }
            }
        }

        if (transcript?.hasButton == true) {
            TranscribeButton(
                state = transcript,
                container = accent.copy(alpha = 0.15f),
                content = accent,
            )
        }
    }
}

/**
 * Ťuknutí a tažení po průběhu posouvá přehrávání — u hlasovky po waveformu, u hudby po liště.
 *
 * Jen u vybraného zvuku ([active]); tažení `pointerInput` spotřebuje, takže se na průběhu
 * nerozjede swipe na odpověď. Během tažení hlásí [onDrag] místo pod prstem (po puštění `null`)
 * a tiká po [steps] dílcích, ne na každý pixel pohybu.
 */
internal fun Modifier.seekGestures(
    active: Boolean,
    steps: Int,
    onDrag: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    this
        .pointerInput(active) {
            if (!active) return@pointerInput
            detectTapGestures { offset -> onSeek(offset.x / size.width) }
        }
        .pointerInput(active) {
            if (!active) return@pointerInput
            var tickedStep = -1
            var dragFraction: Float? = null
            fun at(x: Float): Float = (x / size.width).coerceIn(0f, 1f)
            fun tickAt(fraction: Float) {
                val step = (fraction * steps).toInt().coerceIn(0, steps - 1)
                if (step != tickedStep) {
                    tickedStep = step
                    haptics.tick()
                }
            }
            fun drag(fraction: Float) {
                dragFraction = fraction
                onDrag(fraction)
                tickAt(fraction)
            }
            detectHorizontalDragGestures(
                onDragStart = { offset -> drag(at(offset.x)) },
                onDragEnd = {
                    dragFraction?.let(onSeek)
                    dragFraction = null
                    onDrag(null)
                },
                onDragCancel = {
                    dragFraction = null
                    onDrag(null)
                },
            ) { change, _ ->
                change.consume()
                drag(at(change.position.x))
            }
        }
}

/**
 * Čip rychlosti u hrající hlasovky: ťuknutí přepne na další (1× → 1,5× → 2× → 1×).
 *
 * Jen tónovaný, ne plný akcent — hlavní ovládání je tlačítko přehrání a čip vedle času má být
 * k dispozici, ne přitahovat pozornost. Číslice jsou `tnum`, jinak by „1×" a „1,5×" cukaly.
 */
@Composable
internal fun SpeedChip(speed: VoiceSpeed, accent: Color, onAccent: Color, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(
        when (speed) {
            VoiceSpeed.Normal -> Res.string.voice_speed_normal
            VoiceSpeed.Fast -> Res.string.voice_speed_fast
            VoiceSpeed.Double -> Res.string.voice_speed_double
        },
    )
    LabelChip(
        label = label,
        accent = accent,
        onAccent = onAccent,
        onClick = {
            // Přepnutí stavu, tedy `toggle` — jenže hodnoty jsou tři a „zapnuto" mezi nimi není.
            haptics.tick()
            onClick()
        },
    )
}

/**
 * Tónovaný čip s krátkým popiskem — vzhled [SpeedChip], sdílený s čipem kvality videa, aby
 * v liště přehrávače vypadaly jako jedna rodina. [description] je pro čtečku, když popisek sám
 * nestačí („720p" → „Kvalita videa: 720p").
 */
@Composable
internal fun LabelChip(
    label: String,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = label,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = onAccent,
        modifier = modifier
            .clip(SPEED_CHIP_SHAPE)
            .background(accent.copy(alpha = SPEED_CHIP_ALPHA))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = description },
    )
}

/** Sloupce waveformu; přehraná část plnou barvou akcentu, zbytek průsvitnou. */
@Composable
internal fun Waveform(
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

private val SPEED_CHIP_SHAPE = ShapeDefaults.Small

/** Jak sytý je podklad čipu. Plný akcent by z čipu udělal druhé tlačítko vedle přehrání. */
private const val SPEED_CHIP_ALPHA = 0.85f
private val VOICE_BUTTON_SIZE = 44.dp
private val VOICE_WAVEFORM_WIDTH = 160.dp
private val VOICE_WAVEFORM_HEIGHT = 28.dp
