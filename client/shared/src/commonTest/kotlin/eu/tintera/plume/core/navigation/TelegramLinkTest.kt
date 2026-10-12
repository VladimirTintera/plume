package eu.tintera.plume.core.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramLinkTest {

    @Test
    fun kontaktniOdkazAJmenoJsouOdkazemNaTelegram() {
        assertTrue(isTelegramLink("https://t.me/contact/AbC123"))
        assertTrue(isTelegramLink("https://t.me/durov"))
        assertTrue(isTelegramLink("http://telegram.me/durov"))
        assertTrue(isTelegramLink("HTTPS://T.ME/durov"))
        assertTrue(isTelegramLink("https://telegram.dog/durov?text=ahoj"))
        assertTrue(isTelegramLink("https://durov.t.me"))
    }

    @Test
    fun schemaTgSeBereSLomitkyIBezNich() {
        assertTrue(isTelegramLink("tg://resolve?domain=durov"))
        assertTrue(isTelegramLink("tg:resolve?domain=durov"))
        assertTrue(isTelegramLink("tg://login?token=abc"))
        assertFalse(isTelegramLink("tg://"))
    }

    @Test
    fun ciziKodyNeprojdou() {
        assertFalse(isTelegramLink("WIFI:T:WPA;S:Doma;P:heslo;;"))
        assertFalse(isTelegramLink("BEGIN:VCARD\nFN:Jana\nEND:VCARD"))
        assertFalse(isTelegramLink("https://example.com/t.me/durov"))
        assertFalse(isTelegramLink("https://t.me.example.com/durov"))
        assertFalse(isTelegramLink("https://nott.me/durov"))
        assertFalse(isTelegramLink("t.me/durov"))
        assertFalse(isTelegramLink("mailto:jana@t.me"))
    }

    @Test
    fun prihlasovaciUdajeVAdreseHostitelNemeni() {
        assertFalse(isTelegramLink("https://t.me@example.com/durov"))
        assertTrue(isTelegramLink("https://example.com@t.me/durov"))
    }
}
