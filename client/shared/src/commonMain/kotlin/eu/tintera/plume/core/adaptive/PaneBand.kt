package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import eu.tintera.plume.core.ui.LocalPanesSideBySide

/**
 * Jeden pás hlavičky přes konverzaci a boční panel vedle ní (profil, kalendář, podstránky).
 *
 * Konverzace v něm nechává vlevo sebe (šipka, avatar, název) a své akce (hovor, panel, ⋮) pošle
 * sem ([publishToPaneBand]). Boční panel má hlavičku NAD kartou, v téže řadě (`PaneCard` mu ji
 * natáhne do výšky hlavičky konverzace), a na konec svých akcí přidá akce konverzace
 * ([PaneBandActions]). Řada je tak jedna: „← konverzace | ← nadpis panelu · akce panelu · akce
 * konverzace". Akce konverzace stojí na STEJNÉM místě s panelem i bez něj — dřív s otevřeným
 * panelem uskočily doprostřed okna, i tlačítko, kterým se panel právě otevřel.
 *
 * Šipku zpět boční panel v pásu nemá ([inPaneBand]): zavírá ho přepínač panelu.
 *
 * **Dřív měl boční panel lištu v kartě, pod pásem** (pás nesl jen akce konverzace): úprava
 * kanálu, kalendář i podstránky profilu tak měly dvě horní lišty pod sebou. Kořen profilu vedle
 * vlastní konverzace nadpis nemá (jméno je v hlavičce konverzace), jeho lišta nese jen akce
 * konverzace.
 *
 * Jen v Chatech ([PaneNeighbours.extraCard]) — podstránka Nastavení vedle sekce pás nemá.
 */
@Stable
class PaneBand internal constructor() {
    /** Akce konverzace pro pás; `null` = konverzace vedle bočního panelu teď není. */
    internal var actions by mutableStateOf<(@Composable () -> Unit)?>(null)

    /**
     * Horní hrana karty konverzace v okně (px): karta bočního panelu začne ve stejné výšce, ať
     * jsou obě karty i pás nad nimi v jedné řadě, ať je hlavička konverzace vysoká, jak chce.
     */
    internal var detailCardTop by mutableStateOf<Float?>(null)
}

/** Pás hlavičky scény; `null` mimo shell (bublina konverzace, náhledy). */
val LocalPaneBand = staticCompositionLocalOf<PaneBand?> { null }

@Composable
fun rememberPaneBand(): PaneBand = remember { PaneBand() }

/**
 * Pošle [actions] do pásu nad bočním panelem, když konverzace vedle něj stojí. `true` = kreslí je
 * pás a volající je ve své liště vynechá; `false` = kreslí je volající sám jako dřív.
 *
 * Akce se skládají v kompozici bočního panelu: co v nich bere ViewModel (`koinViewModel`), musí ho
 * dostat hotový zvenku — uvnitř by sáhly do úložiště ViewModelů panelu a dostaly jinou instanci.
 */
@Composable
fun publishToPaneBand(actions: @Composable () -> Unit): Boolean {
    val band = LocalPaneBand.current
    val neighbours = LocalPaneNeighbours.current
    val active = band != null && LocalPanesSideBySide.current && neighbours.extraBeside && neighbours.extraCard
    val current by rememberUpdatedState(actions)
    if (active) {
        val stable: @Composable () -> Unit = remember { { current() } }
        DisposableEffect(band, stable) {
            band.actions = stable
            onDispose { if (band.actions === stable) band.actions = null }
        }
    }
    return active
}

/** Akce konverzace pro hlavičku bočního panelu; `null` = panel v pásu není. */
internal val LocalPaneBandActions = compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Akce konverzace na konci lišty bočního panelu, který stojí v pásu vedle ní ([PaneBand]).
 * Patří do slotu `actions` KAŽDÉ lišty, kterou může panel v Chatech mít (profil, kalendář,
 * `ProfileSubpageTopBar`), za vlastní akce a před [WindowControlsEndSpace]. Lišta bez nich by
 * akce konverzace (přepínač panelu, ⋮) schovala. Mimo pás nedělá nic.
 */
@Composable
fun RowScope.PaneBandActions() {
    LocalPaneBandActions.current?.invoke()
}

/**
 * Stojí obrazovka jako boční panel v pásu vedle konverzace? Pak **nemá šipku zpět ani ✕**:
 * je to boční panel a zavírá ho přepínač panelu v pásu (`ProfilePaneButton`), ne vlastní
 * navigace — ani podstránka (úprava kanálu, pozvánky) nad profilem.
 */
@Composable
fun inPaneBand(): Boolean = LocalPaneBandActions.current != null
