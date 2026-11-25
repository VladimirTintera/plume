package eu.tintera.plume.core.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Podpis vyrobený `openssl pkeyutl -sign -rawin` (tak podepisuje `sign-desktop-update.sh`)
 * nad textem z [UpdateSignature.message] musí projít ověřením v JDK. Klíč je jen testovací,
 * soukromá část nikde neleží.
 */
class UpdateSignatureTest {

    @Test
    fun signatureFromOpensslVerifies() {
        assertTrue(UpdateSignature.verify(SIGNATURE, TAG, NAME, SHA, TEST_KEY))
    }

    @Test
    fun signatureIsBoundToVersionNameAndDigest() {
        assertFalse(UpdateSignature.verify(SIGNATURE, "desktop-1.2.0", NAME, SHA, TEST_KEY), "starší vydání")
        assertFalse(UpdateSignature.verify(SIGNATURE, TAG, "Plume-1.3.0-macos-arm64.dmg", SHA, TEST_KEY), "jiný balíček")
        assertFalse(UpdateSignature.verify(SIGNATURE, TAG, NAME, "cd".repeat(32), TEST_KEY), "jiný obsah")
    }

    @Test
    fun otherKeyOrGarbageFails() {
        assertFalse(UpdateSignature.verify(SIGNATURE, TAG, NAME, SHA), "produkční klíč")
        assertFalse(UpdateSignature.verify("není base64!", TAG, NAME, SHA, TEST_KEY))
        assertFalse(UpdateSignature.verify("AAAA", TAG, NAME, SHA, TEST_KEY), "krátký podpis")
        assertFalse(UpdateSignature.verify(SIGNATURE, TAG, NAME, SHA, "TODO"), "nečitelný klíč")
    }

    @Test
    fun productionKeyIsReadable() {
        // Rozbitý klíč v aplikaci = žádná aktualizace pro nikoho; skript podpisu by to nepoznal.
        assertTrue(java.util.Base64.getDecoder().decode(UpdateSignature.UPDATE_PUBLIC_KEY).size == 44)
    }

    private companion object {
        const val TAG = "desktop-1.3.0"
        const val NAME = "Plume-1.3.0.msi"
        val SHA = "ab".repeat(32)
        const val TEST_KEY = "MCowBQYDK2VwAyEAIUlx/cW7+qrw3h7G1EYwMMYmUZawf0aivIkYJilBWAo="
        const val SIGNATURE = "qvcU+eAskJv2duOMTfx7boDM90HafannwWq053wF4TBaqGpmG9wkYvXR377HttxkEwmkt1PQAamvAYBZvdYZDw=="
    }
}
