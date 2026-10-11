package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatCreation
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.CreatedChat
import eu.tintera.plume.core.telegram.PhoneNumberDetails
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.new_contact_failed
import eu.tintera.plume.resources.new_contact_not_found
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * Nový kontakt: kdy jde přidat, co odejde do TDLib a jak se ohlásí číslo, na kterém nikdo není.
 * Číslo na Telegramu bez účtu není chyba spojení — věta to musí říct jinak než selhání.
 */
class NewContactViewModelTest {

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
    fun bezJmenaNeboCislaNejdePridat() = runTest {
        val creation = FakeCreation()
        val viewModel = NewContactViewModel(creation, FakeSession())
        advanceUntilIdle()

        viewModel.onPhoneChange("601987654")
        assertFalse(viewModel.canAdd)
        viewModel.add()
        advanceUntilIdle()
        assertTrue(creation.added.isEmpty())

        viewModel.onPhoneChange("")
        viewModel.onFirstNameChange("Jana")
        assertFalse(viewModel.canAdd)

        viewModel.onFirstNameChange("   ")
        viewModel.onPhoneChange("601987654")
        assertFalse(viewModel.canAdd, "Jméno jen z mezer TDLib nevezme")
    }

    @Test
    fun nalezenyKontaktOtevreChat() = runTest {
        val creation = FakeCreation(result = Result.success(42L))
        val viewModel = NewContactViewModel(creation, FakeSession())
        advanceUntilIdle()

        viewModel.onFirstNameChange(" Jana ")
        viewModel.onLastNameChange("Nováková ")
        viewModel.onPhoneChange("601 987 654")
        viewModel.add()
        advanceUntilIdle()

        assertEquals(listOf(Triple("+420601987654", "Jana", "Nováková")), creation.added)
        assertEquals(42L, viewModel.added)
        assertNull(viewModel.error)
        assertFalse(viewModel.adding)
    }

    @Test
    fun cisloBezUctuJeVetaNeChyba() = runTest {
        val viewModel = NewContactViewModel(FakeCreation(result = Result.success(null)), FakeSession())
        advanceUntilIdle()

        viewModel.onFirstNameChange("Jana")
        viewModel.onPhoneChange("601987654")
        viewModel.add()
        advanceUntilIdle()

        assertEquals(UiText(Res.string.new_contact_not_found), viewModel.error)
        assertNull(viewModel.added)

        // Oprava čísla hlášku smaže — patřila k tomu předchozímu.
        viewModel.onPhoneChange("732375028")
        assertNull(viewModel.error)
    }

    @Test
    fun selhaniSpadneNaVetuOdNas() = runTest {
        val viewModel = NewContactViewModel(
            FakeCreation(result = Result.failure(IllegalStateException("Connection closed"))),
            FakeSession(),
        )
        advanceUntilIdle()

        viewModel.onFirstNameChange("Jana")
        viewModel.onPhoneChange("601987654")
        viewModel.add()
        advanceUntilIdle()

        assertEquals(UiText(Res.string.new_contact_failed), viewModel.error)
        assertNull(viewModel.added)
        assertFalse(viewModel.adding)
    }

    @Test
    fun vlozeneMezinarodniCisloVybereZemi() = runTest {
        val viewModel = NewContactViewModel(FakeCreation(), FakeSession(suggested = null))
        advanceUntilIdle()
        assertNull(viewModel.phone.country)

        viewModel.onPhoneChange("+420 601 987 654")
        advanceUntilIdle()

        assertEquals(CZECHIA, viewModel.phone.country)
        assertEquals("601987654", viewModel.phone.digits)
        assertEquals("+420601987654", viewModel.phone.international)
    }
}

private class FakeCreation(private val result: Result<Long?> = Result.success(1L)) : ChatCreation {
    val added = mutableListOf<Triple<String, String, String>>()

    override suspend fun addContact(phoneNumber: String, firstName: String, lastName: String): Result<Long?> {
        added += Triple(phoneNumber, firstName, lastName)
        return result
    }

    override suspend fun create(
        title: String,
        description: String,
        channel: Boolean,
        memberChatIds: List<Long>,
        username: String,
    ): Result<CreatedChat> = Result.failure(UnsupportedOperationException())

    override suspend fun checkUsername(username: String): Result<UsernameCheck> =
        Result.failure(UnsupportedOperationException())
}

/** Jen země a rozbor čísla; zbytek přihlášení nový kontakt nevolá. */
private class FakeSession(private val suggested: String? = "CZ") : TelegramSession {
    override val authStage: StateFlow<AuthStage> = MutableStateFlow(AuthStage.Ready)

    override suspend fun countries(): Result<List<Country>> = Result.success(listOf(CZECHIA))
    override suspend fun suggestedCountryCode(): Result<String> =
        suggested?.let { Result.success(it) } ?: Result.failure(UnsupportedOperationException())

    override suspend fun phoneNumberInfo(prefix: String): Result<PhoneNumberDetails> =
        if (prefix.startsWith(CZECHIA.callingCode)) {
            Result.success(PhoneNumberDetails(country = CZECHIA, callingCode = "420", mask = "--- --- ---"))
        } else {
            Result.failure(UnsupportedOperationException())
        }

    override suspend fun start() = Unit
    override suspend fun submitPhoneNumber(phoneNumber: String) = Result.success(Unit)
    override suspend fun submitCode(code: String) = Result.success(Unit)
    override suspend fun resendCode() = Result.success(Unit)
    override suspend fun submitPassword(password: String) = Result.success(Unit)
    override suspend fun requestPasswordRecovery() = Result.success(Unit)
    override suspend fun checkPasswordRecoveryCode(code: String) = Result.success(Unit)
    override suspend fun recoverPassword(code: String, newPassword: String, newHint: String) = Result.success(Unit)
    override suspend fun startQrLogin() = Result.success(Unit)
    override suspend fun restartLogin() = Result.success(Unit)
    override suspend fun logOut() = Result.success(Unit)
    override suspend fun me(): Result<AccountInfo> = Result.failure(UnsupportedOperationException())
}

private val CZECHIA = Country(code = "CZ", name = "Czechia", englishName = "Czechia", flagEmoji = "", callingCode = "+420")
