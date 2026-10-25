package eu.tintera.plume.core.wear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchLoginTest {

    @Test
    fun resultSurvivesTheWire() {
        WatchLogin.Result.entries.forEach { assertEquals(it, WatchLogin.Result.fromWire(it.wire)) }
    }

    @Test
    fun unknownResultFromNewerVersionIsIgnored() {
        assertNull(WatchLogin.Result.fromWire("expired"))
    }

    @Test
    fun onlyTelegramLoginLinksPass() {
        assertTrue(WatchLogin.isLoginLink("tg://login?token=AQJ3Lm9n"))
        assertTrue(WatchLogin.isLoginLink("  TG://LOGIN?token=x "))
        assertFalse(WatchLogin.isLoginLink("https://t.me/login?token=x"))
        assertFalse(WatchLogin.isLoginLink("tg://resolve?domain=plume"))
    }
}
