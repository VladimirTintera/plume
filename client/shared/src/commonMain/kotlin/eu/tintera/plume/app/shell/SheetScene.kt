package eu.tintera.plume.app.shell

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import eu.tintera.plume.core.navigation.SheetRoute

/**
 * Položka se značkou [sheet] navrchu zásobníku se kreslí jako modální sheet NAD scénou, kterou
 * z položek pod ní spočítají ostatní strategie (list-detail). Stejný mechanismus jako
 * `DialogSceneStrategy` z Navigation3: [OverlayScene] s [OverlayScene.overlaidEntries].
 *
 * - **Sheet (`ModalBottomSheet`) si kreslí obsah položky sám**, scéna jen podá `entry.Content()`.
 *   Zavření tahem nebo zpět jde přes `onDismissRequest` sheetu, který routu odebere
 *   (`AppNavigation.closeSheet`); sheet je samostatné okno, takže zpět dostane on, ne `NavDisplay`.
 * - **Pod sheetem se nic nepřekresluje**: scéna pod ním zůstává tatáž, jen přestane být
 *   `RESUMED` (NavDisplay drží `RESUMED` jen nejvyšší překryvnou scénu).
 * - **Odebraný sheet zmizí bez animace**, když ho zavře něco jiného než on sám (otevření chatu
 *   zvenčí srovná zásobník). Zavření tahem animaci má — sheet dojede, než routu odebere.
 *   `onRemove` by animaci zvládlo i tak, ale potřebovalo by stav sheetu sdílený mimo položku.
 * - **Sheet bez ničeho pod sebou se nekreslí jako sheet** (`OverlayScene` musí něco překrývat);
 *   v zásobníku sekce je vždycky aspoň její kořen, takže to nenastane.
 */
internal class SheetSceneStrategy : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[SHEET_KEY] != true) return null
        val below = entries.dropLast(1)
        if (below.isEmpty()) return null
        return SheetScene(key = last.contentKey, entry = last, below = below)
    }

    companion object {
        /** Metadata položky, která se má kreslit jako sheet (její routa je [SheetRoute]). */
        fun sheet(): Map<String, Any> = mapOf(SHEET_KEY to true)
    }
}

private class SheetScene(
    override val key: Any,
    private val entry: NavEntry<NavKey>,
    private val below: List<NavEntry<NavKey>>,
) : OverlayScene<NavKey> {
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val previousEntries: List<NavEntry<NavKey>> get() = below
    override val overlaidEntries: List<NavEntry<NavKey>> get() = below
    override val content: @Composable () -> Unit = { entry.Content() }

    // Scéna se porovnává při každém přepočtu (`SceneState`); stejná položka nad stejnými = táž scéna.
    override fun equals(other: Any?): Boolean =
        this === other || (other is SheetScene && key == other.key && entry == other.entry && below == other.below)

    override fun hashCode(): Int = (key.hashCode() * 31 + entry.hashCode()) * 31 + below.hashCode()
}

private const val SHEET_KEY = "eu.tintera.plume.sheet"

/**
 * Zásobník bez sheetů navrchu ([SheetRoute]) — pro pravidla nad panely, čip sdílení a sekce
 * Nastavení, které se mají počítat ze scény POD sheetem. Bez sheetu vrací tutéž instanci
 * (porovnání identitou u `withProfileFollowing`).
 */
internal fun List<NavKey>.withoutTrailingSheets(): List<NavKey> {
    if (lastOrNull() !is SheetRoute) return this
    val keep = indexOfLast { it !is SheetRoute } + 1
    return take(keep)
}

/** Sheety navrchu zásobníku, v pořadí; kreslí se nad scénou z [withoutTrailingSheets]. */
internal fun List<NavKey>.trailingSheets(): List<NavKey> = drop(withoutTrailingSheets().size)
