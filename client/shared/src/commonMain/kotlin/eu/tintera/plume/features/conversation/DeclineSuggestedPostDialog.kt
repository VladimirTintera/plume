package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.suggested_post_decline
import eu.tintera.plume.resources.suggested_post_decline_comment
import eu.tintera.plume.resources.suggested_post_decline_text
import eu.tintera.plume.resources.suggested_post_decline_title
import org.jetbrains.compose.resources.stringResource

/**
 * Odmítnutí navrhovaného příspěvku. Komentář je nepovinný a dostane ho autor návrhu
 * (servisní zpráva „Navržený příspěvek byl odmítnut: …"); server bere nejvýš 128 znaků.
 */
@Composable
internal fun DeclineSuggestedPostDialog(onConfirm: (comment: String) -> Unit, onDismiss: () -> Unit) {
    var comment by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.suggested_post_decline_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.suggested_post_decline_text), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(DECLINE_COMMENT_MAX) },
                    label = { Text(stringResource(Res.string.suggested_post_decline_comment)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(comment.trim()) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.suggested_post_decline), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

private const val DECLINE_COMMENT_MAX = 128
