package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FakeChatRepository
import eu.tintera.plume.core.telegram.SponsoredSearchChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Sponzorované chaty v globálním hledání: jen hlavní hledání, jen k textovému dotazu. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSearchSponsoredTest {

    private class Repository : FakeChatRepository() {
        val queries = mutableListOf<String>()

        override suspend fun searchSponsoredChats(query: String): Result<List<SponsoredSearchChat>> {
            queries += query
            return Result.success(listOf(ad(1), ad(2)))
        }
    }

    @Test
    fun hlavniHledaniPrimichaReklamuANahlasenaZmizi() = runTest {
        val repository = Repository()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val search = ChatSearch(repository, scope, everywhere = true, sponsored = true)

        search.setQuery("zpravy")
        advanceUntilIdle()
        assertEquals(listOf("zpravy"), repository.queries)
        assertEquals(listOf(1L, 2L), search.state.value.sponsored.map { it.uniqueId })

        search.dropSponsored(1)
        assertEquals(listOf(2L), search.state.value.sponsored.map { it.uniqueId })
        scope.cancel()
    }

    /** Nová konverzace hledá veřejné chaty taky, reklamu ale nechce — a nehlásila by její zhlédnutí. */
    @Test
    fun bezPriznakuSeNaReklamuNepta() = runTest {
        val repository = Repository()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val search = ChatSearch(repository, scope, everywhere = true)

        search.setQuery("zpravy")
        advanceUntilIdle()
        assertEquals(emptyList(), repository.queries)
        assertEquals(emptyList(), search.state.value.sponsored)
        scope.cancel()
    }

    /** Hashtag hledá jen zprávy; reklama k chatům tam nemá kam patřit. */
    @Test
    fun hashtagReklamuNehleda() = runTest {
        val repository = Repository()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val search = ChatSearch(repository, scope, everywhere = true, sponsored = true)

        search.setQuery("#plume")
        advanceUntilIdle()
        assertEquals(emptyList(), repository.queries)
        scope.cancel()
    }

    private companion object {
        fun ad(id: Long) = SponsoredSearchChat(
            uniqueId = id,
            chat = ChatSummary(
                id = 100 + id,
                title = "Kanál $id",
                unreadCount = 0,
                lastMessagePreview = null,
                isPinned = false,
                order = 0,
                avatarFileId = null,
                isMuted = false,
                isMarkedAsUnread = false,
                kind = ChatKind.Channel,
                lastMessageTimestamp = null,
                presence = null,
                activity = null,
                draft = null,
            ),
            sponsorInfo = "",
            additionalInfo = "",
        )
    }
}
