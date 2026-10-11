package eu.tintera.plume.core.contacts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import co.touchlab.kermit.Logger

actual class ContactSaver(private val context: Context) {

    private val logger = Logger.withTag("ContactSaver")

    actual fun save(contact: NewContact): Boolean {
        val insert = Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contact.name?.let { insert.putExtra(ContactsContract.Intents.Insert.NAME, it) }
        contact.phone?.let { insert.putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        contact.email?.let { insert.putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        return try {
            context.startActivity(insert)
            true
        } catch (e: ActivityNotFoundException) {
            logger.w(e) { "Editor kontaktu v telefonu není" }
            false
        }
    }
}
