package eu.tintera.plume.core.accounts

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Které chaty se spojují přes účty a kdo smí ukázat notifikaci zprávy ve společné skupině. */
class SharedChatsTest {

    /** Supergroupa a kanál ano; soukromý chat, obyčejná skupina a tajný chat ne. */
    @Test
    fun spolecnouHistoriiMajiJenSupergroupy() {
        assertTrue(isSharedHistoryChat(-1_001_234_567_890L))
        assertTrue(isSharedHistoryChat(-1_000_000_000_001L))
        assertFalse(isSharedHistoryChat(8_875_105_705L))
        assertFalse(isSharedHistoryChat(-4_345_085_150L))
        assertFalse(isSharedHistoryChat(-2_000_000_000_000L + 5))
        assertFalse(isSharedHistoryChat(-2_000_000_000_000L - 5))
    }

    /** Zprávu ukáže účet, který ji ohlásil první; jeho další ohlášení (náhrada z pushe) taky. */
    @Test
    fun notifikaceJenOdPrvnihoUctu() = runTest {
        val claims = NotificationClaims()
        val first = AccountSlot("a")
        val second = AccountSlot("b")

        assertTrue(claims.claim(first, chatId = -1, messageId = 10))
        assertFalse(claims.claim(second, chatId = -1, messageId = 10))
        assertTrue(claims.claim(first, chatId = -1, messageId = 10))
        assertTrue(claims.claim(second, chatId = -1, messageId = 11))
    }

    /** Pamatuje si jen posledních pár zpráv; nejstarší záznam vypadne. */
    @Test
    fun staraOhlaseniVypadnou() = runTest {
        val claims = NotificationClaims(capacity = 2)
        val first = AccountSlot("a")
        val second = AccountSlot("b")
        claims.claim(first, chatId = -1, messageId = 1)
        claims.claim(first, chatId = -1, messageId = 2)
        claims.claim(first, chatId = -1, messageId = 3)

        assertEquals(true, claims.claim(second, chatId = -1, messageId = 1))
        assertEquals(false, claims.claim(second, chatId = -1, messageId = 3))
    }
}
