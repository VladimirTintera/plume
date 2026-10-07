package eu.tintera.plume.features.auth

import eu.tintera.plume.core.ui.BlockTextField
import eu.tintera.plume.core.ui.PasswordField
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.ui.CountryPickerSheet
import eu.tintera.plume.core.telegram.AuthCode
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.CodeDelivery
import eu.tintera.plume.core.ui.INTRO_ICON_SIZE
import eu.tintera.plume.core.ui.IntroShape
import eu.tintera.plume.core.ui.QrCode
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_proxy
import eu.tintera.plume.resources.vpn_lock_24dp
import eu.tintera.plume.features.proxy.ProxySettingsScreen
import org.koin.compose.viewmodel.koinViewModel
import eu.tintera.plume.resources.auth_back_to_password
import eu.tintera.plume.resources.auth_change_number
import eu.tintera.plume.resources.auth_continue
import eu.tintera.plume.resources.auth_forgot_password
import eu.tintera.plume.resources.auth_headline_new_password
import eu.tintera.plume.resources.auth_headline_recovery
import eu.tintera.plume.resources.auth_input_new_password
import eu.tintera.plume.resources.auth_input_password_hint
import eu.tintera.plume.resources.auth_input_recovery_code
import eu.tintera.plume.resources.auth_recovery_resend
import eu.tintera.plume.resources.auth_recovery_unavailable_ok
import eu.tintera.plume.resources.auth_recovery_unavailable_text
import eu.tintera.plume.resources.auth_recovery_unavailable_title
import eu.tintera.plume.resources.auth_support_new_password
import eu.tintera.plume.resources.auth_support_recovery_pending
import eu.tintera.plume.resources.auth_support_recovery_sent
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
import eu.tintera.plume.resources.auth_support_code_app
import eu.tintera.plume.resources.auth_support_code_call
import eu.tintera.plume.resources.auth_support_code_caller
import eu.tintera.plume.resources.auth_support_code_fragment
import eu.tintera.plume.resources.auth_support_code_phrase
import eu.tintera.plume.resources.auth_support_code_sms
import eu.tintera.plume.resources.auth_support_code_word
import eu.tintera.plume.resources.auth_support_logging_out
import eu.tintera.plume.resources.auth_support_password_hint
import eu.tintera.plume.resources.auth_support_phone
import eu.tintera.plume.resources.auth_support_qr
import eu.tintera.plume.resources.auth_support_registration
import eu.tintera.plume.resources.auth_use_phone_number
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.mail_24dp
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
    val passwordStep by viewModel.passwordStep.collectAsStateWithLifecycle()
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val recoveryUnavailable by viewModel.recoveryUnavailable.collectAsStateWithLifecycle()

    var pickingCountry by rememberSaveable { mutableStateOf(false) }
    var showingProxy by rememberSaveable { mutableStateOf(false) }

    // Krok hesla platí jen nad heslem; ViewModel ho při odchodu nuluje až z odběru, takže by
    // na jeden snímek mohl viset nad jiným stavem.
    val view = AuthView(stage, if (stage is AuthStage.WaitPassword) passwordStep else PasswordStep.Password)

    // Přihlášení není v `NavDisplay`, takže zpět bez tohohle aplikaci zavřelo — i z kódu poslaného
    // na špatné číslo. `NavigationBackHandler`, ne `BackHandler`: ten by zabil predictive back.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = stage.canGoBack() && !busy && !showingProxy,
        onBackCompleted = viewModel::back,
    )

    // Proxy přes přihlášení: kde je Telegram blokovaný, se bez ní k přihlášení nikdo nedostane,
    // a Nastavení je až za ním. Tatáž obrazovka jako v Nastavení, přes celou plochu.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = showingProxy,
        onBackCompleted = { showingProxy = false },
    )

    Box(modifier.fillMaxSize()) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
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
                IntroShape(replayKey = view.phase()) {
                    // Ikona říká, v jaké fázi přihlášení uživatel je. Vyměňuje se prolnutím,
                    // aby se tvar pod ní nehýbal.
                    AnimatedContent(targetState = view.phase().icon, label = "auth-icon") { icon ->
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(INTRO_ICON_SIZE),
                        )
                    }
                }

                AnimatedContent(
                    targetState = view,
                    transitionSpec = { phaseTransition() },
                    contentKey = { it.key },
                    label = "auth-stage",
                ) { shown ->
                    val current = shown.stage
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(shown.headline()),
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.Center,
                        )

                        shown.supportingText()?.let {
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

                            is AuthStage.WaitCode -> CodeForm(
                                code = current.code,
                                value = input,
                                error = error,
                                busy = busy,
                                onValueChange = viewModel::onInputChange,
                                onSubmit = viewModel::submit,
                                onChangeNumber = viewModel::restartLogin,
                            )

                            is AuthStage.WaitPassword -> when (shown.step) {
                                PasswordStep.Password -> PasswordForm(
                                    value = input,
                                    error = error,
                                    busy = busy,
                                    onValueChange = viewModel::onInputChange,
                                    onSubmit = viewModel::submit,
                                    onForgot = viewModel::forgotPassword,
                                    onChangeNumber = viewModel::restartLogin,
                                )

                                PasswordStep.RecoveryCode -> RecoveryCodeForm(
                                    value = input,
                                    error = error,
                                    busy = busy,
                                    onValueChange = viewModel::onInputChange,
                                    onSubmit = viewModel::submit,
                                    onResend = viewModel::forgotPassword,
                                    onBack = viewModel::back,
                                )

                                PasswordStep.NewPassword -> NewPasswordForm(
                                    password = input,
                                    hint = hint,
                                    error = error,
                                    busy = busy,
                                    onPasswordChange = viewModel::onInputChange,
                                    onHintChange = viewModel::onHintChange,
                                    onSubmit = viewModel::submit,
                                )
                            }

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

                if (stage.offersProxy()) {
                    TextButton(onClick = { showingProxy = true }) {
                        Icon(
                            painterResource(Res.drawable.vpn_lock_24dp),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp).size(18.dp),
                        )
                        Text(stringResource(Res.string.auth_proxy))
                    }
                }
            }
        }
    }

    if (showingProxy) {
        ProxySettingsScreen(viewModel = koinViewModel(key = "proxy-auth"), onBack = { showingProxy = false })
    }
    }

    if (recoveryUnavailable) {
        RecoveryUnavailableDialog(onDismiss = viewModel::dismissRecoveryUnavailable)
    }

    if (pickingCountry) {
        CountryPickerSheet(
            countries = countries,
            selectedCodes = setOfNotNull(phone.country?.code),
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
    Recovery(Res.drawable.mail_24dp),
    Qr(Res.drawable.qr_code_scanner_24dp),
    Error(Res.drawable.error_24dp),
    Done(Res.drawable.forum_24dp),
}

/**
 * Co obrazovka ukazuje: stav automatu a u hesla ještě krok obnovy, který TDLib nezná.
 *
 * [key] rozhoduje o animaci. U hesla je to jen krok: maska e-mailu, kterou TDLib pošle novým
 * stavem těsně po žádosti o kód, je pořád tentýž krok — s celým stavem jako klíčem by obsah
 * přijel podruhé ve chvíli, kdy ho uživatel začíná číst.
 */
private data class AuthView(val stage: AuthStage, val step: PasswordStep) {
    val key: Any get() = if (stage is AuthStage.WaitPassword) step else stage
}

private fun AuthView.phase(): AuthPhase =
    if (stage is AuthStage.WaitPassword && step != PasswordStep.Password) AuthPhase.Recovery else stage.phase()

private fun AuthStage.phase(): AuthPhase = when (this) {
    AuthStage.Connecting,
    AuthStage.LoggingOut,
    AuthStage.WaitPhoneNumber,
    AuthStage.WaitRegistration,
        -> AuthPhase.Number

    is AuthStage.WaitCode -> AuthPhase.Code
    is AuthStage.WaitPassword -> AuthPhase.Code
    is AuthStage.WaitQrConfirmation -> AuthPhase.Qr
    is AuthStage.Failed -> AuthPhase.Error
    AuthStage.Ready -> AuthPhase.Done
}

private fun AuthView.headline(): StringResource = when {
    stage !is AuthStage.WaitPassword -> stage.headline()
    step == PasswordStep.RecoveryCode -> Res.string.auth_headline_recovery
    step == PasswordStep.NewPassword -> Res.string.auth_headline_new_password
    else -> stage.headline()
}

private fun AuthView.supportingText(): UiText? = when {
    stage !is AuthStage.WaitPassword -> stage.supportingText()
    step == PasswordStep.RecoveryCode -> stage.recoveryEmailPattern
        ?.let { UiText(Res.string.auth_support_recovery_sent, it) }
        // Maska dorazí novým stavem až po odeslání kódu; do té doby obecně.
        ?: UiText(Res.string.auth_support_recovery_pending)
    step == PasswordStep.NewPassword -> UiText(Res.string.auth_support_new_password)
    else -> stage.supportingText()
}

private fun AuthStage.headline(): StringResource = when (this) {
    AuthStage.Connecting -> Res.string.auth_headline_connecting
    AuthStage.WaitPhoneNumber -> Res.string.auth_headline_phone
    is AuthStage.WaitCode -> Res.string.auth_headline_code
    is AuthStage.WaitPassword -> Res.string.auth_headline_password
    is AuthStage.WaitQrConfirmation -> Res.string.auth_headline_qr
    AuthStage.WaitRegistration -> Res.string.auth_headline_registration
    AuthStage.Ready -> Res.string.auth_headline_ready
    AuthStage.LoggingOut -> Res.string.auth_headline_logging_out
    is AuthStage.Failed -> Res.string.auth_headline_failed
}

private fun AuthStage.supportingText(): UiText? = when (this) {
    AuthStage.WaitPhoneNumber -> UiText(Res.string.auth_support_phone)
    is AuthStage.WaitCode -> UiText(code.delivery.supportingText())
    is AuthStage.WaitPassword -> hint?.let { UiText(Res.string.auth_support_password_hint, it) }
    is AuthStage.WaitQrConfirmation -> UiText(Res.string.auth_support_qr)
    AuthStage.WaitRegistration -> UiText(Res.string.auth_support_registration)
    AuthStage.LoggingOut -> UiText(Res.string.auth_support_logging_out)
    is AuthStage.Failed -> message
    else -> null
}

/**
 * Věta nad polem podle toho, kudy kód doopravdy přišel. Dokud tu stála jedna věta pro všechno
 * („do jiného klienta nebo SMS"), byla polovinu času vedle — a přitom to TDLib pokaždé řekne.
 */
private fun CodeDelivery.supportingText(): StringResource = when (this) {
    CodeDelivery.TelegramApp -> Res.string.auth_support_code_app
    CodeDelivery.Sms -> Res.string.auth_support_code_sms
    CodeDelivery.Call -> Res.string.auth_support_code_call
    CodeDelivery.CallerNumber -> Res.string.auth_support_code_caller
    CodeDelivery.Word -> Res.string.auth_support_code_word
    CodeDelivery.Phrase -> Res.string.auth_support_code_phrase
    CodeDelivery.Fragment -> Res.string.auth_support_code_fragment
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

/**
 * Kód: políčko na číslici, kde délku známe, jinak obyčejné pole.
 *
 * **Hotový kód se odesílá sám.** Zadat ho je jediná věc, kterou na téhle obrazovce jde udělat,
 * a poslední číslice to říká líp než další ťuknutí. Tlačítko přesto zůstává — bez něj by se po
 * chybě, která s kódem nesouvisí (spadlé spojení), nedal tentýž kód poslat znovu: text se
 * nezměnil, takže by se odeslání samo už nespustilo.
 */
@Composable
private fun CodeForm(
    code: AuthCode,
    value: String,
    error: UiText?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    val complete = code.digits == null || value.length == code.digits

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (code.digits == null) {
            // Slovo ani fráze se do políček na číslice nevejdou a chtějí písmenkovou klávesnici.
            AuthTextField(
                value = value,
                onValueChange = onValueChange,
                label = Res.string.auth_input_code,
                keyboardType = KeyboardType.Text,
                isPassword = false,
                isError = error != null,
                onSubmit = onSubmit,
            )
        } else {
            CodeField(
                value = value,
                digits = code.digits,
                isError = error != null,
                onValueChange = onValueChange,
                onSubmit = onSubmit,
            )
            LaunchedEffect(value) { if (value.length == code.digits) onSubmit() }
        }

        FormError(error)
        ContinueButton(enabled = value.isNotBlank() && complete, busy = busy, onClick = onSubmit)
        // Kód poslaný na překlep v čísle nikdy nedorazí; bez tohohle se z kroku nedalo odejít.
        SecondaryActions {
            TextButton(onClick = onChangeNumber, enabled = !busy) {
                Text(stringResource(Res.string.auth_change_number))
            }
        }
    }
}

@Composable
private fun PasswordForm(
    value: String,
    error: UiText?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgot: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PasswordField(
            value = value,
            isError = error != null,
            onValueChange = onValueChange,
            onSubmit = onSubmit,
        )
        FormError(error)
        ContinueButton(enabled = value.isNotEmpty(), busy = busy, onClick = onSubmit)
        /*
         * Zapomenuté heslo bylo slepá ulička: zkoušet dál, nebo aplikaci odinstalovat. Nabízí se
         * vždycky, i bez obnovovacího e-mailu — tam vysvětlí, co zbývá. Tlačítko, které by se
         * u části účtů nekreslilo, by nechalo uživatele hledat cestu, která není.
         */
        SecondaryActions {
            TextButton(onClick = onForgot, enabled = !busy) {
                Text(stringResource(Res.string.auth_forgot_password))
            }
            TextButton(onClick = onChangeNumber, enabled = !busy) {
                Text(stringResource(Res.string.auth_change_number))
            }
        }
    }
}

/**
 * Kód z obnovovacího e-mailu. Obyčejné pole, ne políčka na číslice jako u kódu z SMS: délku
 * kódu z e-mailu TDLib neříká a hádat ji by znamenalo kreslit políčka, která nemusí sedět.
 */
@Composable
private fun RecoveryCodeForm(
    value: String,
    error: UiText?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BlockTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = Res.string.auth_input_recovery_code,
            isError = error != null,
            keyboardType = KeyboardType.Number,
            // Bez `contentType`: `SmsOtpCode` by autofill nabídl kód z SMS, a ten sem nepatří.
            onSubmit = onSubmit,
        )
        FormError(error)
        ContinueButton(enabled = value.isNotBlank(), busy = busy, onClick = onSubmit)
        SecondaryActions {
            TextButton(onClick = onResend, enabled = !busy) {
                Text(stringResource(Res.string.auth_recovery_resend))
            }
            TextButton(onClick = onBack, enabled = !busy) {
                Text(stringResource(Res.string.auth_back_to_password))
            }
        }
    }
}

/**
 * Nové heslo a nápověda. Druhé pole na zopakování hesla tu není: kontrolou překlepu je oko
 * v poli (viz [PasswordField]), a jedno pole navíc na telefonu s klávesnicí vytlačí tlačítko
 * pod okraj.
 */
@Composable
private fun NewPasswordForm(
    password: String,
    hint: String,
    error: UiText?,
    busy: Boolean,
    onPasswordChange: (String) -> Unit,
    onHintChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val hintFocus = remember { FocusRequester() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PasswordField(
            value = password,
            isError = error != null,
            onValueChange = onPasswordChange,
            onSubmit = onSubmit,
            placeholder = Res.string.auth_input_new_password,
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Next,
            onNext = { hintFocus.requestFocus() },
        )
        BlockTextField(
            value = hint,
            onValueChange = onHintChange,
            placeholder = Res.string.auth_input_password_hint,
            isError = false,
            keyboardType = KeyboardType.Text,
            onSubmit = onSubmit,
            fieldModifier = Modifier.focusRequester(hintFocus),
        )
        FormError(error)
        ContinueButton(enabled = password.isNotEmpty(), busy = busy, onClick = onSubmit)
    }
}

/**
 * Účet bez obnovovacího e-mailu. Obnovit heslo odsud nejde a tvářit se jinak by bylo horší než
 * slepá ulička; dialog aspoň řekne nahlas, co zbývá — vzpomenout si, nebo účet resetovat.
 */
@Composable
private fun RecoveryUnavailableDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.auth_recovery_unavailable_title)) },
        text = { Text(stringResource(Res.string.auth_recovery_unavailable_text)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.auth_recovery_unavailable_ok)) }
        },
    )
}

/** Vedlejší cesty pod tlačítkem „dál" — na střed a zalomené, v češtině se dvě vedle sebe nevejdou vždycky. */
@Composable
private fun SecondaryActions(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
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
