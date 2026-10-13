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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.ui.CountryPickerSheet
import eu.tintera.plume.core.ui.PhoneEntry
import eu.tintera.plume.core.ui.PhoneField
import eu.tintera.plume.core.telegram.AuthCode
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.CodeDelivery
import eu.tintera.plume.core.telegram.EmailReset
import eu.tintera.plume.core.ui.INTRO_ICON_SIZE
import eu.tintera.plume.core.ui.IntroShape
import eu.tintera.plume.core.ui.QrCode
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.waitUiText
import eu.tintera.plume.features.conversation.formatDuration
import androidx.compose.material3.LocalTextStyle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_proxy
import eu.tintera.plume.resources.vpn_lock_24dp
import eu.tintera.plume.features.proxy.ProxySettingsScreen
import org.koin.compose.viewmodel.koinViewModel
import eu.tintera.plume.resources.auth_back_to_password
import eu.tintera.plume.resources.auth_change_number
import eu.tintera.plume.resources.auth_email_back_to_code
import eu.tintera.plume.resources.auth_email_change
import eu.tintera.plume.resources.auth_email_reset
import eu.tintera.plume.resources.auth_email_reset_confirm
import eu.tintera.plume.resources.auth_email_reset_finish
import eu.tintera.plume.resources.auth_email_reset_pending
import eu.tintera.plume.resources.auth_email_reset_ready
import eu.tintera.plume.resources.auth_email_reset_text_now
import eu.tintera.plume.resources.auth_email_reset_text_wait
import eu.tintera.plume.resources.auth_email_reset_title
import eu.tintera.plume.resources.auth_headline_email
import eu.tintera.plume.resources.auth_headline_email_code
import eu.tintera.plume.resources.auth_input_email
import eu.tintera.plume.resources.auth_support_email_address
import eu.tintera.plume.resources.auth_support_email_code
import eu.tintera.plume.resources.auth_support_email_code_unknown
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.auth_code_app_only_hint
import eu.tintera.plume.resources.auth_continue
import eu.tintera.plume.resources.auth_forgot_password
import eu.tintera.plume.resources.auth_headline_new_password
import eu.tintera.plume.resources.auth_headline_recovery
import eu.tintera.plume.resources.auth_input_new_password
import eu.tintera.plume.resources.auth_input_password_hint
import eu.tintera.plume.resources.auth_input_recovery_code
import eu.tintera.plume.resources.auth_recovery_resend
import eu.tintera.plume.resources.auth_resend_app
import eu.tintera.plume.resources.auth_resend_call
import eu.tintera.plume.resources.auth_resend_other
import eu.tintera.plume.resources.auth_resend_sms
import eu.tintera.plume.resources.auth_resend_wait
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
import eu.tintera.plume.resources.alternate_email_24dp
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
    // Text polí je Compose stav ve ViewModelu, ne StateFlow — viz `AuthViewModel.input`.
    val input = viewModel.input
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val phone = viewModel.phone
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val passwordStep by viewModel.passwordStep.collectAsStateWithLifecycle()
    val hint = viewModel.hint
    val recoveryUnavailable by viewModel.recoveryUnavailable.collectAsStateWithLifecycle()
    val resendIn by viewModel.resendIn.collectAsStateWithLifecycle()
    val changingEmail by viewModel.changingEmail.collectAsStateWithLifecycle()
    val emailResetIn by viewModel.emailResetIn.collectAsStateWithLifecycle()

    var pickingCountry by rememberSaveable { mutableStateOf(false) }
    var showingProxy by rememberSaveable { mutableStateOf(false) }
    var confirmingEmailReset by rememberSaveable { mutableStateOf(false) }

    // Krok hesla platí jen nad heslem; ViewModel ho při odchodu nuluje až z odběru, takže by
    // na jeden snímek mohl viset nad jiným stavem.
    val view = AuthView(
        stage = stage,
        step = if (stage is AuthStage.WaitPassword) passwordStep else PasswordStep.Password,
        changingEmail = stage is AuthStage.WaitEmailCode && changingEmail,
    )

    // Přihlášení není v `NavDisplay`, takže zpět bez tohohle aplikaci zavřelo — i z kódu poslaného
    // na špatné číslo. `NavigationBackHandler`, ne `BackHandler`: ten by zabil predictive back.
    //
    // **Zpět s klávesnicí vidět napřed schová klávesnici.** Handler se zapíná až s novým krokem,
    // tedy POZDĚJI, než si klávesnice zaregistrovala vlastní zpět, a callback zapnutý později
    // vyhrává — zpět z kódu tak klávesnici přeskočilo a rozepsané přihlášení zahodilo (ověřeno
    // na emulátoru gestem i klávesou). Rozhoduje se až při zpět, jako u `PopupBackAfterKeyboard`:
    // vypínat handler podle klávesnice by bylo horší, protože inset klávesnice po gestu zpět
    // občas zůstane viset (systém klávesnici hlásí skrytou, Compose ne) a zpět by pak prošlo
    // do aktivity a aplikaci zavřelo. Takhle jen jednou nic neudělá. Výška klávesnice, ne
    // `isImeVisible`: ta funguje i na iOS.
    val imeVisible by rememberUpdatedState(WindowInsets.ime.getBottom(LocalDensity.current) > 0)
    val keyboard = LocalSoftwareKeyboardController.current
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = stage.canGoBack() && !busy && !showingProxy,
        onBackCompleted = { if (imeVisible) keyboard?.hide() else viewModel.back() },
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
                                next = current.next,
                                resendIn = resendIn,
                                value = input,
                                error = error,
                                busy = busy,
                                onValueChange = viewModel::onInputChange,
                                onSubmit = viewModel::submit,
                                onResend = viewModel::resendCode,
                                onQrLogin = viewModel::startQrLogin,
                                onChangeNumber = viewModel::restartLogin,
                            )

                            AuthStage.WaitEmailAddress -> EmailAddressForm(
                                value = input,
                                error = error,
                                busy = busy,
                                onValueChange = viewModel::onInputChange,
                                onSubmit = viewModel::submit,
                                onBackToCode = null,
                                onChangeNumber = viewModel::restartLogin,
                            )

                            is AuthStage.WaitEmailCode -> if (shown.changingEmail) {
                                EmailAddressForm(
                                    value = input,
                                    error = error,
                                    busy = busy,
                                    onValueChange = viewModel::onInputChange,
                                    onSubmit = viewModel::submit,
                                    onBackToCode = viewModel::back,
                                    onChangeNumber = viewModel::restartLogin,
                                )
                            } else {
                                EmailCodeForm(
                                    stage = current,
                                    resetIn = emailResetIn,
                                    value = input,
                                    error = error,
                                    busy = busy,
                                    onValueChange = viewModel::onInputChange,
                                    onSubmit = viewModel::submit,
                                    onResend = viewModel::resendEmailCode,
                                    onChangeEmail = viewModel::changeEmail,
                                    onReset = { confirmingEmailReset = true },
                                    onFinishReset = viewModel::resetEmail,
                                    onChangeNumber = viewModel::restartLogin,
                                )
                            }

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

    // Žádost o reset se ptá předem: odpojí přihlašovací e-mail účtu, a kdo se jen přeťukl,
    // by o to po čekací době přišel.
    val resetOffer = ((stage as? AuthStage.WaitEmailCode)?.reset as? EmailReset.Available)
    if (confirmingEmailReset && resetOffer != null) {
        EmailResetDialog(
            waitSeconds = resetOffer.waitSeconds,
            onConfirm = {
                confirmingEmailReset = false
                viewModel.resetEmail()
            },
            onDismiss = { confirmingEmailReset = false },
        )
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
    Email(Res.drawable.alternate_email_24dp),
    Recovery(Res.drawable.mail_24dp),
    Qr(Res.drawable.qr_code_scanner_24dp),
    Error(Res.drawable.error_24dp),
    Done(Res.drawable.forum_24dp),
}

/**
 * Co obrazovka ukazuje: stav automatu a kroky, které TDLib nezná — obnova hesla a oprava
 * adresy nad čekáním na kód z e-mailu.
 *
 * [key] rozhoduje o animaci. U hesla je to jen krok: maska e-mailu, kterou TDLib pošle novým
 * stavem těsně po žádosti o kód, je pořád tentýž krok — s celým stavem jako klíčem by obsah
 * přijel podruhé ve chvíli, kdy ho uživatel začíná číst. Oprava adresy je krok sama o sobě.
 */
private data class AuthView(val stage: AuthStage, val step: PasswordStep, val changingEmail: Boolean) {
    val key: Any get() = when {
        stage is AuthStage.WaitPassword -> step
        changingEmail -> EMAIL_CHANGE_KEY
        else -> stage
    }
}

private const val EMAIL_CHANGE_KEY = "email-change"

private fun AuthView.phase(): AuthPhase =
    if (stage is AuthStage.WaitPassword && step != PasswordStep.Password) AuthPhase.Recovery else stage.phase()

private fun AuthStage.phase(): AuthPhase = when (this) {
    AuthStage.Connecting,
    AuthStage.LoggingOut,
    AuthStage.WaitPhoneNumber,
    AuthStage.WaitRegistration,
        -> AuthPhase.Number

    is AuthStage.WaitCode -> AuthPhase.Code
    AuthStage.WaitEmailAddress, is AuthStage.WaitEmailCode -> AuthPhase.Email
    is AuthStage.WaitPassword -> AuthPhase.Code
    is AuthStage.WaitQrConfirmation -> AuthPhase.Qr
    is AuthStage.Failed -> AuthPhase.Error
    AuthStage.Ready -> AuthPhase.Done
}

private fun AuthView.headline(): StringResource = when {
    changingEmail -> Res.string.auth_headline_email
    stage !is AuthStage.WaitPassword -> stage.headline()
    step == PasswordStep.RecoveryCode -> Res.string.auth_headline_recovery
    step == PasswordStep.NewPassword -> Res.string.auth_headline_new_password
    else -> stage.headline()
}

private fun AuthView.supportingText(): UiText? = when {
    changingEmail -> UiText(Res.string.auth_support_email_address)
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
    AuthStage.WaitEmailAddress -> Res.string.auth_headline_email
    is AuthStage.WaitEmailCode -> Res.string.auth_headline_email_code
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
    AuthStage.WaitEmailAddress -> UiText(Res.string.auth_support_email_address)
    is AuthStage.WaitEmailCode -> emailPattern
        ?.let { UiText(Res.string.auth_support_email_code, it) }
        ?: UiText(Res.string.auth_support_email_code_unknown)
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
internal fun CodeDelivery.supportingText(): StringResource = when (this) {
    CodeDelivery.TelegramApp -> Res.string.auth_support_code_app
    CodeDelivery.Sms -> Res.string.auth_support_code_sms
    CodeDelivery.Call -> Res.string.auth_support_code_call
    CodeDelivery.CallerNumber -> Res.string.auth_support_code_caller
    CodeDelivery.Word -> Res.string.auth_support_code_word
    CodeDelivery.Phrase -> Res.string.auth_support_code_phrase
    CodeDelivery.Fragment -> Res.string.auth_support_code_fragment
}

/**
 * Popisek tlačítka „poslat kód jinak" podle cesty, kterou nabízí server. Slovo a fráze jsou
 * pořád SMS, prozvonění pořád hovor — rozdíl vysvětlí věta nad polem, až kód odejde.
 */
internal fun CodeDelivery.resendLabel(): StringResource = when (this) {
    CodeDelivery.Sms, CodeDelivery.Word, CodeDelivery.Phrase -> Res.string.auth_resend_sms
    CodeDelivery.Call, CodeDelivery.CallerNumber -> Res.string.auth_resend_call
    CodeDelivery.TelegramApp -> Res.string.auth_resend_app
    CodeDelivery.Fragment -> Res.string.auth_resend_other
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
    next: CodeDelivery?,
    resendIn: Int,
    value: String,
    error: UiText?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onQrLogin: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    val complete = code.digits == null || value.length == code.digits
    /*
     * Kód jen do Telegramu a jinou cestu server nedá — našemu `api_id` je to pravidlo, ne
     * výjimka (`docs/poznatky/prihlaseni.md`, „Kód jinou cestou"). Kdo nemá přihlášenou jinou
     * relaci (nové číslo, odhlášený všude), by na kód čekal donekonečna a nepoznal proč.
     */
    val appOnly = code.delivery == CodeDelivery.TelegramApp && next == null

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
        if (appOnly) {
            Text(
                text = stringResource(Res.string.auth_code_app_only_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Kód poslaný na překlep v čísle nikdy nedorazí; bez tohohle se z kroku nedalo odejít.
        SecondaryActions {
            /*
             * Jiná cesta jen tehdy, když ji server nabízí — tlačítko, které by skončilo chybou
             * „kód nejde poslat znovu", by slibovalo cestu, která není. Během lhůty zůstává
             * vidět s odpočtem: uživatel bez přístupu k Telegramu se dozví, že za chvíli
             * přijde jiná možnost, a nemusí zkoušet zpět a znovu.
             */
            if (next != null) {
                val label = stringResource(next.resendLabel())
                TextButton(onClick = onResend, enabled = !busy && resendIn == 0) {
                    Text(
                        text = if (resendIn > 0) {
                            stringResource(Res.string.auth_resend_wait, label, formatDuration(resendIn))
                        } else {
                            label
                        },
                        // Odpočet se mění na místě; s proporcionálními číslicemi by tlačítko cukalo.
                        style = LocalTextStyle.current.tabularNumbers(),
                    )
                }
            }
            // TDLib QR pustí i z čekání na kód. Druhé zařízení je potřeba tak jako tak, a QR
            // je z něj rychlejší než opisovat kód ze zprávy.
            if (appOnly) {
                TextButton(onClick = onQrLogin, enabled = !busy) {
                    Text(stringResource(Res.string.auth_sign_in_with_qr))
                }
            }
            TextButton(onClick = onChangeNumber, enabled = !busy) {
                Text(stringResource(Res.string.auth_change_number))
            }
        }
    }
}

/**
 * Adresa pro přihlašovací kódy — napoprvé i jako oprava překlepu nad čekáním na kód
 * ([onBackToCode] pak vede zpátky ke kódu, který na starou adresu už odešel).
 */
@Composable
private fun EmailAddressForm(
    value: String,
    error: UiText?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToCode: (() -> Unit)?,
    onChangeNumber: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BlockTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = Res.string.auth_input_email,
            isError = error != null,
            keyboardType = KeyboardType.Email,
            contentType = ContentType.EmailAddress,
            onSubmit = onSubmit,
        )
        FormError(error)
        ContinueButton(enabled = value.isNotBlank(), busy = busy, onClick = onSubmit)
        SecondaryActions {
            if (onBackToCode != null) {
                TextButton(onClick = onBackToCode, enabled = !busy) {
                    Text(stringResource(Res.string.auth_email_back_to_code))
                }
            }
            TextButton(onClick = onChangeNumber, enabled = !busy) {
                Text(stringResource(Res.string.auth_change_number))
            }
        }
    }
}

/**
 * Kód z e-mailu. Políčka jako u kódu z SMS, když server řekne délku; jinak obyčejné pole.
 *
 * Pod tlačítkem je reset e-mailu pro toho, kdo k němu ztratil přístup — jinak by byl kód
 * na e-mail slepá ulička, ze které vede jen „Jiné číslo". Oprava adresy a nové poslání kódu
 * jsou jen u adresy zadané v tomhle přihlášení; adresu nastavenou dřív zná jen server.
 */
@Composable
private fun EmailCodeForm(
    stage: AuthStage.WaitEmailCode,
    resetIn: Int,
    value: String,
    error: UiText?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
    onReset: () -> Unit,
    onFinishReset: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    val digits = stage.digits
    val complete = digits == null || value.length == digits

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (digits == null) {
            BlockTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = Res.string.auth_input_recovery_code,
                isError = error != null,
                keyboardType = KeyboardType.Number,
                // Bez `contentType`: `SmsOtpCode` by autofill nabídl kód z SMS, a ten sem nepatří.
                onSubmit = onSubmit,
            )
        } else {
            CodeField(
                value = value,
                digits = digits,
                isError = error != null,
                onValueChange = onValueChange,
                onSubmit = onSubmit,
            )
            LaunchedEffect(value) { if (value.length == digits) onSubmit() }
        }

        FormError(error)
        ContinueButton(enabled = value.isNotBlank() && complete, busy = busy, onClick = onSubmit)

        // Zažádaný reset: kolik zbývá, a po uplynutí cesta ke kódu na číslo.
        if (stage.reset is EmailReset.Pending) {
            Text(
                text = if (resetIn > 0) {
                    UiText(Res.string.auth_email_reset_pending, waitUiText(resetIn)).resolve()
                } else {
                    stringResource(Res.string.auth_email_reset_ready)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SecondaryActions {
            when (stage.reset) {
                is EmailReset.Available -> TextButton(onClick = onReset, enabled = !busy) {
                    Text(stringResource(Res.string.auth_email_reset))
                }
                is EmailReset.Pending -> if (resetIn == 0) {
                    TextButton(onClick = onFinishReset, enabled = !busy) {
                        Text(stringResource(Res.string.auth_email_reset_finish))
                    }
                }
                null -> Unit
            }
            if (stage.isSetup) {
                TextButton(onClick = onResend, enabled = !busy) {
                    Text(stringResource(Res.string.auth_recovery_resend))
                }
                TextButton(onClick = onChangeEmail, enabled = !busy) {
                    Text(stringResource(Res.string.auth_email_change))
                }
            }
            TextButton(onClick = onChangeNumber, enabled = !busy) {
                Text(stringResource(Res.string.auth_change_number))
            }
        }
    }
}

/** Potvrzení žádosti o reset přihlašovacího e-mailu; říká, za jak dlouho proběhne. */
@Composable
private fun EmailResetDialog(waitSeconds: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.auth_email_reset_title)) },
        text = {
            Text(
                if (waitSeconds > 0) {
                    UiText(Res.string.auth_email_reset_text_wait, waitUiText(waitSeconds)).resolve()
                } else {
                    stringResource(Res.string.auth_email_reset_text_now)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.auth_email_reset_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
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
