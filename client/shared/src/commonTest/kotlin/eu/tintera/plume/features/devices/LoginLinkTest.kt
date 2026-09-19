package eu.tintera.plume.features.devices

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rozpoznání přihlašovacího QR kódu. Čtečka přečte jakýkoli QR kód; na server smí jít jen
 * přihlašovací, jinak by uživatel místo „tohle není přihlašovací kód" dostal chybu serveru.
 */
class LoginLinkTest {

    @Test
    fun prihlasovaciOdkaz() {
        assertTrue("tg://login?token=AQJ3Lm9n".isLoginLink())
        assertTrue("  TG://login?token=abc\n".isLoginLink())
    }

    @Test
    fun jineOdkazyNejsou() {
        assertFalse("https://t.me/plume".isLoginLink())
        assertFalse("tg://resolve?domain=plume".isLoginLink())
        assertFalse("tg://login".isLoginLink())
        assertFalse("".isLoginLink())
    }
}
