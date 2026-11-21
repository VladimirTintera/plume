package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import eu.tintera.plume.core.ui.ExpressiveAssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.Ephemeral
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.ephemeral_only_bot
import eu.tintera.plume.resources.ephemeral_only_receiver
import eu.tintera.plume.resources.ephemeral_only_you
import eu.tintera.plume.resources.ephemeral_show_original
import eu.tintera.plume.resources.undo_24dp
import eu.tintera.plume.resources.visibility_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kdo mizející zprávu vidí („Vidíš to jen ty"), a když bot obsah běžné zprávy nahradil jen
 * pro uživatele, čip „Zobrazit původní".
 *
 * **Pod zprávou, ne v ní**, jako počty pod příspěvkem: je to údaj o zprávě, ne její obsah,
 * takže barva patří ploše konverzace. Bez štítku by mizející zpráva od bota ve skupině
 * vypadala jako běžná a uživatel by čekal, že ji vidí i ostatní.
 */
@Composable
internal fun EphemeralFooter(
    ephemeral: Ephemeral,
    isOutgoing: Boolean,
    onShowOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (val name = ephemeral.receiverName) {
        null -> stringResource(Res.string.ephemeral_only_you)
        "" -> stringResource(Res.string.ephemeral_only_bot)
        else -> stringResource(Res.string.ephemeral_only_receiver, name)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Čip u zprávy, štítek za ním — u odchozí zrcadlově (jako komentáře pod příspěvkem).
        if (isOutgoing) EphemeralLabel(label)
        if (ephemeral.replacesOriginal) {
            ExpressiveAssistChip(
                onClick = onShowOriginal,
                label = { Text(stringResource(Res.string.ephemeral_show_original)) },
                leadingIcon = {
                    Icon(
                        painterResource(Res.drawable.undo_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
        if (!isOutgoing) EphemeralLabel(label)
    }
}

@Composable
private fun EphemeralLabel(text: String) {
    Row(
        modifier = Modifier
            .padding(top = 2.dp, start = 4.dp, end = 4.dp)
            .clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painterResource(Res.drawable.visibility_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
