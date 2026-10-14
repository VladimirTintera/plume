package eu.tintera.plume.core.calls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallSideChannelTest {

    private val key = sideChannelKey(ByteArray(256) { it.toByte() })

    @Test
    fun roundTrip() {
        val sealed = CallSideChannel.seal(key, CallSideMessage.Pointer(0.25f, 0.75f, down = true))
        assertTrue(CallSideChannel.isOurs(sealed))
        assertEquals(CallSideMessage.Pointer(0.25f, 0.75f, down = true), CallSideChannel.open(key, sealed))
    }

    @Test
    fun otherKeyCannotRead() {
        val sealed = CallSideChannel.seal(key, CallSideMessage.Hello(pointer = true))
        assertNull(CallSideChannel.open(sideChannelKey(ByteArray(256)), sealed))
    }

    @Test
    fun librarySignalingIsNotOurs() {
        // Signalizace ntgcalls je šifrovaná, tedy náhodné bajty; prázdná ani krátká naše není.
        assertFalse(CallSideChannel.isOurs(ByteArray(0)))
        assertFalse(CallSideChannel.isOurs(ByteArray(64) { (it * 37).toByte() }))
        assertFalse(CallSideChannel.isOurs("PLUMESC1".encodeToByteArray()))
    }
}
