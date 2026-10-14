package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.profile_mute
import eu.tintera.plume.resources.profile_unmute
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import eu.tintera.plume.resources.conversation_join
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Místo vstupního pole ve skupině nebo kanálu, kde uživatel není (otevřeno z globálního hledání),
 * a u nespuštěného bota se Start ([label]). Plné tlačítko přes šířku — je to jediná akce,
 * kterou tu jde udělat.
 */
@Composable
internal fun JoinBar(
    joining: Boolean,
    onJoin: () -> Unit,
    label: StringResource = Res.string.conversation_join,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onJoin,
            enabled = !joining,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(label))
        }
    }
}

/**
 * Místo vstupního pole v kanálu, kde uživatel nepublikuje: ztlumit / zapnout zvuk, jako
 * v oficiálním klientu. Popisek říká, co se stane, ne stav (stejně jako v profilu chatu).
 */
@Composable
internal fun MuteBar(muted: Boolean?, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        FilledTonalButton(
            onClick = onToggle,
            enabled = muted != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(
                painterResource(if (muted == true) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (muted == true) Res.string.profile_unmute else Res.string.profile_mute))
        }
    }
}

/** Místo vstupního pole tam, kde psát nejde a nic se s tím udělat nedá — aspoň proč. */
@Composable
internal fun ReadOnlyNote(text: StringResource, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    )
}
