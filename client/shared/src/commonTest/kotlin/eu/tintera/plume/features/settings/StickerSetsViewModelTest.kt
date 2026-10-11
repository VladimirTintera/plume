package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.ArchivedStickerSets
import eu.tintera.plume.core.telegram.GroupStickerSet
import eu.tintera.plume.core.telegram.StickerCategory
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerQuery
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSearchResult
import eu.tintera.plume.core.telegram.StickerSetDetail
import eu.tintera.plume.core.telegram.StickerSetSample
import eu.tintera.plume.core.telegram.StickerSetSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StickerSetsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun set(id: Long) = StickerSetSummary(id = id, title = "Sada $id", cover = null, size = 10)

    /** Server s nainstalovanými sadami, který změny zapisuje a potvrzuje, až když se mu řekne. */
    private class FakeServer(initial: List<StickerSetSummary>) : StickerRepository {
        val installed = MutableStateFlow(initial)
        val reorders = mutableListOf<List<Long>>()
        val changes = mutableListOf<String>()

        override fun installedSets(): Flow<List<StickerSetSummary>> = installed
        override suspend fun reorderSets(setIds: List<Long>): Result<Unit> {
            reorders += setIds
            return Result.success(Unit)
        }
        override suspend fun setArchived(setId: Long, archived: Boolean): Result<Unit> {
            changes += "archived $setId $archived"
            return Result.success(Unit)
        }
        override suspend fun setInstalled(setId: Long, installed: Boolean): Result<Unit> {
            changes += "installed $setId $installed"
            return Result.success(Unit)
        }
        override suspend fun archivedSets(afterSetId: Long) = Result.success(ArchivedStickerSets(0, emptyList()))

        override fun recentStickers(): Flow<List<StickerItem>> = flowOf(emptyList())
        override fun favoriteStickers(): Flow<List<StickerItem>> = flowOf(emptyList())
        override suspend fun removeRecent(stickerFileId: Int) = Result.success(Unit)
        override suspend fun clearRecent() = Result.success(Unit)
        override suspend fun setFavorite(stickerFileId: Int, favorite: Boolean) = Result.success(Unit)
        override fun stickerSetDetail(setId: Long): Flow<StickerSetDetail?> = emptyFlow()
        override fun trendingSets(): Flow<List<StickerSetSample>> = emptyFlow()
        override suspend fun markTrendingViewed(setIds: List<Long>) = Unit
        override suspend fun groupStickerSet(chatId: Long): GroupStickerSet? = null
        override suspend fun stickerSet(setId: Long) = Result.success(emptyList<StickerItem>())
        override suspend fun search(query: StickerQuery) = StickerSearchResult(emptyList(), emptyList())
        override suspend fun categories(): List<StickerCategory> = emptyList()
        override suspend fun stickersForEmoji(emoji: String): List<StickerItem> = emptyList()
    }

    @Test
    fun tahPosleNovePoradiAzPoPusteni() = runTest {
        val server = FakeServer(listOf(set(1), set(2), set(3)))
        val viewModel = StickerSetsViewModel(server)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.move(from = 3L, to = 1L)
        advanceUntilIdle()
        assertEquals(listOf(3L, 1L, 2L), viewModel.state.value.installed?.map { it.id })
        assertEquals(emptyList(), server.reorders)

        viewModel.commitOrder()
        advanceUntilIdle()
        assertEquals(listOf(listOf(3L, 1L, 2L)), server.reorders)
    }

    @Test
    fun lokalniPoradiVydrziDoPotvrzeniServerem() = runTest {
        val server = FakeServer(listOf(set(1), set(2), set(3)))
        val viewModel = StickerSetsViewModel(server)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.move(from = 3L, to = 1L)
        viewModel.commitOrder()
        advanceTimeBy(500)
        // Update ještě nepřišel: seznam nesmí skočit zpátky do starého pořadí.
        assertEquals(listOf(3L, 1L, 2L), viewModel.state.value.installed?.map { it.id })

        server.installed.value = listOf(set(3), set(1), set(2))
        advanceUntilIdle()
        assertEquals(listOf(3L, 1L, 2L), viewModel.state.value.installed?.map { it.id })
    }

    @Test
    fun pustenyTahBezZmenyNicNeposle() = runTest {
        val server = FakeServer(listOf(set(1), set(2)))
        val viewModel = StickerSetsViewModel(server)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.move(from = 1L, to = 2L)
        viewModel.move(from = 1L, to = 2L)
        viewModel.commitOrder()
        advanceUntilIdle()

        assertEquals(emptyList(), server.reorders)
    }

    @Test
    fun archivaceSeDaVratit() = runTest {
        val server = FakeServer(listOf(set(1), set(2)))
        val viewModel = StickerSetsViewModel(server)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.archive(set(2))
        advanceUntilIdle()
        val notice = viewModel.state.value.notice
        assertEquals(StickerSetNotice.Archived(set(2), orderBefore = listOf(1L, 2L)), notice)

        viewModel.consumeNotice()
        viewModel.undo(notice!!)
        advanceUntilIdle()
        assertEquals(listOf("archived 2 true", "archived 2 false"), server.changes)
        // Vrácená sada se zařadí zpátky na své místo, ne na začátek.
        assertEquals(listOf(listOf(1L, 2L)), server.reorders)
        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun odebraniSeDaVratit() = runTest {
        val server = FakeServer(listOf(set(1)))
        val viewModel = StickerSetsViewModel(server)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.remove(set(1))
        advanceUntilIdle()
        viewModel.undo(viewModel.state.value.notice!!)
        advanceUntilIdle()

        assertEquals(listOf("installed 1 false", "installed 1 true"), server.changes)
        assertEquals(listOf(listOf(1L)), server.reorders)
    }
}
