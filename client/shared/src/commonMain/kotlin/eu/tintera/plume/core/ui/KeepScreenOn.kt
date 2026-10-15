package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Displej nezhasne, dokud je tohle v kompozici — přehrávané video.
 *
 * **Požadavky se sčítají**, ne ukládají a vracejí: dvě obrazovky, které se překryjí mimo pořadí
 * LIFO (přechody v Navigation3 ho nezaručují), by si jinak navzájem vrátily stav. Tatáž past jako
 * u [DarkSystemBars].
 */
@Composable
expect fun KeepScreenOn()
