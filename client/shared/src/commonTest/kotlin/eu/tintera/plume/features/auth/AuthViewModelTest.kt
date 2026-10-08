package eu.tintera.plume.features.auth

import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AuthCode
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.CodeDelivery
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.PhoneNumberDetails
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
        assertEquals("", viewModel.input.value)

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
        assertEquals("000000", viewModel.input.value)
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
        assertEquals("123456", viewModel.input.value)

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
        assertEquals("", viewModel.hint.value)
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
        assertEquals(CZECHIA, viewModel.phone.value.country)
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

        assertEquals(GERMANY, viewModel.phone.value.country)
    }

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
