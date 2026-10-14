package eu.tintera.plume.core.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Okraj sloupce uprostřed ([columnInset]) — seznam zpráv si ho přidá do `contentPadding`. */
class ColumnInsetTest {

    @Test
    fun uzsiPlochaOkrajNema() {
        assertEquals(0.dp, columnInset(available = 600.dp, maxWidth = ContentWidth.Conversation))
    }

    @Test
    fun sirsiPlochaSloupecVycentruje() {
        // 2000 dp: sloupec 840 a z každé strany 580.
        assertEquals(580.dp, columnInset(available = 2000.dp, maxWidth = ContentWidth.Conversation))
    }
}
