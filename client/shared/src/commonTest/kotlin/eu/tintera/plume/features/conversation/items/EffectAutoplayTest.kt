package eu.tintera.plume.features.conversation.items

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kdy se efekt zprávy přehraje sám: po příchodu a u nepřečtené příchozí, jinak jen ťuknutím. */
class EffectAutoplayTest {

    private val openedAt = 1_000_000L
    private val old = (openedAt - 3_600).toInt()

    @Test
    fun zpravaPoOtevreniHraje() {
        assertTrue(effectAutoplays(openedAt.toInt(), openedAt, isOutgoing = true, messageId = 50, unreadBoundary = null))
        // Hodiny serveru se můžou o chvilku lišit.
        assertTrue(effectAutoplays((openedAt - 3).toInt(), openedAt, isOutgoing = false, messageId = 50, unreadBoundary = null))
    }

    @Test
    fun neprectenaPrichoziHraje() {
        assertTrue(effectAutoplays(old, openedAt, isOutgoing = false, messageId = 11, unreadBoundary = 10))
    }

    @Test
    fun prectenaHistorieNehraje() {
        assertFalse(effectAutoplays(old, openedAt, isOutgoing = false, messageId = 10, unreadBoundary = 10))
        assertFalse(effectAutoplays(old, openedAt, isOutgoing = false, messageId = 11, unreadBoundary = null))
    }

    @Test
    fun vlastniStaraZpravaNehrajeANiZaHranici() {
        // Vlastní zpráva není „nepřečtená" — odesílatel efekt viděl při odeslání.
        assertFalse(effectAutoplays(old, openedAt, isOutgoing = true, messageId = 11, unreadBoundary = 10))
    }
}
