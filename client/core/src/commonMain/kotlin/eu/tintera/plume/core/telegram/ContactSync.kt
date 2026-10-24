package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ImportedContact
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.contacts.PhoneContact

/** Kontakty z adresáře telefonu na serveru Telegramu (synchronizace adresáře). */
interface ContactSync {
    /**
     * Nahraje adresář (`changeImportedContacts`). TDLib si pamatuje předchozí stav, takže pošle
     * jen přidané a smaže odebrané; výsledek je počet čísel, za kterými je účet Telegramu.
     */
    suspend fun sync(contacts: List<PhoneContact>): Result<Int>

    /**
     * Smaže ze serveru nahraná čísla (`clearImportedContacts`). **Kontakty, které už v Telegramu
     * vznikly, zůstanou** — tak to TDLib dělá a uživatel je smaže jako každý jiný kontakt.
     */
    suspend fun clear(): Result<Unit>
}

class TdlContactSync(private val holder: TelegramClientHolder) : ContactSync {

    private val client: TdlClient get() = holder.client

    override suspend fun sync(contacts: List<PhoneContact>): Result<Int> =
        client.changeImportedContacts(
            contacts = contacts.map {
                ImportedContact(phoneNumber = it.phoneNumber, firstName = it.firstName, lastName = it.lastName, note = null)
            }.toTypedArray(),
        ).toResult().map { imported -> imported.userIds.count { it != 0L } }

    override suspend fun clear(): Result<Unit> = client.clearImportedContacts().toResult().map { }
}
