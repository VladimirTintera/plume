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
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_discard_recording
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
 */
@Composable
fun RecordingBar(
    recording: () -> VoiceRecording?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = recording()
    val barColor = MaterialTheme.colorScheme.primary

    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.conversation_discard_recording))
        }
        Box(
            Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(((current?.elapsedMs ?: 0L) / 1000).toInt()),
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.width(12.dp))

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
