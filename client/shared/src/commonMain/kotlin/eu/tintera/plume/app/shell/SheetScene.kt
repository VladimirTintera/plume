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
 * Zásobník bez sheetů ([SheetRoute]) — pro pravidla nad panely, čip sdílení a sekce Nastavení,
 * které se mají počítat ze scény POD sheetem. Bez sheetu vrací tutéž instanci (porovnání
 * identitou u `withProfileFollowing`).
 *
 * **Vynechá i sheet uprostřed** ([dropBuriedSheets]): ten se nekreslí vůbec, ani jako obrazovka
 * v panelu, ani jako předchozí scéna v náhledu gesta zpět — tam by se jeho `ModalBottomSheet`
 * otevřel jako samostatné okno, gesto sebral a z obrazovky nad ním by nešlo odejít.
 */
internal fun List<NavKey>.withoutSheets(): List<NavKey> =
    if (none { it is SheetRoute }) this else filterNot { it is SheetRoute }

/** Sheety navrchu zásobníku, v pořadí; kreslí se nad scénou z [withoutSheets]. */
internal fun List<NavKey>.trailingSheets(): List<NavKey> = takeLastWhile { it is SheetRoute }

/** Leží v zásobníku sheet pod něčím, co sheet není? Pak ho [dropBuriedSheets] odklidí. */
internal fun List<NavKey>.hasBuriedSheet(): Boolean =
    dropLastWhile { it is SheetRoute }.any { it is SheetRoute }

/**
 * Odebere sheety, které nestojí navrchu. Sheet patří k obrazovce pod sebou a nad ním se nic
 * neotevírá: kdo vede jinam, sheet zavře napřed (`AppNavigation.closeSheet`) a vstupy zvenčí
 * srovnají zásobník (`popToRoot`). Tohle je pojistka pro cestu, která na to zapomene —
 * zpět z obrazovky nad sheetem by ho jinak vrátil. Vrací, jestli něco odebrala.
 */
internal fun MutableList<NavKey>.dropBuriedSheets(): Boolean {
    val keepFrom = size - trailingSheets().size
    var removed = false
    for (index in keepFrom - 1 downTo 0) {
        if (this[index] is SheetRoute) {
            removeAt(index)
            removed = true
        }
    }
    return removed
}
