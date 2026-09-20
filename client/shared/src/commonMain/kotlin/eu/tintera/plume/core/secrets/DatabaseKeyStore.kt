package eu.tintera.plume.core.secrets

/**
 * Klíč, kterým TDLib šifruje svoji lokální databázi zpráv.
 *
 * **Tenhle balíček je hranice**, stejně jako `core/telegram` kolem TDLib a `core/logging` kolem
 * Crashlytics: `java.security.KeyStore` a `platform.Security.*` smí být importované jen tady.
 * Zbytek aplikace vidí jen tohle rozhraní a neví, kde klíč fyzicky leží.
 *
 * Je to rozhraní, ne `expect class` — jako `ShareTargetPublisher` a `CrashReporter`. Rozhoduje
 * se podle něj, jestli se smažou lokální data, a to chce test s podstrčeným úložištěm
 * (`DatabaseUnlockTest`); `expect class` se v `commonTest` nahradit nedá.
 *
 * Klíč **nesmí vyžadovat ověření uživatele**. Notifikace budí mrtvý proces pushem, když telefon
 * leží zamčený v kapse, a TDLib potřebuje klíč dřív, než se složí jakékoli UI — klíč za otiskem
 * prstu by znamenal, že z pozadí nechodí notifikace vůbec.
 *
 * Tím je daná i mez toho, co šifrování koupí: klíč dostane každý kód běžící pod naší aplikací.
 * Chrání to proti vytažení disku, záloze, `adb backup` a forenznímu čtení souborů — ne proti
 * rootnutému zařízení s běžící aplikací. Stejný kompromis dělá i oficiální klient.
 */
interface DatabaseKeyStore {

    /**
     * Nový náhodný klíč z kryptografického generátoru platformy. **Neukládá ho** — o tom, kdy je
     * na uložení správná chvíle, rozhoduje volající (viz `unlockDatabase`).
     */
    fun generate(): ByteArray

    /** Uložený klíč, nebo `null`, když žádný není (první spuštění, instalace z verze bez šifrování). */
    suspend fun load(): ByteArray?

    suspend fun store(key: ByteArray)

    /** Zahodí uložený klíč. Po tomhle je databáze zašifrovaná jím nenávratně nečitelná. */
    suspend fun clear()
}

/**
 * Délka klíče v bajtech. TDLib bere libovolně dlouhý, 256 bitů je obvyklá volba a odpovídá
 * velikosti klíče, kterým se na Androidu obaluje.
 */
const val DATABASE_KEY_SIZE: Int = 32
