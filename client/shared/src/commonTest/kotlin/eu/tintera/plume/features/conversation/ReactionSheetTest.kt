package eu.tintera.plume.features.conversation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rozvržení vyjetého výběru reakcí.
 *
 * Sheet sedí u spodní hrany a bere podíl výšky okna; pruh rychlých reakcí zůstává u zprávy,
 * dokud se nad něj vejde. Je to samý hraniční případ (zpráva dole, nahoře, nízké okno, vysoké
 * okno), takže je pravidlo čistá funkce mimo composable — jako `photoFrame`.
 */
class ReactionSheetTest {

    /** Běžný telefon: stavová lišta nahoře, odstup od kraje. */
    private val screenHeight = 800.dp
    private val topMargin = 36.dp

    private fun place(anchorTop: Dp, screenHeight: Dp = this.screenHeight) = reactionSheetLayout(
        anchorTop = anchorTop,
        screenHeight = screenHeight,
        topMargin = topMargin,
    )

    private fun assertDp(expected: Dp, actual: Dp) =
        assertEquals(expected.value, actual.value, absoluteTolerance = 0.5f)

    @Test
    fun sheetBereZhrubaPulOkna() {
        assertDp(360.dp, place(anchorTop = 600.dp).height)
    }

    /** Zpráva dole leží pod sheetem, takže se pruh reakcí uklidí těsně nad něj. */
    @Test
    fun pruhReakciSeUkliditNadSheet() {
        val layout = place(anchorTop = 600.dp)

        // Sheet začíná na 440, nad ním osm dp mezera a 52 dp pruh.
        assertDp(380.dp, layout.quickY)
    }

    /** Zpráva nahoře se sheetem nekoliduje — pruh zůstane u ní. */
    @Test
    fun pruhReakciZustaneUZpravy() {
        assertDp(140.dp, place(anchorTop = 200.dp).quickY)
    }

    @Test
    fun naVysokemOkneSeSheetZastropuje() {
        assertDp(REACTION_SHEET_MAX_HEIGHT, place(anchorTop = 600.dp, screenHeight = 1600.dp).height)
    }

    @Test
    fun naMalemOkneSheetNeklesnePodPodlahu() {
        val layout = place(anchorTop = 100.dp, screenHeight = 400.dp)

        assertDp(REACTION_SHEET_MIN_HEIGHT, layout.height)
        assertDp(40.dp, layout.quickY)
    }

    /** Ani podlaha se nevejde: sheet ustoupí, aby nad ním zbylo na pruh reakcí. */
    @Test
    fun naNizkemOkneSheetUstoupiPruhu() {
        val layout = place(anchorTop = 250.dp, screenHeight = 300.dp)

        assertDp(204.dp, layout.height)
        assertDp(topMargin, layout.quickY)
    }
}
