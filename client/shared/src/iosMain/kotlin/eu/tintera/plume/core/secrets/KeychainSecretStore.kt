package eu.tintera.plume.core.secrets

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Security.kSecRandomDefault
import platform.posix.memcpy

/**
 * iOS: Keychain (`kSecClassGenericPassword`). Na rozdíl od Androidu umí uložit rovnou bajty,
 * takže se nic neobaluje.
 *
 * Rozhodují dva atributy:
 * - **`AfterFirstUnlock`**, ne `WhenUnlocked`. Push budí aplikaci se zamčeným telefonem a TDLib
 *   potřebuje klíč hned; s `WhenUnlocked` by notifikace chodily jen s odemčenou obrazovkou.
 * - **`ThisDeviceOnly`**. Klíč se tím nedostane do iCloud Keychainu ani do zálohy. Je to záměr:
 *   klíč obnovitelný na cizí zařízení ruší půlku toho, proč se šifruje. Databáze na obnoveném
 *   zařízení proto nepůjde otevřít a aplikace ji zahodí a začne od přihlášení — což je po přenosu
 *   na nový telefon stejně to, co se od Telegramu čeká.
 *
 * **Do App Group klíč nepatří.** Rozšíření sdílení TDLib nespouští a databázi nevidí, takže
 * `kSecAttrAccessGroup` by jen rozšířil plochu bez užitku.
 */
@OptIn(ExperimentalForeignApi::class)
class KeychainSecretStore(service: String, account: String, logTag: String) : SecretStore {

    private val logger = Logger.withTag(logTag)

    /** Bridgované jednou na život úložiště (singleton v Koinu), takže se neuvolňují. */
    private val serviceRef: CFTypeRef? = CFBridgingRetain(service)
    private val accountRef: CFTypeRef? = CFBridgingRetain(account)

    /** Bajty z generátoru systému, ne z `kotlin.random.Random` — ten kryptograficky bezpečný není. */
    override fun generate(): ByteArray {
        val bytes = ByteArray(SECRET_SIZE)
        val status = bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, SECRET_SIZE.convert(), pinned.addressOf(0))
        }
        check(status == errSecSuccess.toInt()) { "Náhodná data pro klíč se nepodařilo získat ($status)." }
        return bytes
    }

    override suspend fun load(): ByteArray? = memScoped {
        val query = baseQuery()
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val found = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, found.ptr)
        CFRelease(query)

        if (status == errSecItemNotFound.toInt()) return@memScoped null
        if (status != errSecSuccess.toInt()) {
            logger.e { "Klíč se nepodařilo přečíst z Keychainu ($status)." }
            return@memScoped null
        }
        (CFBridgingRelease(found.value) as? NSData)?.toByteArray()
    }

    override suspend fun store(key: ByteArray) {
        // Keychain nezná „vlož nebo přepiš"; starý záznam musí pryč, jinak vrátí errSecDuplicateItem.
        clear()

        val data = CFBridgingRetain(key.toNSData())
        val query = baseQuery()
        CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        CFDictionarySetValue(query, kSecValueData, data)

        val status = SecItemAdd(query, null)
        CFRelease(query)
        data?.let { CFRelease(it) }

        check(status == errSecSuccess.toInt()) { "Klíč se nepodařilo uložit do Keychainu ($status)." }
    }

    override suspend fun clear() {
        val query = baseQuery()
        val status = SecItemDelete(query)
        CFRelease(query)
        if (status != errSecSuccess.toInt() && status != errSecItemNotFound.toInt()) {
            logger.w { "Klíč se nepodařilo z Keychainu smazat ($status)." }
        }
    }

    /** Záznam, o který jde. Volající ho musí uvolnit `CFRelease`. */
    private fun baseQuery(): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, serviceRef)
        CFDictionarySetValue(query, kSecAttrAccount, accountRef)
        return query!!
    }

}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.convert())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { out ->
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
