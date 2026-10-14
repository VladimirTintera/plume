package eu.tintera.plume.features.chats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Úklid návrhů pod prázdným dotazem: jeden chat z historie, jeden člověk z nejčastějších. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSearchSuggestionsTest {

    private class Repository : FakeChatRepository() {
        val removedRecent = mutableListOf<Long>()
        val removedTop = mutableListOf<Long>()

        override suspend fun removeRecentlyFound(chatId: Long): Result<Unit> {
            removedRecent += chatId
            return Result.success(Unit)
        }

        override suspend fun removeTopChat(chatId: Long): Result<Unit> {
            removedTop += chatId
            return Result.success(Unit)
        }
    }

    @Test
    fun odebraniZHistorieAZNavrhuJdeNaServer() = runTest {
        val repository = Repository()
        // Vlastní scope: sběr návrhů v `init` běží napořád a runTest by na něj čekal.
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val search = ChatSearch(repository, scope)

        search.removeRecent(7)
        search.removeTop(9)
        advanceUntilIdle()

        assertEquals(listOf(7L), repository.removedRecent)
        assertEquals(listOf(9L), repository.removedTop)
        scope.cancel()
    }
}
