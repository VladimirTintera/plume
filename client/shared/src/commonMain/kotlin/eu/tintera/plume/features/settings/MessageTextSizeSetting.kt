package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.highlightAnchor
import androidx.compose.foundation.background
import androidx.compose.material3.ShapeDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.settings.DEFAULT_MESSAGE_TEXT_SIZE
import eu.tintera.plume.core.settings.MESSAGE_TEXT_SIZES
import eu.tintera.plume.core.ui.LocalMessageTextSize
import eu.tintera.plume.core.ui.messageTextStyle
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_message_text_size
import eu.tintera.plume.resources.settings_message_text_size_default
import eu.tintera.plume.resources.settings_message_text_size_preview_in
import eu.tintera.plume.resources.settings_message_text_size_preview_out
import eu.tintera.plume.resources.settings_message_text_size_value
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Velikost písma zpráv: náhled dvou bublin a posuvník pod ním.
 *
 * **Během tahu se mění jen náhled**, uloží se až po puštění. Velikost jde do kořene aplikace
 * (`LocalMessageTextSize`, statický) a každý zápis by překreslil všechno — na každý dílek tahu.
 */
@Composable
internal fun MessageTextSizeSetting(stored: Int, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    // Klíč `stored`: změna odjinud (obnovené nastavení) přepíše rozpracovaný tah.
    var size by remember(stored) { mutableIntStateOf(stored) }

    Column(Modifier.highlightAnchor(stringResource(Res.string.settings_message_text_size)).padding(vertical = 12.dp)) {
        CompositionLocalProvider(LocalMessageTextSize provides size) {
            PreviewBubbles(Modifier.padding(horizontal = 16.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.settings_message_text_size),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (size == DEFAULT_MESSAGE_TEXT_SIZE) {
                    stringResource(Res.string.settings_message_text_size_default, size)
                } else {
                    stringResource(Res.string.settings_message_text_size_value, size)
                },
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val valueLabel = stringResource(Res.string.settings_message_text_size_value, size)
        Slider(
            value = size.toFloat(),
            onValueChange = { value ->
                val next = value.roundToInt().coerceIn(MESSAGE_TEXT_SIZES)
                if (next != size) {
                    haptics.tick()
                    size = next
                }
            },
            onValueChangeFinished = { onChange(size) },
            valueRange = MESSAGE_TEXT_SIZES.first.toFloat()..MESSAGE_TEXT_SIZES.last.toFloat(),
            // Dílky MEZI krajními hodnotami.
            steps = MESSAGE_TEXT_SIZES.last - MESSAGE_TEXT_SIZES.first - 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics { stateDescription = valueLabel },
        )
    }
}

/**
 * Příchozí a odchozí bublina v barvách konverzace (`surfaceContainerHigh` / `primary`, viz
 * `MessageFrame`). Jen podobizna — skutečná bublina potřebuje zprávu a celou konverzaci kolem.
 *
 * Leží na `surface` jako v konverzaci: na kartě nastavení by příchozí bublina s kartou splývala.
 */
@Composable
private fun PreviewBubbles(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, ShapeDefaults.Large)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PreviewBubble(
            text = stringResource(Res.string.settings_message_text_size_preview_in),
            container = colors.surfaceContainerHigh,
            content = colors.onSurface,
            modifier = Modifier.align(Alignment.Start),
        )
        PreviewBubble(
            text = stringResource(Res.string.settings_message_text_size_preview_out),
            container = colors.primary,
            content = colors.onPrimary,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun PreviewBubble(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = messageTextStyle(),
        color = content,
        modifier = modifier
            .widthIn(max = 280.dp)
            .background(container, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
