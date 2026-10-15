package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.AutosaveCandidate
import eu.tintera.plume.core.telegram.AutosaveChatType
import eu.tintera.plume.core.telegram.AutosaveException
import eu.tintera.plume.core.telegram.AutosaveRepository
import eu.tintera.plume.core.telegram.AutosaveScope
import eu.tintera.plume.core.telegram.AutosaveScopeSettings
import eu.tintera.plume.core.telegram.AutosaveSettings
import eu.tintera.plume.core.telegram.ProfileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ukládání do galerie z profilu chatu: kdy výjimku založit, upravit a odebrat. */
class ChatAutosaveViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val off = AutosaveScopeSettings(photos = false, videos = false, maxVideoBytes = 512 * 1024)
    private val photos = off.copy(photos = true)
    private val chatId = 42L

    private fun settings(vararg exceptions: AutosaveException) =
        AutosaveSettings(privateChats = off, groups = photos, channels = off, exceptions = exceptions.toList())

    @Test
    fun beziVyjimkyPlatiVychoziPravidloDruhu() {
        val autosave = settings().chatAutosave(chatId, AutosaveChatType.Group)
        assertEquals(photos, autosave.settings)
        assertEquals(false, autosave.isException)
    }

    @Test
    fun volbaJinaNezVychoziZalozivyjimku() = runTest {
        val repository = FakeAutosave(settings())
        val viewModel = ChatAutosaveViewModel(chatId, repository)
        advanceUntilIdle()

        viewModel.save(photos, AutosaveChatType.Private)
        advanceUntilIdle()

        assertEquals(listOf<Pair<AutosaveScope, AutosaveScopeSettings?>>(AutosaveScope.Chat(chatId) to photos), repository.calls)
        val autosave = assertNotNull(viewModel.state.value.settings).chatAutosave(chatId, AutosaveChatType.Private)
        assertEquals(photos, autosave.settings)
        assertTrue(autosave.isException)
    }

    @Test
    fun volbaShodnaSVychozimVyjimkuNezaklada() = runTest {
        val repository = FakeAutosave(settings())
        val viewModel = ChatAutosaveViewModel(chatId, repository)
        advanceUntilIdle()

        // „Hotovo" nad nezměněným dialogem nesmí chat odpojit od výchozího pravidla skupin.
        viewModel.save(photos, AutosaveChatType.Group)
        advanceUntilIdle()

        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun existujiciVyjimkaSeUpraviIKdyzSeShodujeSVychozim() {
        val changed = settings(AutosaveException(chatId, off)).withChatException(chatId, AutosaveChatType.Group, photos)
        assertEquals(listOf(AutosaveException(chatId, photos)), assertNotNull(changed).exceptions)
        assertNull(settings(AutosaveException(chatId, off)).withChatException(chatId, AutosaveChatType.Group, off))
    }

    @Test
    fun odebraniVratiVychoziPravidlo() = runTest {
        val repository = FakeAutosave(settings(AutosaveException(chatId, photos), AutosaveException(7L, photos)))
        val viewModel = ChatAutosaveViewModel(chatId, repository)
        advanceUntilIdle()

        viewModel.removeException()
        advanceUntilIdle()

        assertEquals(listOf<Pair<AutosaveScope, AutosaveScopeSettings?>>(AutosaveScope.Chat(chatId) to null), repository.calls)
        assertEquals(listOf(AutosaveException(7L, photos)), viewModel.state.value.settings?.exceptions)
    }

    @Test
    fun neulozenaZmenaSeVratiSHlaskou() = runTest {
        val repository = FakeAutosave(settings(), fail = true)
        val viewModel = ChatAutosaveViewModel(chatId, repository)
        advanceUntilIdle()

        viewModel.save(photos, AutosaveChatType.Private)
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.state.value.settings?.exceptions)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun druhChatuZProfilu() {
        assertEquals(AutosaveChatType.Private, autosaveChatType(ProfileKind.Bot, isSecretChat = false))
        assertEquals(AutosaveChatType.Channel, autosaveChatType(ProfileKind.Channel, isSecretChat = false))
        // Tajný chat se do galerie neukládá nikdy, Uložené zprávy jsou samé vlastní zprávy.
        assertNull(autosaveChatType(ProfileKind.User, isSecretChat = true))
        assertNull(autosaveChatType(ProfileKind.SavedMessages, isSecretChat = false))
    }
}

/** Server: `set` zapíše výjimku, `settings` vrátí, co je zapsané. */
private class FakeAutosave(private var stored: AutosaveSettings, private val fail: Boolean = false) : AutosaveRepository {
    val calls = mutableListOf<Pair<AutosaveScope, AutosaveScopeSettings?>>()

    override suspend fun settings(): Result<AutosaveSettings> = Result.success(stored)

    override suspend fun set(scope: AutosaveScope, settings: AutosaveScopeSettings?): Result<Unit> {
        if (fail) return Result.failure(IllegalStateException("CHAT_INVALID"))
        calls += scope to settings
        val chatId = (scope as AutosaveScope.Chat).chatId
        val others = stored.exceptions.filterNot { it.chatId == chatId }
        stored = stored.copy(exceptions = if (settings == null) others else others + AutosaveException(chatId, settings))
        return Result.success(Unit)
    }

    override val incoming: Flow<AutosaveCandidate> = emptyFlow()
}
