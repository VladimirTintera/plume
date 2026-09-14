package eu.tintera.plume.features.conversation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import eu.tintera.plume.core.telegram.MessageReaction

/**
 * Řádek reakcí pod obsahem bubliny.
 *
 * [FlowRow] schválně: u zprávy může být reakcí víc, než se vejde na šířku, a vodorovný scroll
 * uvnitř bubliny by se pral se scrollem konverzace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionRow(
    reactions: List<MessageReaction>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            ReactionChip(reaction = reaction, onClick = { onToggle(reaction.emoji) })
        }
    }
}

/**
 * Jeden čip reakce.
 *
 * Vlastní reakce se odliší **tvarem i barvou naráz**: zaoblení se stáhne z kapsle do měkkého
 * obdélníku a čip lehce povyroste. Obojí jede přes `MaterialTheme.motionScheme`, takže to má
 * ten samý pružný charakter jako zbytek aplikace — barva bez bounce, tvar a velikost s ním.
 */
@Composable
private fun ReactionChip(
    reaction: MessageReaction,
    onClick: () -> Unit,
) {
    val chosen = reaction.isChosen

    val corner by animateDpAsState(
        targetValue = if (chosen) 10.dp else 20.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "reaction-corner",
    )
    val scale by animateFloatAsState(
        targetValue = if (chosen) 1.06f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "reaction-scale",
    )
    val container by animateColorAsState(
        targetValue = if (chosen) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        // Barvy bez bounce — overshoot u barvy se ořízne a působí jako bliknutí, ne jako pružnost.
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "reaction-container",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = if (chosen) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.scale(scale),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(text = reaction.emoji, fontSize = 15.sp)
            Text(
                text = reaction.count.toString(),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Výběr reakce po dlouhém stisku zprávy. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReactionPickerSheet(
    picker: ReactionPicker,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "Reagovat",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            picker.emoji.forEach { emoji ->
                Surface(
                    onClick = { onPick(emoji) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(52.dp),
                ) {
                    Text(
                        text = emoji,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}
