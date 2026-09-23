package eu.tintera.plume.features.auth

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeEntryTest {

    @Test
    fun ciscePropustiJenCislice() {
        assertEquals("12345", codeDigits("12345", digits = 5))
    }

    @Test
    fun vlozenaSmsSeOreze() {
        // Uživatel zkopíruje celou zprávu, ne jen kód.
        assertEquals("12345", codeDigits("Telegram code: 12345", digits = 5))
    }

    @Test
    fun oddelovaceZmizi() {
        assertEquals("123456", codeDigits("123-456", digits = 6))
    }

    @Test
    fun cisliceNavicSeZahodi() {
        assertEquals("12345", codeDigits("123456789", digits = 5))
    }

    @Test
    fun prazdnyVstupDaPrazdno() {
        assertEquals("", codeDigits("", digits = 5))
        assertEquals("", codeDigits("kód", digits = 5))
    }

    @Test
    fun aktivniPolickoJdeSPsanim() {
        assertEquals(0, activeCodeCell(typed = 0, digits = 5))
        assertEquals(3, activeCodeCell(typed = 3, digits = 5))
    }

    @Test
    fun plnyKodNechaRamecekNaPoslednimPolicku() {
        // Ne na indexu 5 — ten v řadě není a rámeček by zmizel při odesílání.
        assertEquals(4, activeCodeCell(typed = 5, digits = 5))
        assertEquals(4, activeCodeCell(typed = 9, digits = 5))
    }
}

class CodeCellHeightTest {

    @Test
    fun sirokyRadekNechaPevnouVysku() {
        // 420 dp na pět políček — místa dost, výška se nezmenšuje.
        assertEquals(64.dp, codeCellHeight(rowWidth = 420.dp, digits = 5, gap = 8.dp, maxHeight = 64.dp))
    }

    @Test
    fun uzkyRadekVyskuSrazi() {
        // Telefon široký 360 dp po odečtení okrajů: šest políček po 45 dp.
        val height = codeCellHeight(rowWidth = 312.dp, digits = 6, gap = 8.dp, maxHeight = 64.dp)
        assertEquals(54.4.dp.value, height.value, absoluteTolerance = 0.1f)
    }

    @Test
    fun zapornaSirkaNevyrobiZapornouVysku() {
        assertEquals(0.dp, codeCellHeight(rowWidth = 0.dp, digits = 6, gap = 8.dp, maxHeight = 64.dp))
    }

    @Test
    fun zadneCislicePolickoNerozbiji() {
        assertEquals(64.dp, codeCellHeight(rowWidth = 312.dp, digits = 0, gap = 8.dp, maxHeight = 64.dp))
    }
}
