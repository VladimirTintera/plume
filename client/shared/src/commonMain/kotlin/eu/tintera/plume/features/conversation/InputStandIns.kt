package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Spacer
import eu.tintera.plume.core.ui.ButtonEmphasis
import eu.tintera.plume.core.ui.MediumButtonIcon
import eu.tintera.plume.core.ui.MediumButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import eu.tintera.plume.resources.mail_24dp
import eu.tintera.plume.resources.channel_direct_message
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
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
        MediumButton(
            onClick = onJoin,
            enabled = !joining,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(label))
        }
    }
}

/**
 * Místo vstupního pole v kanálu, kde uživatel nepublikuje: ztlumit / zapnout zvuk, jako
 * v oficiálním klientu. Popisek říká, co se stane, ne stav (stejně jako v profilu chatu).
 *
 * Kanál s přímými zprávami ([onMessage]) nabídne vedle i „Napsat" — do kanálu se nepíše, ale
 * jemu ano. Dvě tlačítka stejné šíře, žádné není hlavní.
 */
@Composable
internal fun MuteBar(
    muted: Boolean?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onMessage: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediumButton(
            onClick = onToggle,
            enabled = muted != null,
            emphasis = ButtonEmphasis.Tonal,
            modifier = Modifier.weight(1f),
        ) {
            MediumButtonIcon(if (muted == true) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp)
            Text(stringResource(if (muted == true) Res.string.profile_unmute else Res.string.profile_mute))
        }
        if (onMessage != null) {
            MediumButton(onClick = onMessage, emphasis = ButtonEmphasis.Tonal, modifier = Modifier.weight(1f)) {
                MediumButtonIcon(Res.drawable.mail_24dp)
                Text(stringResource(Res.string.channel_direct_message))
            }
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
