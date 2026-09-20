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
