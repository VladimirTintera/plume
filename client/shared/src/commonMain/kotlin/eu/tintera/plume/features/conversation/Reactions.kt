package eu.tintera.plume.features.conversation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_react
import eu.tintera.plume.resources.reactions_title
import eu.tintera.plume.resources.reactions_you
import org.jetbrains.compose.resources.stringResource

/**
 * Řádek reakcí u zprávy.
 *
 * **Jeden řádek, který se posouvá do strany**, ne lámání do víc řádků: čipy jsou zapuštěné do
 * bubliny a druhý řádek by ji přerostl. Svislý scroll konverzace to neruší — vodorovný posun
 * konzumuje jen vodorovné tahy, a ty by jinak patřily odkrývání časů.
 *
 * **Ťuknutí otevírá detail, nepřepíná reakci.** Kdo reagoval, se odjinud nedozvíme (čip nese jen
 * emoji a počet), a přidat nebo odebrat jde z nabídky nad zprávou, kde je i výběr emoji.
 */
@Composable
fun ReactionRow(
    reactions: List<MessageReaction>,
    /** `null` tam, kde detail není k dispozici — pak čip nereaguje na ťuknutí a nemá ani vlnku. */
    onOpenDetails: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            // Klíč podle emoji: server pořadí reakcí mění (optimisticky přidaná jde na konec,
            // jeho odpověď ji může dát jinam). Bez klíče by se stav animací přenesl na sousední
            // čip a ten by přeblikl do cizí barvy a tvaru.
            key(reaction.emoji) {
                ReactionChip(reaction = reaction, onClick = onOpenDetails)
            }
        }
    }
}

/**
 * Jeden čip reakce: kolečko s emoji a počtem jako malým odznakem v rohu.
 *
 * Kolečko, ne kapsle s číslem vedle emoji: čip leží PŘES nároží bubliny, takže musí být co
 * nejmenší a kulatý tvar na to sedí líp než pilulka. Počet je proto odznak přes pravý horní roh,
 * ne další prvek v řádku — tak to dělá Google Messages.
 *
 * Profilovky těch, kdo reagovali, tu byly a jsou pryč — čip s nimi byl široký, takže se jich
 * vedle sebe vešlo pár, a stejně ukazovaly jen několik posledních. Kdo reagoval, teď říká detail.
 *
 * **Počet se vypisuje až od dvou.** U jediné reakce je číslo „1" šum; že tam někdo je, říká
 * samotný čip.
 *
 * Vlastní reakce se odliší barvou a lehkým zvětšením. Barva jede bez bounce — overshoot u barvy
 * se ořízne a působí jako bliknutí, ne jako pružnost.
 */
@Composable
private fun ReactionChip(
    reaction: MessageReaction,
    onClick: (() -> Unit)?,
) {
    val chosen = reaction.isChosen

    val scale by animateFloatAsState(
        targetValue = if (chosen) 1.06f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "reaction-scale",
    )
    val container by animateColorAsState(
        // `primaryContainer`, ne `primary`: odchozí bublina je sama `primary`, takže by vybraná
        // reakce na vlastní zprávě splynula s pozadím. Světlý čip je čitelný na obojím.
        targetValue = if (chosen) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "reaction-container",
    )
    val content = if (chosen) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    /*
     * Místo pro odznak si vyhradí obálka, odznak z čipu NEČOUHÁ.
     *
     * Řádek reakcí se posouvá do strany a posouvatelný kontejner si obsah ořezává na svoje
     * hranice — odznak zavěšený mimo kolečko by se tím uřízl.
     */
    Box(Modifier.scale(scale)) {
        Surface(
            shape = CircleShape,
            color = container,
            contentColor = content,
            // Čip leží NA bublině; bez stínu by na tmavším pozadí splynul s jejím okrajem.
            shadowElevation = 2.dp,
            modifier = Modifier
                .padding(top = REACTION_BADGE_ROOM, end = REACTION_BADGE_ROOM)
                .size(REACTION_CHIP_SIZE),
        ) {
            Box(
                // Klikání je až uvnitř `Surface`, takže ho tvar ořízne — jinak by vlnka přetekla
                // přes kulaté nároží čipu.
                Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                // Emoji záměrně mimo typografickou škálu: role jako `labelLarge` míří na výšku
                // písmen, ale u emoji je nosná celá kresba, takže ve stejné velikosti působí menší.
                Text(text = reaction.emoji, fontSize = 17.sp)
            }
        }

        if (reaction.count > 1) {
            Surface(
                shape = CircleShape,
                color = container,
                contentColor = content,
                shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Text(
                    text = reaction.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** Průměr kolečka s emoji. */
private val REACTION_CHIP_SIZE = 30.dp

/** Kolik místa si čip nechá nahoře a vpravo na odznak s počtem. */
private val REACTION_BADGE_ROOM = 7.dp

/**
 * Plný seznam reakcí.
 *
 * Otevírá se z tlačítka „+" v nabídce nad zprávou — ta unese jen pár emoji na jedno ťuknutí,
 * zbytek je tady. Bottom sheet zůstal schválně: nabídnutých emoji bývá přes třicet, takže se
 * v tom listuje, a to popup neumí.
 *
 * Akce nad zprávou (odpovědět, kopírovat, smazat) sem **nepatří** — mají vlastní nabídku
 * u zprávy, viz [MessageMenuOverlay].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReactionPickerSheet(
    picker: ReactionPicker,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Modální sheet začíná skrytý a rozjede se sám. Náhrada za `rememberModalBottomSheetState`,
    // které je od Compose 1.12 zastaralé.
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.conversation_react),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp),
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
 * Detail reakcí: kdo a čím reagoval.
 *
 * Otevírá se **ťuknutím na čip reakce** — jinde se to nedozvíme, protože čip nese jen emoji
 * a počet. Přidat nebo odebrat reakci tudy nejde; to má nabídku nad zprávou, kde je i výběr emoji.
 *
 * Seznam je jedna stránka od serveru (viz `REACTION_DETAIL_LIMIT`). U příspěvku s tisíci reakcemi
 * ho nikdo neprojde a stránkování by znamenalo držet offset ve stavu sheetu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionDetailsSheet(
    details: ReactionDetails,
    onDismiss: () -> Unit,
) {
    // Modální sheet začíná skrytý a rozjede se sám. Náhrada za `rememberModalBottomSheetState`,
    // které je od Compose 1.12 zastaralé.
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.reactions_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp),
        )

        if (details.loading) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            return@ModalBottomSheet
        }

        LazyColumn(
            // Strop výšky: u příspěvku s padesáti reakcemi by sheet jinak zabral celou obrazovku.
            Modifier.heightIn(max = REACTION_DETAILS_MAX_HEIGHT).padding(vertical = 8.dp),
        ) {
            items(details.items, key = { "${it.sender.id}-${it.emoji}" }) { detail ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(
                        chatId = detail.sender.id,
                        title = detail.sender.title,
                        avatarFileId = detail.sender.avatarFileId,
                        size = 36.dp,
                    )
                    Text(
                        // U vlastní reakce jméno nic nepřidá — „Vy" je kratší a čte se rychleji.
                        text = if (detail.isOwn) {
                            stringResource(Res.string.reactions_you)
                        } else {
                            detail.sender.title
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = detail.emoji, fontSize = 20.sp)
                }
            }
        }
    }
}

/** Strop výšky seznamu v detailu reakcí. */
private val REACTION_DETAILS_MAX_HEIGHT = 420.dp
