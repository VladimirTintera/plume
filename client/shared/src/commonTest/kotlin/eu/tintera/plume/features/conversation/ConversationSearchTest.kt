package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatSearchPage
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSearchTest {

    /** Dotaz, jak ho hledání poslalo serveru. */
    private data class Query(val query: String, val fromMessageId: Long, val senderUserId: Long?)

    private class FakeRepository(
        private val pages: (Query) -> Result<ChatSearchPage>,
    ) : UnimplementedConversationRepository() {
        val queries = mutableListOf<Query>()

        override suspend fun searchInChat(chatId: Long, query: String, fromMessageId: Long, senderUserId: Long?): Result<ChatSearchPage> {
            val q = Query(query, fromMessageId, senderUserId)
            queries += q
            return pages(q)
        }
    }

    private class RecordingJumper : MessageJumper {
        val jumps = mutableListOf<Long>()

        override fun jumpToMessage(messageId: Long, fromSearch: Boolean, anchor: ScrollAnchor) {
            assertTrue(fromSearch, "výsledek hledání se ukazuje jako výsledek hledání")
            jumps += messageId
        }
    }

    private val state = testState()
    private val jumper = RecordingJumper()

    private fun search(scope: CoroutineScope, repository: FakeRepository) =
        ConversationSearch(TEST_CHAT, state, scope, repository, jumper, testLogger)

    private fun page(vararg ids: Long, next: Long? = null) =
        Result.success(ChatSearchPage(messageIds = ids.toList(), total = null, nextFromMessageId = next))

    @Test
    fun dotazOdejdeAzPoPauzeVPsaniASkociNaNejnovejsiVysledek() = runTest {
        val repository = FakeRepository { page(30, 20, 10) }
        val search = search(this, repository)

        search.openSearch()
        search.onSearchQuery("ahoj")
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(repository.queries.isEmpty(), "během psaní se nehledá")

        advanceUntilIdle()
        assertEquals(listOf(Query("ahoj", 0, null)), repository.queries)
        assertEquals(listOf(30L, 20L, 10L), state.value.search?.results)
        assertEquals(30L, state.value.search?.current)
        assertEquals(listOf(30L), jumper.jumps)
    }

    @Test
    fun dalsiPismenoZrusiRozbehnuteHledani() = runTest {
        val repository = FakeRepository { page(1) }
        val search = search(this, repository)

        search.openSearch()
        search.onSearchQuery("a")
        advanceTimeBy(SEARCH_DEBOUNCE_MS / 2)
        search.onSearchQuery("ah")
        advanceUntilIdle()

        assertEquals(listOf("ah"), repository.queries.map { it.query })
    }

    @Test
    fun prazdnyDotazVysledkyVycistiBezDotazuNaServer() = runTest {
        val repository = FakeRepository { page(5) }
        val search = search(this, repository)

        search.openSearch()
        search.onSearchQuery("x")
        advanceUntilIdle()
        search.onSearchQuery("")
        advanceUntilIdle()

        assertEquals(1, repository.queries.size)
        assertEquals(emptyList(), state.value.search?.results)
    }

    @Test
    fun sipkyProchaziVysledkyASkaciNaKazdy() = runTest {
        val repository = FakeRepository { page(30, 20, 10) }
        val search = search(this, repository)
        search.openSearch()
        search.onSearchQuery("ahoj")
        advanceUntilIdle()

        search.searchOlder()
        assertEquals(20L, state.value.search?.current)
        search.searchNewer()
        assertEquals(30L, state.value.search?.current)
        assertEquals(listOf(30L, 20L, 30L), jumper.jumps)
    }

    @Test
    fun naKonciNactenychSeDotahneDalsiStranka() = runTest {
        val repository = FakeRepository { q -> if (q.fromMessageId == 0L) page(30, next = 29) else page(25, 15) }
        val search = search(this, repository)
        search.openSearch()
        search.onSearchQuery("ahoj")
        advanceUntilIdle()

        search.searchOlder()
        advanceUntilIdle()

        assertEquals(Query("ahoj", 29, null), repository.queries.last())
        assertEquals(listOf(30L, 25L, 15L), state.value.search?.results)
        assertEquals(25L, state.value.search?.current)
    }

    @Test
    fun odesilatelSeHledaHnedIBezTextu() = runTest {
        val repository = FakeRepository { page(7) }
        val search = search(this, repository)
        search.openSearch()

        search.setSearchSender(SenderInfo(id = 99, title = "Eva", avatarFileId = null))
        runCurrent()

        assertEquals(listOf(Query("", 0, 99)), repository.queries)
    }

    @Test
    fun chybaHledaniSkonciHlaskouANecekaDal() = runTest {
        val repository = FakeRepository { Result.failure(IllegalStateException("SEARCH_QUERY_EMPTY")) }
        val search = search(this, repository)
        search.openSearch()
        search.onSearchQuery("ahoj")
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertFalse(state.value.search?.loading ?: true)
        assertTrue(jumper.jumps.isEmpty())
    }

    @Test
    fun zavreniHledaniZrusiRozbehnutyDotaz() = runTest {
        val repository = FakeRepository { page(1) }
        val search = search(this, repository)
        search.openSearch()
        search.onSearchQuery("ahoj")

        search.closeSearch()
        advanceUntilIdle()

        assertTrue(repository.queries.isEmpty())
        assertNull(state.value.search)
    }
}
