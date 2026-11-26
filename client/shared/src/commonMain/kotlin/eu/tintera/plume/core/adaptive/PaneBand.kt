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
    // Ve scéně Chatů ([SceneBar]) vždycky: akce stojí na konci lišty scény s panelem i bez něj.
    val inScene = LocalSceneBarTarget.current != null
    val active = band != null && LocalPanesSideBySide.current && (inScene || neighbours.extraBeside && neighbours.extraCard)
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
    val actions = LocalPaneBandActions.current ?: return
    // Ve scéně Chatů kreslí akce scéna na konci lišty ([PaneBandEnd]); tady zůstane jen jejich místo.
    if (LocalSceneBarTarget.current != null) PaneBandSpace() else actions()
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
