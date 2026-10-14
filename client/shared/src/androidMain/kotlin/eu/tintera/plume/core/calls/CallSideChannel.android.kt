package eu.tintera.plume.core.calls

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual fun sideChannelKey(callKey: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest("plume-side-channel".encodeToByteArray() + callKey)

internal actual fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray {
    val nonce = ByteArray(NONCE).also(SecureRandom()::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
    return nonce + cipher.doFinal(plain)
}

internal actual fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE))
    return cipher.doFinal(sealed, NONCE, sealed.size - NONCE)
}

private const val NONCE = 12
private const val TAG_BITS = 128
