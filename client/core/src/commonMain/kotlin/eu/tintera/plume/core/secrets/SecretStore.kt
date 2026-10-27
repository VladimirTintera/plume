package eu.tintera.plume.core.secrets

/**
 * Tajemství uložené v trezoru platformy: Android Keystore (bajty obalené AES-GCM klíčem, který
 * z Keystore nevyjde), iOS Keychain. Implementace jsou `KeystoreSecretStore`
 * a `KeychainSecretStore`; každé tajemství má vlastní soubor / záznam a vlastní klíč.
 *
 * Používají ho dvě věci s odlišným smyslem, a proto každá přes vlastní rozhraní, aby je Koin
 * nezaměnil: klíč k databázi TDLib ([DatabaseKeyStore]) a záznam PINu zámku aplikace
 * (`AppLockStore`). **Ani jedno nevyžaduje ověření uživatele** — viz [DatabaseKeyStore].
 */
interface SecretStore {

    /**
     * Nových [SECRET_SIZE] náhodných bajtů z kryptografického generátoru platformy. **Neukládá
     * je** — o tom, kdy je na uložení správná chvíle, rozhoduje volající.
     */
    fun generate(): ByteArray

    /** Uložené tajemství, nebo `null`, když žádné není (nebo nejde přečíst). */
    suspend fun load(): ByteArray?

    suspend fun store(key: ByteArray)

    /** Zahodí uložené tajemství. */
    suspend fun clear()
}

/** Délka tajemství z [SecretStore.generate] v bajtech (256 bitů). */
const val SECRET_SIZE: Int = 32

/**
 * Záznam zámku aplikace (PIN jako odvozený otisk, doba a počítadlo chybných pokusů, viz
 * `AppLock`). Samostatné rozhraní jen kvůli Koinu, aby se nezaměnil s [DatabaseKeyStore].
 */
interface AppLockStore : SecretStore

/**
 * Zabezpečené úložiště mini aplikací (`SecureStorage` z Bot API): všechny klíče všech botů jako
 * jeden zašifrovaný záznam (`MiniAppSecureStorage` ve `shared`). Samostatné rozhraní ze stejného
 * důvodu jako [AppLockStore].
 */
interface MiniAppSecretStore : SecretStore
