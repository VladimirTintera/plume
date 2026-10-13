package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.ContactLink
import eu.tintera.plume.core.telegram.EditableProfile
import eu.tintera.plume.core.telegram.LoginPasskey
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.passkey_remove_failed
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Přístupové klíče: seznam, odebrání po potvrzení, chyby. */
class PasskeysViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val phone = LoginPasskey(id = "a", name = "Google Password Manager", addedAt = 1, lastUsedAt = null, iconCustomEmojiId = null)
    private val laptop = LoginPasskey(id = "b", name = "iCloud Keychain", addedAt = 2, lastUsedAt = 3, iconCustomEmojiId = 42)

    @Test
    fun odebiraSeAzPoPotvrzeniAZmiziZeSeznamu() = runTest {
        val accounts = PasskeyAccounts(listOf(phone, laptop))
        val viewModel = PasskeysViewModel(accounts)
        advanceUntilIdle()
        assertEquals(listOf(phone, laptop), viewModel.state.value.passkeys)

        viewModel.requestRemove(phone)
        assertEquals(phone, viewModel.state.value.confirming)
        assertTrue(accounts.removed.isEmpty(), "Bez potvrzení se nic neodebírá.")

        viewModel.confirmRemove()
        advanceUntilIdle()

        assertEquals(listOf("a"), accounts.removed)
        assertEquals(listOf(laptop), viewModel.state.value.passkeys)
        assertNull(viewModel.state.value.removing)
    }

    @Test
    fun neodebranyKlicZustaneSHlaskou() = runTest {
        val accounts = PasskeyAccounts(listOf(phone), removeResult = Result.failure(IllegalStateException("network")))
        val viewModel = PasskeysViewModel(accounts)
        advanceUntilIdle()

        viewModel.requestRemove(phone)
        viewModel.confirmRemove()
        advanceUntilIdle()

        assertEquals(listOf(phone), viewModel.state.value.passkeys)
        assertEquals(UiText(Res.string.passkey_remove_failed), viewModel.state.value.error)
        viewModel.consumeError()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun nenactenySeznamJdeZkusitZnovu() = runTest {
        val accounts = PasskeyAccounts(listOf(phone), failLoad = true)
        val viewModel = PasskeysViewModel(accounts)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadFailed)
        assertNull(viewModel.state.value.passkeys)

        accounts.failLoad = false
        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf(phone), viewModel.state.value.passkeys)
        assertEquals(false, viewModel.state.value.loadFailed)
    }
}

private class PasskeyAccounts(
    private val keys: List<LoginPasskey>,
    var failLoad: Boolean = false,
    private val removeResult: Result<Unit> = Result.success(Unit),
) : AccountRepository {
    val removed = mutableListOf<String>()

    override suspend fun loginPasskeys(): Result<List<LoginPasskey>> =
        if (failLoad) Result.failure(IllegalStateException("network")) else Result.success(keys)

    override suspend fun removeLoginPasskey(id: String): Result<Unit> {
        removed += id
        return removeResult
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
