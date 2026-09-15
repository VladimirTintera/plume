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
import eu.tintera.plume.core.ui.Avatar

/**
 * Řádek reakcí pod obsahem bubliny.
 *
 * [FlowRow] schválně: u zprávy může být reakcí víc, než se vejde na šířku, a vodorovný scroll
 * uvnitř bubliny by se pral se scrollem konverzace.
 *
 * **Avatary vs. počet.** U reakce s pár lidmi je zajímavé, KDO reagoval, a ukážou se jejich
 * profilovky. Nad stropem se čip sbalí na emoji + číslo, protože:
 * - u příspěvku se stovkou reakcí nemá výčet tváří smysl a TDLib stejně posílá jen pár posledních,
 * - řada avatarů je široká a v bublině by se reakce lámaly do mnoha řádků.
 *
 * Druhá pojistka je [MAX_REACTIONS_WITH_AVATARS]: když má zpráva hodně různých emoji, sbalí se
 * všechny čipy, i kdyby jednotlivě byly pod stropem. Jinak by pod krátkou zprávou vznikla
 * stěna z profilovek.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionRow(
    reactions: List<MessageReaction>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return

    val showAvatars = reactions.size <= MAX_REACTIONS_WITH_AVATARS

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            ReactionChip(
                reaction = reaction,
                // Avatary jen když je jich málo A zároveň je opravdu máme načtené.
                withAvatars = showAvatars &&
                    reaction.count <= MAX_AVATARS_PER_REACTION &&
                    reaction.recentSenders.isNotEmpty(),
                onClick = { onToggle(reaction.emoji) },
            )
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
    withAvatars: Boolean,
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
            // Emoji záměrně mimo typografickou škálu: role jako `labelLarge` míří na výšku
            // písmen, ale u emoji je nosná celá kresba, takže ve stejné velikosti působí menší
            // než číslo vedle ní.
            Text(text = reaction.emoji, fontSize = 18.sp)

            if (withAvatars) {
                ReactionSenders(reaction)
            } else {
                Text(
                    text = reaction.count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Profilovky těch, kdo reagovali.
 *
 * Avatary se **překrývají** (záporný offset), aby řada zabrala co nejmíň místa — čip musí zůstat
 * srovnatelně široký jako varianta s číslem, jinak by se řádek reakcí lámal jinak podle toho,
 * kolik lidí zrovna reagovalo.
 */
@Composable
private fun ReactionSenders(reaction: MessageReaction) {
    // Záporný rozestup dělá překryv; pozdější avatar se kreslí přes dřívější sám tím,
    // že je v Row později.
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        reaction.recentSenders.take(MAX_AVATARS_PER_REACTION).forEach { sender ->
            Avatar(
                chatId = sender.id,
                title = sender.title,
                avatarFileId = sender.avatarFileId,
                size = 18.dp,
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
    onReply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Odpověď je akce nad zprávou, ne reakce — proto stojí samostatně nad nabídkou emoji.
        Surface(
            onClick = onReply,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Odpovědět",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        Text(
            text = "Reagovat",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp),
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

/**
 * Kolik profilovek se u jedné reakce ukáže. Nad tím se čip sbalí na počet — a musí to sedět
 * s `MAX_SENDERS_SHOWN` v `TdlConversationRepository`, který nad stejným stropem odesílatele
 * vůbec nenačítá.
 */
private const val MAX_AVATARS_PER_REACTION = 3

/** Kolik různých emoji u zprávy ještě snese avatary, než se všechny čipy sbalí na počty. */
private const val MAX_REACTIONS_WITH_AVATARS = 3
