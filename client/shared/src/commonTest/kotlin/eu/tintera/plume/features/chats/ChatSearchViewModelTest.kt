package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.FakeChatRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Hledání vyčleněné ze seznamu chatů (`ChatSearchViewModel`, docs/navrh-hledani.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSearchViewModelTest {

    /** `viewModelScope` běží na `Main`; bez testovacího by test na iOS visel (Gotchas v CLAUDE.md). */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Repository : FakeChatRepository() {
        val viewed = mutableListOf<Long>()
        val opened = mutableListOf<Long>()

        override suspend fun reportSponsoredChatView(uniqueId: Long): Result<Unit> {
            viewed += uniqueId
            return Result.success(Unit)
        }

        override suspend fun reportSponsoredChatOpen(uniqueId: Long): Result<Unit> {
            opened += uniqueId
            return Result.success(Unit)
        }
    }

    /** Zhlédnutí se hlásí jednou za výsledek, ne při každém posunu seznamu výsledků. */
    @Test
    fun zhlednutiSponzorovanehoChatuSeHlasiJednou() = runTest {
        val repository = Repository()
        val viewModel = ChatSearchViewModel(repository)

        viewModel.sponsoredChatSeen(7)
        viewModel.sponsoredChatSeen(7)
        viewModel.sponsoredChatSeen(8)
        advanceUntilIdle()

        assertEquals(listOf(7L, 8L), repository.viewed)
    }

    /** Otevření se hlásí pokaždé — každé otevření je samostatná událost. */
    @Test
    fun otevreniSponzorovanehoChatuSeHlasiPokazde() = runTest {
        val repository = Repository()
        val viewModel = ChatSearchViewModel(repository)

        viewModel.sponsoredChatOpened(7)
        viewModel.sponsoredChatOpened(7)
        advanceUntilIdle()

        assertEquals(listOf(7L, 7L), repository.opened)
    }

    /** Bez modelu v zařízení se „Hledat s AI" nenabízí a věta se nerozebírá. */
    @Test
    fun bezModeluNeniHledaniPrirozenymJazykem() = runTest {
        val viewModel = ChatSearchViewModel(Repository())
        advanceUntilIdle()

        assertFalse(viewModel.naturalSearchAvailable.value)
        assertNull(viewModel.parseSearch("fotky z léta"))
    }
}
