package eu.tintera.plume.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.ui.QrCode
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_continue
import eu.tintera.plume.resources.auth_headline_code
import eu.tintera.plume.resources.auth_headline_connecting
import eu.tintera.plume.resources.auth_headline_failed
import eu.tintera.plume.resources.auth_headline_logging_out
import eu.tintera.plume.resources.auth_headline_password
import eu.tintera.plume.resources.auth_headline_phone
import eu.tintera.plume.resources.auth_headline_qr
import eu.tintera.plume.resources.auth_headline_ready
import eu.tintera.plume.resources.auth_headline_registration
import eu.tintera.plume.resources.auth_input_code
import eu.tintera.plume.resources.auth_input_password
import eu.tintera.plume.resources.auth_input_phone
import eu.tintera.plume.resources.auth_qr_content_description
import eu.tintera.plume.resources.auth_sign_in_with_qr
import eu.tintera.plume.resources.auth_support_code
import eu.tintera.plume.resources.auth_support_logging_out
import eu.tintera.plume.resources.auth_support_password_hint
import eu.tintera.plume.resources.auth_support_phone
import eu.tintera.plume.resources.auth_support_qr
import eu.tintera.plume.resources.auth_support_registration
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // AnimatedContent nese přechod mezi fázemi přihlášení — spatial spec z AppMotion
        // dá tomu ten lehký expressive bounce, o který jde.
        AnimatedContent(targetState = stage, label = "auth-stage") { current ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 420.dp),
            ) {
                Text(
                    text = stringResource(current.headline()),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )

                current.supportingText()?.let {
                    Text(
                        text = it.resolve(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                if (current.acceptsInput()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = viewModel::onInputChange,
                        label = { Text(stringResource(current.inputLabel())) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it.resolve()) } },
                        visualTransformation = if (current is AuthStage.WaitPassword) {
                            PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (current) {
                                AuthStage.WaitPhoneNumber -> KeyboardType.Phone
                                AuthStage.WaitCode -> KeyboardType.NumberPassword
                                else -> KeyboardType.Password
                            },
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = viewModel::submit,
                        enabled = !busy && input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // M3 Expressive LoadingIndicator místo popisku — na rozdíl od
                        // CircularProgressIndicator je to tvarově morfující indikátor z expressive sady.
                        if (busy) LoadingIndicator() else Text(stringResource(Res.string.auth_continue))
                    }

                    if (current == AuthStage.WaitPhoneNumber) {
                        TextButton(onClick = viewModel::startQrLogin, enabled = !busy) {
                            Text(stringResource(Res.string.auth_sign_in_with_qr))
                        }
                    }
                }

                if (current is AuthStage.Connecting || current is AuthStage.LoggingOut) {
                    LoadingIndicator()
                }

                if (current is AuthStage.WaitQrConfirmation) {
                    QrCode(
                        content = current.link,
                        contentDescription = stringResource(Res.string.auth_qr_content_description),
                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun AuthStage.headline(): StringResource = when (this) {
    AuthStage.Connecting -> Res.string.auth_headline_connecting
    AuthStage.WaitPhoneNumber -> Res.string.auth_headline_phone
    AuthStage.WaitCode -> Res.string.auth_headline_code
    is AuthStage.WaitPassword -> Res.string.auth_headline_password
    is AuthStage.WaitQrConfirmation -> Res.string.auth_headline_qr
    AuthStage.WaitRegistration -> Res.string.auth_headline_registration
    AuthStage.Ready -> Res.string.auth_headline_ready
    AuthStage.LoggingOut -> Res.string.auth_headline_logging_out
    is AuthStage.Failed -> Res.string.auth_headline_failed
}

private fun AuthStage.supportingText(): UiText? = when (this) {
    AuthStage.WaitPhoneNumber -> UiText(Res.string.auth_support_phone)
    AuthStage.WaitCode -> UiText(Res.string.auth_support_code)
    is AuthStage.WaitPassword -> hint?.let { UiText(Res.string.auth_support_password_hint, it) }
    is AuthStage.WaitQrConfirmation -> UiText(Res.string.auth_support_qr)
    AuthStage.WaitRegistration -> UiText(Res.string.auth_support_registration)
    AuthStage.LoggingOut -> UiText(Res.string.auth_support_logging_out)
    is AuthStage.Failed -> message
    else -> null
}

private fun AuthStage.acceptsInput(): Boolean =
    this == AuthStage.WaitPhoneNumber || this == AuthStage.WaitCode || this is AuthStage.WaitPassword

private fun AuthStage.inputLabel(): StringResource = when (this) {
    AuthStage.WaitPhoneNumber -> Res.string.auth_input_phone
    AuthStage.WaitCode -> Res.string.auth_input_code
    else -> Res.string.auth_input_password
}
