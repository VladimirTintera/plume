package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Světlé ikony systémových lišt po dobu, kdy je obrazovka na tmavém podkladu bez ohledu na motiv
 * (prohlížeč médií, náhled přílohy, ořez fotky, tmavá hlavička mini aplikace).
 *
 * **Jen přihlásí požadavek, na okno nesahá.** Vzhled lišt nastavuje jediné místo
 * (`SystemBarsAppearance` v `AppTheme`) z motivu a z toho, jestli nějaký požadavek trvá. Dřív si
 * tahle funkce při vstupu zapamatovala stav lišt a při odchodu ho vrátila — a to se rozbije,
 * jakmile se dvě tmavé obrazovky překryjí mimo pořadí LIFO: druhý prohlížeč otevřený, než první
 * dojel zavírací animací, si zapamatoval „světlé ikony" a po zavření je vrátil nad světlý seznam
 * chatů. Stejně dopadla změna motivu během prohlížení: vrátil se stav ze starého motivu.
 */
@Composable
fun DarkSystemBars() {
    val requests = LocalDarkSystemBars.current
    DisposableEffect(requests) {
        requests.count++
        onDispose { requests.count-- }
    }
}

/** Kolik obrazovek právě chce světlé ikony lišt; čte ho kořenové téma. */
class DarkSystemBarsRequests {
    internal var count by mutableIntStateOf(0)

    val active: Boolean get() = count > 0
}

val LocalDarkSystemBars = staticCompositionLocalOf { DarkSystemBarsRequests() }
