package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PriceTest {
    @Test
    fun hvezdyNemajiDesetiny() {
        val price = Price(amount = 25, currency = "XTR")
        assertTrue(price.isStars)
        assertEquals(PriceParts(whole = 25, fraction = null), price.parts())
    }

    @Test
    fun beznaMenaMaDveDesetinnaMista() {
        assertEquals(PriceParts(whole = 12, fraction = "50"), Price(1250, "EUR").parts())
        assertFalse(Price(1250, "EUR").isStars)
    }

    @Test
    fun desetinySeDoplniNulami() {
        assertEquals(PriceParts(whole = 3, fraction = "05"), Price(305, "USD").parts())
        assertEquals(PriceParts(whole = 0, fraction = "99"), Price(99, "CZK").parts())
    }

    @Test
    fun menaBezDesetinSeNedeli() {
        assertEquals(PriceParts(whole = 1250, fraction = null), Price(1250, "JPY").parts())
    }

    @Test
    fun menaSeTremiDesetinnymiMisty() {
        assertEquals(PriceParts(whole = 1, fraction = "250"), Price(1250, "KWD").parts())
    }

    @Test
    fun neznamaMenaMaDveMista() {
        assertEquals(PriceParts(whole = 7, fraction = "00"), Price(700, "ABC").parts())
    }
}
