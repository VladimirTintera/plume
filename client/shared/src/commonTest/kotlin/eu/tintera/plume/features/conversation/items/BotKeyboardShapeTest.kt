package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.test.Test
import kotlin.test.assertEquals

class BotKeyboardShapeTest {
    private fun RoundedCornerShape.corners() = listOf(topStart, topEnd, bottomEnd, bottomStart)

    @Test
    fun velkeZaobleniJenVDolnichRozichBloku() {
        // Klávesnice 2 × 2: jen levé dolní a pravé dolní tlačítko má velký roh, a jen ten vnější.
        assertEquals(
            RoundedCornerShape(BUBBLE_TIGHT, BUBBLE_TIGHT, BUBBLE_TIGHT, BUBBLE_CORNER).corners(),
            botButtonShape(row = 1, column = 0, rows = 2, columns = 2).corners(),
        )
        assertEquals(
            RoundedCornerShape(BUBBLE_TIGHT, BUBBLE_TIGHT, BUBBLE_CORNER, BUBBLE_TIGHT).corners(),
            botButtonShape(row = 1, column = 1, rows = 2, columns = 2).corners(),
        )
        assertEquals(
            RoundedCornerShape(BUBBLE_TIGHT).corners(),
            botButtonShape(row = 0, column = 0, rows = 2, columns = 2).corners(),
        )
    }

    @Test
    fun jedineTlacitkoMaZaoblenyObaDolniRohy() {
        assertEquals(
            RoundedCornerShape(BUBBLE_TIGHT, BUBBLE_TIGHT, BUBBLE_CORNER, BUBBLE_CORNER).corners(),
            botButtonShape(row = 0, column = 0, rows = 1, columns = 1).corners(),
        )
    }
}
