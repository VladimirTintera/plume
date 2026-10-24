package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sběratelská jména a čísla z Fragmentu: co je sběratelské a jak se čte cena v TONech. */
class CollectibleTest {

    @Test
    fun sberatelskaJsouZapnutaJmenaKromeVlastniho() {
        assertEquals(setOf("durov"), collectibleUsernames(listOf("pavel", "durov"), editable = "pavel"))
        // Vlastní jméno může chybět a hlavní (první) je pak koupené.
        assertEquals(setOf("durov", "pavel"), collectibleUsernames(listOf("durov", "pavel"), editable = ""))
        assertEquals(emptySet(), collectibleUsernames(listOf("pavel"), editable = "pavel"))
        assertEquals(emptySet(), collectibleUsernames(emptyList(), editable = null))
    }

    @Test
    fun anonymniCisloJeJen888() {
        assertTrue(isCollectiblePhone("+88812345678"))
        assertTrue(isCollectiblePhone("88812345678"))
        assertFalse(isCollectiblePhone("+420777888999"))
    }

    @Test
    fun castkaVTonechBezKoncovychNul() {
        assertEquals(PriceParts(whole = 1250, fraction = null), cryptoAmountParts(1_250_000_000_000, "TON"))
        assertEquals(PriceParts(whole = 1, fraction = "5"), cryptoAmountParts(1_500_000_000, "TON"))
        assertEquals(PriceParts(whole = 0, fraction = "000000001"), cryptoAmountParts(1, "TON"))
    }
}
