package eu.tintera.plume.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
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
