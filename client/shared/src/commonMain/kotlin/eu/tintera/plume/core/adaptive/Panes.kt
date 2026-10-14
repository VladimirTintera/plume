package eu.tintera.plume.core.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavEntry

/** Panel list-detail scény, do kterého obrazovka patří. */
enum class PaneRole { List, Detail, Extra }

/** Role a scéna položky zásobníku; scéna odlišuje hlavní seznam chatů od archivu a Nastavení. */
data class PaneInfo(val role: PaneRole, val scene: Any)

/**
 * Metadata panelů list-detail scény: obalují `ListDetailSceneStrategy.listPane` / `detailPane`
 * / `extraPane` a přidávají k nim roli a scénu, kterou si knihovna drží pro sebe (klíč i hodnoty
 * má `internal`). Shell podle nich srovnává zásobník ([coveredExtras]) a volí počet panelů.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
object Panes {
    fun list(
        sceneKey: Any = Unit,
        detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = {},
    ): Map<String, Any> = ListDetailSceneStrategy.listPane(sceneKey, detailPlaceholder) + info(PaneRole.List, sceneKey)

    fun detail(sceneKey: Any = Unit): Map<String, Any> =
        ListDetailSceneStrategy.detailPane(sceneKey) + info(PaneRole.Detail, sceneKey)

    /** Třetí panel — profil a obrazovky, které se z něj otevírají. Patří k detailu pod sebou. */
    fun extra(sceneKey: Any = Unit): Map<String, Any> =
        ListDetailSceneStrategy.extraPane(sceneKey) + info(PaneRole.Extra, sceneKey)

    /** Preferovaná šířka panelu; `null` = výchozí z direktivy (nebo podle táhla). */
    fun width(width: Dp?): Map<String, Any> =
        if (width == null) emptyMap() else ListDetailSceneStrategy.preferredPaneSize(width = width)

    private fun info(role: PaneRole, sceneKey: Any): Map<String, Any> = mapOf(PANE_INFO_KEY to PaneInfo(role, sceneKey))
}

private const val PANE_INFO_KEY = "eu.tintera.plume.paneInfo"

/** Role položky podle metadat z [Panes]; obrazovka přes celou plochu (prohlížeč) žádnou nemá. */
val NavEntry<*>.paneInfo: PaneInfo? get() = metadata[PANE_INFO_KEY] as? PaneInfo

/**
 * Indexy extra položek, nad kterými v téže scéně leží detail — profil, nad který se otevřela
 * jiná konverzace (zmínka, „Napsat zprávu" členovi, jiný chat ze seznamu).
 *
 * Extra panel patří k detailu POD sebou. Scéna ale kreslí poslední detail a poslední extra
 * položku zvlášť, takže by zakrytý profil zůstal ve třetím panelu vedle cizí konverzace. Na
 * telefonu to nevadí — tam je to obyčejná historie a zpět se do profilu vrací.
 */
fun <K> List<K>.coveredExtras(paneOf: (K) -> PaneInfo?): List<Int> {
    val coveredScenes = mutableSetOf<Any>()
    val covered = mutableListOf<Int>()
    for (index in indices.reversed()) {
        val info = paneOf(this[index]) ?: continue
        when (info.role) {
            PaneRole.Detail -> coveredScenes += info.scene
            PaneRole.Extra -> if (info.scene in coveredScenes) covered += index
            PaneRole.List -> Unit
        }
    }
    return covered
}

/** Zásobník bez [coveredExtras]. */
fun <K> List<K>.withoutCoveredExtras(paneOf: (K) -> PaneInfo?): List<K> {
    val covered = coveredExtras(paneOf).toSet()
    return if (covered.isEmpty()) this else filterIndexed { index, _ -> index !in covered }
}

/** Odebere ze zásobníku [coveredExtras]. Indexy jdou od konce, takže se odebíráním neposunou. */
fun <K> MutableList<K>.dropCoveredExtras(paneOf: (K) -> PaneInfo?) {
    coveredExtras(paneOf).forEach(::removeAt)
}
