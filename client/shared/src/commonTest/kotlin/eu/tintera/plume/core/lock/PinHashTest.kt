package eu.tintera.plume.core.lock

import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinHashTest {

    private fun pbkdf2(iterations: Int) =
        pbkdf2Sha256("password".encodeToByteArray(), "salt".encodeToByteArray(), iterations).toByteString().hex()

    // Testovací vektory PBKDF2-HMAC-SHA256 (RFC 7914, první blok výstupu).
    @Test
    fun odpovidaTestovacimVektorum() {
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", pbkdf2(1))
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43", pbkdf2(2))
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", pbkdf2(4096))
    }

    @Test
    fun porovnaniBezPredcasnehoKonce() {
        assertTrue(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun zaznamProjdeZapisemACtenim() {
        val config = LockConfig(
            iterations = 10_000,
            salt = byteArrayOf(1, 2, 3),
            hash = byteArrayOf(-1, 0, 127),
            timeoutSeconds = 300,
            biometrics = true,
            failures = 6,
            blockedUntil = 1_800_000_000_000,
        )
        assertEquals(config, decodeLockConfig(config.encode()))
    }

    @Test
    fun poskozenyZaznamJeNull() {
        assertNull(decodeLockConfig("nesmysl".encodeToByteArray()))
        assertNull(decodeLockConfig("v2;1;aa;bb;60;0;0;0".encodeToByteArray()))
        assertNull(decodeLockConfig("v1;x;aa;bb;60;0;0;0".encodeToByteArray()))
        assertNull(decodeLockConfig("v1;0;aa;bb;60;0;0;0".encodeToByteArray()))
    }
}
