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
                    text = current.headline(),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )

                current.supportingText()?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                if (current.acceptsInput()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = viewModel::onInputChange,
                        label = { Text(current.inputLabel()) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
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
                        if (busy) LoadingIndicator() else Text("Pokračovat")
                    }

                    if (current == AuthStage.WaitPhoneNumber) {
                        TextButton(onClick = viewModel::startQrLogin, enabled = !busy) {
                            Text("Přihlásit se QR kódem")
                        }
                    }
                }

                if (current is AuthStage.Connecting) {
                    LoadingIndicator()
                }

                if (current is AuthStage.WaitQrConfirmation) {
                    QrCode(
                        content = current.link,
                        contentDescription = "QR kód pro přihlášení",
                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun AuthStage.headline(): String = when (this) {
    AuthStage.Connecting -> "Připojuji se"
    AuthStage.WaitPhoneNumber -> "Tvoje číslo"
    AuthStage.WaitCode -> "Ověřovací kód"
    is AuthStage.WaitPassword -> "Dvoufázové ověření"
    is AuthStage.WaitQrConfirmation -> "Naskenuj kód"
    AuthStage.WaitRegistration -> "Nový účet"
    AuthStage.Ready -> "Hotovo"
    AuthStage.LoggedOut -> "Odhlášeno"
    is AuthStage.Failed -> "Nepovedlo se"
}

private fun AuthStage.supportingText(): String? = when (this) {
    AuthStage.WaitPhoneNumber -> "Zadej číslo i s předvolbou, například +420…"
    AuthStage.WaitCode -> "Kód dorazil do jiného Telegram klienta nebo SMS."
    is AuthStage.WaitPassword -> hint?.let { "Nápověda: $it" }
    is AuthStage.WaitQrConfirmation -> "V Telegramu na přihlášeném zařízení otevři Nastavení → Zařízení → Připojit zařízení a naskenuj kód."
    AuthStage.WaitRegistration -> "Tohle číslo zatím účet nemá. Založ si ho v oficiálním klientovi."
    is AuthStage.Failed -> message
    else -> null
}

private fun AuthStage.acceptsInput(): Boolean =
    this == AuthStage.WaitPhoneNumber || this == AuthStage.WaitCode || this is AuthStage.WaitPassword

private fun AuthStage.inputLabel(): String = when (this) {
    AuthStage.WaitPhoneNumber -> "Telefonní číslo"
    AuthStage.WaitCode -> "Kód"
    else -> "Heslo"
}
