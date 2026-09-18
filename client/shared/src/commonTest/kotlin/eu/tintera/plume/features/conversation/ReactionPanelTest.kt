package eu.tintera.plume.features.conversation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Umístění rozbaleného výběru reakcí.
 *
 * Panel jde na tu stranu bubliny, kde je víc místa, a výšku bere z toho, co zbylo — vybraná
 * zpráva musí zůstat vidět. Je to samý hraniční případ (zpráva u horní hrany, u spodní, přes
 * celou obrazovku), takže je pravidlo čistá funkce mimo composable, jako `photoFrame`.
 */
class ReactionPanelTest {

    /** Běžný telefon: stavová lišta nahoře, gesta dole. */
    private val screenHeight = 800.dp
    private val topMargin = 36.dp
    private val bottomMargin = 36.dp

    private fun place(top: Dp, bottom: Dp) = placeReactionPanel(
        anchorTop = top,
        anchorBottom = bottom,
        screenHeight = screenHeight,
        topMargin = topMargin,
        bottomMargin = bottomMargin,
    )

    private fun assertDp(expected: Dp, actual: Dp) =
        assertEquals(expected.value, actual.value, absoluteTolerance = 0.5f)

    @Test
    fun zpravaDoleDostanePanelNadSebe() {
        val panel = place(top = 600.dp, bottom = 660.dp)

        assertDp(REACTION_PANEL_MAX_HEIGHT, panel.height)
        // Osm dp nad bublinou, stejná mezera jako u sbaleného pruhu.
        assertDp(600.dp - 8.dp - REACTION_PANEL_MAX_HEIGHT, panel.y)
    }

    @Test
    fun zpravaNahoreDostanePanelPodSebe() {
        val panel = place(top = 60.dp, bottom = 120.dp)

        assertDp(REACTION_PANEL_MAX_HEIGHT, panel.height)
        assertDp(128.dp, panel.y)
    }

    /** Nad zprávou je málo místa, pod ní dost — panel jde dolů, i když se nahoru „vejde". */
    @Test
    fun panelJdeNaStranuSVicMistem() {
        val panel = place(top = 200.dp, bottom = 260.dp)

        assertTrue(panel.y > 260.dp, "panel má být pod zprávou, je na ${panel.y}")
    }

    /** Zbylé místo je menší než strop, takže se panel zmenší — mřížka se v něm listuje. */
    @Test
    fun panelSeSmrstiNaZbyleMisto() {
        val panel = place(top = 340.dp, bottom = 560.dp)

        // Nad zprávou zbývá 340 - 8 - 36 = 296, pod ní 800 - 36 - 568 = 196 → jde nahoru.
        assertDp(280.dp, panel.height)
        assertDp(52.dp, panel.y)
    }

    /** Zpráva přes skoro celou obrazovku: panel se s ní překryje, ale pod lišty nezaleze. */
    @Test
    fun uZpravyPresCelouObrazovkuPanelZustaneMeziListami() {
        val panel = place(top = 40.dp, bottom = 770.dp)

        assertDp(REACTION_PANEL_MIN_HEIGHT, panel.height)
        assertTrue(panel.y >= topMargin, "panel zalezl pod stavovou lištu: ${panel.y}")
        assertTrue(
            panel.y + panel.height <= screenHeight - bottomMargin,
            "panel zalezl pod navigační lištu: ${panel.y + panel.height}",
        )
    }

    /** Okno nižší než podlaha panelu — výška se uřízne na to, co je mezi lištami. */
    @Test
    fun naNizkemOkneSePanelVejdeMeziListy() {
        val panel = placeReactionPanel(
            anchorTop = 40.dp,
            anchorBottom = 90.dp,
            screenHeight = 160.dp,
            topMargin = topMargin,
            bottomMargin = bottomMargin,
        )

        assertDp(88.dp, panel.height)
        assertDp(topMargin, panel.y)
    }
}
