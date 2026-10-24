package eu.tintera.plume.core.contacts

/** Kontakt z adresáře telefonu: číslo, jak je v adresáři, a jméno ke spárování v Telegramu. */
data class PhoneContact(val phoneNumber: String, val firstName: String, val lastName: String)
