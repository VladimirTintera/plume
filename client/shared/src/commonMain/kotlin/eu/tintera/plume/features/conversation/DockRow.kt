package eu.tintera.plume.features.conversation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Společný základ řádků docku pod pruhy nad konverzací — živé polohy (`LiveLocationDockBar`)
 * i mini přehrávače (`PlayerDockBar`). Oba se vysouvají tím, jak jejich zpráva odjíždí
 * z obrazovky (`dockProgress`), a oba jsou táž pilulka ze skla; dvě kopie téhož by se rozešly
 * při prvním doladění.
 */

/**
 * Řádek vyjíždí zpod pruhů nad ním: hlásí jen vysunutou část výšky ([reveal], 0–1) a obsah sedí
 * spodkem na ní, takže se odkrývá odspodu jako něco, co zpod pruhů vyklouzlo. Průběh se čte až
 * při rozmístění — scroll nic nerekomponuje.
 */
internal fun Modifier.dockReveal(reveal: () -> Float): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shown = reveal().coerceIn(0f, 1f)
        val height = (placeable.height * shown).roundToInt()
        layout(placeable.width, height) {
            placeable.placeRelativeWithLayer(0, height - placeable.height) { alpha = shown }
        }
    }

/** Zaoblení docku: u sbaleného řádku ([DOCK_ROW_HEIGHT]) přesně pilulka. */
internal val DOCK_SHAPE = RoundedCornerShape(28.dp)
internal val DOCK_ROW_HEIGHT = 56.dp
internal val DOCK_BADGE_SIZE = 40.dp
internal val DOCK_BADGE_INSET = (DOCK_ROW_HEIGHT - DOCK_BADGE_SIZE) / 2

/** Stín sbaleného řádku. Pod průsvitným sklem prosvítá dovnitř plochy, viz `LiveLocationDockBar`. */
internal val DOCK_ELEVATION = 3.dp
