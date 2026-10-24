package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Cíl skoku na datum: první zpráva za poslední zprávou před vybraným dnem. */
class JumpToDateTest {

    @Test
    fun cilemJePrvniZpravaZaPosledniPredDnem() {
        // getChatHistory o jednu novější vrací i zprávu samotnou, novější napřed.
        assertEquals(2048L, firstMessageAfter(before = 1024L, newer = listOf(2048L, 1024L)))
    }

    @Test
    fun denNovejsiNezPosledniZpravaNemaCil() {
        assertNull(firstMessageAfter(before = 1024L, newer = listOf(1024L)))
    }

    @Test
    fun denStarsiNezChatVedeNaNejstarsiZpravu() {
        assertEquals(1_048_576L, firstMessageAfter(before = null, newer = listOf(2_097_152L, 1_048_576L)))
    }
}
