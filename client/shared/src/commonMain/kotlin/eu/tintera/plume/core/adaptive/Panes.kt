package eu.tintera.plume.core.adaptive

import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavEntry

/** Panel scény panelů (`PaneScene`), do kterého obrazovka patří. */
enum class PaneRole { List, Detail, Extra }

/** Role a scéna položky zásobníku; scéna odlišuje hlavní seznam chatů od archivu a Nastavení. */
data class PaneInfo(val role: PaneRole, val scene: Any)

/**
 * Metadata panelů: role a scéna položky. Podle nich scéna panelů (`PaneScene`) vybírá, co kreslí,
 * a shell srovnává zásobník ([coveredExtras]). Dřív obalovala i metadata knihovního
 * `ListDetailSceneStrategy`; od scény panelů pro Chaty i Nastavení knihovna nic nekreslí.
 */
object Panes {
    fun list(sceneKey: Any = Unit): Map<String, Any> = info(PaneRole.List, sceneKey)

    fun detail(sceneKey: Any = Unit): Map<String, Any> = info(PaneRole.Detail, sceneKey)

    /** Boční sloupec — profil a obrazovky, které se z něj otevírají. Patří k detailu pod sebou. */
    fun extra(sceneKey: Any = Unit): Map<String, Any> = info(PaneRole.Extra, sceneKey)

    /**
     * Obsah panelu nejvýš [maxWidth] široký, na širším panelu vycentrovaný (`PaneCard`). Pro
     * obrazovky, které si strop samy nehlídají — sekce Nastavení jich mají přes deset, každá
     * s vlastním seznamem. Zúží se i jejich hlavička, takže nadpis lícuje s obsahem.
     */
    fun contentWidth(maxWidth: Dp): Map<String, Any> = mapOf(CONTENT_MAX_WIDTH_KEY to maxWidth)

    /**
     * Obrazovka, která NENÍ stránkou s bloky řádků (`asListPage`) a nechává si `surface`:
     * konverzace (na té ploše jsou vyladěné bubliny), článek, prohlížeč médií a podobně.
     */
    fun ownSurface(): Map<String, Any> = mapOf(OWN_SURFACE_KEY to true)

    /**
     * Vedle jiných panelů stojí hlavička obrazovky NAD kartou, na ploše okna, a karta začíná až pod
     * ní (styl Google Zpráv): konverzace a profil vedle ní. Hlavičku obrazovka obalí do
     * [PaneHeader]; stránka s bloky řádků navíc nastaví Scaffoldu [paneScaffoldColor] a obsah
     * obalí do [PaneBody].
     */
    fun headerOutside(): Map<String, Any> = mapOf(HEADER_OUTSIDE_KEY to true)

    /**
     * Zpět z téhle položky opouští celou scénu (`PaneSceneStrategy`), ne jen ji: sekce Nastavení,
     * která se v širokém okně otevírá sama a obyčejné zpět by ji hned vrátilo.
     */
    fun backLeavesScene(): Map<String, Any> = mapOf(BACK_LEAVES_SCENE_KEY to true)

    private fun info(role: PaneRole, sceneKey: Any): Map<String, Any> = mapOf(PANE_INFO_KEY to PaneInfo(role, sceneKey))
}

/**
 * Boční položky navrchu zásobníku (profil, podstránka Nastavení) — tedy to, co je v bočním
 * sloupci vidět. Položka přes celou plochu (prohlížeč nad profilem) se přeskočí: sloupec pod ní
 * pořád je.
 */
fun <K> List<K>.shownExtras(paneOf: (K) -> PaneInfo?): List<K> {
    val shown = ArrayList<K>()
    for (index in indices.reversed()) {
        val info = paneOf(this[index]) ?: continue
        if (info.role != PaneRole.Extra) break
        shown += this[index]
    }
    return shown.asReversed()
}

private const val BACK_LEAVES_SCENE_KEY = "eu.tintera.plume.backLeavesScene"

/** Položka z [Panes.backLeavesScene]. */
val NavEntry<*>.backLeavesScene: Boolean get() = metadata[BACK_LEAVES_SCENE_KEY] == true

private const val PANE_INFO_KEY = "eu.tintera.plume.paneInfo"

/** Role položky podle metadat z [Panes]; obrazovka přes celou plochu (prohlížeč) žádnou nemá. */
val NavEntry<*>.paneInfo: PaneInfo? get() = metadata[PANE_INFO_KEY] as? PaneInfo

private const val CONTENT_MAX_WIDTH_KEY = "eu.tintera.plume.contentMaxWidth"

/** Strop šířky obsahu z [Panes.contentWidth]; `null` = obsah přes celý panel. */
val NavEntry<*>.contentMaxWidth: Dp? get() = metadata[CONTENT_MAX_WIDTH_KEY] as? Dp

private const val OWN_SURFACE_KEY = "eu.tintera.plume.ownSurface"

/** Položka z [Panes.ownSurface]; ostatní jsou stránky s bloky řádků. */
val NavEntry<*>.ownSurface: Boolean get() = metadata[OWN_SURFACE_KEY] == true

private const val HEADER_OUTSIDE_KEY = "eu.tintera.plume.headerOutside"

/** Položka z [Panes.headerOutside]. */
val NavEntry<*>.headerOutside: Boolean get() = metadata[HEADER_OUTSIDE_KEY] == true

/**
 * Indexy extra položek, nad kterými v téže scéně leží detail — profil, nad který se otevřela
 * jiná konverzace (zmínka, „Napsat zprávu" členovi, jiný chat ze seznamu).
 *
 * Extra panel patří k detailu POD sebou. Scéna ale kreslí poslední detail a poslední extra
 * položku zvlášť, takže by zakrytý profil zůstal ve třetím panelu vedle cizí konverzace. Na
 * telefonu to nevadí — tam je to obyčejná historie a zpět se do profilu vrací. Co s nimi, řeší
 * shell (`followProfile`: panel přejde na profil nové konverzace).
 *
 * Indexy jdou OD KONCE, takže se jejich odebíráním po řadě neposunou.
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
