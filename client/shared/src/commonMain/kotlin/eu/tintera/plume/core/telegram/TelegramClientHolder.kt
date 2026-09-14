package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient

/**
 * Drží jedinou instanci [TdlClient] pro celý proces.
 *
 * TDLib klient je jeden na aplikaci — má vlastní spojení, lokální databázi a vlákno na updaty.
 * Víc instancí by si šláplo na stejný databázový adresář. Holder existuje proto, aby ho mohly
 * sdílet obě implementace nad TDLib ([TdlTelegramSession] pro přihlášení, [TdlChatRepository]
 * pro seznam chatů) a přitom zůstal privátní vůči zbytku aplikace.
 */
class TelegramClientHolder {
    val client: TdlClient by lazy { TdlClient.create() }
}
