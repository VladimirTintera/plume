package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.horizontalScrollWithWheel
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.NewReaction
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.CustomEmojiIcon
import eu.tintera.plume.core.ui.FaceStack
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.reactions_filter_all
import eu.tintera.plume.resources.reactions_new_others
import eu.tintera.plume.resources.reactions_title
import eu.tintera.plume.resources.reactions_you
import kotlinx.coroutines.delay
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
    /** Nepřečtené reakce na vlastní zprávu — viz [rememberReactionHighlight]. */
    newReactions: List<NewReaction> = emptyList(),
    /** Ukázat u zvýraznění i KDO reagoval (jen ve skupině). */
    namesReactors: Boolean = false,
    /** Popisek s tím, kdo reagoval, stojí před čipy (u odchozí zprávy), jinak za nimi. */
    reactorsAtStart: Boolean = true,
) {
    val highlight = rememberReactionHighlight(newReactions)
    /*
     * Které reakce tu byly při složení ZPRÁVY — podle toho čip pozná, že vlastní reakce přibyla
     * před očima (výbuch). Musí to být tady, ne v `ReactionChips`: ty se skládají až s první
     * reakcí, takže by ji měly za „byla tu odjakživa".
     */
    val composedWith = remember { reactions.map { it.emoji }.toSet() }
    /*
     * Čipy NABĚHNOU a odejdou zvětšením. Při odebrání poslední reakce se musí dál kreslit ta
     * poslední, jinak by `AnimatedVisibility` dobíhala s prázdným řádkem (stejná past jako
     * u odznaku doručení).
     */
    var shown by remember { mutableStateOf(reactions) }
    if (reactions.isNotEmpty()) shown = reactions
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    Box(modifier) {
        AnimatedVisibility(
            visible = reactions.isNotEmpty(),
            enter = scaleIn(
                spring(dampingRatio = CHIP_POP_DAMPING, stiffness = Spring.StiffnessMedium),
                initialScale = 0f,
            ) + fadeIn(effects),
            exit = scaleOut(spatial, targetScale = 0.4f) + fadeOut(effects),
            label = "reaction-row",
        ) {
            ReactionChips(
                shown,
                onOpenDetails,
                highlighted = highlight.map { it.key }.toSet(),
                composedWith = composedWith,
            )
        }
        ReactorsLabel(
            senders = if (namesReactors) highlight.mapNotNull { it.sender }.distinctBy { it.id } else emptyList(),
            atStart = reactorsAtStart,
            modifier = Modifier.align(if (reactorsAtStart) Alignment.CenterStart else Alignment.CenterEnd),
        )
    }
}

/**
 * Nepřečtené reakce ke zvýraznění, podržené na [REACTOR_HIGHLIGHT_MS].
 *
 * Podržet je nutné: zobrazená zpráva se jako zhlédnutá hlásí po 400 ms a TDLib pak reakce
 * přečte (`newReactions` zmizí) — bez podržení by zvýraznění zhaslo dřív, než si ho kdo všimne.
 * Stav patří složené položce: po odscrollování a návratu je reakce přečtená a nezvýrazní se.
 */
@Composable
private fun rememberReactionHighlight(newReactions: List<NewReaction>): List<NewReaction> {
    var held by remember { mutableStateOf<List<NewReaction>>(emptyList()) }
    LaunchedEffect(newReactions) {
        if (newReactions.isNotEmpty()) held = newReactions
    }
    LaunchedEffect(held) {
        if (held.isEmpty()) return@LaunchedEffect
        delay(REACTOR_HIGHLIGHT_MS)
        held = emptyList()
    }
    return held
}

/**
 * Kdo právě reagoval: tváře a jména v pilulce vedle čipů. Jen dočasně, se zvýrazněním.
 *
 * **Místo v rozvržení nezabírá** (měří se bez omezení, hlásí nulovou velikost a kreslí se přes
 * spodní okraj zprávy vedle čipů, jako čip sám). Řádek reakcí je široký, jak potřebuje, a sloupec
 * zprávy se podle něj roztáhne — popisek v něm by zprávu na pár vteřin rozšířil a bublina by
 * uskočila.
 */
@Composable
private fun ReactorsLabel(senders: List<SenderInfo>, atStart: Boolean, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(senders) }
    if (senders.isNotEmpty()) shown = senders
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    AnimatedVisibility(
        visible = senders.isNotEmpty(),
        enter = scaleIn(spatial, initialScale = 0.6f) + fadeIn(effects),
        exit = scaleOut(spatial, targetScale = 0.6f) + fadeOut(effects),
        label = "reactors",
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0))
            val gap = REACTOR_LABEL_GAP.roundToPx()
            layout(0, 0) {
                placeable.place(
                    x = if (atStart) -placeable.width - gap else gap,
                    y = -placeable.height / 2,
                )
            }
        },
    ) {
        val names = shown.take(REACTOR_NAMES_SHOWN).joinToString(", ") { it.title }
        val others = shown.size - REACTOR_NAMES_SHOWN
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = REACTION_ELEVATION,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            ) {
                FaceStack(
                    faces = shown.take(REACTOR_FACES_SHOWN),
                    ringColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    size = REACTOR_FACE_SIZE,
                )
                Text(
                    text = if (others > 0) stringResource(Res.string.reactions_new_others, names, others) else names,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = REACTOR_LABEL_MAX_WIDTH),
                )
            }
        }
    }
}

@Composable
private fun ReactionChips(
    reactions: List<MessageReaction>,
    onOpenDetails: (() -> Unit)?,
    highlighted: Set<String> = emptySet(),
    /** Reakce, které zpráva měla při složení (viz [ReactionRow]). */
    composedWith: Set<String> = emptySet(),
) {
    /*
     * Nový čip v už viditelném řádku naskočí sám; čipy, které tu byly při prvním složení
     * (zpráva přijela při scrollu), se nezvětšují.
     */
    val initial = remember { reactions.map { it.emoji }.toSet() }

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            // Posouvatelný řádek ořezává obsah na svoje hranice — bez místa kolem by uřízl stín
            // čipů zprava i zespodu.
            .padding(REACTION_SHADOW_ROOM),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            // Klíč podle emoji: server pořadí reakcí mění (optimisticky přidaná jde na konec,
            // jeho odpověď ji může dát jinam). Bez klíče by se stav animací přenesl na sousední
            // čip a ten by přeblikl do cizí barvy a tvaru.
            key(reaction.emoji) {
                // Z nuly s pružným přeskokem: čip „vyskočí" z nároží zprávy.
                val appear = remember { Animatable(if (reaction.emoji in initial) 1f else 0f) }
                val spec = spring<Float>(dampingRatio = CHIP_POP_DAMPING, stiffness = Spring.StiffnessMedium)
                LaunchedEffect(Unit) { appear.animateTo(1f, spec) }
                Box(Modifier.graphicsLayer { scaleX = appear.value; scaleY = appear.value }) {
                    ReactionChip(
                        reaction = reaction,
                        onClick = onOpenDetails,
                        highlighted = reaction.emoji in highlighted,
                        fresh = reaction.emoji !in composedWith,
                    )
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
    /** Nepřečtená reakce: čip dvakrát výrazněji povyskočí, jakmile ji uživatel uvidí. */
    highlighted: Boolean = false,
    /** Reakce u zprávy při jejím složení nebyla — přibyla před očima. */
    fresh: Boolean = false,
) {
    val chosen = reaction.isChosen

    val scale by animateFloatAsState(
        targetValue = if (chosen) 1.06f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "reaction-scale",
    )
    /*
     * Výplň je NEUTRÁLNÍ i u vlastní reakce. Emoji od Telegramu jsou kreslená se stíny a přechody
     * a na tónované ploše (`primaryContainer`) splývala — žlutý palec na světle modré nebyl skoro
     * k poznání. Nejčitelnější je na nejsvětlejší (v tmavém režimu nejtmavší) roli.
     * Vlastní reakci proto nese kroužek a odznak počtu, ne podbarvení.
     */
    val container = MaterialTheme.colorScheme.surfaceContainerLowest
    val selection by animateColorAsState(
        targetValue = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "reaction-selection",
    )
    val badgeContainer by animateColorAsState(
        targetValue = if (chosen) MaterialTheme.colorScheme.primary else container,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "reaction-badge-container",
    )
    val badgeContent = if (chosen) {
        MaterialTheme.colorScheme.onPrimary
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
    LaunchedEffect(highlighted) {
        if (!highlighted) return@LaunchedEffect
        repeat(2) {
            pulse.animateTo(NEW_REACTION_PULSE_SCALE, pulseSpec)
            pulse.animateTo(1f, pulseSpec)
        }
    }

    /*
     * Vlastní reakce přidaná PŘED OČIMA: za emoji se na okamžik otevře SoftBurst (živý signál,
     * `ShapeMorphs`) a čip povyskočí. Ne konfety — ty patří výhře v kostce.
     *
     * Jen přechod do „moje": čip, který byl vlastní už při prvním složení (zpráva najela při
     * scrollu, návrat do chatu), nevybuchuje — jinak by se to dělo při každém průletu historií.
     * Nový čip s vlastní reakcí (přidaná nová emoji) vybuchne taky, proto `fresh`.
     */
    val burst = remember { Animatable(0f) }
    var wasChosen by remember { mutableStateOf(chosen && !fresh) }
    LaunchedEffect(chosen) {
        val now = chosen
        if (now && !wasChosen) {
            wasChosen = true
            burst.snapTo(0f)
            launch { burst.animateTo(1f, tween(BURST_MS, easing = FastOutSlowInEasing)) }
            pulse.animateTo(NEW_REACTION_PULSE_SCALE, pulseSpec)
            pulse.animateTo(1f, pulseSpec)
        } else {
            wasChosen = now
        }
    }
    val burstColor = MaterialTheme.colorScheme.primary
    val burstShape = MaterialShapes.SoftBurst.toShape()

    Box(Modifier.graphicsLayer { scaleX = scale * pulse.value; scaleY = scale * pulse.value }) {
        Surface(
            shape = CircleShape,
            color = container,
            contentColor = MaterialTheme.colorScheme.onSurface,
            /*
             * Lem v barvě pozadí konverzace, ne stín: čip vypadá, jako by byl z bubliny
             * vykrojený, a od ní i od pozadí se odliší v obou režimech. Tak to dělají Google
             * Zprávy; stín navíc v posouvatelném řádku ořezával.
             */
            border = BorderStroke(REACTION_RING, MaterialTheme.colorScheme.surface),
            shadowElevation = REACTION_ELEVATION,
            modifier = Modifier
                .padding(top = REACTION_BADGE_ROOM, end = REACTION_BADGE_ROOM)
                .size(REACTION_CHIP_SIZE),
        ) {
            Box(
                // Klikání je až uvnitř `Surface`, takže ho tvar ořízne — jinak by vlnka přetekla
                // přes kulaté nároží čipu.
                Modifier
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    // Kroužek vlastní reakce UVNITŘ lemu v barvě pozadí: na odchozí `primary`
                    // bublině by jinak splynul s ní, takhle ho od ní lem odděluje.
                    .padding(REACTION_RING)
                    .border(REACTION_SELECTED_RING, selection, CircleShape)
                    // Za kroužkem i za emoji; průběh se čte až při kreslení, nic se nerekomponuje.
                    // Uvnitř kolečka, ne přes něj: řádek reakcí se posouvá a ořezává na svoje
                    // hranice, takže výbuch přesahující čip by se uřízl.
                    .drawBehind {
                        val p = burst.value
                        if (p <= 0f || p >= 1f) return@drawBehind
                        val grown = BURST_START_SCALE + (1f - BURST_START_SCALE) * (p / BURST_GROW_PART).coerceAtMost(1f)
                        val alpha = BURST_ALPHA * (1f - ((p - BURST_GROW_PART) / (1f - BURST_GROW_PART)).coerceIn(0f, 1f))
                        withTransform({
                            rotate(BURST_TURN_DEGREES * p)
                            scale(grown, grown)
                        }) {
                            drawOutline(burstShape.createOutline(size, layoutDirection, this), burstColor.copy(alpha = alpha))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Emoji záměrně mimo typografickou škálu: role jako `labelLarge` míří na výšku
                // písmen, ale u emoji je nosná celá kresba, takže ve stejné velikosti působí menší.
                // Výška řádku = velikost písma, zarovnaná na střed a oříznutá: jinak nese text nad
                // kresbou rezervu řádku a emoji sedí v kolečku o kus níž.
                val customId = customEmojiIdOf(reaction.emoji)
                if (customId != null) {
                    CustomEmojiIcon(customId, size = CUSTOM_EMOJI_CHIP_SIZE)
                } else {
                    Text(text = reaction.emoji, style = CENTERED_EMOJI)
                }
            }
        }

        if (reaction.count > 1) {
            Surface(
                shape = CircleShape,
                color = badgeContainer,
                contentColor = badgeContent,
                border = BorderStroke(REACTION_RING, MaterialTheme.colorScheme.surface),
                shadowElevation = REACTION_ELEVATION,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Text(
                    text = reaction.count.toString(),
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
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

/** Jak dlouho zůstane nepřečtená reakce zvýrazněná (a popisek s tím, kdo reagoval). */
private const val REACTOR_HIGHLIGHT_MS = 4_000L

/** Povyskočení čipu nepřečtené reakce — výraznější než při změně počtu. */
private const val NEW_REACTION_PULSE_SCALE = 1.3f

/**
 * Výbuch za vlastní reakcí: SoftBurst se z třetiny rozevře do celého kolečka (první třetina
 * průběhu), pootočí se a vybledne. Krátký — je to odezva na ťuknutí, ne oslava.
 */
private const val BURST_MS = 600
private const val BURST_START_SCALE = 0.3f
private const val BURST_GROW_PART = 0.35f
private const val BURST_ALPHA = 0.5f
private const val BURST_TURN_DEGREES = 45f

private const val REACTOR_NAMES_SHOWN = 2
private const val REACTOR_FACES_SHOWN = 3
private val REACTOR_FACE_SIZE = 20.dp
private val REACTOR_LABEL_GAP = 6.dp
private val REACTOR_LABEL_MAX_WIDTH = 180.dp

/**
 * Tlumení pružiny náběhu čipu. Víc přeskoku než běžný spatial spec (0,6–0,78): náběh reakce je
 * drobná radostná událost, jak ji M3 Expressive pojmenovává, ne přesun obsahu.
 */
private const val CHIP_POP_DAMPING = 0.45f

/** Lem čipu v barvě pozadí — odděluje ho od bubliny, na které leží. */
private val REACTION_RING = 2.dp

/**
 * Drobný stín čipu. Lem v barvě `surface` odděluje čip od bubliny, ale mimo ni leží na pozadí
 * konverzace (`background` = `surface`) a zmizí — bílý čip na skoro bílé ploše splýval.
 * Stín se musí vejít do [REACTION_SHADOW_ROOM], jinak ho posouvatelný řádek uřízne.
 */
private val REACTION_ELEVATION = 1.dp

/** Kroužek, kterým se odliší vlastní reakce. */
private val REACTION_SELECTED_RING = 1.5.dp

/**
 * Vlastní emoji v čipu. Větší než písmo obyčejného emoji ([CENTERED_EMOJI]): to je bez rezervy
 * řádku, kdežto obrázek vlastního emoji má kolem kresby vlastní okraj.
 */
private val CUSTOM_EMOJI_CHIP_SIZE = 18.dp

/** Emoji v čipu: výška řádku rovná písmu, na střed, bez rezervy nad kresbou. */
private val CENTERED_EMOJI = TextStyle(
    // Menší než kolečko o dost, aby kolem kresby zůstal vzduch.
    fontSize = 14.sp,
    lineHeight = 14.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/**
 * Místo kolem čipů uvnitř posouvatelného řádku, který obsah ořezává. Stín už čipy nemají, ale
 * zvětšení při náběhu a pulzu by se bez rezervy na okraji uřízlo stejně.
 */
internal val REACTION_SHADOW_ROOM = 3.dp

/**
 * Výška řádku reakcí: čip, místo na odznak počtu a na stín. Podle ní si zpráva nechá místo pod
 * sebou. Musí být deklarovaná ZA svými sčítanci — top-level `val` se inicializují v pořadí.
 */
internal val REACTION_ROW_HEIGHT = REACTION_CHIP_SIZE + REACTION_BADGE_ROOM + REACTION_SHADOW_ROOM * 2


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
    onFilter: (String?) -> Unit = {},
) {
    // Modální sheet začíná skrytý a rozjede se sám. Náhrada za `rememberModalBottomSheetState`,
    // které je od Compose 1.12 zastaralé.
    val sheetState = rememberExpandedSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.reactions_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp),
        )

        // Filtr má smysl až s víc druhy reakcí; s jedním by „Vše" a to emoji ukazovaly totéž.
        if (details.counts.size > 1) {
            ReactionFilter(counts = details.counts, filter = details.filter, onFilter = onFilter)
        }

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
                    val customId = customEmojiIdOf(detail.emoji)
                    if (customId != null) {
                        CustomEmojiIcon(customId, size = 24.dp)
                    } else {
                        Text(text = detail.emoji, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

/**
 * Filtr detailu reakcí: „Vše" a každá reakce s počtem, jako spojená skupina přepínacích
 * tlačítek (tentýž prvek jako druh chatu ve filtrech hledání zpráv). Vybrané je vždycky právě
 * jedno — prázdný filtr tu nedává smysl, „nic" je „Vše". Řádek se posouvá do strany: reakcí
 * bývá u příspěvků i deset.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReactionFilter(counts: List<MessageReaction>, filter: String?, onFilter: (String?) -> Unit) {
    val options = listOf<MessageReaction?>(null) + counts
    val total = counts.sumOf { it.count }
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Filtr leží nad seznamem lidí, ne v něm, takže kolečko nad ním seznam nezastaví.
            .horizontalScrollWithWheel()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        options.forEachIndexed { index, reaction ->
            val key = reaction?.emoji
            ToggleButton(
                checked = filter == key,
                onCheckedChange = { onFilter(key) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                if (reaction == null) {
                    Text(stringResource(Res.string.reactions_filter_all, total.grouped()))
                } else {
                    val customId = customEmojiIdOf(reaction.emoji)
                    if (customId != null) {
                        CustomEmojiIcon(customId, size = 20.dp)
                    } else {
                        Text(text = reaction.emoji, fontSize = 18.sp)
                    }
                    Text(
                        text = reaction.count.grouped(),
                        style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/** Strop výšky seznamu v detailu reakcí. */
private val REACTION_DETAILS_MAX_HEIGHT = 420.dp

/** Na kolik čip povyskočí, když se změní počet. */
private const val REACTION_PULSE_SCALE = 1.25f
