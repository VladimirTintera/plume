package eu.tintera.plume.core.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramPushTest {

    @Test
    fun soukromaZpravaMiriNaChatUzivatele() {
        val push = TelegramPush.parse("MESSAGE_TEXT", mapOf("from_id" to 444555666, "msg_id" to 123))!!
        assertEquals(444555666L, push.chatId)
        assertEquals(123L shl 20, push.messageId)
        assertFalse(push.isGroup)
        assertEquals(NotificationCategories.chatCategory(canReply = true, canMute = true), push.category)
    }

    /** Příklady v dokumentaci TDLib nesou čísla jako řetězce. */
    @Test
    fun cislaJdouIJakoRetezce() {
        val push = TelegramPush.parse("CHAT_MESSAGE_TEXT", mapOf("channel_id" to "56781234", "msg_id" to "7", "mention" to "1"))!!
        assertEquals(-1_000_056_781_234L, push.chatId)
        assertEquals(7L shl 20, push.messageId)
        assertTrue(push.isGroup)
        assertTrue(push.isMention)
    }

    @Test
    fun zakladniSkupinaJeZaporna() {
        val push = TelegramPush.parse("CHAT_MESSAGE_PHOTO", mapOf("chat_id" to 241233, "chat_from_id" to 5))!!
        assertEquals(-241233L, push.chatId)
    }

    /** Zmínka jde i ze ztlumeného chatu — ztlumit na hodinu by jí po hodině vrátilo zvuk. */
    @Test
    fun zminkaNenabiziZtlumeni() {
        val push = TelegramPush.parse("CHAT_MESSAGE_TEXT", mapOf("chat_id" to 1, "mention" to 1))!!
        assertEquals(NotificationCategories.chatCategory(canReply = true, canMute = false), push.category)
    }

    @Test
    fun doKanaluSeOdpovedetNeda() {
        val push = TelegramPush.parse("CHANNEL_MESSAGE_TEXT", mapOf("channel_id" to 10))!!
        assertTrue(push.isChannel)
        assertEquals(NotificationCategories.chatCategory(canReply = false, canMute = true), push.category)
    }

    @Test
    fun pushBezChatuNemaAkce() {
        val push = TelegramPush.parse("AUTH_UNKNOWN", emptyMap())!!
        assertNull(push.chatId)
        assertNull(push.category)
        // Kontakt se připojil: chat je, ale akce nad zprávou nedávají smysl.
        assertNull(TelegramPush.parse("CONTACT_JOINED", mapOf("from_id" to 5))!!.category)
    }

    @Test
    fun bezKliceNicNeni() {
        assertNull(TelegramPush.parse(null, mapOf("from_id" to 5)))
    }
}
