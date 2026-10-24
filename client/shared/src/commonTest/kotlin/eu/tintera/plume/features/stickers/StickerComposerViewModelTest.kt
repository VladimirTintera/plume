package eu.tintera.plume.features.stickers

import eu.tintera.plume.core.stickers.MadeSticker
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.core.stickers.StickerModelNotReadyException
import eu.tintera.plume.core.telegram.NewStickerFile
import eu.tintera.plume.core.telegram.OwnedStickerSet
import eu.tintera.plume.core.telegram.UnimplementedStickerRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sticker_added
import eu.tintera.plume.resources.sticker_model_not_ready
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StickerComposerViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeMaker(override val canRemoveBackground: Boolean = true) : StickerMaker {
        var notReady = false
        val calls = mutableListOf<Pair<String, Boolean>>()
        override suspend fun make(path: String, removeBackground: Boolean): Result<MadeSticker> {
            calls += path to removeBackground
            if (notReady && removeBackground) return Result.failure(StickerModelNotReadyException(IllegalStateException()))
            return Result.success(MadeSticker("$path.webp", 512, 480))
        }
    }

    private class FakeStickers(private val owned: List<OwnedStickerSet>) : UnimplementedStickerRepository() {
        val added = mutableListOf<Pair<String, NewStickerFile>>()
        val created = mutableListOf<Pair<String, NewStickerFile>>()
        override suspend fun ownedSets() = Result.success(owned)
        override suspend fun addToSet(set: OwnedStickerSet, sticker: NewStickerFile): Result<Unit> {
            added += set.name to sticker
            return Result.success(Unit)
        }
        override suspend fun createSet(title: String, sticker: NewStickerFile): Result<OwnedStickerSet> {
            created += title to sticker
            return Result.success(OwnedStickerSet(9, "new_set", title, 1, null))
        }
    }

    private fun set(id: Long, size: Int) = OwnedStickerSet(id, "set$id", "Sada $id", size, null)

    private fun viewModel(stickers: FakeStickers, maker: FakeMaker = FakeMaker()) =
        StickerComposerViewModel("photo.jpg", 800, 600, stickers, maker, markupRenderer = null)

    @Test
    fun defaultsToFirstSetWithRoomAndRemovesBackground() = runTest {
        val maker = FakeMaker()
        val vm = viewModel(FakeStickers(listOf(set(1, OwnedStickerSet.MAX_SIZE), set(2, 3))), maker)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(StickerTarget.Existing(set(2, 3)), state.target, "plná sada se nepředvybírá")
        assertEquals(listOf("photo.jpg" to true), maker.calls, "s Play službami pozadí pryč hned")
        assertEquals("photo.jpg.webp", state.preview?.path)
        assertFalse(state.canSave, "bez emoji přidat nejde")
        vm.setEmoji("😎")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun savesToExistingSetWithTrimmedEmoji() = runTest {
        val stickers = FakeStickers(listOf(set(2, 3)))
        val vm = viewModel(stickers)
        advanceUntilIdle()
        vm.setEmoji(" 👍 ")
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("set2" to NewStickerFile("photo.jpg.webp", "👍")), stickers.added)
        assertEquals(UiText(Res.string.sticker_added, "Sada 2"), vm.state.value.done)
    }

    @Test
    fun newSetNeedsTitle() = runTest {
        val stickers = FakeStickers(emptyList())
        val vm = viewModel(stickers)
        advanceUntilIdle()
        assertEquals(StickerTarget.NewSet, vm.state.value.target, "bez vlastních sad nová")
        vm.setEmoji("🔥")
        assertFalse(vm.state.value.canSave, "nová sada chce název")
        vm.setNewSetTitle("Moje")
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("Moje" to NewStickerFile("photo.jpg.webp", "🔥")), stickers.created)
    }

    @Test
    fun modelNotReadyCanBeRetriedOrSkipped() = runTest {
        val maker = FakeMaker().apply { notReady = true }
        val vm = viewModel(FakeStickers(emptyList()), maker)
        advanceUntilIdle()
        assertEquals(UiText(Res.string.sticker_model_not_ready), vm.state.value.prepareError)
        assertNull(vm.state.value.preview)
        maker.notReady = false
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.state.value.prepareError)
        assertEquals("photo.jpg.webp", vm.state.value.preview?.path)
        vm.setRemoveBackground(false)
        advanceUntilIdle()
        assertEquals("photo.jpg" to false, maker.calls.last(), "bez odstranění pozadí celá fotka")
    }

    @Test
    fun withoutPlayServicesBackgroundStays() = runTest {
        val maker = FakeMaker(canRemoveBackground = false)
        val vm = viewModel(FakeStickers(emptyList()), maker)
        advanceUntilIdle()
        assertFalse(vm.state.value.canRemoveBackground)
        assertEquals(listOf("photo.jpg" to false), maker.calls)
    }
}
