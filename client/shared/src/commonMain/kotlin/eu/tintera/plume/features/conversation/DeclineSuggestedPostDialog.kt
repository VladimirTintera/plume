package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import eu.tintera.plume.resources.Res
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
    ConfirmDialog(
        title = stringResource(Res.string.suggested_post_decline_title),
        text = stringResource(Res.string.suggested_post_decline_text),
        confirmLabel = stringResource(Res.string.suggested_post_decline),
        onConfirm = { onConfirm(comment.trim()) },
        onDismiss = onDismiss,
        icon = Res.drawable.close_24dp,
        destructive = true,
    ) {
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it.take(DECLINE_COMMENT_MAX) },
            label = { Text(stringResource(Res.string.suggested_post_decline_comment)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val DECLINE_COMMENT_MAX = 128
