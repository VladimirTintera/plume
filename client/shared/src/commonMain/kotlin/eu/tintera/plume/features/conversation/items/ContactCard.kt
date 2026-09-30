package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.contact_write
import org.jetbrains.compose.resources.stringResource

/**
 * Sdílený kontakt: tvář, jméno a číslo, pod tím „Napsat zprávu", když je kontakt na Telegramu.
 *
 * Stejná stavba jako řádek souboru a hovoru (kolečko vlevo, text vedle), aby se v konverzaci
 * nevymýšlel další tvar. **„Přidat do kontaktů" tu zatím není** — mění adresář účtu (a u čísla
 * bez Telegramu nemá co přidat); číslo jde zkopírovat z nabídky nad zprávou.
 *
 * Tlačítko je v barvě popředí bubliny ([accent]): v odchozí `primary` bublině by `primary`
 * splynulo s pozadím.
 */
@Composable
internal fun ContactCard(contact: MessageMedia.Contact, accent: Color, onWrite: () -> Unit) {
    val contentAccent = accent.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(
                // Bez účtu na Telegramu nemá tvář id; iniciály a barva se berou ze jména.
                chatId = contact.userId ?: contact.phone.hashCode().toLong(),
                title = contact.name.ifBlank { contact.phone },
                avatarFileId = contact.photoFileId,
                size = CONTACT_AVATAR_SIZE,
            )
            Column {
                Text(
                    text = contact.name.ifBlank { contact.phone },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contact.name.isNotBlank()) {
                    Text(text = contact.phone, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }
        // Do chatu, ve kterém karta leží, „Napsat zprávu" nevede nikam (viz `isThisChat`).
        if (contact.userId != null && !contact.isThisChat) {
            TextButton(
                onClick = onWrite,
                colors = ButtonDefaults.textButtonColors(contentColor = contentAccent),
                // Popisek lícuje se jménem nad sebou (kolečko + mezera), ne s kolečkem.
                modifier = Modifier.padding(start = CONTACT_AVATAR_SIZE - 2.dp),
            ) {
                Text(stringResource(Res.string.contact_write))
            }
        }
    }
}

/** Jako kolečko u souboru a hovoru — řádky stojí v konverzaci vedle sebe. */
private val CONTACT_AVATAR_SIZE = 48.dp
