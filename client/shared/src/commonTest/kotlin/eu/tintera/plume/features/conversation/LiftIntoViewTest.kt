package eu.tintera.plume.features.conversation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Posun zprávy do pohledu při otevřené nabídce (jako v Google Zprávách). */
class LiftIntoViewTest {

    private fun lift(top: Int, height: Int, quickRow: Int = 60) = liftIntoView(
        anchorTop = top.dp,
        anchorHeight = height.dp,
        screenHeight = 800.dp,
        topMargin = 40.dp,
        bottomMargin = 40.dp,
        quickRowSpace = quickRow.dp,
    )

    @Test
    fun zpravaViditelnaSPruhemNadSebouSeNehne() {
        assertEquals(0.dp, lift(top = 300, height = 100))
    }

    @Test
    fun zpravaPodHlavickouSjedeDolu() {
        // Začíná nad okrajem: dolů tak, aby nad ní byl pruh reakcí (40 + 60).
        assertEquals(150.dp, lift(top = -50, height = 200))
    }

    @Test
    fun zpravaUSpodniHranyVyjedeNahoru() {
        // Spodek na 820 přesahuje okraj (760): nahoru o 60.
        assertEquals((-60).dp, lift(top = 620, height = 200))
    }

    @Test
    fun bezPruhuReakciStaciOkraj() {
        assertEquals(10.dp, lift(top = 30, height = 100, quickRow = 0))
    }

    @Test
    fun zpravaVyssiNezObrazovkaSeSrovnaHorniHranou() {
        // Nevejde se celá: začátek zprávy pod pruh reakcí, zbytek přesahuje dolů.
        assertEquals(300.dp, lift(top = -200, height = 1200))
    }
}
