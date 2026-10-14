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
    private val spacer = 24.dp

    @Test
    fun sbalenyZustaneSbaleny() {
        assertEquals(COLLAPSED_LIST_WIDTH, listWidthBesideProfile(ListPaneAnchor.Collapsed, 1200.dp, default, spacer))
    }

    @Test
    fun vychoziSeVejde() {
        // 1200 − profil 360 − konverzace 360 − dvě mezery = 432 dp, výchozí 360 se vejde.
        assertEquals(default, listWidthBesideProfile(ListPaneAnchor.Default, 1200.dp, default, spacer))
    }

    @Test
    fun polovinaSeZuziKvuliKonverzaci() {
        assertEquals(432.dp, listWidthBesideProfile(ListPaneAnchor.Half, 1200.dp, default, spacer))
    }

    @Test
    fun polovinaNaSirokePlochoZustanePolovinou() {
        // 3000 dp: polovina je 1488 dp, konverzaci zbyde 3000 − 1488 − 360 − 48 = 1104 dp.
        assertEquals(1488.dp, listWidthBesideProfile(ListPaneAnchor.Half, 3000.dp, default, spacer))
    }
}
