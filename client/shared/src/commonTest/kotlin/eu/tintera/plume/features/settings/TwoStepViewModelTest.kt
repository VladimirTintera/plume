package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.EmailCodeInfo
import eu.tintera.plume.core.telegram.PasswordRepository
import eu.tintera.plume.core.telegram.PasswordResetResult
import eu.tintera.plume.core.telegram.TwoStepStatus
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_two_step_done_enabled
import eu.tintera.plume.resources.settings_two_step_error_hint_reveals
import eu.tintera.plume.resources.settings_two_step_error_mismatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

/**
 * Průvodce dvoufázového ověření: kdy se volá server, kdy se ptá dialogem a že hesla po konci
 * průvodce ve ViewModelu nezůstanou.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TwoStepViewModelTest {

    private class FakePasswords(var status: TwoStepStatus) : PasswordRepository {
        val calls = mutableListOf<String>()
        var resetResult: PasswordResetResult = PasswordResetResult.Done

        override suspend fun status() = Result.success(status)
        override suspend fun recoveryEmail(password: String): Result<String> {
            calls += "recoveryEmail"
            return Result.success("j***@example.cz")
        }

        override suspend fun setPassword(oldPassword: String, newPassword: String, hint: String, newEmail: String?): Result<TwoStepStatus> {
            calls += "setPassword(email=${newEmail != null})"
            status = if (newEmail != null) {
                status.copy(pendingEmail = EmailCodeInfo("j***@example.cz", 6))
            } else {
                status.copy(hasPassword = newPassword.isNotEmpty(), hint = hint)
            }
            return Result.success(status)
        }

        override suspend fun setRecoveryEmail(password: String, email: String) = Result.success(status)
        override suspend fun checkEmailCode(code: String): Result<TwoStepStatus> {
            calls += "checkEmailCode"
            status = status.copy(hasPassword = true, hasRecoveryEmail = true, pendingEmail = null)
            return Result.success(status)
        }

        override suspend fun resendEmailCode() = Result.success(status)
        override suspend fun cancelEmailVerification() = Result.success(status.copy(pendingEmail = null))
        override suspend fun requestRecovery() = Result.success(EmailCodeInfo("j***@example.cz", 6))
        override suspend fun checkRecoveryCode(code: String) = Result.success(Unit)
        override suspend fun recover(code: String, newPassword: String, hint: String) = Result.success(status)
        override suspend fun reset(): Result<PasswordResetResult> {
            calls += "reset"
            return Result.success(resetResult)
        }

        override suspend fun cancelReset() = Result.success(Unit)
    }

    private val off = TwoStepStatus(
        hasPassword = false,
        hint = "",
        hasRecoveryEmail = false,
        hasPassportData = false,
        pendingEmail = null,
        resetDate = 0,
    )

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TwoStepViewModel.fillPassword(password: String = "tajne heslo", repeat: String = password) {
        onNewPasswordChange(password)
        onRepeatPasswordChange(repeat)
        submit()
    }

    @Test
    fun zapnutiBezEmailuSeNejdrivZepta() = runTest {
        val repository = FakePasswords(off)
        val viewModel = TwoStepViewModel(repository)
        advanceUntilIdle()

        viewModel.start(TwoStepAction.Enable)
        viewModel.fillPassword()
        viewModel.onHintChange("pes")
        viewModel.submit()
        assertEquals(TwoStepStep.Email, viewModel.state.value.flow?.step)

        viewModel.submit()
        assertEquals(TwoStepDialog.SkipEmail, viewModel.state.value.dialog)
        assertTrue(repository.calls.isEmpty(), "bez potvrzení se na server nic nepošle")

        viewModel.confirmDialog()
        advanceUntilIdle()
        assertEquals(listOf("setPassword(email=false)"), repository.calls)
        assertNull(viewModel.state.value.flow)
        assertEquals(true, viewModel.state.value.status?.hasPassword)
        assertEquals(UiText(Res.string.settings_two_step_done_enabled), viewModel.state.value.notice)
        assertEquals("", viewModel.newPassword, "heslo po konci průvodce ve ViewModelu nezůstane")
    }

    @Test
    fun zapnutiSEmailemPokracujeKodem() = runTest {
        val repository = FakePasswords(off)
        val viewModel = TwoStepViewModel(repository)
        advanceUntilIdle()

        viewModel.start(TwoStepAction.Enable)
        viewModel.fillPassword()
        viewModel.submit() // nápověda prázdná
        viewModel.onEmailChange("jana@example.cz")
        viewModel.submit()
        advanceUntilIdle()

        val flow = viewModel.state.value.flow
        assertEquals(TwoStepStep.EmailCode, flow?.step)
        assertEquals("j***@example.cz", flow?.codePattern)

        viewModel.onCodeChange("123456")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(listOf("setPassword(email=true)", "checkEmailCode"), repository.calls)
        assertNull(viewModel.state.value.flow)
        assertEquals(UiText(Res.string.settings_two_step_done_enabled), viewModel.state.value.notice)
    }

    @Test
    fun hesloSeMusiShodovat() = runTest {
        val viewModel = TwoStepViewModel(FakePasswords(off))
        advanceUntilIdle()

        viewModel.start(TwoStepAction.Enable)
        viewModel.fillPassword(password = "tajne", repeat = "tajny")
        assertEquals(TwoStepStep.NewPassword, viewModel.state.value.flow?.step)
        assertEquals(UiText(Res.string.settings_two_step_error_mismatch), viewModel.state.value.error)
    }

    @Test
    fun napovedaNesmiProzraditHeslo() = runTest {
        val viewModel = TwoStepViewModel(FakePasswords(off))
        advanceUntilIdle()

        viewModel.start(TwoStepAction.Enable)
        viewModel.fillPassword("kocka")
        viewModel.onHintChange("kocka")
        viewModel.submit()
        assertEquals(TwoStepStep.Hint, viewModel.state.value.flow?.step)
        assertEquals(UiText(Res.string.settings_two_step_error_hint_reveals), viewModel.state.value.error)
    }

    @Test
    fun vypnutiOveriHesloAPakSeZepta() = runTest {
        val repository = FakePasswords(off.copy(hasPassword = true))
        val viewModel = TwoStepViewModel(repository)
        advanceUntilIdle()

        viewModel.start(TwoStepAction.Disable)
        viewModel.onCurrentPasswordChange("tajne")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(listOf("recoveryEmail"), repository.calls)
        assertEquals(TwoStepDialog.Disable, viewModel.state.value.dialog)

        viewModel.confirmDialog()
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.status?.hasPassword)
        assertEquals("", viewModel.currentPassword)
    }

    @Test
    fun zapomenuteHesloBezEmailuNabidneReset() = runTest {
        val repository = FakePasswords(off.copy(hasPassword = true))
        repository.resetResult = PasswordResetResult.Declined(retryAt = 999)
        val viewModel = TwoStepViewModel(repository)
        advanceUntilIdle()

        viewModel.start(TwoStepAction.Recover)
        assertNull(viewModel.state.value.flow)
        assertEquals(TwoStepDialog.Reset, viewModel.state.value.dialog)

        viewModel.confirmDialog()
        advanceUntilIdle()
        assertEquals(listOf("reset"), repository.calls)
        assertEquals(999L, viewModel.state.value.resetDeclinedUntil)
    }

    @Test
    fun zpetVPruvodciVraciKrok() = runTest {
        val viewModel = TwoStepViewModel(FakePasswords(off))
        advanceUntilIdle()

        viewModel.start(TwoStepAction.Enable)
        viewModel.fillPassword()
        assertEquals(TwoStepStep.Hint, viewModel.state.value.flow?.step)
        viewModel.back()
        assertEquals(TwoStepStep.NewPassword, viewModel.state.value.flow?.step)
        viewModel.back()
        assertNull(viewModel.state.value.flow)
        assertEquals("", viewModel.newPassword)
    }
}
