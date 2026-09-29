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
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chevron_left_24dp
import eu.tintera.plume.resources.conversation_discard_recording
import eu.tintera.plume.resources.conversation_recording_slide_to_cancel
import eu.tintera.plume.resources.delete_24dp
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
 */
@Composable
fun RecordingBar(
    recording: () -> VoiceRecording?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    holding: Boolean = false,
    cancelProgress: () -> Float = { 0f },
) {
    val current = recording()
    // Karta pole je při nahrávání `tertiaryContainer` (hlas je v aplikaci terciární, stejně jako
    // tlačítko vedle); v paletě se zvýšeným kontrastem na ní `tertiary` splyne (viz `accentOn`).
    val barColor = accentOn(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        accent = MaterialTheme.colorScheme.tertiary,
        fallback = MaterialTheme.colorScheme.onTertiaryContainer,
    )

    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 16.dp),
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
    }
}

private const val LIVE_LEVEL_BARS = 40

/** Kam až nápověda „přejetím zrušíš" odjede, než zmizí. */
private val SLIDE_HINT_TRAVEL = 48.dp
