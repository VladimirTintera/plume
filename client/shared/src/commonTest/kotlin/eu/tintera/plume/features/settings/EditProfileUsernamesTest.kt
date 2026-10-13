package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.AccountUsernames
import eu.tintera.plume.core.telegram.ContactLink
import eu.tintera.plume.core.telegram.EditableProfile
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.ProfilePhotoCrop
import eu.tintera.plume.core.ui.ProfileVideo
import eu.tintera.plume.core.ui.SquarePhoto
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_usernames_active_too_much
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
import kotlin.test.assertTrue

/** Víc uživatelských jmen: zapnutí, vypnutí a pořadí jdou na server hned, chyba je vrátí. */
class EditProfileUsernamesTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val names = AccountUsernames(active = listOf("jan", "kral"), disabled = listOf("pan"), editable = "jan")

    @Test
    fun jedinyUpravitelnyNicDalsihoNeukazuje() {
        assertFalse(AccountUsernames(active = listOf("jan"), editable = "jan").hasMore)
        assertFalse(AccountUsernames().hasMore)
        assertTrue(names.hasMore)
    }

    @Test
    fun zapnuteJdeNaKonecVypnuteMeziVypnuta() = runTest {
        val accounts = UsernameAccounts(names)
        val viewModel = newViewModel(accounts)
        advanceUntilIdle()

        viewModel.setUsernameActive("pan", true)
        assertEquals(listOf("jan", "kral", "pan"), viewModel.usernames.active)
        viewModel.setUsernameActive("kral", false)
        advanceUntilIdle()

        assertEquals(listOf("jan", "pan"), viewModel.usernames.active)
        assertEquals(listOf("kral"), viewModel.usernames.disabled)
        assertEquals(listOf("pan" to true, "kral" to false), accounts.toggles)
    }

    @Test
    fun prilisMnohoZapnutychSeVratiSHlaskou() = runTest {
        val accounts = UsernameAccounts(names, toggleResult = Result.failure(IllegalStateException("USERNAMES_ACTIVE_TOO_MUCH")))
        val viewModel = newViewModel(accounts)
        advanceUntilIdle()

        viewModel.setUsernameActive("pan", true)
        advanceUntilIdle()

        assertEquals(names, viewModel.usernames)
        assertEquals(UiText(Res.string.error_usernames_active_too_much), viewModel.notice)
    }

    @Test
    fun posunPosleCelePoradiAMimoOkrajNic() = runTest {
        val accounts = UsernameAccounts(names)
        val viewModel = newViewModel(accounts)
        advanceUntilIdle()

        viewModel.moveUsername("jan", up = true)
        assertTrue(accounts.orders.isEmpty(), "První výš nejde.")

        viewModel.moveUsername("kral", up = true)
        advanceUntilIdle()
        assertEquals(listOf("kral", "jan"), viewModel.usernames.active)
        assertEquals(listOf(listOf("kral", "jan")), accounts.orders)
    }

    private fun newViewModel(accounts: AccountRepository) =
        EditProfileViewModel(accounts, ProfilePhotoCrop(UsernamesTestSquarePhoto, UsernamesTestProfileVideo))
}

private object UsernamesTestSquarePhoto : SquarePhoto {
    override suspend fun crop(path: String, region: CropRegion?): String = path
}

private object UsernamesTestProfileVideo : ProfileVideo {
    override suspend fun durationMs(path: String): Long = 0
    override suspend fun frame(path: String, atMs: Long, edge: Int): String = path
    override suspend fun crop(path: String, region: CropRegion?, startMs: Long): String = path
}

private class UsernameAccounts(
    private val usernames: AccountUsernames,
    private val toggleResult: Result<Unit> = Result.success(Unit),
) : AccountRepository {
    val toggles = mutableListOf<Pair<String, Boolean>>()
    val orders = mutableListOf<List<String>>()

    override suspend fun editableProfile(): Result<EditableProfile> = Result.success(
        EditableProfile(
            id = 1, firstName = "Jan", lastName = "", username = usernames.editable, bio = "", bioMaxLength = 70,
            photoFileId = null, photoThumbnailFileId = null, usernames = usernames,
        ),
    )

    override suspend fun setUsernameActive(username: String, active: Boolean): Result<Unit> {
        toggles += username to active
        return toggleResult
    }

    override suspend fun reorderUsernames(active: List<String>): Result<Unit> {
        orders += active
        return Result.success(Unit)
    }

    override val account: Flow<AccountInfo?> = flowOf(null)
    override suspend fun setName(firstName: String, lastName: String) = Result.success(Unit)
    override suspend fun setUsername(username: String) = Result.success(Unit)
    override suspend fun checkUsername(username: String): Result<UsernameCheck> = Result.failure(UnsupportedOperationException())
    override suspend fun setBio(bio: String) = Result.success(Unit)
    override suspend fun setPhoto(photo: ProfilePhotoFile) = Result.success(Unit)
    override suspend fun deletePhoto() = Result.success(Unit)
    override suspend fun contactLink(): Result<ContactLink> = Result.failure(UnsupportedOperationException())
}
