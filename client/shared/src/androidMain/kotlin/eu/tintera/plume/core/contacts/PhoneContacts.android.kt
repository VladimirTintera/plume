package eu.tintera.plume.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adresář telefonu přes `ContactsContract`: každé číslo jako samostatný kontakt se zobrazovaným
 * jménem (jako ho ukazuje aplikace Kontakty). Jméno se do Telegramu dává celé jako křestní —
 * rozdělit „Jan Novák" na jméno a příjmení spolehlivě nejde a Telegram ho stejně ukazuje vcelku.
 */
class AndroidPhoneContacts(private val context: Context) : PhoneContacts {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override suspend fun read(): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        )
        val found = LinkedHashMap<String, PhoneContact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null,
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                // Klíč bez mezer a pomlček: totéž číslo zapsané dvakrát jinak je jeden kontakt.
                val key = number.filter { it.isDigit() || it == '+' }
                if (key.count { it.isDigit() } < MIN_DIGITS) continue
                val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { number }
                found.getOrPut(key) { PhoneContact(phoneNumber = key, firstName = name, lastName = "") }
            }
        }
        found.values.toList()
    }

    private companion object {
        /** Kratší „čísla" (zkrácené volby, služby) do Telegramu nepatří. */
        const val MIN_DIGITS = 6
    }
}

@Composable
actual fun rememberContactsPermission(): ContactsPermission? {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.invoke(granted)
        pending[0] = null
    }
    return remember(launcher) {
        ContactsPermission { onResult ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                onResult(true)
            } else {
                pending[0] = onResult
                launcher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }
}
