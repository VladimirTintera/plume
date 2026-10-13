package eu.tintera.plume.features.auth

import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AuthCode
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.CodeDelivery
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.EmailReset
import eu.tintera.plume.core.telegram.PhoneNumberDetails
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_error_email_reset
import eu.tintera.plume.resources.auth_error_resend
import eu.tintera.plume.resources.error_email_not_allowed
import eu.tintera.plume.resources.settings_two_step_error_email_invalid
import eu.tintera.plume.resources.error_code_resend_unavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Obnova hesla a cesta zpátky k číslu. TDLib zná jediný stav (heslo), kroky obnovy drží
 * ViewModel — a právě jejich pořadí je to, co se dá rozbít, aniž by to překlad poznal.
 */
class AuthViewModelTest {

    // `viewModelScope` běží na `Dispatchers.Main`; bez testovacího by na iOS test visel
    // (viz `NewChatViewModelTest`).
    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bezObnovovacihoEmailuSeNicNeposilaAleVysvetli() = runTest {
        val session = FakeSession(AuthStage.WaitPassword(hint = null, canRecover = false))
        val viewModel = AuthViewModel(session)

        viewModel.forgotPassword()
        advanceUntilIdle()

        assertTrue(viewModel.recoveryUnavailable.value)
        assertEquals(0, session.recoveryRequests)
        assertEquals(PasswordStep.Password, viewModel.passwordStep.value)

        viewModel.dismissRecoveryUnavailable()
        assertFalse(viewModel.recoveryUnavailable.value)
    }

    @Test
    fun celaObnovaOdKoduPoNoveHeslo() = runTest {
        val session = FakeSession(AuthStage.WaitPassword(hint = null, canRecover = true))
        val viewModel = AuthViewModel(session)

        viewModel.forgotPassword()
        advanceUntilIdle()
        assertEquals(1, session.recoveryRequests)
        assertEquals(PasswordStep.RecoveryCode, viewModel.passwordStep.value)

        viewModel.submitInput(" 123456 ")
        assertEquals(listOf("123456"), session.checkedCodes)
        assertEquals(PasswordStep.NewPassword, viewModel.passwordStep.value)
        assertEquals("", viewModel.input)

        viewModel.onHintChange("kočka")
        viewModel.submitInput("nove heslo ")
        assertEquals(Triple("123456", "nove heslo ", "kočka"), session.recovered)
    }

    @Test
    fun spatnyKodZustaneVKrokuSChybou() = runTest {
        val session = FakeSession(AuthStage.WaitPassword(hint = null, canRecover = true))
        session.codeResult = Result.failure(IllegalStateException("CODE_INVALID"))
        val viewModel = AuthViewModel(session)
        viewModel.forgotPassword()
        advanceUntilIdle()

        viewModel.submitInput("000000")

        assertEquals(PasswordStep.RecoveryCode, viewModel.passwordStep.value)
        assertNotNull(viewModel.error.value)
        // Pole se po chybě nemaže — překlep se opravuje, ne přepisuje celý.
        assertEquals("000000", viewModel.input)
    }

    @Test
    fun napovedaStejnaJakoHesloSeNeodesle() = runTest {
        val viewModel = recoveringAt(PasswordStep.NewPassword)

        viewModel.onHintChange("tajne")
        viewModel.submitInput("tajne")

        assertNotNull(viewModel.error.value)
        assertNull(session.recovered)
    }

    @Test
    fun zpetJdeKrokZaKrokemAzKCislu() = runTest {
        val viewModel = recoveringAt(PasswordStep.NewPassword)

        viewModel.back()
        assertEquals(PasswordStep.RecoveryCode, viewModel.passwordStep.value)
        // Kód byl platný — návrat kvůli e-mailu nemá nutit psát ho znovu.
        assertEquals("123456", viewModel.input)

        viewModel.back()
        assertEquals(PasswordStep.Password, viewModel.passwordStep.value)
        assertEquals(0, session.restarts)

        viewModel.back()
        advanceUntilIdle()
        assertEquals(1, session.restarts)
    }

    @Test
    fun zKoduVedeZpetKCislu() = runTest {
        val session = FakeSession(AuthStage.WaitCode(AuthCode(5, CodeDelivery.Sms)))
        val viewModel = AuthViewModel(session)

        viewModel.back()
        advanceUntilIdle()

        assertEquals(1, session.restarts)
    }

    @Test
    fun kodJinakAzPoUplynutiLhuty() = runTest {
        val session = FakeSession(
            AuthStage.WaitCode(AuthCode(5, CodeDelivery.TelegramApp), next = CodeDelivery.Sms, resendTimeout = 3),
        )
        val viewModel = AuthViewModel(session)
        runCurrent()
        assertEquals(3, viewModel.resendIn.value)

        // Před koncem lhůty by TDLib odmítl; místo chyby se nic nepošle.
        viewModel.resendCode()
        runCurrent()
        assertEquals(0, session.resends)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, viewModel.resendIn.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, viewModel.resendIn.value)

        viewModel.onInputChange("12")
        viewModel.resendCode()
        runCurrent()
        assertEquals(1, session.resends)
        // Nový kód bude jiný, rozepsaný se zahodí.
        assertEquals("", viewModel.input)

        // Kód odešel SMS a server nabízí ještě hovor — lhůta se odpočítává znovu.
        session.stage.value = AuthStage.WaitCode(AuthCode(6, CodeDelivery.Sms), next = CodeDelivery.Call, resendTimeout = 60)
        runCurrent()
        assertEquals(60, viewModel.resendIn.value)
    }

    @Test
    fun bezDalsiCestySeKodJinakNeposila() = runTest {
        val session = FakeSession(AuthStage.WaitCode(AuthCode(5, CodeDelivery.Sms)))
        val viewModel = AuthViewModel(session)
        advanceUntilIdle()

        viewModel.resendCode()
        advanceUntilIdle()

        assertEquals(0, session.resends)
    }

    @Test
    fun chybaPriPoslaniJinakJeVetaNeHlaskaZTdlib() = runTest {
        val session = FakeSession(AuthStage.WaitCode(AuthCode(5, CodeDelivery.TelegramApp), next = CodeDelivery.Sms))
        val viewModel = AuthViewModel(session)
        advanceUntilIdle()

        session.resendResult = Result.failure(IllegalStateException("SEND_CODE_UNAVAILABLE"))
        viewModel.resendCode()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.error_code_resend_unavailable), viewModel.error.value)

        // Neznámá hláška spadne na naši větu, ne na text od TDLib.
        session.resendResult = Result.failure(IllegalStateException("Something unexpected"))
        viewModel.resendCode()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.auth_error_resend), viewModel.error.value)
    }

    @Test
    fun odchodZKoduOdpocetZrusi() = runTest {
        val session = FakeSession(
            AuthStage.WaitCode(AuthCode(5, CodeDelivery.TelegramApp), next = CodeDelivery.Sms, resendTimeout = 30),
        )
        val viewModel = AuthViewModel(session)
        runCurrent()

        session.stage.value = AuthStage.WaitPassword(hint = null)
        runCurrent()

        assertEquals(0, viewModel.resendIn.value)
    }

    @Test
    fun zCislaZpetNikamNevede() = runTest {
        val session = FakeSession(AuthStage.WaitPhoneNumber)
        val viewModel = AuthViewModel(session)

        viewModel.back()
        advanceUntilIdle()

        assertEquals(0, session.restarts)
        assertFalse(AuthStage.WaitPhoneNumber.canGoBack())
    }

    @Test
    fun proxySeNabiziJenVKrocichPrihlaseni() {
        assertTrue(AuthStage.WaitPhoneNumber.offersProxy())
        assertTrue(AuthStage.WaitPassword(hint = null).offersProxy())
        assertTrue(AuthStage.WaitRegistration.offersProxy())
        // Start klienta, odhlašování a chyba se proxy nespraví.
        assertFalse(AuthStage.Connecting.offersProxy())
        assertFalse(AuthStage.LoggingOut.offersProxy())
    }

    @Test
    fun hesloSeNeorezava() = runTest {
        val session = FakeSession(AuthStage.WaitPassword(hint = null))
        val viewModel = AuthViewModel(session)

        viewModel.submitInput(" heslo s mezerou ")

        assertEquals(listOf(" heslo s mezerou "), session.passwords)
    }

    @Test
    fun odchodZHeslaZahodiRozdelanouObnovu() = runTest {
        val viewModel = recoveringAt(PasswordStep.NewPassword)

        session.stage.value = AuthStage.Connecting
        advanceUntilIdle()
        session.stage.value = AuthStage.WaitPassword(hint = null, canRecover = true)
        advanceUntilIdle()

        assertEquals(PasswordStep.Password, viewModel.passwordStep.value)
        assertEquals("", viewModel.hint)
    }

    @Test
    fun prihlasenyUzivatelZemeNenacita() = runTest {
        val session = FakeSession(AuthStage.Ready)
        val viewModel = AuthViewModel(session)
        advanceUntilIdle()

        assertEquals(0, session.countryRequests)
        assertTrue(viewModel.countries.value.isEmpty())
    }

    @Test
    fun zemeSeNactouAzPriZadaniCisla() = runTest {
        val session = FakeSession(AuthStage.Connecting)
        session.countryList = listOf(CZECHIA, GERMANY)
        session.suggestedCode = "cz"
        val viewModel = AuthViewModel(session)
        advanceUntilIdle()
        assertEquals(0, session.countryRequests)

        session.stage.value = AuthStage.WaitPhoneNumber
        advanceUntilIdle()

        assertEquals(1, session.countryRequests)
        assertEquals(listOf(CZECHIA, GERMANY), viewModel.countries.value)
        assertEquals(CZECHIA, viewModel.phone.country)
    }

    @Test
    fun navratKCisluZemeNenacitaZnovu() = runTest {
        val session = FakeSession(AuthStage.WaitPhoneNumber)
        session.countryList = listOf(CZECHIA)
        AuthViewModel(session)
        advanceUntilIdle()

        session.stage.value = AuthStage.WaitCode(AuthCode(5, CodeDelivery.Sms))
        advanceUntilIdle()
        session.stage.value = AuthStage.WaitPhoneNumber
        advanceUntilIdle()

        assertEquals(1, session.countryRequests)
    }

    @Test
    fun rucniVolbuOdhadZemeNeprepise() = runTest {
        val session = FakeSession(AuthStage.WaitPhoneNumber)
        session.countryList = listOf(CZECHIA, GERMANY)
        session.suggestedCode = "CZ"
        val viewModel = AuthViewModel(session)

        viewModel.selectCountry(GERMANY)
        advanceUntilIdle()

        assertEquals(GERMANY, viewModel.phone.country)
    }

    @Test
    fun emailProjdeHrubymSitemAzPakJdeNaServer() = runTest {
        val session = FakeSession(AuthStage.WaitEmailAddress)
        val viewModel = AuthViewModel(session)

        viewModel.submitInput("jana.example.com")
        assertEquals(UiText(Res.string.settings_two_step_error_email_invalid), viewModel.error.value)
        assertTrue(session.emailAddresses.isEmpty())

        viewModel.submitInput(" jana@example.com ")
        assertEquals(listOf("jana@example.com"), session.emailAddresses)
        assertNull(viewModel.error.value)
    }

    @Test
    fun emailOdmitnutyServeremMaVlastniVetu() = runTest {
        val session = FakeSession(AuthStage.WaitEmailAddress)
        session.emailAddressResult = Result.failure(IllegalStateException("EMAIL_NOT_ALLOWED"))
        val viewModel = AuthViewModel(session)

        viewModel.submitInput("jana@example.com")

        assertEquals(UiText(Res.string.error_email_not_allowed), viewModel.error.value)
        assertEquals("jana@example.com", viewModel.input)
    }

    @Test
    fun kodZEmailuJdeJakoKodNeJakoAdresa() = runTest {
        val session = FakeSession(emailCode(isSetup = true))
        val viewModel = AuthViewModel(session)

        viewModel.submitInput(" 123456 ")

        assertEquals(listOf("123456"), session.emailCodes)
        assertTrue(session.emailAddresses.isEmpty())
    }

    @Test
    fun opravaAdresyNadKodemAZpetKeKodu() = runTest {
        val session = FakeSession(emailCode(isSetup = true))
        val viewModel = AuthViewModel(session)
        advanceUntilIdle()

        viewModel.changeEmail()
        assertTrue(viewModel.changingEmail.value)
        viewModel.submitInput("jana@example.org")
        assertEquals(listOf("jana@example.org"), session.emailAddresses)
        assertTrue(session.emailCodes.isEmpty())
        // Úspěch opravu zavře i bez nového stavu — `StateFlow` stejný stav znovu nepošle.
        assertFalse(viewModel.changingEmail.value)

        viewModel.changeEmail()
        viewModel.back()
        advanceUntilIdle()
        assertFalse(viewModel.changingEmail.value)
        assertEquals(0, session.restarts)

        // Z kódu samotného vede zpět k číslu.
        viewModel.back()
        advanceUntilIdle()
        assertEquals(1, session.restarts)
    }

    @Test
    fun drivejsiPrihlasovaciEmailNejdeOpravitAniObeslat() = runTest {
        val session = FakeSession(emailCode(isSetup = false))
        val viewModel = AuthViewModel(session)
        advanceUntilIdle()

        viewModel.changeEmail()
        viewModel.resendEmailCode()
        advanceUntilIdle()

        assertFalse(viewModel.changingEmail.value)
        assertEquals(0, session.emailResends)
    }

    @Test
    fun zazadanyResetCekaNaUplynutiLhuty() = runTest {
        val session = FakeSession(emailCode(isSetup = false, reset = EmailReset.Pending(resetInSeconds = 2)))
        val viewModel = AuthViewModel(session)
        runCurrent()
        assertEquals(2, viewModel.emailResetIn.value)

        viewModel.resetEmail()
        runCurrent()
        assertEquals(0, session.emailResets)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(0, viewModel.emailResetIn.value)

        viewModel.resetEmail()
        runCurrent()
        assertEquals(1, session.emailResets)
    }

    @Test
    fun resetBezNabidkyServeruSeNeposila() = runTest {
        val session = FakeSession(emailCode(isSetup = false, reset = null))
        val viewModel = AuthViewModel(session)
        advanceUntilIdle()

        viewModel.resetEmail()
        advanceUntilIdle()

        assertEquals(0, session.emailResets)
    }

    @Test
    fun chybaResetuJeNaseVeta() = runTest {
        val session = FakeSession(emailCode(isSetup = false, reset = EmailReset.Available(waitSeconds = 604_800)))
        session.emailResetResult = Result.failure(IllegalStateException("Something unexpected"))
        val viewModel = AuthViewModel(session)
        advanceUntilIdle()

        viewModel.resetEmail()
        advanceUntilIdle()

        assertEquals(1, session.emailResets)
        assertEquals(UiText(Res.string.auth_error_email_reset), viewModel.error.value)
    }

    @Test
    fun emailoveKrokyVedouZpetANabizejiProxy() {
        assertTrue(AuthStage.WaitEmailAddress.canGoBack())
        assertTrue(emailCode(isSetup = false).canGoBack())
        assertTrue(AuthStage.WaitEmailAddress.offersProxy())
        assertTrue(emailCode(isSetup = true).offersProxy())
    }

    private fun emailCode(isSetup: Boolean, reset: EmailReset? = null) =
        AuthStage.WaitEmailCode(emailPattern = "j***@e***.com", digits = 6, isSetup = isSetup, reset = reset)

    private lateinit var session: FakeSession

    /** ViewModel v daném kroku obnovy, s uznaným kódem „123456". */
    private fun TestScope.recoveringAt(step: PasswordStep): AuthViewModel {
        session = FakeSession(AuthStage.WaitPassword(hint = null, canRecover = true))
        val viewModel = AuthViewModel(session)
        viewModel.forgotPassword()
        advanceUntilIdle()
        if (step == PasswordStep.NewPassword) viewModel.submitInput("123456")
        assertEquals(step, viewModel.passwordStep.value)
        return viewModel
    }

    private fun AuthViewModel.submitInput(value: String) {
        onInputChange(value)
        submit()
        main.scheduler.advanceUntilIdle()
    }
}

private class FakeSession(initial: AuthStage) : TelegramSession {
    val stage = MutableStateFlow(initial)
    override val authStage: StateFlow<AuthStage> = stage

    var recoveryRequests = 0
    val checkedCodes = mutableListOf<String>()
    var codeResult: Result<Unit> = Result.success(Unit)
    var recovered: Triple<String, String, String>? = null
    val passwords = mutableListOf<String>()
    var restarts = 0

    override suspend fun start() = Unit
    override suspend fun submitPhoneNumber(phoneNumber: String) = Result.success(Unit)
    override suspend fun submitCode(code: String) = Result.success(Unit)

    var resends = 0
    var resendResult: Result<Unit> = Result.success(Unit)
    override suspend fun resendCode(): Result<Unit> {
        resends++
        return resendResult
    }

    val emailAddresses = mutableListOf<String>()
    var emailAddressResult: Result<Unit> = Result.success(Unit)
    override suspend fun submitEmailAddress(email: String): Result<Unit> {
        emailAddresses += email
        return emailAddressResult
    }

    val emailCodes = mutableListOf<String>()
    override suspend fun submitEmailCode(code: String): Result<Unit> {
        emailCodes += code
        return Result.success(Unit)
    }

    var emailResends = 0
    override suspend fun resendEmailCode(): Result<Unit> {
        emailResends++
        return Result.success(Unit)
    }

    var emailResets = 0
    var emailResetResult: Result<Unit> = Result.success(Unit)
    override suspend fun resetEmailAddress(): Result<Unit> {
        emailResets++
        return emailResetResult
    }

    override suspend fun submitPassword(password: String): Result<Unit> {
        passwords += password
        return Result.success(Unit)
    }

    override suspend fun requestPasswordRecovery(): Result<Unit> {
        recoveryRequests++
        return Result.success(Unit)
    }

    override suspend fun checkPasswordRecoveryCode(code: String): Result<Unit> {
        checkedCodes += code
        return codeResult
    }

    override suspend fun recoverPassword(code: String, newPassword: String, newHint: String): Result<Unit> {
        recovered = Triple(code, newPassword, newHint)
        return Result.success(Unit)
    }

    override suspend fun startQrLogin() = Result.success(Unit)
    override suspend fun restartLogin(): Result<Unit> {
        restarts++
        return Result.success(Unit)
    }

    var countryRequests = 0
    var countryList: List<Country> = emptyList()
    var suggestedCode: String? = null

    override suspend fun countries(): Result<List<Country>> {
        countryRequests++
        return Result.success(countryList)
    }

    override suspend fun suggestedCountryCode(): Result<String> =
        suggestedCode?.let { Result.success(it) } ?: Result.failure(UnsupportedOperationException())
    override suspend fun phoneNumberInfo(prefix: String): Result<PhoneNumberDetails> =
        Result.failure(UnsupportedOperationException())

    override suspend fun logOut() = Result.success(Unit)
    override suspend fun me(): Result<AccountInfo> = Result.failure(UnsupportedOperationException())
}

private val CZECHIA = Country(code = "CZ", name = "Czechia", englishName = "Czechia", flagEmoji = "", callingCode = "+420")
private val GERMANY = Country(code = "DE", name = "Germany", englishName = "Germany", flagEmoji = "", callingCode = "+49")
