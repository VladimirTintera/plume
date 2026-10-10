package eu.tintera.plume.core.telegram

/**
 * Kam TDLib ukládá svoji databázi a stažené soubory.
 *
 * Musí to být zapisovatelný adresář privátní pro aplikaci — TDLib si tam drží šifrovanou databázi
 * zpráv, takže to nesmí být sdílené ani zálohované úložiště.
 */
expect class TelegramPaths {
    val databaseDirectory: String
    val filesDirectory: String

    /**
     * Založí adresáře a označí je, aby nešly do zálohy.
     *
     * TDLib si je vyrobí sám, jenže pak by na nich příznak chvíli nebyl. Volá se proto před
     * KAŽDÝM `setTdlibParameters` — je to pár syscallů a jediné místo, kde se na to nedá
     * zapomenout ani po smazání nečitelné databáze, kdy adresáře vznikají znovu.
     *
     * **Zálohovaná databáze je k ničemu a škodí.** Klíč k ní leží v Keystore / Keychainu
     * a ten se na jiné zařízení nepřenáší, takže obnovená databáze by se stejně zahodila —
     * jen by do zálohy putovala celá historie zpráv.
     */
    fun prepare()

    /**
     * Ladicí build. Řídí výpisy, které do release nepatří — třeba co server nabídl k doručení
     * přihlašovacího kódu: release logcat zůstává zapnutý (viz `configureLogging`) a s ním by
     * šlo i to, ke kterému číslu a jakou cestou kód odešel.
     */
    val isDebugBuild: Boolean
}

/** Popis zařízení, který se posílá serveru a zobrazuje v „Aktivní relace" v oficiálním klientovi. */
expect val platformDeviceModel: String

expect val platformSystemVersion: String

/**
 * Dvoupísmenný kód jazyka zařízení. TDLib podle něj překládá názvy zemí a formátuje čísla
 * (`getCountries`, `getPhoneNumberInfoSync`), takže se nesmí hádat z jazyka aplikace —
 * Compose prostředky na něj v `core` nedosáhnou a mimo kompozici by se stejně nečetly.
 */
expect val platformLanguageCode: String

/**
 * Název země v jazyce zařízení podle kódu ISO 3166-1 alpha-2, nebo `null`, když ho systém nezná.
 *
 * **Data dává operační systém** (na obou platformách CLDR), takže se nestahují a nedrží se
 * vlastní tabulka dvou set zemí. TDLib je použít nejde: `CountryInfo.name` má být podle
 * dokumentace „native name", ale před autorizací vrací anglické názvy — ověřeno na emulátoru
 * se systémovou i aplikační češtinou.
 */
expect fun localizedCountryName(code: String): String?

/**
 * Název jazyka podle kódu BCP 47 (`en`, `cs`, `zh-TW`) v jazyce zařízení, s velkým počátečním
 * písmenem („Angličtina"), nebo `null`, když ho systém nezná. Data dává systém, jako
 * u [localizedCountryName]. Písmo nebo region se připíše („Čínština (Tchaj-wan)") — jinak by
 * čínština pro Čínu a pro Tchaj-wan měly v nabídce jazyků překladu tentýž název.
 */
expect fun localizedLanguageName(code: String): String?
