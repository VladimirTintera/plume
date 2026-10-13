package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.ContactLink
import eu.tintera.plume.core.telegram.EditableProfile
import eu.tintera.plume.core.telegram.EmailCodeInfo
import eu.tintera.plume.core.telegram.PasswordRepository
import eu.tintera.plume.core.telegram.PasswordResetResult
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import eu.tintera.plume.core.telegram.TwoStepStatus
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_password_invalid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

/** Smazání účtu: heslo jen s dvoufázovým ověřením, poslední potvrzení, chyby. */
class DeleteAccountViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun bezHeslaSeMazePoPotvrzeniSPrazdnymHeslem() = runTest {
        val accounts = DeleteAccountAccounts()
        val viewModel = DeleteAccountViewModel(accounts, DeleteAccountPasswords(hasPassword = false))
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.needsPassword)
        assertTrue(viewModel.canDelete)

        viewModel.onReasonChange("  už nechci  ")
        viewModel.requestDelete()
        assertTrue(viewModel.state.value.confirming)
        assertTrue(accounts.deletions.isEmpty(), "Bez potvrzení se nic nemaže.")

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("už nechci" to ""), accounts.deletions)
        // Klient se zavírá a aplikace přejde na přihlášení sama; tlačítko zůstává zaneprázdněné.
        assertTrue(viewModel.state.value.busy)
        assertFalse(viewModel.state.value.confirming)
    }

    @Test
    fun sDvoufazovymOverenimBezHeslaSmazatNejde() = runTest {
        val accounts = DeleteAccountAccounts()
        val viewModel = DeleteAccountViewModel(accounts, DeleteAccountPasswords(hasPassword = true))
        advanceUntilIdle()

        assertFalse(viewModel.canDelete)
        viewModel.requestDelete()
        assertFalse(viewModel.state.value.confirming)

        viewModel.onPasswordChange("tajne heslo ")
        assertTrue(viewModel.canDelete)
        viewModel.requestDelete()
        viewModel.confirmDelete()
        advanceUntilIdle()

        // Heslo se neořezává (mezera na konci k němu patří), jako všude v dvoufázovém ověření.
        assertEquals(listOf("" to "tajne heslo "), accounts.deletions)
    }

    @Test
    fun spatneHesloMaVlastniVetuAOpravaJiSmaze() = runTest {
        val accounts = DeleteAccountAccounts(result = Result.failure(IllegalStateException("PASSWORD_HASH_INVALID")))
        val viewModel = DeleteAccountViewModel(accounts, DeleteAccountPasswords(hasPassword = true))
        advanceUntilIdle()
        viewModel.onPasswordChange("spatne")
        viewModel.requestDelete()
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(UiText(Res.string.error_password_invalid), viewModel.state.value.error)
        assertFalse(viewModel.state.value.busy)

        viewModel.onPasswordChange("spravne")
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun bezZjistenehoHeslaSmazatNejdeDokudSeToNepovede() = runTest {
        val passwords = DeleteAccountPasswords(hasPassword = false, fails = true)
        val viewModel = DeleteAccountViewModel(DeleteAccountAccounts(), passwords)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.statusFailed)
        assertNull(viewModel.state.value.needsPassword)
        assertFalse(viewModel.canDelete)

        passwords.fails = false
        viewModel.loadStatus()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.statusFailed)
        assertTrue(viewModel.canDelete)
    }

    @Test
    fun samosmazaniPoznaMesiceIZHodnotyServeru() {
        assertEquals(listOf(1, 3, 6, 12, 18, 24), ACCOUNT_TTL_OPTIONS.map { accountTtlMonths(it) })
        // Server vrací i 180 a 183 dní: pořád „6 měsíců", ne další volba vedle našich 182.
        assertEquals(6, accountTtlMonths(180))
        assertEquals(6, accountTtlMonths(183))
        assertNull(accountTtlMonths(100))
    }
}

private class DeleteAccountAccounts(private val result: Result<Unit> = Result.success(Unit)) : AccountRepository {
    /** Důvod a heslo každého pokusu. */
    val deletions = mutableListOf<Pair<String, String>>()

    override suspend fun deleteAccount(reason: String, password: String): Result<Unit> {
        deletions += reason to password
        return result
    }

    override val account: Flow<AccountInfo?> = flowOf(null)
    override suspend fun editableProfile(): Result<EditableProfile> = Result.failure(UnsupportedOperationException())
    override suspend fun setName(firstName: String, lastName: String) = Result.success(Unit)
    override suspend fun setUsername(username: String) = Result.success(Unit)
    override suspend fun checkUsername(username: String): Result<UsernameCheck> = Result.failure(UnsupportedOperationException())
    override suspend fun setBio(bio: String) = Result.success(Unit)
    override suspend fun setPhoto(photo: ProfilePhotoFile) = Result.success(Unit)
    override suspend fun deletePhoto() = Result.success(Unit)
    override suspend fun contactLink(): Result<ContactLink> = Result.failure(UnsupportedOperationException())
}

private class DeleteAccountPasswords(private val hasPassword: Boolean, var fails: Boolean = false) : PasswordRepository {
    override suspend fun status(): Result<TwoStepStatus> =
        if (fails) {
            Result.failure(IllegalStateException("network"))
        } else {
            Result.success(TwoStepStatus(hasPassword = hasPassword, hint = "", hasRecoveryEmail = false, hasPassportData = false, pendingEmail = null, resetDate = 0))
        }

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
