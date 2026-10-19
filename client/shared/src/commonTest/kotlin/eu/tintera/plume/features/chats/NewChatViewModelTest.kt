package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatTyping
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ArchiveSettings
import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FoundMessagesPage
import eu.tintera.plume.core.telegram.MessageSearchFilter
import eu.tintera.plume.core.telegram.SpamActions
import eu.tintera.plume.core.telegram.ReportRequest
import eu.tintera.plume.core.telegram.ReportStep
import eu.tintera.plume.core.telegram.SpamBar
import eu.tintera.plume.core.telegram.BlockedSendersPage
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.JoinResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewChatViewModelTest {

    /*
     * `viewModelScope` běží na `Dispatchers.Main`. Na JVM žádný není a lifecycle místo něj vezme
     * prázdný kontext, takže to tam prošlo samo. Na iOS je `Main` SKUTEČNÁ hlavní fronta, kterou
     * `runTest` na hlavním vlákně nevyprázdní — načtení kontaktů se nikdy nerozběhlo a test visel
     * do minutového limitu. Testovací `Main` sdílí plánovač s `runTest` (ten si ho převezme sám),
     * takže ViewModel i test jedou na stejném virtuálním čase na obou platformách.
     */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nacteniKontaktuPoStartu() = runTest {
        val contact1 = testChat(1, "Alice")
        val contact2 = testChat(2, "Bob")
        val fakeRepo = FakeChatRepository(contactsResult = Result.success(listOf(contact1, contact2)))

        val viewModel = NewChatViewModel(fakeRepo)

        val state = viewModel.state.first { !it.loading }
        assertEquals(2, state.contacts.size)
        assertEquals("Alice", state.contacts[0].title)
        assertEquals("Bob", state.contacts[1].title)
        assertNull(state.error)
    }

    @Test
    fun selhaniNacteniKontaktuUkazeChybu() = runTest {
        val fakeRepo = FakeChatRepository(contactsResult = Result.failure(RuntimeException("Network error")))

        val viewModel = NewChatViewModel(fakeRepo)

        val state = viewModel.state.first { !it.loading }
        assertTrue(state.contacts.isEmpty())
        assertNotNull(state.error)

        viewModel.consumeError()
        val errorClearedState = viewModel.state.first { it.error == null }
        assertNull(errorClearedState.error)
    }

    @Test
    fun filtraceKontaktuPodleDotazu() = runTest {
        val contact1 = testChat(1, "Alice Smith")
        val contact2 = testChat(2, "Bob Jones")
        val fakeRepo = FakeChatRepository(contactsResult = Result.success(listOf(contact1, contact2)))

        val viewModel = NewChatViewModel(fakeRepo)
        viewModel.state.first { !it.loading }

        viewModel.onQueryChange("alice")

        val filteredState = viewModel.state.first { it.query == "alice" }
        assertEquals(1, filteredState.contacts.size)
        assertEquals("Alice Smith", filteredState.contacts[0].title)
    }

    @Test
    fun tajnyChatSeZakladaAzPoVyberuCloveka() = runTest {
        val started = mutableListOf<Long>()
        val fakeRepo = object : FakeChatRepository(contactsResult = Result.success(listOf(testChat(1, "Alice")))) {
            override suspend fun createSecretChat(userId: Long): Result<Long> {
                started += userId
                return Result.success(-2_000_000_000_001L)
            }
        }
        val viewModel = NewChatViewModel(fakeRepo)
        assertEquals(999L, viewModel.state.first { !it.loading }.savedMessagesChatId)

        viewModel.startSecretMode()
        // Uložené zprávy tajný chat mít nemůžou — karta nad kontakty zmizí.
        val secret = viewModel.state.first { it.secretMode }
        assertNull(secret.savedMessagesChatId)
        assertTrue(started.isEmpty(), "Přepnutí do režimu nic nezakládá")

        viewModel.pickForSecretChat(1L)
        assertEquals(-2_000_000_000_001L, viewModel.openSecretChat.first { it != null })
        assertEquals(listOf(1L), started)

        viewModel.consumeOpenSecretChat()
        assertNull(viewModel.openSecretChat.value)
        assertTrue(viewModel.leaveSecretMode())
        assertFalse(viewModel.state.first { !it.secretMode }.secretMode)
        assertFalse(viewModel.leaveSecretMode(), "Mimo režim zpět patří obrazovce")
    }

    private fun testChat(id: Long, title: String) = ChatSummary(
        id = id,
        title = title,
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )
}
