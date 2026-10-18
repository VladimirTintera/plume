package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tvary a rozestupy **bloku zaoblených řádků** — vzor, kterým M3 Expressive nahrazuje kartu
 * u každé položky. Velké zaoblení nese krajní položka skupiny, uvnitř jsou malá nároží; se
 * spárou [GROUP_ITEM_SPACING] z toho vznikne jeden blok a mezi bloky je [GROUP_SPACING].
 *
 * Bydlí to v `core/ui`, protože týmž jazykem mluví seznam chatů, sekce nastavení, profil chatu
 * i výběr cíle přeposlání. Tři kopie konstant se dřív nebo později rozejdou a z jedné obrazovky
 * by byl cizí prvek.
 */
fun groupItemShape(index: Int, count: Int, selected: Boolean = false): Shape {
    // Vybraná položka se z bloku vyčlení TVAREM, ne jen barvou: je zaoblená celá, jako skupina
    // sama. Sousedé si svá malá nároží nechávají.
    val top = if (index == 0 || selected) GROUP_CORNER else INNER_CORNER
    val bottom = if (index == count - 1 || selected) GROUP_CORNER else INNER_CORNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * Řádek bloku v lazy seznamu (výsledky hledání, koncepty, výběr země): okraj, spára nad ním,
 * tvar podle pozice, podklad a ořez — vlnka po ťuknutí nepřeteče přes zaoblení. Klikání patří
 * ZA tenhle modifikátor. V obyčejném sloupci je na to [GroupedColumn], který pozici spočítá sám;
 * lazy seznam ji zná až v `itemsIndexed`.
 *
 * **Stisknutý řádek se z bloku vyčlení** ([animatedGroupItemShape]). Stisk se sleduje tady, ne
 * přes `interactionSource` klikání: to bývá schované v řádkové komponentě. Události se jen
 * poslouchají (nespotřebovávají), stisk se ohlásí až po [PRESS_DELAY_MS] a zruší se posunem
 * nebo scrollem — jako `clickable` v posouvaném seznamu, aby tvar při scrollu neblikal. Řádek,
 * který ťuknout nejde, má [pressable] `false`.
 */
@Composable
fun Modifier.groupedItem(
    index: Int,
    count: Int,
    color: Color,
    selected: Boolean = false,
    /** Okraj bloku od kraje seznamu; 0, když ho už má sloupec kolem (sheet s vlastním odsazením). */
    edge: Dp = 16.dp,
    pressable: Boolean = true,
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val shape = animatedGroupItemShape(first = index == 0, last = index == count - 1, selected = selected, pressed = pressed)
    return this
        .padding(horizontal = edge)
        .padding(top = if (index > 0) GROUP_ITEM_SPACING else 0.dp)
        .then(if (pressable) Modifier.pointerInput(Unit) { trackPress { pressed = it } } else Modifier)
        .clip(shape)
        .background(color)
}

/** Stisk řádku bez spotřebování událostí: se zpožděním, zrušený posunem nebo scrollem. */
private suspend fun PointerInputScope.trackPress(onPressed: (Boolean) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        fun moved(change: PointerInputChange) = (change.position - down.position).getDistance() > slop
        // Dokud prst drží na místě a nikdo událost nespotřeboval (scroll), gesto trvá.
        suspend fun AwaitPointerEventScope.holding(): Boolean {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
                    ?: return false
                // Final průchod téhož dotyku dolů: `clickable` ho spotřebovává vždycky, gesto
                // to neruší. Bez téhle výjimky stisk skončil dřív, než začal.
                if (change.changedToDownIgnoreConsumed()) continue
                return change.pressed && !change.isConsumed && !moved(change)
            }
        }
        // Krátký dotyk (ťuknutí) do prodlevy stisk neohlásí — vlnka ťuknutí stačí.
        val ended = withTimeoutOrNull(PRESS_DELAY_MS) {
            while (holding()) Unit
            true
        }
        if (ended != null) return@awaitEachGesture
        onPressed(true)
        try {
            while (holding()) Unit
        } finally {
            onPressed(false)
        }
    }
}

/** Prodleva, po které se dotyk počítá jako stisk (tap timeout `clickable` v posouvaném seznamu). */
private const val PRESS_DELAY_MS = 100L

/**
 * **Tvar řádku bloku s animací výběru a stisku.** Stisknutý řádek se z bloku vyčlení: všechna
 * nároží [PRESSED_CORNER] (M3 Expressive `ListTokens.ItemPressedContainerExpressiveShape`,
 * `CornerLarge`), stejně jako se stisknuté tlačítko zaoblí jinak. Vybraný má nároží skupiny
 * ([GROUP_CORNER]) a vyhrává nad stiskem — výběr je stav, stisk jen odezva.
 *
 * Animuje se pružinou, protože skupiny se přeskládávají za běhu a stisk je okamžitý; pružina
 * smí přestřelit, záporné zaoblení se ořízne (`RoundedCornerShape` ho neunese).
 */
@Composable
fun animatedGroupItemShape(first: Boolean, last: Boolean, selected: Boolean, pressed: Boolean = false): RoundedCornerShape {
    fun target(edge: Boolean): Dp = when {
        selected -> GROUP_CORNER
        pressed -> PRESSED_CORNER
        edge -> GROUP_CORNER
        else -> INNER_CORNER
    }
    val top by animateDpAsState(target(first), MaterialTheme.motionScheme.fastSpatialSpec(), label = "group-corner-top")
    val bottom by animateDpAsState(target(last), MaterialTheme.motionScheme.fastSpatialSpec(), label = "group-corner-bottom")
    return RoundedCornerShape(
        topStart = top.coerceAtLeast(0.dp),
        topEnd = top.coerceAtLeast(0.dp),
        bottomStart = bottom.coerceAtLeast(0.dp),
        bottomEnd = bottom.coerceAtLeast(0.dp),
    )
}

/** Nároží stisknutého řádku bloku (M3 Expressive: `CornerLarge`). */
val PRESSED_CORNER = 16.dp

/** Zaoblení na krajích skupiny. */
val GROUP_CORNER = 20.dp

/** Zaoblení mezi sousedy uvnitř skupiny — tak akorát, aby byly rozeznatelné, ne oddělené. */
val INNER_CORNER = 4.dp

/** Rozestup mezi položkami jedné skupiny. */
val GROUP_ITEM_SPACING = 2.dp

/** Mezera mezi skupinami. */
val GROUP_SPACING = 14.dp

/** Mezera po [GroupBreak] — blok se rozdělí, ale pořád patří pod jeden nadpis. */
val GROUP_BREAK_SPACING = 12.dp

/**
 * Sloupec, ze kterého je **blok zaoblených řádků sám**: každé dítě je jeden řádek, dostane tvar
 * podle pozice ([groupItemShape]), podklad [containerColor] a ořez (vlnka po ťuknutí nepřeteče
 * přes zaoblení). Řádky tak nemusí vědět, kolikátý jsou — stačí jim průhledný `ListItem`.
 *
 * Pozici dopočítává rozvržení z toho, co je opravdu vidět: dítě s nulovou výškou (skrytý
 * `AnimatedVisibility`, řádek, který se nekreslí) se nepočítá, takže krajní zaoblení nese vždycky
 * krajní VIDITELNÝ řádek. Seznam s `if` uvnitř by si jinak index musel počítat ručně a s každou
 * podmínkou navíc by se krajní nároží rozjela.
 *
 * Podklad kreslí sloupec, ne řádky: dítě se nedá obalit modifikátorem zvenku, ale jeho umístění
 * zná rozvržení, a ořez nese vrstva, do které se dítě umístí.
 */
@Composable
fun GroupedColumn(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable () -> Unit,
) {
    // Zapisuje se při umístění a čte při kreslení, takže změna rozvržení překreslí i podklad.
    val segments = remember { mutableStateOf(emptyList<Segment>()) }
    // Stisk: který řádek (horní hrana v px) a jak daleko je jeho vyčlenění (0 = v bloku).
    val press = remember { GroupPress() }
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var columnCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    /*
     * Řádky jsou obyčejné `ListItem`y a o stisku ví jen jejich `clickable`. Sloupec jim proto
     * podstrčí indikaci (vlnku), která stisk navíc ohlásí sem: zaoblí se jen řádek, který jde
     * opravdu ťuknout, a stisk přijde se stejným zpožděním jako vlnka, takže scroll tvar nemění.
     */
    val base = LocalIndication.current
    val indication = remember(base) {
        (base as? IndicationNodeFactory)?.let { factory ->
            PressReportingIndication(factory) { child, pressed ->
                val column = columnCoordinates ?: return@PressReportingIndication
                if (!child.isAttached || !column.isAttached) return@PressReportingIndication
                val center = column.localPositionOf(child, Offset(0f, child.size.height / 2f)).y
                val hit = segments.value.firstOrNull { center >= it.top && center < it.top + it.height }
                    ?: return@PressReportingIndication
                if (pressed) press.top = hit.top
                scope.launch { press.progress.animateTo(if (pressed) 1f else 0f, spec) }
            }
        } ?: base
    }

    CompositionLocalProvider(LocalIndication provides indication) {
        Layout(
            content = content,
            modifier = modifier
                .onPlaced { columnCoordinates = it }
                .drawBehind {
                    segments.value.forEach { segment ->
                        val outline = press.shapeOf(segment).createOutline(Size(size.width, segment.height.toFloat()), layoutDirection, this)
                        translate(top = segment.top.toFloat()) { drawOutline(outline, containerColor) }
                    }
                },
        ) { measurables, constraints ->
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
            // Každý řádek přes celou šířku, i krátký text: blok má rovné boky.
            val itemConstraints = Constraints.fixedWidth(width)
            val placeables = measurables.map { if (it.layoutId == GroupBreakId) null else it.measure(itemConstraints) }

            // Běhy viditelných řádků mezi zlomy; prázdný běh (zlom na kraji, dva zlomy po sobě) nic nedělá.
            val runs = mutableListOf(mutableListOf<Int>())
            placeables.forEachIndexed { index, placeable ->
                when {
                    placeable == null -> if (runs.last().isNotEmpty()) runs += mutableListOf<Int>()
                    placeable.height > 0 -> runs.last() += index
                }
            }

            val gap = GROUP_ITEM_SPACING.roundToPx()
            val breakGap = GROUP_BREAK_SPACING.roundToPx()
            val tops = IntArray(placeables.size)
            val first = BooleanArray(placeables.size)
            val last = BooleanArray(placeables.size)
            val inRun = BooleanArray(placeables.size)
            var y = 0
            runs.filter { it.isNotEmpty() }.forEachIndexed { runIndex, run ->
                if (runIndex > 0) y += breakGap
                run.forEachIndexed { position, index ->
                    if (position > 0) y += gap
                    tops[index] = y
                    first[index] = position == 0
                    last[index] = position == run.lastIndex
                    inRun[index] = true
                    y += placeables[index]!!.height
                }
            }

            layout(width, y.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                val placed = ArrayList<Segment>(placeables.size)
                placeables.forEachIndexed { index, placeable ->
                    when {
                        placeable == null -> Unit
                        !inRun[index] -> placeable.placeRelative(0, 0)
                        else -> {
                            val segment = Segment(tops[index], placeable.height, first[index], last[index])
                            // Tvar čte stav stisku až ve vrstvě: stisk překreslí, nepřeměří.
                            placeable.placeRelativeWithLayer(0, tops[index]) {
                                this.shape = press.shapeOf(segment)
                                clip = true
                            }
                            placed += segment
                        }
                    }
                }
                segments.value = placed
            }
        }
    }
}

/** Stisknutý řádek [GroupedColumn]: jeho horní hrana a průběh vyčlenění z bloku. */
private class GroupPress {
    var top by mutableIntStateOf(-1)
    val progress = Animatable(0f)

    fun shapeOf(segment: Segment): Shape {
        val p = if (segment.top == top) progress.value else 0f
        val topCorner = if (segment.first) GROUP_CORNER else INNER_CORNER
        val bottomCorner = if (segment.last) GROUP_CORNER else INNER_CORNER
        // Pružina smí přestřelit; záporné zaoblení `RoundedCornerShape` neunese.
        val top = lerp(topCorner, PRESSED_CORNER, p).coerceAtLeast(0.dp)
        val bottom = lerp(bottomCorner, PRESSED_CORNER, p).coerceAtLeast(0.dp)
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }
}

/**
 * Vlnka, která navíc hlásí stisk ([report]: souřadnice prvku, který ji nese, a jestli je
 * stisknutý). Kreslí ji původní indikace ([ripple]), tahle jen poslouchá tytéž interakce.
 */
private class PressReportingIndication(
    private val ripple: IndicationNodeFactory,
    private val report: (LayoutCoordinates, Boolean) -> Unit,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressReportingNode(ripple.create(interactionSource), interactionSource, report)

    override fun equals(other: Any?): Boolean = other is PressReportingIndication && other.ripple == ripple
    override fun hashCode(): Int = ripple.hashCode()
}

private class PressReportingNode(
    ripple: DelegatableNode,
    private val source: InteractionSource,
    private val report: (LayoutCoordinates, Boolean) -> Unit,
) : DelegatingNode(), GlobalPositionAwareModifierNode {
    private var coordinates: LayoutCoordinates? = null

    init {
        delegate(ripple)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun onAttach() {
        // Hned, ne až po naplánování: `clickable` indikaci zakládá líně při prvním stisku
        // a stisk vyšle hned po připojení — odběr spuštěný později by ho minul.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            source.interactions.collect { interaction ->
                val at = coordinates ?: return@collect
                when (interaction) {
                    is PressInteraction.Press -> report(at, true)
                    is PressInteraction.Release, is PressInteraction.Cancel -> report(at, false)
                }
            }
        }
    }
}

/**
 * Zlom uvnitř [GroupedColumn]: blok se tu rozdělí na dva, každý se svými krajními zaobleními.
 * Náhrada dělicí čáry — ta mezi zaoblenými řádky působí jako pozůstatek karty se seznamem.
 */
@Composable
fun GroupBreak() {
    Spacer(Modifier.layoutId(GroupBreakId))
}

/**
 * Prázdný blok: „tady zatím nic není" uprostřed obrazovky, kde by stál blok zaoblených řádků
 * (žádné pozvánky, žádné vlastní sady samolepek, hledání bez výsledku). Jeden řádek v týchž
 * barvách a tvaru jako blok, takže prázdný seznam vypadá jako seznam, jen bez položek — holý
 * šedý text pod nadpisem sekce působil jako nedodělek. Celou obrazovku má [IntroEmptyState].
 */
@Composable
fun GroupEmptyRow(text: String, modifier: Modifier = Modifier, icon: DrawableResource? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, groupItemShape(0, 1))
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private object GroupBreakId

private data class Segment(val top: Int, val height: Int, val first: Boolean, val last: Boolean)
