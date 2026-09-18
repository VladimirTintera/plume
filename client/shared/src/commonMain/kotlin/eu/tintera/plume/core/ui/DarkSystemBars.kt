package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Světlé ikony systémových lišt po dobu, kdy je obrazovka na tmavém podkladu bez ohledu na motiv
 * (prohlížeč médií). Při odchodu se vrátí, co bylo předtím.
 *
 * Motiv lišty nastavuje jednou za kompozici kořene; kořen se během prohlížení neskládá znovu
 * (jen při změně motivu), takže se tyhle dva zápisy nepřepisují.
 */
@Composable
expect fun DarkSystemBars()
