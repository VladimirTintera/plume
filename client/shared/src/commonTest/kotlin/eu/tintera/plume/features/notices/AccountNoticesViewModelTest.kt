package eu.tintera.plume.features.notices

import eu.tintera.plume.core.telegram.AccountFreeze
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.PhoneNumberDetails
import eu.tintera.plume.core.telegram.ServiceNotice
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.TermsOfServiceUpdate
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notice_terms_accept_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Které dialogy od Telegramu se ukážou a co dělají jejich tlačítka. */
class AccountNoticesViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun oznameniJdouPoJednomOdNejstarsiho() = runTest {
        val notices = FakeNotices()
        notices.serviceNotices.value = listOf(notice(1), notice(2))
        val viewModel = AccountNoticesViewModel(notices, FakeSession())
        advanceUntilIdle()

        assertEquals(1L, viewModel.serviceNotice.value?.id)
        viewModel.dismiss(viewModel.serviceNotice.value!!)
        advanceUntilIdle()
        assertEquals(2L, viewModel.serviceNotice.value?.id)
    }

    @Test
    fun odhlaseniZahodiLokalniDataAOznameniZavre() = runTest {
        val notices = FakeNotices()
        notices.serviceNotices.value = listOf(notice(1, logOut = true))
        val session = FakeSession()
        val viewModel = AccountNoticesViewModel(notices, session)
        advanceUntilIdle()

        viewModel.logOut(viewModel.serviceNotice.value!!)
        advanceUntilIdle()

        // `destroy` (návrat na číslo), ne `logOut` — klíč relace server zahodil.
        assertEquals(1, session.restarts)
        assertEquals(0, session.logOuts)
        assertNull(viewModel.serviceNotice.value)
    }

    @Test
    fun podminkyBezPopupuSeNeukazuji() = runTest {
        val notices = FakeNotices()
        notices.termsOfService.value = terms(blocking = false)
        val viewModel = AccountNoticesViewModel(notices, FakeSession())
        advanceUntilIdle()
        assertNull(viewModel.terms.value)

        notices.termsOfService.value = terms(blocking = true)
        advanceUntilIdle()
        assertEquals("tos-1", viewModel.terms.value?.id)
    }

    @Test
    fun neprijatePodminkyZustanouSChybou() = runTest {
        val notices = FakeNotices(acceptResult = Result.failure(IllegalStateException("Something unexpected")))
        notices.termsOfService.value = terms(blocking = true)
        val viewModel = AccountNoticesViewModel(notices, FakeSession())
        advanceUntilIdle()

        viewModel.acceptTerms()
        advanceUntilIdle()

        assertEquals(UiText(Res.string.notice_terms_accept_failed), viewModel.error.value)
        assertEquals("tos-1", viewModel.terms.value?.id)

        // „Teď ne" chybu i dialog schová.
        viewModel.postponeTerms()
        advanceUntilIdle()
        assertNull(viewModel.error.value)
        assertNull(viewModel.terms.value)
    }

    private fun notice(id: Long, logOut: Boolean = false) =
        ServiceNotice(id = id, text = "Oznámení $id", entities = emptyList(), offersLogOut = logOut)

    private fun terms(blocking: Boolean) =
        TermsOfServiceUpdate(id = "tos-1", text = "Podmínky", entities = emptyList(), minUserAge = 0, blocking = blocking)
}

private class FakeNotices(private val acceptResult: Result<Unit> = Result.success(Unit)) : AccountNotices {
    override val serviceNotices = MutableStateFlow<List<ServiceNotice>>(emptyList())
    override val termsOfService = MutableStateFlow<TermsOfServiceUpdate?>(null)
    override val freeze = MutableStateFlow<AccountFreeze?>(null)

    override fun dismissServiceNotice(id: Long) = serviceNotices.update { list -> list.filterNot { it.id == id } }

    override suspend fun acceptTermsOfService(): Result<Unit> =
        acceptResult.onSuccess { termsOfService.value = null }

    override fun postponeTermsOfService() {
        termsOfService.value = null
    }
}

/** Jen odhlášení; zbytek přihlášení dialogy nevolají. */
private class FakeSession : TelegramSession {
    var restarts = 0
    var logOuts = 0

    override val authStage: StateFlow<AuthStage> = MutableStateFlow(AuthStage.Ready)
    override suspend fun restartLogin(): Result<Unit> {
        restarts++
        return Result.success(Unit)
    }
    override suspend fun logOut(): Result<Unit> {
        logOuts++
        return Result.success(Unit)
    }

    override suspend fun start() = Unit
    override suspend fun submitPhoneNumber(phoneNumber: String) = Result.success(Unit)
    override suspend fun submitCode(code: String) = Result.success(Unit)
    override suspend fun resendCode() = Result.success(Unit)
    override suspend fun submitPassword(password: String) = Result.success(Unit)
    override suspend fun submitEmailAddress(email: String) = Result.success(Unit)
    override suspend fun submitEmailCode(code: String) = Result.success(Unit)
    override suspend fun resendEmailCode() = Result.success(Unit)
    override suspend fun resetEmailAddress() = Result.success(Unit)
    override suspend fun requestPasswordRecovery() = Result.success(Unit)
    override suspend fun checkPasswordRecoveryCode(code: String) = Result.success(Unit)
    override suspend fun recoverPassword(code: String, newPassword: String, newHint: String) = Result.success(Unit)
    override suspend fun startQrLogin() = Result.success(Unit)
    override suspend fun countries(): Result<List<Country>> = Result.success(emptyList())
    override suspend fun suggestedCountryCode(): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun phoneNumberInfo(prefix: String): Result<PhoneNumberDetails> = Result.failure(UnsupportedOperationException())
    override suspend fun me(): Result<AccountInfo> = Result.failure(UnsupportedOperationException())
}
