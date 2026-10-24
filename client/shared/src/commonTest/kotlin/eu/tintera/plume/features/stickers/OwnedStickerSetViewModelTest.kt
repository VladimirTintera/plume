package eu.tintera.plume.features.stickers

import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerSetDetail
import eu.tintera.plume.core.telegram.UnimplementedStickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

@OptIn(ExperimentalCoroutinesApi::class)
class OwnedStickerSetViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun sticker(id: Long) = StickerItem(
        id = id,
        sendFileId = id.toInt() + 100,
        preview = MessageMedia.Sticker(
            stickerFileId = id.toInt() + 100,
            setId = 1,
            fileId = id.toInt(),
            animationFileId = null,
            thumbnailFileId = null,
            width = 512,
            height = 512,
            emoji = "🙂",
        ),
    )

    private fun detail(vararg ids: Long) = StickerSetDetail(
        id = 1,
        title = "Moje sada",
        name = "moje_sada",
        isInstalled = true,
        isOfficial = false,
        stickers = ids.map(::sticker),
        isOwned = true,
    )

    private class FakeStickers(initial: StickerSetDetail) : UnimplementedStickerRepository() {
        val detail = MutableStateFlow<StickerSetDetail?>(initial)
        val moved = mutableListOf<Pair<Int, Int>>()
        val renamed = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<Pair<Long, String>>()
        var failDelete = false

        override fun stickerSetDetail(setId: Long): Flow<StickerSetDetail?> = detail
        override suspend fun moveInSet(stickerFileId: Int, position: Int): Result<Unit> {
            moved += stickerFileId to position
            return Result.success(Unit)
        }
        override suspend fun renameSet(name: String, title: String): Result<Unit> {
            renamed += name to title
            return Result.success(Unit)
        }
        override suspend fun deleteSet(setId: Long, name: String): Result<Unit> {
            if (failDelete) return Result.failure(IllegalStateException("STICKERSET_INVALID"))
            deleted += setId to name
            return Result.success(Unit)
        }
    }

    @Test
    fun tazenaSamolepkaOdejdeJednimVolanimNaNoveMisto() = runTest {
        val stickers = FakeStickers(detail(1, 2, 3, 4))
        val vm = OwnedStickerSetViewModel(1, stickers)
        advanceUntilIdle()
        // Tah první samolepky přes dvě sousední: pořadí se mění místně krok po kroku.
        vm.move(1L, 2L)
        vm.move(1L, 3L)
        runCurrent()
        assertEquals(listOf(2L, 3L, 1L, 4L), vm.state.value.stickers.map { it.id })
        assertTrue(stickers.moved.isEmpty(), "během tahu se server nevolá")
        vm.commitMove()
        advanceUntilIdle()
        assertEquals(listOf(101 to 2), stickers.moved, "jedna samolepka, cílová pozice")
    }

    @Test
    fun tahZpatkyNaPuvodniMistoServerNevola() = runTest {
        val stickers = FakeStickers(detail(1, 2, 3))
        val vm = OwnedStickerSetViewModel(1, stickers)
        advanceUntilIdle()
        vm.move(2L, 3L)
        vm.move(2L, 1L)
        vm.move(2L, 3L)
        vm.move(2L, 1L)
        vm.move(1L, 2L) // zpátky ve výchozím pořadí
        vm.commitMove()
        advanceUntilIdle()
        assertTrue(stickers.moved.isEmpty())
        assertEquals(listOf(1L, 2L, 3L), vm.state.value.stickers.map { it.id })
    }

    @Test
    fun nazevJdeUlozitJenZmenenyANeprazdny() = runTest {
        val stickers = FakeStickers(detail(1))
        val vm = OwnedStickerSetViewModel(1, stickers)
        advanceUntilIdle()
        assertFalse(vm.state.value.canSaveTitle)
        vm.setTitleDraft("Moje sada ")
        runCurrent()
        assertFalse(vm.state.value.canSaveTitle, "stejný název po ořezu mezer")
        vm.setTitleDraft("  ")
        runCurrent()
        assertFalse(vm.state.value.canSaveTitle)
        vm.setTitleDraft(" Nová ")
        runCurrent()
        assertTrue(vm.state.value.canSaveTitle)
        vm.saveTitle()
        advanceUntilIdle()
        assertEquals(listOf("moje_sada" to "Nová"), stickers.renamed)
        assertNull(vm.state.value.titleDraft, "po uložení se ukazuje název ze serveru")
    }

    @Test
    fun smazaniZavreObrazovkuChybaJenOhlasi() = runTest {
        val stickers = FakeStickers(detail(1)).apply { failDelete = true }
        val vm = OwnedStickerSetViewModel(1, stickers)
        advanceUntilIdle()
        vm.deleteSet()
        advanceUntilIdle()
        assertFalse(vm.state.value.deleted)
        assertNotNull(vm.state.value.error)

        stickers.failDelete = false
        vm.errorShown()
        vm.deleteSet()
        advanceUntilIdle()
        assertEquals(listOf(1L to "moje_sada"), stickers.deleted)
        assertTrue(vm.state.value.deleted)
    }
}
