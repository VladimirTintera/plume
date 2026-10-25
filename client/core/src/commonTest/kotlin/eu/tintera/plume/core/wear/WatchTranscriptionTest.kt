package eu.tintera.plume.core.wear

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
    fun garbageIsIgnored() {
        assertNull(WatchTranscription.Request.decode("1 2".encodeToByteArray()))
        assertNull(WatchTranscription.Response.decode("1 2 expired\n".encodeToByteArray()))
    }
}
