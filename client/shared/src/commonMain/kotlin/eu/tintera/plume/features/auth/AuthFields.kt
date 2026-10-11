package eu.tintera.plume.features.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import eu.tintera.plume.core.ui.tabularNumbers
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pole pro kód, který není číslo (slovo nebo fráze z SMS). Číselný kód má vlastní políčka,
 * viz [CodeField], a heslo vlastní blok, viz [PasswordField].
 */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    keyboardType: KeyboardType,
    isPassword: Boolean,
    isError: Boolean,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        // Do kódu ani do hesla nemá klávesnice co opravovat — slovní kód by z „hokej" udělala
        // „hokeje" a uživatel by nepoznal proč.
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        textStyle = MaterialTheme.typography.bodyLarge.tabularNumbers(),
        modifier = Modifier.fillMaxWidth(),
    )
}
