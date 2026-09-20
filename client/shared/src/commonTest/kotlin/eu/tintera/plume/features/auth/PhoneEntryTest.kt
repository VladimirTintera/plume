package eu.tintera.plume.features.auth

import androidx.compose.ui.text.AnnotatedString
import eu.tintera.plume.core.telegram.Country
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Formátování čísla a mapování pozic kurzoru. Testuje se proto, že maska od TDLib je jediný
 * vstup, který nemáme pod kontrolou — a chyba v mapování se na zařízení projeví až skákajícím
 * kurzorem, což se při klikání snadno přehlédne.
 */
class PhoneEntryTest {

    private val czechia = Country(code = "CZ", name = "Česko", flagEmoji = "🇨🇿", callingCode = "+420")
    private val switzerland = Country(code = "CH", name = "Švýcarsko", flagEmoji = "🇨🇭", callingCode = "+41")

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
    fun `kurzor za n-tou cislici stoji za odpovidajici pozici v masce`() {
        val mapping = PhoneOffsetMapping(formatted = "777 123 456", digitCount = 9)

        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(3, mapping.originalToTransformed(3))
        // Čtvrtá číslice leží až za mezerou.
        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(11, mapping.originalToTransformed(9))
    }

    @Test
    fun `pozice v masce se prevadi zpatky na pocet cislic`() {
        val mapping = PhoneOffsetMapping(formatted = "777 123 456", digitCount = 9)

        assertEquals(0, mapping.transformedToOriginal(0))
        assertEquals(3, mapping.transformedToOriginal(3))
        // Mezera se za číslici nepočítá.
        assertEquals(3, mapping.transformedToOriginal(4))
        assertEquals(9, mapping.transformedToOriginal(11))
    }

    /**
     * Maska, která nesedí s počtem číslic, se ignoruje. Nastane to na jeden snímek, než dorazí
     * přeformátování po změně země — a pole, které ukazuje jiné číslo, než v něm je, by bylo horší.
     */
    @Test
    fun `nesedici maska transformaci vypne`() {
        val transformed = PhoneNumberTransformation("777 123 456").filter(AnnotatedString("12"))

        assertEquals("12", transformed.text.text)
        assertEquals(2, transformed.offsetMapping.originalToTransformed(2))
    }

    @Test
    fun `hledani zeme nezalezi na diakritice ani na velikosti pismen`() {
        val list = listOf(czechia, switzerland)

        assertEquals(listOf(switzerland), list.matching("svycar"))
        assertEquals(listOf(switzerland), list.matching("ŠVÝCARSKO"))
        assertEquals(listOf(czechia), list.matching("Česko"))
    }

    @Test
    fun `hledat jde i podle predvolby`() {
        val list = listOf(czechia, switzerland)

        assertEquals(listOf(czechia), list.matching("+420"))
        assertEquals(listOf(czechia), list.matching("42"))
        assertEquals(list, list.matching("  "))
    }
}
