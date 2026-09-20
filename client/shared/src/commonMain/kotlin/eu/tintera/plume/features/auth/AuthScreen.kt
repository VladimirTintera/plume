package eu.tintera.plume.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.ui.INTRO_ICON_SIZE
import eu.tintera.plume.core.ui.IntroShape
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
import eu.tintera.plume.resources.auth_qr_content_description
import eu.tintera.plume.resources.auth_sign_in_with_qr
import eu.tintera.plume.resources.auth_support_code
import eu.tintera.plume.resources.auth_support_logging_out
import eu.tintera.plume.resources.auth_support_password_hint
import eu.tintera.plume.resources.auth_support_phone
import eu.tintera.plume.resources.auth_support_qr
import eu.tintera.plume.resources.auth_support_registration
import eu.tintera.plume.resources.auth_use_phone_number
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.qr_code_scanner_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.smartphone_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Přihlášení.
 *
 * **Uvítací tvar stojí NAD `AnimatedContent`, ne uvnitř.** Uvnitř by se jeho úvodní animace
 * pouštěla při každém kroku přihlášení a z uvítání by byl tik; takhle naběhne jednou a pak
 * jen mění ikonu podle fáze. Je to tentýž tvar jako u prázdného stavu — jeden význam, jeden
 * vzhled (viz [IntroShape]).
 *
 * Obsah se scrolluje a bere `safeDrawing` i `imePadding`: na nízkém okně naležato se
 * s otevřenou klávesnicí nevejde ani samotné pole s tlačítkem.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    val countries by viewModel.countries.collectAsStateWithLifecycle()

    var pickingCountry by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH),
            ) {
                // Tvar se proanimuje při každé změně kroku — nad měnícím se obsahem by jinak
                // stál jako obrázek, a přitom je to jediné, co drží souvislost mezi kroky.
                IntroShape(replayKey = stage.phase()) {
                    // Ikona říká, v jaké fázi přihlášení uživatel je. Vyměňuje se prolnutím,
                    // aby se tvar pod ní nehýbal.
                    AnimatedContent(targetState = stage.phase().icon, label = "auth-icon") { icon ->
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(INTRO_ICON_SIZE),
                        )
                    }
                }

                AnimatedContent(
                    targetState = stage,
                    transitionSpec = { phaseTransition() },
                    label = "auth-stage",
                ) { current ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
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

                        Spacer(Modifier.height(8.dp))

                        when (current) {
                            AuthStage.WaitPhoneNumber -> PhoneForm(
                                phone = phone,
                                error = error,
                                busy = busy,
                                onPhoneChange = viewModel::onPhoneChange,
                                onPickCountry = { pickingCountry = true },
                                onSubmit = viewModel::submit,
                                onQrLogin = viewModel::startQrLogin,
                            )

                            AuthStage.WaitCode -> SecretForm(
                                value = input,
                                label = Res.string.auth_input_code,
                                keyboardType = KeyboardType.NumberPassword,
                                isPassword = false,
                                error = error,
                                busy = busy,
                                onValueChange = viewModel::onInputChange,
                                onSubmit = viewModel::submit,
                            )

                            is AuthStage.WaitPassword -> SecretForm(
                                value = input,
                                label = Res.string.auth_input_password,
                                keyboardType = KeyboardType.Password,
                                isPassword = true,
                                error = error,
                                busy = busy,
                                onValueChange = viewModel::onInputChange,
                                onSubmit = viewModel::submit,
                            )

                            is AuthStage.WaitQrConfirmation -> QrForm(
                                link = current.link,
                                error = error,
                                busy = busy,
                                onUsePhoneNumber = viewModel::restartLogin,
                            )

                            AuthStage.Connecting, AuthStage.LoggingOut -> LoadingIndicator()

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    if (pickingCountry) {
        CountryPickerSheet(
            countries = countries,
            selected = phone.country,
            onPick = {
                viewModel.selectCountry(it)
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false },
        )
    }
}

/**
 * Fáze přijíždějí zdola a odjíždějí nahoru — přihlášení jde dopředu, takže i pohyb jde jedním
 * směrem. Prolnutí samo by nedalo najevo, že se postoupilo o krok dál.
 */
private fun phaseTransition() =
    (slideInVertically { it / PHASE_SLIDE_FRACTION } + fadeIn())
        .togetherWith(slideOutVertically { -it / PHASE_SLIDE_FRACTION } + fadeOut())

/**
 * Krok přihlášení pro uvítací tvar: nese ikonu a zároveň rozhoduje, kdy se tvar proanimuje znovu.
 *
 * Je to hrubší dělení než [AuthStage] schválně. Startovní `Connecting` patří k zadání čísla,
 * protože k němu automat míří — jinak by se tvar hned po náběhu proanimoval podruhé a vypadalo
 * by to jako zadrhnutí.
 */
private enum class AuthPhase(val icon: DrawableResource) {
    Number(Res.drawable.smartphone_24dp),
    Code(Res.drawable.schedule_24dp),
    Qr(Res.drawable.qr_code_scanner_24dp),
    Error(Res.drawable.error_24dp),
    Done(Res.drawable.forum_24dp),
}

private fun AuthStage.phase(): AuthPhase = when (this) {
    AuthStage.Connecting,
    AuthStage.LoggingOut,
    AuthStage.WaitPhoneNumber,
    AuthStage.WaitRegistration,
        -> AuthPhase.Number

    AuthStage.WaitCode -> AuthPhase.Code
    is AuthStage.WaitPassword -> AuthPhase.Code
    is AuthStage.WaitQrConfirmation -> AuthPhase.Qr
    is AuthStage.Failed -> AuthPhase.Error
    AuthStage.Ready -> AuthPhase.Done
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

/** Tlačítko „dál" je ve všech fázích stejné, takže jen jedno místo, kde se mění stav. */
@Composable
internal fun ContinueButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
    ) {
        // M3 Expressive LoadingIndicator místo popisku — na rozdíl od CircularProgressIndicator
        // je to tvarově morfující indikátor z expressive sady.
        if (busy) LoadingIndicator() else Text(stringResource(Res.string.auth_continue))
    }
}

/** Chyba pod formulářem. Místo si nedrží — prázdný řádek pod polem by vypadal jako nedodělek. */
@Composable
internal fun FormError(error: UiText?) {
    if (error == null) return
    Text(
        text = error.resolve(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun QrForm(
    link: String,
    error: UiText?,
    busy: Boolean,
    onUsePhoneNumber: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        QrCode(
            content = link,
            contentDescription = stringResource(Res.string.auth_qr_content_description),
            modifier = Modifier.widthIn(max = QR_MAX_WIDTH).fillMaxWidth(),
        )

        FormError(error)

        /*
         * Cesta zpátky k číslu. Bez ní je QR slepá ulička: TDLib z
         * `authorizationStateWaitOtherDeviceConfirmation` nepustí ani `setAuthenticationPhoneNumber`,
         * ani `requestQrCodeAuthentication`, takže kdo nemá čím kód naskenovat, musel by aplikaci
         * odinstalovat. Uvnitř to znamená zavřít klienta a začít znovu — proto to chvíli trvá.
         */
        TextButton(onClick = onUsePhoneNumber, enabled = !busy) {
            Text(stringResource(Res.string.auth_use_phone_number))
        }
    }
}

@Composable
private fun SecretForm(
    value: String,
    label: StringResource,
    keyboardType: KeyboardType,
    isPassword: Boolean,
    error: UiText?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AuthTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            keyboardType = keyboardType,
            isPassword = isPassword,
            isError = error != null,
            onSubmit = onSubmit,
        )
        FormError(error)
        ContinueButton(enabled = value.isNotBlank(), busy = busy, onClick = onSubmit)
    }
}

@Composable
private fun PhoneForm(
    phone: PhoneEntry,
    error: UiText?,
    busy: Boolean,
    onPhoneChange: (String) -> Unit,
    onPickCountry: () -> Unit,
    onSubmit: () -> Unit,
    onQrLogin: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PhoneField(
            phone = phone,
            isError = error != null,
            onPhoneChange = onPhoneChange,
            onPickCountry = onPickCountry,
            onSubmit = onSubmit,
        )
        FormError(error)
        ContinueButton(enabled = phone.isComplete, busy = busy, onClick = onSubmit)

        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onQrLogin, enabled = !busy) {
                Text(stringResource(Res.string.auth_sign_in_with_qr))
            }
        }
    }
}

private val CONTENT_MAX_WIDTH = 420.dp
private val QR_MAX_WIDTH = 280.dp
private val BUTTON_HEIGHT = 56.dp
private const val PHASE_SLIDE_FRACTION = 4
