package eu.tintera.plume.core.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * Okno je nízké — pod 480 dp, třída výšky Compact: telefon naležato (kolem 360 dp po odečtení
 * lišt), malé okno na ploše. Na takovém okně se nevejde to, s čím počítá obrazovka na výšku:
 * velký nadpis, uvítací tvar, klávesnice PINu pod hlavičkou.
 *
 * Kde se rozhoduje podle místa, které obrazovka opravdu dostane (panel vedle panelu, sheet),
 * je lepší `BoxWithConstraints` a hranice stejného čísla; tohle je pro rozhodnutí za celé okno
 * (hlavička Nastavení má být ve všech panelech stejně vysoká).
 */
@Composable
fun isWindowHeightCompact(): Boolean =
    !currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
