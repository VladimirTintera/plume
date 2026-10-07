package eu.tintera.plume.core.lock

import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.experimental.xor

/**
 * PBKDF2-HMAC-SHA256 s jediným blokem výstupu (32 bajtů), tedy přesně to, co PIN potřebuje.
 *
 * Vlastní implementace nad `hmacSha256` z Okio, protože commonMain žádné PBKDF2 nemá
 * a platformní (`SecretKeyFactory`, `CCKeyDerivationPBKDF`) by znamenaly dvě implementace
 * téhož výpočtu, který se musí shodovat bajt po bajtu — záznam se čte na zařízení, kde vznikl,
 * ale test běží na JVM. Ověřené testovacími vektory RFC 7914 (`PinHashTest`).
 *
 * Čtyřmístný PIN má jen deset tisíc možností a pomalý výpočet z něj silné heslo neudělá.
 * Hlavní ochrana je jinde: záznam leží zašifrovaný klíčem z Keystore / Keychainu
 * (`AppLockStore`) a pokusy v aplikaci brzdí [LockConfig.blockedUntil]. Iterace jsou pojistka
 * pro případ, že by se záznam dostal ven i s klíčem.
 */
internal fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
    require(iterations > 0)
    val key = password.toByteString()
    // Blok 1: U1 = HMAC(P, S || INT(1)), Ui = HMAC(P, Ui-1), výsledek je XOR všech Ui.
    var block: ByteString = (salt + byteArrayOf(0, 0, 0, 1)).toByteString().hmacSha256(key)
    val result = block.toByteArray()
    repeat(iterations - 1) {
        block = block.hmacSha256(key)
        val bytes = block.toByteArray()
        for (i in result.indices) result[i] = result[i] xor bytes[i]
    }
    return result
}

/** Porovnání bez předčasného konce, ať délka výpočtu neprozradí, kolik bajtů sedí. */
internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
    return diff == 0
}
