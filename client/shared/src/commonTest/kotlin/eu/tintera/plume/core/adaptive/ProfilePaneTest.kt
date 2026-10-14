package eu.tintera.plume.core.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Šířka profilu vedle konverzace ([profileWidthBeside]) a kotva po puštění táhla. */
class ProfilePaneTest {

    private val default = 360.dp

    @Test
    fun sirokyProfilSeVejdeNaVelkePlose() {
        // 2000 dp: seznam 360 a mezera, konverzaci zbyde přes 1000 dp.
        assertEquals(600.dp, profileWidthBeside(600.dp, total = 2000.dp, listWidth = 360.dp, defaultWidth = default))
    }

    @Test
    fun konverzaciZbydeAsponMinimum() {
        // 1200 dp ve třech panelech: 1200 − seznam 360 − mezera 12 − konverzace 360 = 468 dp na profil.
        assertEquals(1200.dp - 360.dp - PANE_SPACING - MIN_CONVERSATION_WIDTH, profileWidthBeside(600.dp, total = 1200.dp, listWidth = 360.dp, defaultWidth = default))
    }

    @Test
    fun bezSeznamuJeVicMista() {
        // 900 dp, seznam schovaný: 900 − 360 = 540 dp na profil.
        assertEquals(540.dp, profileWidthBeside(600.dp, total = 900.dp, listWidth = null, defaultWidth = default))
    }

    @Test
    fun uzsiNezVychoziNejde() {
        assertEquals(default, profileWidthBeside(200.dp, total = 2000.dp, listWidth = 360.dp, defaultWidth = default))
    }

    @Test
    fun pustenyTahDosedneNaNejblizsiKotvu() {
        assertEquals(ProfilePaneAnchor.Default, nearestProfileAnchor(400.dp, default))
        assertEquals(ProfilePaneAnchor.Medium, nearestProfileAnchor(470.dp, default))
        assertEquals(ProfilePaneAnchor.Wide, nearestProfileAnchor(580.dp, default))
    }
}
