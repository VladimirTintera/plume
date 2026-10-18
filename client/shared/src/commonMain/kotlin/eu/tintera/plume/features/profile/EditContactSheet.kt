package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.FormTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ContactDetails
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.sheetActionShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.contact_edit
import eu.tintera.plume.resources.contact_note
import eu.tintera.plume.resources.contact_note_hint
import eu.tintera.plume.resources.contact_photo_hint
import eu.tintera.plume.resources.contact_photo_remove_action
import eu.tintera.plume.resources.contact_photo_replace_action
import eu.tintera.plume.resources.contact_photo_set_action
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_profile_first_name
import eu.tintera.plume.resources.edit_profile_last_name
import eu.tintera.plume.resources.edit_profile_save
import eu.tintera.plume.resources.photo_camera_24dp
import org.jetbrains.compose.resources.stringResource

/**
 * Úprava kontaktu: jméno, pod kterým ho má uživatel uložené, poznámka a vlastní fotka. Nic z toho
 * kontakt nevidí — proto to sheet u poznámky i fotky říká.
 *
 * **Fotka sheet zavře.** Výběr a ořez jsou překryv nad profilem a modální sheet je okno nad
 * vším, takže by ořez zůstal pod ním. Fotka se navíc ukládá hned (jiné volání než jméno),
 * nečeká na Uložit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditContactSheet(
    contact: ContactDetails,
    /** Jak kontakt oslovit u fotky („Nastavit fotku pro Jana"). */
    name: String,
    onSave: (firstName: String, lastName: String, note: String) -> Unit,
    /** `null` = fotku nastavit nejde (není čím ořezat). */
    onSetPhoto: (() -> Unit)?,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by rememberSaveable { mutableStateOf(contact.firstName) }
    var lastName by rememberSaveable { mutableStateOf(contact.lastName) }
    var note by rememberSaveable { mutableStateOf(contact.note) }
    val changed = firstName.trim() != contact.firstName || lastName.trim() != contact.lastName || note.trim() != contact.note

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(stringResource(Res.string.contact_edit))
            FormTextField(
                value = firstName,
                onValueChange = { firstName = it.take(NAME_MAX) },
                label = { Text(stringResource(Res.string.edit_profile_first_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = lastName,
                onValueChange = { lastName = it.take(NAME_MAX) },
                label = { Text(stringResource(Res.string.edit_profile_last_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            // Server, který poznámky neumí (limit 0), pole nedostane — uložení by skončilo chybou.
            if (contact.noteMaxLength > 0) {
                FormTextField(
                    value = note,
                    onValueChange = { note = it.take(contact.noteMaxLength) },
                    label = { Text(stringResource(Res.string.contact_note)) },
                    supportingText = {
                        Text(stringResource(Res.string.contact_note_hint))
                    },
                    minLines = 2,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (onSetPhoto != null) {
                Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
                    val count = if (contact.hasPersonalPhoto) 2 else 1
                    SheetActionRow(
                        icon = Res.drawable.photo_camera_24dp,
                        label = if (contact.hasPersonalPhoto) {
                            stringResource(Res.string.contact_photo_replace_action)
                        } else {
                            stringResource(Res.string.contact_photo_set_action, name)
                        },
                        shape = sheetActionShape(0, count),
                        onClick = {
                            onDismiss()
                            onSetPhoto()
                        },
                    )
                    if (contact.hasPersonalPhoto) {
                        SheetActionRow(
                            icon = Res.drawable.delete_24dp,
                            label = stringResource(Res.string.contact_photo_remove_action),
                            shape = sheetActionShape(1, count),
                            onClick = {
                                onDismiss()
                                onRemovePhoto()
                            },
                        )
                    }
                }
                Text(
                    stringResource(Res.string.contact_photo_hint, name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            MediumButton(
                onClick = {
                    onSave(firstName, lastName, note)
                    onDismiss()
                },
                enabled = changed && firstName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.edit_profile_save))
            }
        }
    }
}

/** Jméno i příjmení bere Telegram nejvýš o 64 znacích. */
private const val NAME_MAX = 64
