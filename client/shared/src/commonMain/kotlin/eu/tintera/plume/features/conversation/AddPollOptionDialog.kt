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
import eu.tintera.plume.core.telegram.POLL_ANSWER_MAX
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
 */
@Composable
internal fun AddPollOptionDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(Res.string.poll_add_option_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
