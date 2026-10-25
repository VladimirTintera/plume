package eu.tintera.plume.core.wear

import eu.tintera.plume.core.telegram.OnDeviceFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchTranscriptionTest {

    @Test
    fun requestSurvivesTheWire() {
        val request = WatchTranscription.Request(accountId = 444555666, chatId = -1003861231833, messageId = 2240L shl 20)
        assertEquals(request, WatchTranscription.Request.decode(request.encode()))
    }

    @Test
    fun responseKeepsMultilineText() {
        val response = WatchTranscription.Response(1, 2, WatchTranscription.Status.Done, "Ahoj,\nsejdeme se v deset.")
        assertEquals(response, WatchTranscription.Response.decode(response.encode()))
    }

    @Test
    fun failedResponseHasNoText() {
        val response = WatchTranscription.Response(1, 2, WatchTranscription.Status.WrongAccount)
        assertEquals(response, WatchTranscription.Response.decode(response.encode()))
    }

    @Test
    fun unavailableCarriesTheFix() {
        OnDeviceFix.entries.forEach { fix ->
            val decoded = WatchTranscription.Response.decode(WatchTranscription.Response.unavailable(1, 2, fix).encode())
            assertEquals(WatchTranscription.Status.Unavailable, decoded?.status)
            assertEquals(fix, decoded?.fix)
        }
        assertNull(WatchTranscription.Response.decode(WatchTranscription.Response.unavailable(1, 2, null).encode())?.fix)
    }

    /** Starší telefon pošle „unavailable" bez textu, novější hodnotu, kterou hodinky neznají. */
    @Test
    fun unknownFixIsNoFix() {
        assertNull(WatchTranscription.Response.decode("1 2 unavailable\n".encodeToByteArray())?.fix)
        assertNull(WatchTranscription.Response.decode("1 2 unavailable\nupdate_android".encodeToByteArray())?.fix)
        assertNull(WatchTranscription.Response(1, 2, WatchTranscription.Status.Done, "enable_ai").fix)
    }

    @Test
    fun garbageIsIgnored() {
        assertNull(WatchTranscription.Request.decode("1 2".encodeToByteArray()))
        assertNull(WatchTranscription.Response.decode("1 2 expired\n".encodeToByteArray()))
    }
}
