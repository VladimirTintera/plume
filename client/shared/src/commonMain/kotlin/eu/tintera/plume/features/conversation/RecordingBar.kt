package eu.tintera.plume.features.conversation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_continue_recording
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.resources.content_video_note
import eu.tintera.plume.resources.conversation_play_video_note
import eu.tintera.plume.resources.conversation_stop_video_note
import eu.tintera.plume.resources.chevron_left_24dp
import eu.tintera.plume.resources.conversation_discard_recording
import eu.tintera.plume.resources.conversation_recording_slide_to_cancel
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.conversation_play_voice
import eu.tintera.plume.resources.conversation_stop_recording
import eu.tintera.plume.resources.conversation_voice_progress
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.stop_24dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh nahrávání ve vstupní kartě místo textového pole: zahodit, tečka, čas a živé
 * úrovně hlasitosti. Odesílá se tlačítkem vedle karty, stejným místem jako text.
 *
 * - **Stejná výška jako jednořádkové pole** (56 dp) — karta při přepnutí neposkočí.
 * - **[recording] je lambda** a čte se až tady: čas a úrovně se mění po 50 ms.
 * - Tečka je v barvě `error`: nahrávání je stav, který se nemá přehlédnout (mikrofon je otevřený).
 *   Nebliká — živost nese tlačítko odeslání, které při nahrávání dýchá (morf kruh ↔ SoftBurst);
 *   dva blikající prvky vedle sebe by byly šum.
 * - Čas má číslice stejné šíře (`tnum`), aby text při každé vteřině necukal.
 * - **Dokud prst drží tlačítko ([holding])**, není tu koš ani úrovně, ale nápověda „přejetím
 *   doleva zrušíš", která jede s prstem a slábne ([cancelProgress]). Koš by šlo zmáčknout jen
 *   druhou rukou, a tah doleva je tu ta cesta, kterou si uživatel má všimnout. Po zamčení se
 *   vrátí koš i úrovně.
 * - **Zamčená nahrávka má na konci ■ ([onStop])**: nahrávání skončí, ale nic neodejde — pruh
 *   se změní na náhled ([VoicePreviewBar], u kolečka [VideoNotePreviewBar]) a nahrávka jde nejdřív
 *   poslechnout nebo přehrát. [video] mění jen popis ■ pro čtečku obrazovky.
 */
@Composable
fun RecordingBar(
    recording: () -> VoiceRecording?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    holding: Boolean = false,
    cancelProgress: () -> Float = { 0f },
    onStop: (() -> Unit)? = null,
    video: Boolean = false,
) {
    val current = recording()
    // Karta pole je při nahrávání `tertiaryContainer` (hlas je v aplikaci terciární, stejně jako
    // tlačítko vedle); v paletě se zvýšeným kontrastem na ní `tertiary` splyne (viz `accentOn`).
    val barColor = accentOn(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        accent = MaterialTheme.colorScheme.tertiary,
        fallback = MaterialTheme.colorScheme.onTertiaryContainer,
    )

    val stop = onStop?.takeIf { !holding }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = if (stop != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (holding) {
            Spacer(Modifier.width(20.dp))
        } else {
            IconButton(onClick = onCancel) {
                Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.conversation_discard_recording))
            }
        }
        Box(
            Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(((current?.elapsedMs ?: 0L) / 1000).toInt()),
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
        )
        Spacer(Modifier.width(12.dp))

        if (holding) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer {
                        val progress = cancelProgress().coerceIn(0f, 1f)
                        translationX = -SLIDE_HINT_TRAVEL.toPx() * progress
                        alpha = 1f - progress
                    },
                ) {
                    Icon(
                        painterResource(Res.drawable.chevron_left_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(Res.string.conversation_recording_slide_to_cancel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            return@Row
        }

        val levels = current?.recentLevels.orEmpty()
        Canvas(Modifier.weight(1f).height(28.dp)) {
            val slots = LIVE_LEVEL_BARS
            val step = size.width / slots
            val barWidth = step * 0.6f
            // Nejnovější úroveň vpravo; dokud jich není dost, zleva je prázdno.
            levels.takeLast(slots).reversed().forEachIndexed { fromEnd, level ->
                val height = barWidth + (size.height - barWidth) * level
                val left = size.width - (fromEnd + 1) * step
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, (size.height - height) / 2),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
        }
        stop?.let {
            IconButton(onClick = it) {
                Icon(
                    painterResource(Res.drawable.stop_24dp),
                    contentDescription = stringResource(if (video) Res.string.conversation_stop_video_note else Res.string.conversation_stop_recording),
                )
            }
        }
    }
}

/**
 * Zastavená hlasovka ve vstupní kartě, k poslechu před odesláním: koš, přehrát / pozastavit,
 * waveform s průběhem a čas. Odesílá se tlačítkem vedle karty, jako při nahrávání.
 *
 * - **Stejná výška i barvy jako pruh nahrávání** — karta se při zastavení nepřeskládá, jen se
 *   živé úrovně vymění za celý waveform.
 * - **Waveform je týž jako v bublině hlasovky** ([Waveform], [seekGestures]): uživatel slyší
 *   a vidí přesně to, co protějšek dostane, a posouvá se ťuknutím i tažením stejně.
 * - **[playback] je lambda**, pozice se mění dvacetkrát za vteřinu. `null` = náhled nehraje.
 * - Dokud se nahrávka dokončuje ([VoicePreview.voice] `null`, na iOS se kóduje), točí se místo
 *   tlačítka přehrání indikátor a waveform je prázdný.
 */
@Composable
fun VoicePreviewBar(
    preview: VoicePreview,
    playback: () -> VoicePlayback?,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Nahrávat dál; mikrofon v pruhu je jen u nahrávky, na kterou navázat jde
     * ([VoicePreview.canContinue] — M4A ze staršího Androidu ne).
     */
    onContinue: (() -> Unit)? = null,
) {
    val current = playback()
    val voice = preview.voice
    val continuable = onContinue != null && voice != null && preview.canContinue
    val accent = accentOn(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        accent = MaterialTheme.colorScheme.tertiary,
        fallback = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    Row(
        // Mikrofon na konci si nese vlastní odsazení, jako ■ v pruhu nahrávání.
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = if (continuable) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDiscard) {
            Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.conversation_discard_recording))
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            when {
                voice == null || current?.loading == true ->
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else -> IconButton(onClick = onToggle) {
                    Icon(
                        painterResource(if (current?.playing == true) Res.drawable.pause_24dp else Res.drawable.play_arrow_24dp),
                        contentDescription = stringResource(
                            if (current?.playing == true) Res.string.media_pause else Res.string.conversation_play_voice,
                        ),
                        tint = accent,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))

        var dragFraction by remember { mutableStateOf<Float?>(null) }
        val active = current != null && !current.loading
        val progressLabel = stringResource(Res.string.conversation_voice_progress)
        Waveform(
            bars = preview.bars,
            progress = dragFraction ?: current?.progress ?: 0f,
            played = accent,
            unplayed = accent.copy(alpha = 0.35f),
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .semantics { contentDescription = progressLabel }
                .seekGestures(active = active, steps = preview.bars.size.coerceAtLeast(1), onDrag = { dragFraction = it }, onSeek = onSeek),
        )
        Spacer(Modifier.width(12.dp))
        // Hraje-li, kde přehrávání je; jinak celá délka, jako čas u hlasovky v bublině.
        val seconds = if (active) {
            ((dragFraction?.let { it * current.durationMs } ?: current.positionMs.toFloat()) / 1000).toInt()
        } else {
            voice?.durationSeconds ?: 0
        }
        Text(
            text = formatDuration(seconds),
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
        )
        /*
         * Mikrofon na konci pruhu, na místě ■ z nahrávání: to je ta cesta zpátky. Nahrávání pak
         * pokračuje od konce náhledu a odejde jako jedna hlasovka. Dokud se nahrávka dokončuje,
         * není na co navázat.
         */
        if (continuable) {
            IconButton(onClick = onContinue) {
                Icon(
                    painterResource(Res.drawable.mic_24dp),
                    contentDescription = stringResource(Res.string.conversation_continue_recording),
                    tint = accent,
                )
            }
        }
    }
}

/**
 * Zastavené kolečko ve vstupní kartě: koš, přehrát se zvukem / zastavit a čas. Kolečko samo hraje
 * v kruhu nad konverzací; pruh je jen ovládání a má stejnou výšku i barvy jako pruh nahrávání,
 * takže se karta při ■ nepřeskládá.
 *
 * Čas je délka kolečka, se zvukem uplynulý čas — jako odznak u kolečka v bublině.
 */
@Composable
internal fun VideoNotePreviewBar(
    preview: VideoNotePreview,
    playback: VideoNotePreviewPlayback,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    /** Nahrávat dál ([VideoNotePreview.canContinue]) — kamera na konci pruhu, jako mikrofon u hlasovky. */
    onContinue: (() -> Unit)? = null,
) {
    val note = preview.note
    val continuable = onContinue != null && note != null && preview.canContinue
    val accent = accentOn(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        accent = MaterialTheme.colorScheme.tertiary,
        fallback = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = if (continuable) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDiscard) {
            Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.conversation_discard_recording))
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (note == null) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = playback::toggle) {
                    Icon(
                        painterResource(if (playback.sounding) Res.drawable.pause_24dp else Res.drawable.play_arrow_24dp),
                        contentDescription = stringResource(
                            if (playback.sounding) Res.string.media_pause else Res.string.conversation_play_video_note,
                        ),
                        tint = accent,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(Res.string.content_video_note),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        val seconds = if (playback.sounding) (playback.elapsedMs / 1000).toInt() else note?.durationSeconds ?: 0
        Text(
            text = formatDuration(seconds),
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
        )
        if (continuable) {
            IconButton(onClick = onContinue) {
                Icon(
                    painterResource(Res.drawable.videocam_24dp),
                    contentDescription = stringResource(Res.string.conversation_continue_recording),
                    tint = accent,
                )
            }
        }
    }
}

private const val LIVE_LEVEL_BARS = 40

/** Kam až nápověda „přejetím zrušíš" odjede, než zmizí. */
private val SLIDE_HINT_TRAVEL = 48.dp
