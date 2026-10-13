package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.AccountFreeze
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.EmailCodeInfo
import eu.tintera.plume.core.telegram.PasswordRepository
import eu.tintera.plume.core.telegram.PasswordResetResult
import eu.tintera.plume.core.telegram.ServiceNotice
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.core.telegram.TermsOfServiceUpdate
import eu.tintera.plume.core.telegram.TwoStepStatus
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_recovery_code_invalid
import eu.tintera.plume.resources.settings_two_step_error_email_invalid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Přihlašovací e-mail: adresa → kód → hotovo. */
class LoginEmailViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun bezEmailuSeNapredZeptaZdaHoTelegramChce() = runTest {
        val passwords = FakePasswords(pattern = null)
        newViewModel(passwords)
        advanceUntilIdle()
        assertEquals(1, passwords.requiredChecks)

        val withEmail = FakePasswords(pattern = "j***@e***.cz")
        val viewModel = newViewModel(withEmail)
        advanceUntilIdle()
        assertEquals(0, withEmail.requiredChecks)
        assertEquals("j***@e***.cz", viewModel.state.value.currentPattern)
    }

    @Test
    fun adresaProjdeSitemAzPakJdeNaServer() = runTest {
        val passwords = FakePasswords()
        val viewModel = newViewModel(passwords)
        advanceUntilIdle()

        viewModel.onEmailChange("jana.example.cz")
        viewModel.submitEmail()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.settings_two_step_error_email_invalid), viewModel.state.value.error)
        assertTrue(passwords.emails.isEmpty())

        viewModel.onEmailChange(" jana@example.cz ")
        viewModel.submitEmail()
        advanceUntilIdle()
        assertEquals(listOf("jana@example.cz"), passwords.emails)
        assertEquals("j***@e***.cz", viewModel.state.value.pendingCode?.pattern)
    }

    @Test
    fun spatnyKodZustaneVKrokuASpravnyToDokonci() = runTest {
        val passwords = FakePasswords()
        val notices = FakeNotices(listOf(SuggestedAction.SetLoginEmail(canBeHidden = false), SuggestedAction.CheckPassword))
        val viewModel = newViewModel(passwords, notices)
        advanceUntilIdle()
        viewModel.onEmailChange("jana@example.cz")
        viewModel.submitEmail()
        advanceUntilIdle()

        viewModel.onCodeChange("000000")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.error_recovery_code_invalid), viewModel.state.value.error)
        assertFalse(viewModel.state.value.done)

        viewModel.onCodeChange("123456")
        viewModel.submitCode()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.done)
        // Splněná doporučená akce se schová; jiné ne.
        assertEquals(listOf<SuggestedAction>(SuggestedAction.SetLoginEmail(canBeHidden = false)), notices.hidden)
    }

    @Test
    fun zpetZKoduVedeKAdreseAPakPryc() = runTest {
        val viewModel = newViewModel(FakePasswords())
        advanceUntilIdle()
        viewModel.onEmailChange("jana@example.cz")
        viewModel.submitEmail()
        advanceUntilIdle()

        assertTrue(viewModel.back())
        assertNull(viewModel.state.value.pendingCode)
        // Adresa v poli zůstala — návrat bývá kvůli překlepu v ní.
        assertEquals("jana@example.cz", viewModel.email)
        assertFalse(viewModel.back())
    }

    private fun newViewModel(passwords: PasswordRepository, notices: AccountNotices? = null) =
        LoginEmailViewModel(passwords, notices)
}

private class FakePasswords(private val pattern: String? = null) : PasswordRepository {
    var requiredChecks = 0
    val emails = mutableListOf<String>()

    override suspend fun status() = Result.success(
        TwoStepStatus(hasPassword = false, hint = "", hasRecoveryEmail = false, hasPassportData = false, pendingEmail = null, resetDate = 0, loginEmailPattern = pattern),
    )
    override suspend fun isLoginEmailRequired(): Result<Boolean> {
        requiredChecks++
        return Result.success(true)
    }
    override suspend fun setLoginEmail(email: String): Result<EmailCodeInfo> {
        emails += email
        return Result.success(EmailCodeInfo(pattern = "j***@e***.cz", length = 6))
    }
    override suspend fun checkLoginEmailCode(code: String): Result<Unit> =
        if (code == "123456") Result.success(Unit) else Result.failure(IllegalStateException("CODE_INVALID"))
    override suspend fun resendLoginEmailCode() = Result.success(EmailCodeInfo(pattern = "j***@e***.cz", length = 6))

    override suspend fun recoveryEmail(password: String) = Result.success("")
    override suspend fun setPassword(oldPassword: String, newPassword: String, hint: String, newEmail: String?) = status()
    override suspend fun setRecoveryEmail(password: String, email: String) = status()
    override suspend fun checkEmailCode(code: String) = status()
    override suspend fun resendEmailCode() = status()
    override suspend fun cancelEmailVerification() = status()
    override suspend fun requestRecovery() = Result.success(EmailCodeInfo("", 0))
    override suspend fun checkRecoveryCode(code: String) = Result.success(Unit)
    override suspend fun recover(code: String, newPassword: String, hint: String) = status()
    override suspend fun reset(): Result<PasswordResetResult> = Result.failure(UnsupportedOperationException())
    override suspend fun cancelReset() = Result.success(Unit)
}

private class FakeNotices(actions: List<SuggestedAction>) : AccountNotices {
    val hidden = mutableListOf<SuggestedAction>()
    override val serviceNotices = MutableStateFlow<List<ServiceNotice>>(emptyList())
    override val termsOfService = MutableStateFlow<TermsOfServiceUpdate?>(null)
    override val freeze = MutableStateFlow<AccountFreeze?>(null)
    override val suggestedActions = MutableStateFlow(actions)
    override fun dismissServiceNotice(id: Long) = Unit
    override suspend fun acceptTermsOfService() = Result.success(Unit)
    override fun postponeTermsOfService() = Unit
    override suspend fun hideSuggestedAction(action: SuggestedAction): Result<Unit> {
        hidden += action
        return Result.success(Unit)
    }
}
