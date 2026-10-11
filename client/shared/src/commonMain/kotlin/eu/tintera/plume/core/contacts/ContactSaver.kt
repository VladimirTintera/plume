package eu.tintera.plume.core.contacts

/** Kontakt k uložení do telefonu; co ve zprávě nebylo, je `null`. */
data class NewContact(val name: String?, val phone: String?, val email: String?)

/**
 * Otevře systémový editor nového kontaktu s předvyplněnými údaji. **Nic se neukládá samo**:
 * kontakt uloží uživatel v editoru, kde doplní nebo opraví jméno.
 *
 * Android: `ContactsContract.Intents.Insert` (bez oprávnění ke kontaktům — zapisuje aplikace
 * Kontakty, ne my). iOS: `CNContactViewController(forNewContact:)`.
 */
expect class ContactSaver {
    /** `false`, když editor kontaktu v telefonu není; uživateli se to musí říct. */
    fun save(contact: NewContact): Boolean
}
