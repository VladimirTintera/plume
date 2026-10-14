package eu.tintera.plume.features.conversation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.POLL_ANSWER_MAX
import eu.tintera.plume.core.telegram.PollDraftMedia
import eu.tintera.plume.core.ui.TextFieldSlot
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.poll_compose_counter
import eu.tintera.plume.resources.poll_add_option_confirm
import eu.tintera.plume.resources.poll_add_option_title
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog s polem na vlastní odpověď do ankety. Pole si fokus bere samo — napsat odpověď je
 * jediné, co tu jde udělat. Délka je držená na [POLL_ANSWER_MAX]; delší odpověď by server
 * odmítl až po odeslání.
 *
 * **K odpovědi jde přidat přílohu**, stejnou ikonou a nabídkou jako u odpovědi ve formuláři
 * ankety ([MediaSlot], [PollMediaChooser]): TDLib bere u přidané odpovědi tytéž druhy jako při
 * zakládání. Příloha žije jen v dialogu — přežít otočení nemusí, dialog je na jednu odpověď.
 * Výběry se otevírají NAD dialogem (každý je vlastní okno), takže se po výběru vrací do něj.
 */
@Composable
internal fun AddPollOptionDialog(
    chatId: Long,
    places: PlaceSearcher,
    onConfirm: (text: String, media: PollDraftMedia?) -> Unit,
    onError: (UiText) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var media by remember { mutableStateOf<PollDraftMedia?>(null) }
    var choosing by remember { mutableStateOf<PollMediaKind?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val mediaPicker = rememberAttachmentPicker(
        onPicked = { picked ->
            // Výběr vydá i víc souborů naráz; odpověď nese jednu fotku nebo video.
            picked.firstOrNull { it is OutgoingAttachment.Photo || it is OutgoingAttachment.Video }
                ?.let { media = PollDraftMedia.File(it) }
        },
        onError = onError,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.poll_add_option_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(POLL_ANSWER_MAX) },
                singleLine = true,
                supportingText = { Text(stringResource(Res.string.poll_compose_counter, text.length, POLL_ANSWER_MAX)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                trailingIcon = {
                    TextFieldSlot {
                        MediaSlot(
                            media = media,
                            kinds = PollMediaKind.entries,
                            onChoose = { kind -> if (kind == PollMediaKind.PhotoOrVideo) mediaPicker.pickMedia() else choosing = kind },
                            onRemove = { media = null },
                        )
                    }
                },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            // Příloha bez textu neodejde: odpověď musí mít text, server by ji jinak odmítl.
            TextButton(onClick = { onConfirm(text, media) }, enabled = text.isNotBlank()) {
                Text(stringResource(Res.string.poll_add_option_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
    choosing?.let { kind ->
        PollMediaChooser(
            kind = kind,
            chatId = chatId,
            places = places,
            onChosen = { chosen ->
                choosing = null
                media = chosen
            },
            onDismiss = { choosing = null },
        )
    }
}
