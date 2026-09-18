package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.resources.Res
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
    /*
     * Čipy NABĚHNOU a odejdou zvětšením. Při odebrání poslední reakce se musí dál kreslit ta
     * poslední, jinak by `AnimatedVisibility` dobíhala s prázdným řádkem (stejná past jako
     * u odznaku doručení).
     */
    var shown by remember { mutableStateOf(reactions) }
    if (reactions.isNotEmpty()) shown = reactions
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    AnimatedVisibility(
        visible = reactions.isNotEmpty(),
        modifier = modifier,
        enter = scaleIn(spatial, initialScale = 0.4f) + fadeIn(effects),
        exit = scaleOut(spatial, targetScale = 0.4f) + fadeOut(effects),
        label = "reaction-row",
    ) {
        ReactionChips(shown, onOpenDetails)
    }
}

@Composable
private fun ReactionChips(reactions: List<MessageReaction>, onOpenDetails: (() -> Unit)?) {
    /*
     * Nový čip v už viditelném řádku naskočí sám; čipy, které tu byly při prvním složení
     * (zpráva přijela při scrollu), se nezvětšují.
     */
    val initial = remember { reactions.map { it.emoji }.toSet() }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            // Klíč podle emoji: server pořadí reakcí mění (optimisticky přidaná jde na konec,
            // jeho odpověď ji může dát jinam). Bez klíče by se stav animací přenesl na sousední
            // čip a ten by přeblikl do cizí barvy a tvaru.
            key(reaction.emoji) {
                val appear = remember { Animatable(if (reaction.emoji in initial) 1f else 0.4f) }
                val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                LaunchedEffect(Unit) { appear.animateTo(1f, spec) }
                Box(Modifier.graphicsLayer { scaleX = appear.value; scaleY = appear.value }) {
                    ReactionChip(reaction = reaction, onClick = onOpenDetails)
                }
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
    /*
     * Změna počtu čip krátce POVYSKOČÍ — někdo právě zareagoval. Jen při změně, ne při prvním
     * složení (při scrollu by pulzovalo všechno, co najede na obrazovku).
     */
    val pulse = remember { Animatable(1f) }
    var shownCount by remember { mutableIntStateOf(reaction.count) }
    val pulseSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(reaction.count) {
        if (reaction.count == shownCount) return@LaunchedEffect
        shownCount = reaction.count
        pulse.animateTo(REACTION_PULSE_SCALE, pulseSpec)
        pulse.animateTo(1f, pulseSpec)
    }

    Box(Modifier.graphicsLayer { scaleX = scale * pulse.value; scaleY = scale * pulse.value }) {
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
internal val REACTION_BADGE_ROOM = 7.dp

/** Výška řádku reakcí: čip i s místem na odznak počtu. Podle ní si zpráva nechá místo pod sebou. */
internal val REACTION_ROW_HEIGHT = REACTION_CHIP_SIZE + REACTION_BADGE_ROOM


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

/** Na kolik čip povyskočí, když se změní počet. */
private const val REACTION_PULSE_SCALE = 1.25f
