package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Který štítek se u jména ukáže. */
class VerificationTest {

    @Test
    fun podvodMaPrednostPredOverenim() {
        // Ověření by uklidnilo přesně tam, kde má uživatel zbystřit.
        assertEquals(Verification.Scam, verificationOf(isVerified = true, isScam = true, isFake = false))
        assertEquals(Verification.Fake, verificationOf(isVerified = true, isScam = false, isFake = true))
    }

    @Test
    fun podvodPredFalesnym() {
        assertEquals(Verification.Scam, verificationOf(isVerified = false, isScam = true, isFake = true))
    }

    @Test
    fun overeny() {
        assertEquals(Verification.Verified, verificationOf(isVerified = true, isScam = false, isFake = false))
    }

    @Test
    fun beznyUcetStitekNema() {
        assertNull(verificationOf(isVerified = false, isScam = false, isFake = false))
    }

    @Test
    fun idChatuSupergroupy() {
        assertEquals(-1_001_234_567_890L, supergroupChatId(1_234_567_890L))
    }
}
