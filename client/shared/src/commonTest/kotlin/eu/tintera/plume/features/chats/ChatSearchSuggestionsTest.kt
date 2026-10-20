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

        val tags = mutableListOf("#plume", "#kotlin")
        override suspend fun searchedTags(): Result<List<String>> = Result.success(tags.toList())
        override suspend fun removeSearchedTag(tag: String): Result<Unit> {
            tags -= tag
            return Result.success(Unit)
        }
    }

    /** Hledané hashtagy jsou v návrzích hlavního hledání; odebraný zmizí po novém čtení. */
    @Test
    fun hledaneHashtagyVNavrzichAOdebrani() = runTest {
        val repository = Repository()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val search = ChatSearch(repository, scope, everywhere = true)
        search.setSuggestionsWanted(true)
        advanceUntilIdle()
        assertEquals(listOf("#plume", "#kotlin"), search.state.value.tags)

        search.removeTag("#plume")
        advanceUntilIdle()
        assertEquals(listOf("#kotlin"), search.state.value.tags)
        scope.cancel()
    }

    /** Výběr chatu pro přeposílání (ne hlavní hledání) hashtagy nenabízí — hledat podle tagu tam nejde. */
    @Test
    fun vyberChatuHashtagyNenabizi() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val search = ChatSearch(Repository(), scope)
        search.setSuggestionsWanted(true)
        advanceUntilIdle()
        assertEquals(emptyList(), search.state.value.tags)
        scope.cancel()
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
