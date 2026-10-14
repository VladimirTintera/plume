package eu.tintera.plume.core.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Šířka seznamu ve třech panelech ([listWidthBesideProfile]): táhlo tam knihovna nekreslí, takže
 * se zvolená kotva přenáší sem — a konverzaci vedle profilu musí zbýt dost místa.
 */
class ListWidthBesideProfileTest {

    private val default = 360.dp
    private val gap = 12.dp

    @Test
    fun sbalenyZustaneSbaleny() {
        assertEquals(COLLAPSED_LIST_WIDTH, listWidthBesideProfile(ListPaneAnchor.Collapsed, 1200.dp, default, gap))
    }

    @Test
    fun vychoziSeVejde() {
        // 1200 − profil 360 − konverzace 360 − mezera před kartou = 468 dp, výchozí 360 se vejde.
        assertEquals(default, listWidthBesideProfile(ListPaneAnchor.Default, 1200.dp, default, gap))
    }

    @Test
    fun polovinaSeZuziKvuliKonverzaci() {
        assertEquals(468.dp, listWidthBesideProfile(ListPaneAnchor.Half, 1200.dp, default, gap))
    }

    @Test
    fun polovinaNaSirokePlochoZustanePolovinou() {
        // 3000 dp: polovina je 1500 dp, konverzaci zbyde 3000 − 1500 − 360 − 12 = 1128 dp.
        assertEquals(1500.dp, listWidthBesideProfile(ListPaneAnchor.Half, 3000.dp, default, gap))
    }

    @Test
    fun tahSeOmeziJakoKotvy() {
        assertEquals(COLLAPSED_LIST_WIDTH, clampListBesideProfile(40.dp, 2000.dp, default, gap))
        // 1200 dp: 1200 − profil 360 − konverzace 360 − mezera = 468 dp nejvýš.
        assertEquals(468.dp, clampListBesideProfile(900.dp, 1200.dp, default, gap))
    }

    @Test
    fun pustenyTahDosedneNaNejblizsiKotvu() {
        assertEquals(ListPaneAnchor.Collapsed, nearestListAnchorBesideProfile(150.dp, 2000.dp, default, gap))
        assertEquals(ListPaneAnchor.Default, nearestListAnchorBesideProfile(420.dp, 2000.dp, default, gap))
        assertEquals(ListPaneAnchor.Half, nearestListAnchorBesideProfile(900.dp, 2000.dp, default, gap))
    }
}
