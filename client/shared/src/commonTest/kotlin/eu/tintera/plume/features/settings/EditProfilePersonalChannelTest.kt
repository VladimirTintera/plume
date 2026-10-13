package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.ContactLink
import eu.tintera.plume.core.telegram.EditableProfile
import eu.tintera.plume.core.telegram.PersonalChannel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Osobní kanál v úpravě profilu: výběr se uloží tlačítkem Uložit, „Žádný" kanál odebere. */
class EditProfilePersonalChannelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val blog = PersonalChannel(chatId = -100L, title = "Blog", photoFileId = null, subscribers = 12)
    private val photos = PersonalChannel(chatId = -200L, title = "Fotky", photoFileId = null, subscribers = null)

    @Test
    fun vybranyKanalSeUloziAzTlacitkem() = runTest {
        val accounts = ChannelAccounts(current = null, suitable = listOf(blog, photos))
        val viewModel = EditProfileViewModel(accounts, ProfilePhotoCrop(ChannelTestSquarePhoto, ChannelTestProfileVideo))
        advanceUntilIdle()
        assertNull(viewModel.suitableChannels, "Kanály se čtou až s otevřením výběru.")

        viewModel.loadSuitableChannels()
        advanceUntilIdle()
        assertEquals(listOf(blog, photos), viewModel.suitableChannels)

        viewModel.personalChannel = photos
        assertTrue(viewModel.changed)
        assertTrue(accounts.saved.isEmpty())

        viewModel.save()
        advanceUntilIdle()
        assertEquals(listOf<Long?>(-200L), accounts.saved)
    }

    @Test
    fun zadnyKanalHoOdeberePodobnyKanalNicNemeni() = runTest {
        val accounts = ChannelAccounts(current = blog, suitable = listOf(blog))
        val viewModel = EditProfileViewModel(accounts, ProfilePhotoCrop(ChannelTestSquarePhoto, ChannelTestProfileVideo))
        advanceUntilIdle()

        // Tentýž kanál s novějším počtem odběratelů není změna.
        viewModel.personalChannel = blog.copy(subscribers = 13)
        assertFalse(viewModel.changed)

        viewModel.personalChannel = null
        viewModel.save()
        advanceUntilIdle()
        assertEquals(listOf<Long?>(null), accounts.saved)
    }
}

private object ChannelTestSquarePhoto : SquarePhoto {
    override suspend fun crop(path: String, region: CropRegion?): String = path
}

private object ChannelTestProfileVideo : ProfileVideo {
    override suspend fun durationMs(path: String): Long = 0
    override suspend fun frame(path: String, atMs: Long, edge: Int): String = path
    override suspend fun crop(path: String, region: CropRegion?, startMs: Long): String = path
}

private class ChannelAccounts(private val current: PersonalChannel?, private val suitable: List<PersonalChannel>) : AccountRepository {
    val saved = mutableListOf<Long?>()

    override suspend fun editableProfile(): Result<EditableProfile> = Result.success(
        EditableProfile(
            id = 1, firstName = "Jan", lastName = "", username = "", bio = "", bioMaxLength = 70,
            photoFileId = null, photoThumbnailFileId = null, personalChannel = current,
        ),
    )

    override suspend fun suitablePersonalChannels(): Result<List<PersonalChannel>> = Result.success(suitable)

    override suspend fun setPersonalChannel(chatId: Long?): Result<Unit> {
        saved += chatId
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
