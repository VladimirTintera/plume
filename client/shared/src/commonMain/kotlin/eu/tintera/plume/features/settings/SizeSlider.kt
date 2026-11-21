package eu.tintera.plume.features.settings

import androidx.compose.runtime.remember
import androidx.compose.material3.SliderState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_size_default
import eu.tintera.plume.resources.settings_size_value
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Posuvník velikosti s názvem a hodnotou nad ním. Každý dílek tahu tikne ([tick]).
 *
 * [onSizeChange] mění jen náhled volajícího, zápis patří do [onChangeFinished]
 * (viz [MessageTextSizeSetting]).
 */
@Composable
internal fun SizeSlider(
    title: StringResource,
    size: Int,
    default: Int,
    range: IntRange,
    step: Int = 1,
    onSizeChange: (Int) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (size == default) {
                stringResource(Res.string.settings_size_default, size)
            } else {
                stringResource(Res.string.settings_size_value, size)
            },
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val valueLabel = stringResource(Res.string.settings_size_value, size)
    // Varianta se stavem (`Slider(value)` je od material3 1.13 zastaralý); stav si pamatuje rozsah
    // a dílky a hodnota se do něj zapisuje stejně, jako to dělala zastaralá varianta uvnitř.
    val valueRange = range.first.toFloat()..range.last.toFloat()
    // Dílky MEZI krajními hodnotami.
    val steps = (range.last - range.first) / step - 1
    val sliderState = remember(steps, valueRange) { SliderState(size.toFloat(), steps, valueRange) }
    sliderState.value = size.toFloat()
    Slider(
        state = sliderState,
        onValueChange = { value ->
            val next = value.roundToInt().coerceIn(range)
            if (next != size) {
                haptics.tick()
                onSizeChange(next)
            }
        },
        onValueChangeFinished = onChangeFinished,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics { stateDescription = valueLabel },
    )
}
