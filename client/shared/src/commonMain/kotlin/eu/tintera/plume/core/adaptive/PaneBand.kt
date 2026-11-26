package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.LocalPanesSideBySide

/**
 * Akce konverzace (hovor, přepínač panelu, ⋮) jako konec lišty scény Chatů.
 *
 * Konverzace v liště nechává vlevo sebe (šipka, avatar, název) a své akce pošle sem
 * ([publishToPaneBand]). Scéna je kreslí na pevném místě u pravého okraje oblasti chatu
 * ([PaneBandEnd]) a hlavička, která tam končí (konverzace bez bočního sloupce, sloupec s ním),
 * nechá na svém konci jen jejich místo ([PaneBandSpace], [PaneBandActions]). Řada je tak jedna:
 * „← konverzace | nadpis sloupce · akce sloupce · akce konverzace" a akce konverzace se při
 * otevření ani zavření sloupce nehnou.
 *
 * Šipku zpět boční sloupec v pásu nemá ([inPaneBand]): zavírá ho přepínač panelu.
 *
 * **Dřív pás měřil karty v okně** (`detailCardTop`, nejmenší výška hlavičky sloupce), aby hlavička
 * sloupce lícovala s hlavičkou konverzace, a akce kreslila hlavička sloupce. S lištou scény
 * (`SceneBar`) lícují úseky rozvržením a měření zmizelo.
 *
 * Jen v Chatech ([PaneNeighbours.extraCard]) — podstránka Nastavení vedle sekce pás nemá.
 */
@Stable
class PaneBand internal constructor() {
    /** Akce konverzace pro pás; `null` = konverzace vedle bočního panelu teď není. */
    internal var actions by mutableStateOf<(@Composable () -> Unit)?>(null)

    /**
     * Kontext, ve kterém konverzace akce poslala (`currentCompositionLocalContext`): scéna Chatů
     * je kreslí sama ([PaneBandEnd]) a skládá je v něm, ať mají barvy hlavičky konverzace.
     */
    internal var actionsContext by mutableStateOf<CompositionLocalContext?>(null)

    /** Šířka akcí na konci lišty scény; tolik místa si na svém konci nechají hlavičky ([PaneBandActions]). */
    internal var actionsWidth by mutableStateOf(0.dp)
}

/** Pás hlavičky scény; `null` mimo shell (bublina konverzace, náhledy). */
val LocalPaneBand = staticCompositionLocalOf<PaneBand?> { null }

@Composable
fun rememberPaneBand(): PaneBand = remember { PaneBand() }

/**
 * Pošle [actions] na konec lišty scény Chatů. `true` = kreslí je scéna a volající nechá ve své liště
 * jen jejich místo ([PaneBandSpace]); `false` = kreslí je volající sám (telefon, mimo scénu).
 *
 * Akce se skládají v kontextu hlavičky konverzace ([PaneBand.actionsContext]), takže `koinViewModel`
 * v nich vrátí ViewModel konverzace.
 */
@Composable
fun publishToPaneBand(actions: @Composable () -> Unit): Boolean {
    val band = LocalPaneBand.current
    // Jen ve scéně Chatů ([SceneBar]): tam akce stojí na konci lišty s panelem i bez něj.
    val active = band != null && LocalPanesSideBySide.current && LocalSceneBarTarget.current != null
    val current by rememberUpdatedState(actions)
    val context = currentCompositionLocalContext
    if (active) {
        val stable: @Composable () -> Unit = remember { { current() } }
        SideEffect { if (band.actions === stable) band.actionsContext = context }
        DisposableEffect(band, stable) {
            band.actions = stable
            band.actionsContext = context
            onDispose {
                if (band.actions === stable) {
                    band.actions = null
                    band.actionsContext = null
                }
            }
        }
    }
    return active
}

/** Akce konverzace, je-li boční sloupec v pásu vedle ní; `null` = není. Hlavička je nekreslí, jen podle nich pozná pás. */
internal val LocalPaneBandActions = compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Místo pro akce konverzace na konci lišty bočního sloupce, který stojí v pásu vedle ní
 * ([PaneBand]); akce samotné kreslí scéna ([PaneBandEnd]). Patří do slotu `actions` KAŽDÉ lišty,
 * kterou může sloupec v Chatech mít (profil, kalendář, `ProfileSubpageTopBar`), za vlastní akce
 * a před [WindowControlsEndSpace]. Lišta bez něj by vlastní akce schovala pod akce konverzace.
 * Mimo pás nedělá nic.
 */
@Composable
fun RowScope.PaneBandActions() {
    if (LocalPaneBandActions.current != null) PaneBandSpace()
}

/**
 * Místo pro akce konverzace na konci hlavičky, kterou ve scéně Chatů kreslí scéna sama
 * ([PaneBandEnd]). Mimo scénu nic. Konverzace ho dává místo svých akcí, když je poslala do pásu
 * a vedle ní žádný boční sloupec není — je pak sama u pravého okraje.
 */
@Composable
fun RowScope.PaneBandSpace() {
    if (LocalSceneBarTarget.current == null) return
    if (LocalPaneNeighbours.current.extraBeside && LocalPaneBandActions.current == null) return
    Spacer(Modifier.width(LocalPaneBand.current?.actionsWidth ?: 0.dp))
}

/**
 * Akce konverzace na konci lišty scény Chatů: na pevném místě u pravého okraje oblasti chatu,
 * mimo boční sloupec, takže se při jeho otevření ani zavření nehnou. Dřív je nesla hlavička toho
 * sloupce, který byl zrovna vpravo — z konverzace zmizely hned, ve sloupci se objevily až s jeho
 * animací a jely s jeho rostoucí šířkou.
 *
 * [rowHeight] je výška řady lišty ([SceneBar]); akce sedí v jejím spodním řádku, jako akce
 * `TopAppBar` hlaviček vedle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaneBandEnd(band: PaneBand, rowHeight: Dp, endClearance: Dp, modifier: Modifier = Modifier) {
    val actions = band.actions ?: return
    val context = band.actionsContext ?: return
    val density = LocalDensity.current
    Box(modifier.height(rowHeight)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = PANE_HEADER_GAP)
                .height(TopAppBarDefaults.TopAppBarExpandedHeight)
                .padding(end = TOP_APP_BAR_END_PADDING + endClearance)
                .onSizeChanged { band.actionsWidth = with(density) { it.width.toDp() } - endClearance },
        ) {
            CompositionLocalProvider(context) { actions() }
        }
    }
}

/** Vodorovný okraj akcí v `TopAppBar` (M3 `TopAppBarHorizontalPadding`). */
private val TOP_APP_BAR_END_PADDING = 4.dp

/**
 * Stojí obrazovka jako boční panel v pásu vedle konverzace? Pak **nemá šipku zpět ani ✕**:
 * je to boční panel a zavírá ho přepínač panelu v pásu (`ProfilePaneButton`), ne vlastní
 * navigace — ani podstránka (úprava kanálu, pozvánky) nad profilem.
 */
@Composable
fun inPaneBand(): Boolean = LocalPaneBandActions.current != null
