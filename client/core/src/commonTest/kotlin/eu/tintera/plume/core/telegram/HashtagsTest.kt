package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HashtagsTest {

    @Test
    fun hashtagUKurzoru() {
        assertEquals(HashtagQuery(5, "pl"), hashtagQueryAt("ahoj #pl", 8))
        assertEquals(HashtagQuery(0, ""), hashtagQueryAt("#", 1))
        assertEquals(HashtagQuery(0, "čeština_1"), hashtagQueryAt("#čeština_1", 10))
    }

    @Test
    fun mrizkaUprostredSlovaHashtagNeni() {
        assertNull(hashtagQueryAt("C#", 2))
        assertNull(hashtagQueryAt("C#sharp", 7))
    }

    @Test
    fun poMezereUzSePisVetaDal() {
        assertNull(hashtagQueryAt("#plume a dál", 12))
        assertNull(hashtagQueryAt("bez mřížky", 10))
        assertNull(hashtagQueryAt("", 0))
    }

    @Test
    fun hashtagUprostredTextuSeHledaUKurzoru() {
        // Kurzor za „#pl" uprostřed věty, za ním pokračuje text.
        assertEquals(HashtagQuery(4, "pl"), hashtagQueryAt("Ano #pl je to", 7))
    }

    @Test
    fun vlozeniPridaMezeru() {
        val query = HashtagQuery(5, "pl")
        assertEquals("ahoj #plume " to 12, hashtagInsertion("ahoj #pl", 8, query, "plume"))
    }

    @Test
    fun vlozeniMezeruNezdvoji() {
        val query = HashtagQuery(4, "pl")
        assertEquals("Ano #plume je to" to 11, hashtagInsertion("Ano #pl je to", 7, query, "plume"))
    }
}
