package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Formátování čísla a mapování pozic kurzoru. Testuje se proto, že maska od TDLib je jediný
 * vstup, který nemáme pod kontrolou — a chyba v mapování se na zařízení projeví až skákajícím
 * kurzorem, což se při klikání snadno přehlédne.
 */
class PhoneEntryTest {

    private val czechia = Country(code = "CZ", name = "Česko", englishName = "Czech Republic", flagEmoji = "🇨🇿", callingCode = "+420")
    private val switzerland = Country(code = "CH", name = "Švýcarsko", englishName = "Switzerland", flagEmoji = "🇨🇭", callingCode = "+41")

    @Test
    fun `z masky se bere jen to co uzivatel napsal`() {
        assertEquals("777", formatNationalNumber("777 --- ---"))
        assertEquals("777 123", formatNationalNumber("777 123 ---"))
        assertEquals("777 123 456", formatNationalNumber("777 123 456"))
    }

    @Test
    fun `prazdna maska da prazdny text`() {
        assertEquals("", formatNationalNumber(""))
        assertEquals("", formatNationalNumber("--- --- ---"))
    }

    @Test
    fun `cislic navic nez maska ceka se neztrati`() {
        assertEquals("777 123 4567", formatNationalNumber("777 123 4567"))
    }

    @Test
    fun `mezinarodni cislo se sklada z predvolby a narodni casti`() {
        val entry = PhoneEntry(country = czechia, digits = "777123456", formatted = "777 123 456")
        assertEquals("+420777123456", entry.international)
    }

    @Test
    fun `bez zeme se posila aspon to co uzivatel napsal`() {
        assertEquals("+777123456", PhoneEntry(digits = "777123456").international)
    }

    @Test
    fun `oddelovace se vkladaji tam kde je ma format`() {
        // Pozice jsou ve VÝSLEDNÉM textu: mezera na 3 a 7, vkládá se zleva.
        assertEquals(listOf(3 to ' ', 7 to ' '), phoneSeparators("777 123 456", "777123456"))
        assertEquals(listOf(0 to '(', 4 to ')', 5 to ' ', 9 to '-'), phoneSeparators("(212) 555-0100", "2125550100"))
    }

    @Test
    fun `vlozeni oddelovacu da presne format`() {
        val digits = StringBuilder("2125550100")
        phoneSeparators("(212) 555-0100", digits.toString())!!.forEach { (at, c) -> digits.insert(at, c) }
        assertEquals("(212) 555-0100", digits.toString())
    }

    /**
     * Maska, která nesedí s číslicemi v poli, se ignoruje. Nastane to na jeden snímek, než dorazí
     * přeformátování po změně země — a pole, které ukazuje jiné číslo, než v něm je, by bylo horší.
     */
    @Test
    fun `nesedici maska nevlozi nic`() {
        assertNull(phoneSeparators("777 123 456", "12"))
        // Stejný počet číslic, ale jiné číslo (formát pro předchozí hodnotu).
        assertNull(phoneSeparators("777 123 456", "777123457"))
        assertNull(phoneSeparators("777123456", "777123456"))
    }

    @Test
    fun `hledani zeme nezalezi na diakritice ani na velikosti pismen`() {
        val list = listOf(czechia, switzerland)

        assertEquals(listOf(switzerland), list.matching("svycar"))
        assertEquals(listOf(switzerland), list.matching("ŠVÝCARSKO"))
        assertEquals(listOf(czechia), list.matching("Česko"))
    }

    /**
     * Názvy překládá systém, takže v české aplikaci je „Německo" — kdo zná zemi jen anglicky,
     * ji musí najít taky.
     */
    @Test
    fun `hledat jde i podle anglickeho nazvu`() {
        val list = listOf(czechia, switzerland)

        assertEquals(listOf(switzerland), list.matching("switz"))
        assertEquals(listOf(czechia), list.matching("Czech Rep"))
    }

    @Test
    fun `hledat jde i podle predvolby`() {
        val list = listOf(czechia, switzerland)

        assertEquals(listOf(czechia), list.matching("+420"))
        assertEquals(listOf(czechia), list.matching("42"))
        assertEquals(list, list.matching("  "))
    }
}
