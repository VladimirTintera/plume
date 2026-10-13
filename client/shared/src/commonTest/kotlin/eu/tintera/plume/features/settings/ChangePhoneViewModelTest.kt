package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.AccountFreeze
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.AuthCode
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.CodeDelivery
import eu.tintera.plume.core.telegram.ContactLink
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.EditableProfile
import eu.tintera.plume.core.telegram.PhoneChangeCode
import eu.tintera.plume.core.telegram.PhoneNumberDetails
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import eu.tintera.plume.core.telegram.ServiceNotice
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.TermsOfServiceUpdate
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_phone_code_invalid
import eu.tintera.plume.resources.error_phone_number_occupied
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Změna telefonního čísla: číslo → kód → hotovo. */
class ChangePhoneViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun obsazeneCisloMaVlastniVetu() = runTest {
        val accounts = ChangePhoneAccounts(sendResult = Result.failure(IllegalStateException("PHONE_NUMBER_OCCUPIED")))
        val viewModel = ChangePhoneViewModel(accounts, ChangePhoneSession())
        advanceUntilIdle()
        viewModel.onPhoneChange("+420777123456")
        advanceUntilIdle()

        viewModel.submitPhone()
        advanceUntilIdle()

        assertEquals(UiText(Res.string.error_phone_number_occupied), viewModel.state.value.error)
        assertNull(viewModel.state.value.sent)
    }

    @Test
    fun kodNaNoveCisloASpravnyKodZmenuDokonci() = runTest {
        val accounts = ChangePhoneAccounts()
        val notices = ChangePhoneNotices(listOf(SuggestedAction.CheckPhoneNumber, SuggestedAction.CheckPassword))
        val viewModel = ChangePhoneViewModel(accounts, ChangePhoneSession(), notices)
        advanceUntilIdle()
        viewModel.onPhoneChange("+420777123456")
        advanceUntilIdle()

        viewModel.submitPhone()
        advanceUntilIdle()
        assertEquals(listOf("+420777123456"), accounts.sentTo)
        assertEquals(5, viewModel.state.value.sent?.code?.digits)

        viewModel.onCodeChange("00000")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.error_phone_code_invalid), viewModel.state.value.error)
        assertFalse(viewModel.state.value.done)

        viewModel.onCodeChange("12345")
        viewModel.submitCode()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.done)
        // „Je … pořád tvoje číslo?" je vyřízené; jiné akce zůstanou.
        assertEquals(listOf<SuggestedAction>(SuggestedAction.CheckPhoneNumber), notices.hidden)
    }

    @Test
    fun kodJinakAzPoLhute() = runTest {
        val accounts = ChangePhoneAccounts()
        val viewModel = ChangePhoneViewModel(accounts, ChangePhoneSession())
        advanceUntilIdle()
        viewModel.onPhoneChange("+420777123456")
        advanceUntilIdle()
        viewModel.submitPhone()
        runCurrent()
        assertEquals(2, viewModel.state.value.resendIn)

        viewModel.resendCode()
        runCurrent()
        assertEquals(0, accounts.resends)

        advanceTimeBy(2_000)
        runCurrent()
        viewModel.resendCode()
        runCurrent()
        assertEquals(1, accounts.resends)
    }

    @Test
    fun zpetZKoduVedeKCislu() = runTest {
        val viewModel = ChangePhoneViewModel(ChangePhoneAccounts(), ChangePhoneSession())
        advanceUntilIdle()
        viewModel.onPhoneChange("+420777123456")
        advanceUntilIdle()
        viewModel.submitPhone()
        advanceUntilIdle()

        assertTrue(viewModel.back())
        assertNull(viewModel.state.value.sent)
        assertFalse(viewModel.back())
    }
}

private class ChangePhoneAccounts(
    private val sendResult: Result<PhoneChangeCode> =
        Result.success(PhoneChangeCode(AuthCode(5, CodeDelivery.Sms), next = CodeDelivery.Call, resendTimeout = 2)),
) : AccountRepository {
    val sentTo = mutableListOf<String>()
    var resends = 0

    override suspend fun sendPhoneChangeCode(phoneNumber: String): Result<PhoneChangeCode> {
        sentTo += phoneNumber
        return sendResult
    }
    override suspend fun resendPhoneChangeCode(): Result<PhoneChangeCode> {
        resends++
        return sendResult
    }
    override suspend fun checkPhoneChangeCode(code: String): Result<Unit> =
        if (code == "12345") Result.success(Unit) else Result.failure(IllegalStateException("PHONE_CODE_INVALID"))

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

/** Jen to, co potřebuje zadání čísla ([eu.tintera.plume.core.ui.PhoneInput]). */
private class ChangePhoneSession : TelegramSession {
    private val czechia = Country(code = "CZ", name = "Česko", englishName = "Czechia", flagEmoji = "", callingCode = "+420")
    override val authStage: StateFlow<AuthStage> = MutableStateFlow(AuthStage.Ready)
    override suspend fun countries(): Result<List<Country>> = Result.success(listOf(czechia))
    override suspend fun suggestedCountryCode(): Result<String> = Result.success("CZ")
    override suspend fun phoneNumberInfo(prefix: String): Result<PhoneNumberDetails> =
        Result.success(PhoneNumberDetails(country = czechia, callingCode = "420", mask = "777 123 456"))

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
    override suspend fun restartLogin() = Result.success(Unit)
    override suspend fun logOut() = Result.success(Unit)
    override suspend fun me(): Result<AccountInfo> = Result.failure(UnsupportedOperationException())
}

private class ChangePhoneNotices(actions: List<SuggestedAction>) : AccountNotices {
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
