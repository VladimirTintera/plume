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
}

/** Popis zařízení, který se posílá serveru a zobrazuje v „Aktivní relace" v oficiálním klientovi. */
expect val platformDeviceModel: String

expect val platformSystemVersion: String
