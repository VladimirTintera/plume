package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.Birthdate
import eu.tintera.plume.core.telegram.ContactLink
import eu.tintera.plume.core.telegram.EditableProfile
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.ui.CropRegion
import eu.tintera.plume.core.ui.ProfilePhotoCrop
import eu.tintera.plume.core.ui.ProfileVideo
import eu.tintera.plume.core.ui.SquarePhoto
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

/** Datum narození v úpravě profilu: rok se jen schovává, ukládá se tlačítkem Uložit. */
class EditProfileBirthdateTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun vypnutyRokSeUloziBezRokuAZapnutiHoVrati() = runTest {
        val accounts = BirthdateAccounts(birthdate = null)
        val viewModel = newViewModel(accounts)
        advanceUntilIdle()
        assertTrue(viewModel.showBirthYear, "Nové datum rok ukazuje.")

        viewModel.birthdate = Birthdate(13, 10, 1990)
        viewModel.showBirthYear = false
        assertEquals(Birthdate(13, 10, null), viewModel.birthdateToSave)
        viewModel.showBirthYear = true
        assertEquals(Birthdate(13, 10, 1990), viewModel.birthdateToSave)

        viewModel.showBirthYear = false
        viewModel.save()
        advanceUntilIdle()
        assertEquals(listOf<Birthdate?>(Birthdate(13, 10, null)), accounts.saved)
    }

    @Test
    fun datumBezRokuSeOtevreSVypnutymRokemANicSeNemeni() = runTest {
        val accounts = BirthdateAccounts(birthdate = Birthdate(13, 10, null))
        val viewModel = newViewModel(accounts)
        advanceUntilIdle()

        assertFalse(viewModel.showBirthYear)
        assertFalse(viewModel.changed)

        viewModel.birthdate = null
        assertTrue(viewModel.changed)
        viewModel.save()
        advanceUntilIdle()
        assertEquals(listOf<Birthdate?>(null), accounts.saved)
    }

    private fun newViewModel(accounts: AccountRepository) =
        EditProfileViewModel(accounts, ProfilePhotoCrop(NoSquarePhoto, NoProfileVideo))
}

private object NoSquarePhoto : SquarePhoto {
    override suspend fun crop(path: String, region: CropRegion?): String = path
}

private object NoProfileVideo : ProfileVideo {
    override suspend fun durationMs(path: String): Long = 0
    override suspend fun frame(path: String, atMs: Long, edge: Int): String = path
    override suspend fun crop(path: String, region: CropRegion?, startMs: Long): String = path
}

private class BirthdateAccounts(private val birthdate: Birthdate?) : AccountRepository {
    val saved = mutableListOf<Birthdate?>()

    override suspend fun editableProfile(): Result<EditableProfile> = Result.success(
        EditableProfile(
            id = 1, firstName = "Jan", lastName = "", username = "", bio = "", bioMaxLength = 70,
            photoFileId = null, photoThumbnailFileId = null, birthdate = birthdate,
        ),
    )

    override suspend fun setBirthdate(birthdate: Birthdate?): Result<Unit> {
        saved += birthdate
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
