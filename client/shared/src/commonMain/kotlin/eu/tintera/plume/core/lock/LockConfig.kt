package eu.tintera.plume.core.lock

import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

/**
 * Všechno, co zámek aplikace ví, v JEDNOM záznamu (`AppLockStore`).
 *
 * Nastavení zámku neleží v `AppSettings`, ale u otisku PINu: zapnutí, doba a počítadlo chybných
 * pokusů se mění spolu a jeden záznam se zapisuje naráz — nemůže vzniknout stav „zámek zapnutý,
 * ale PIN chybí". Počítadlo navíc musí přežít restart, jinak by brzdu pokusů obešlo zabití
 * aplikace.
 *
 * Záznam neexistuje = zámek je vypnutý. Zámek chrání obrazovku před někým, kdo drží odemčený
 * telefon; proti rootnutému zařízení, které umí soubor smazat, nechrání (stejná mez jako klíč
 * k databázi, viz `DatabaseKeyStore`).
 */
internal data class LockConfig(
    val iterations: Int,
    val salt: ByteArray,
    val hash: ByteArray,
    val timeoutSeconds: Int,
    val biometrics: Boolean,
    /** Chybné pokusy od posledního úspěšného odemčení. Přežívají restart, jinak by ho stačilo zabít. */
    val failures: Int = 0,
    /** Do kdy se nesmí zkoušet další PIN (ms od epochy), 0 = smí se hned. */
    val blockedUntil: Long = 0,
) {
    override fun equals(other: Any?): Boolean = other is LockConfig &&
        iterations == other.iterations && salt.contentEquals(other.salt) &&
        hash.contentEquals(other.hash) && timeoutSeconds == other.timeoutSeconds &&
        biometrics == other.biometrics && failures == other.failures &&
        blockedUntil == other.blockedUntil

    override fun hashCode(): Int = hash.contentHashCode()
}

private const val FORMAT = "v1"

/** Text, ne binární formát: čte se jednou za start a rozšířit ho o pole je otázka jedné položky. */
internal fun LockConfig.encode(): ByteArray = listOf(
    FORMAT,
    iterations.toString(),
    salt.toByteString().hex(),
    hash.toByteString().hex(),
    timeoutSeconds.toString(),
    if (biometrics) "1" else "0",
    failures.toString(),
    blockedUntil.toString(),
).joinToString(";").encodeToByteArray()

/** `null` u poškozeného nebo neznámého záznamu; `AppLock` ho zahodí jako vypnutý zámek. */
internal fun decodeLockConfig(bytes: ByteArray): LockConfig? = runCatching {
    val parts = bytes.decodeToString().split(';')
    if (parts.size < 8 || parts[0] != FORMAT) return null
    LockConfig(
        iterations = parts[1].toInt(),
        salt = parts[2].decodeHex().toByteArray(),
        hash = parts[3].decodeHex().toByteArray(),
        timeoutSeconds = parts[4].toInt(),
        biometrics = parts[5] == "1",
        failures = parts[6].toInt(),
        blockedUntil = parts[7].toLong(),
    ).takeIf { it.iterations > 0 && it.hash.isNotEmpty() && it.salt.isNotEmpty() }
}.getOrNull()
