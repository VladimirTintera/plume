package eu.tintera.plume.core.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android: **Keystore neumí uložit bajty, jen klíče.** Tohle je past, na které se to nejčastěji
 * láme — `AndroidKeyStore` není trezor na data. Klíč k databázi je proto obyčejných 32 náhodných
 * bajtů, zašifrovaných AES-GCM klíčem, který v Keystore (a tím v TEE nebo StrongBoxu) bydlí
 * a ven se nedostane. Na disku leží jen ten šifrotext.
 *
 * `androidx.security:security-crypto` dělá zhruba totéž, ale je to závislost navíc kvůli
 * padesáti řádkům — stejná úvaha jako u `app-update` proti `app-update-ktx`.
 *
 * Parametry klíče, na kterých záleží:
 * - **Bez ověření uživatele** (`setUserAuthenticationRequired` se nevolá, výchozí je `false`).
 *   Viz [DatabaseKeyStore] — s ověřením by z pozadí nechodily notifikace.
 * - **Bez `setInvalidatedByBiometricEnrollment`.** Přidaný otisk prstu nesmí zneplatnit klíč,
 *   jinak by uživatel přišel o přihlášení kvůli nastavení telefonu.
 * - IV si vyrábí systém (`setRandomizedEncryptionRequired` je ve výchozím stavu zapnuté) a čte
 *   se z `cipher.iv`; předávat vlastní by GCM zeslabilo, kdyby se někdy zopakoval.
 */
class KeystoreDatabaseKeyStore(context: Context) : DatabaseKeyStore {

    private val logger = Logger.withTag("DatabaseKey")

    private val file = File(context.filesDir, FILE_NAME)

    override fun generate(): ByteArray = ByteArray(DATABASE_KEY_SIZE).also { SecureRandom().nextBytes(it) }

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        runCatching { unwrap(file.readBytes()) }
            .getOrElse { failure ->
                // Sem se dá dostat, když Keystore klíč ztratil (přenos na jiné zařízení, oprava
                // systému) nebo když soubor nedopsal zabitý proces. V obou případech je klíč
                // pryč nadobro — soubor je jen odpad, který by mátl při příštím startu.
                logger.e(failure) { "Klíč k databázi nejde rozšifrovat, zahazuji ho." }
                file.delete()
                null
            }
    }

    override suspend fun store(key: ByteArray): Unit = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        write(wrapped = pack(iv = cipher.iv, ciphertext = cipher.doFinal(key)))
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        if (file.exists() && !file.delete()) logger.w { "Soubor s klíčem k databázi nejde smazat." }
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
            .onFailure { logger.w(it) { "Klíč v Keystore se nepodařilo smazat." } }
    }

    /**
     * Zápis přes dočasný soubor a přejmenování. Zabitý proces uprostřed zápisu by jinak nechal
     * na disku uříznutý klíč — a s ním databázi, která už nejde otevřít.
     */
    private fun write(wrapped: ByteArray) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeBytes(wrapped)
        if (!temp.renameTo(file)) {
            temp.delete()
            error("Klíč k databázi se nepodařilo uložit.")
        }
    }

    /** `[délka IV][IV][šifrotext]` — délka IV je v souboru proto, že ji GCM smí měnit. */
    private fun pack(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val out = ByteArray(1 + iv.size + ciphertext.size)
        out[0] = iv.size.toByte()
        iv.copyInto(out, destinationOffset = 1)
        ciphertext.copyInto(out, destinationOffset = 1 + iv.size)
        return out
    }

    private fun unwrap(wrapped: ByteArray): ByteArray {
        val ivSize = wrapped[0].toInt()
        require(ivSize in 1..MAX_IV_SIZE && wrapped.size > 1 + ivSize) { "Poškozený soubor s klíčem." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            wrappingKey(),
            GCMParameterSpec(GCM_TAG_BITS, wrapped, 1, ivSize),
        )
        return cipher.doFinal(wrapped, 1 + ivSize, wrapped.size - 1 - ivSize)
    }

    private fun wrappingKey(): SecretKey {
        val existing = keyStore().getKey(KEY_ALIAS, null) as SecretKey?
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(WRAPPING_KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        /** `filesDir`, ne cache: soubor, který systém uklidí, by znamenal odhlášení. */
        const val FILE_NAME = "tdlib-key"

        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "plume_tdlib_database"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val WRAPPING_KEY_BITS = 256
        const val GCM_TAG_BITS = 128

        /** GCM používá 12 bajtů; strop je jen pojistka proti nesmyslu z poškozeného souboru. */
        const val MAX_IV_SIZE = 16
    }
}
