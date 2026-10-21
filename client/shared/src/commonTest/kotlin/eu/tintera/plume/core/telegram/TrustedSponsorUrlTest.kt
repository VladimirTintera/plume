package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustedSponsorUrlTest {
    @Test
    fun domenyTelegramuBezPotvrzeni() {
        assertTrue(isTrustedSponsorUrl("https://t.me/somebot?start=ad"))
        assertTrue(isTrustedSponsorUrl("http://telegram.org"))
        assertTrue(isTrustedSponsorUrl("t.me/channel"))
        assertTrue(isTrustedSponsorUrl("https://te.legra.ph/Article-01-01"))
        assertTrue(isTrustedSponsorUrl("https://FRAGMENT.com/username/x"))
        assertTrue(isTrustedSponsorUrl("https://promote.telegram.org/"))
    }

    @Test
    fun ciziAdresaSPotvrzenim() {
        assertFalse(isTrustedSponsorUrl("https://example.com"))
        // Doména jen končí stejnými písmeny.
        assertFalse(isTrustedSponsorUrl("https://notelegram.org"))
        assertFalse(isTrustedSponsorUrl("https://t.me.evil.example/"))
        // Přihlašovací údaje a port: skutečný hostitel je jinde, nebo to specifikace nepouští.
        assertFalse(isTrustedSponsorUrl("https://t.me@evil.example/"))
        assertFalse(isTrustedSponsorUrl("https://t.me:8080/x"))
        // Jiné schéma (tg://) jde přes rozpoznání odkazu v aplikaci, tady neplatí.
        assertFalse(isTrustedSponsorUrl("ftp://t.me/x"))
        assertFalse(isTrustedSponsorUrl(""))
    }
}
