package eu.tintera.plume.features.conversation.items

import androidx.compose.ui.unit.dp

/** Mezera navíc tam, kde začíná nová skupina — jiný odesílatel, jiný blok. */
internal val GROUP_SPACING = 8.dp

/** Zaoblení bubliny. Sdílí ho i startovní strana letu, aby se tvar po cestě neměnil. */
internal val BUBBLE_CORNER = 18.dp

/** Stažené nároží MEZI navazujícími bublinami téhož odesílatele. */
internal val BUBBLE_TIGHT = 4.dp

/**
 * Šířka sloupce s časy, který se odkrývá tahem doleva. Vejde se do něj „00:00" s rezervou.
 */
internal val TIME_COLUMN_WIDTH = 56.dp

/** Nad tímhle počtem už emoji netvoří gesto, ale odstavec, a kreslí se normálně velké. */
internal const val MAX_LARGE_EMOJI = 3

/** Rozestup mezi zprávami téhož odesílatele. Patří k sobě, tak se drží u sebe. */
internal val MESSAGE_SPACING = 4.dp
