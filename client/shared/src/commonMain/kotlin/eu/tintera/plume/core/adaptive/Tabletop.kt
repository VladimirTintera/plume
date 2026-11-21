package eu.tintera.plume.core.adaptive

import androidx.compose.material3.adaptive.allHorizontalHingeBounds
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect

/**
 * Pant skládačky napůl otevřené „na stole" (tabletop): vodorovný ohyb přes okno dělí displej na
 * horní půlku, která stojí, a spodní, která leží. Meze pantu jsou v souřadnicích OKNA v pixelech;
 * `null`, když okno pantem rozdělené není — telefon, plně otevřená nebo zavřená skládačka,
 * skládačka „jako kniha" (svislý pant), okno jen na jedné půlce displeje.
 *
 * Bere se z `windowPosture` v material3 adaptive (Posture, `isTabletop`, `hingeList`), ne
 * z `mediaQuery`: ten řekne jen „je napůl otevřená", ne KDE pant je. Na desktopu a iOS je poloha
 * prázdná, takže tam vrací vždy `null`. Pant Pixel Foldu má nulovou výšku (čára přes střed).
 */
@Composable
fun rememberTabletopHinge(): Rect? {
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    return remember(posture) {
        if (posture.isTabletop) posture.allHorizontalHingeBounds.firstOrNull() else null
    }
}
