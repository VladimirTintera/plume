package eu.tintera.plume.core.contacts

import androidx.compose.runtime.Composable

/**
 * Adresář telefonu pro synchronizaci kontaktů (`ContactSyncer`). Jen tam, kde ho platforma
 * vydá (Android); jinde se nezaregistruje a synchronizace se nenabízí.
 */
interface PhoneContacts {
    /** Smí aplikace adresář číst (oprávnění)? Bez něj [read] nic nevrátí. */
    fun hasPermission(): Boolean

    /** Všechna čísla z adresáře, každé jednou. Prázdné bez oprávnění. */
    suspend fun read(): List<PhoneContact>
}

/** Žádost o oprávnění ke kontaktům; výsledek přijde v callbacku. */
fun interface ContactsPermission {
    fun request(onResult: (granted: Boolean) -> Unit)
}

/** Žádost o oprávnění ke kontaktům; `null` = platforma synchronizaci neumí. */
@Composable
expect fun rememberContactsPermission(): ContactsPermission?
