package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
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
    Layout(
        content = content,
        modifier = modifier.drawBehind {
            segments.value.forEach { segment ->
                val outline = segment.shape.createOutline(Size(size.width, segment.height.toFloat()), layoutDirection, this)
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
        val shapes = arrayOfNulls<Shape>(placeables.size)
        var y = 0
        runs.filter { it.isNotEmpty() }.forEachIndexed { runIndex, run ->
            if (runIndex > 0) y += breakGap
            run.forEachIndexed { position, index ->
                if (position > 0) y += gap
                tops[index] = y
                shapes[index] = groupItemShape(position, run.size)
                y += placeables[index]!!.height
            }
        }

        layout(width, y.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            val placed = ArrayList<Segment>(placeables.size)
            placeables.forEachIndexed { index, placeable ->
                val shape = shapes[index]
                when {
                    placeable == null -> Unit
                    shape == null -> placeable.placeRelative(0, 0)
                    else -> {
                        placeable.placeRelativeWithLayer(0, tops[index]) {
                            this.shape = shape
                            clip = true
                        }
                        placed += Segment(tops[index], placeable.height, shape)
                    }
                }
            }
            segments.value = placed
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

private object GroupBreakId

private data class Segment(val top: Int, val height: Int, val shape: Shape)
