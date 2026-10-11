package eu.tintera.plume.core.contacts

/** Kontakty systému z JVM nedosáhne; volající uživateli řekne, že kontakt nejde uložit. */
actual class ContactSaver {
    actual fun save(contact: NewContact): Boolean = false
}
