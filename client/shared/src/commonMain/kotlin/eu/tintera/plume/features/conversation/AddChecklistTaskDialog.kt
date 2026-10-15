package eu.tintera.plume.features.conversation

import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.checklist_add_task_confirm
import eu.tintera.plume.resources.checklist_add_task_title
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.poll_compose_counter
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog s polem na nový úkol do cizího (nebo vlastního) seznamu — jako přidání odpovědi do
 * ankety. Pole si fokus bere samo. Úkol je jeden řádek: TDLib konec řádku v úkolu nebere, takže
 * pole je jednořádkové a vložený text se o konce řádků připraví ([checklistTaskText]).
 */
@Composable
internal fun AddChecklistTaskDialog(
    maxLength: Int,
    onConfirm: (text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.checklist_add_task_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = checklistTaskText(it).take(maxLength) },
                singleLine = true,
                supportingText = { Text(stringResource(Res.string.poll_compose_counter, text.length, maxLength)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(Res.string.checklist_add_task_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
