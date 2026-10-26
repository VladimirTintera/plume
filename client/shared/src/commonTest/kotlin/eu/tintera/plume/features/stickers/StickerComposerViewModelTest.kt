package eu.tintera.plume.features.stickers

import eu.tintera.plume.core.stickers.MadeSticker
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.core.stickers.StickerModelNotReadyException
import eu.tintera.plume.core.telegram.NewStickerFile
import eu.tintera.plume.core.telegram.OwnedStickerSet
import eu.tintera.plume.core.telegram.StickerSetNameCheck
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
        val outlines = mutableListOf<Boolean>()
        override suspend fun make(path: String, removeBackground: Boolean, outline: Boolean): Result<MadeSticker> {
            calls += path to removeBackground
            outlines += outline
            if (notReady && removeBackground) return Result.failure(StickerModelNotReadyException(IllegalStateException()))
            return Result.success(MadeSticker("$path.webp", 512, 480))
        }
    }

    private class FakeStickers(private val owned: List<OwnedStickerSet>) : UnimplementedStickerRepository() {
        val added = mutableListOf<Pair<String, NewStickerFile>>()
        val created = mutableListOf<Pair<String, NewStickerFile>>()
        val createdNames = mutableListOf<String>()
        val suggested = mutableListOf<String>()
        val checked = mutableListOf<String>()
        var check: StickerSetNameCheck = StickerSetNameCheck.Ok
        override suspend fun suggestSetName(title: String): Result<String> {
            suggested += title
            return Result.success(title.lowercase().replace(' ', '_') + "_by_plume")
        }
        override suspend fun checkSetName(name: String): Result<StickerSetNameCheck> {
            checked += name
            return Result.success(check)
        }
        override suspend fun ownedSets() = Result.success(owned)
        override suspend fun addToSet(set: OwnedStickerSet, sticker: NewStickerFile): Result<Unit> {
            added += set.name to sticker
            return Result.success(Unit)
        }
        override suspend fun createSet(title: String, sticker: NewStickerFile, name: String): Result<OwnedStickerSet> {
            created += title to sticker
            createdNames += name
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
    fun suggestedNameFollowsTitleUntilEdited() = runTest {
        val stickers = FakeStickers(emptyList())
        val vm = viewModel(stickers)
        advanceUntilIdle()
        vm.setEmoji("🔥")
        vm.setNewSetTitle("Moje")
        vm.setNewSetTitle("Moje sada")
        advanceUntilIdle()
        assertEquals(listOf("Moje sada"), stickers.suggested, "návrh až po dopsání, ne na každé písmeno")
        assertEquals("moje_sada_by_plume", vm.state.value.newSetName)
        assertEquals(SetNameStatus.Ok, vm.state.value.newSetNameStatus)
        assertEquals(emptyList(), stickers.checked, "návrh od Telegramu se neověřuje")

        vm.setNewSetName("vlastni jmeno")
        advanceUntilIdle()
        assertEquals("vlastni_jmeno", vm.state.value.newSetName)
        assertEquals(listOf("vlastni_jmeno"), stickers.checked)
        vm.setNewSetTitle("Jiný název")
        advanceUntilIdle()
        assertEquals("vlastni_jmeno", vm.state.value.newSetName, "upravené jméno návrh nepřepíše")

        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("vlastni_jmeno"), stickers.createdNames)
    }

    @Test
    fun occupiedNameBlocksSave() = runTest {
        val stickers = FakeStickers(emptyList()).apply { check = StickerSetNameCheck.Occupied }
        val vm = viewModel(stickers)
        advanceUntilIdle()
        vm.setEmoji("🔥")
        vm.setNewSetTitle("Moje")
        vm.setNewSetName("obsazene")
        assertFalse(vm.state.value.canSave, "během ověřování se nezakládá")
        advanceUntilIdle()
        assertEquals(SetNameStatus.Occupied, vm.state.value.newSetNameStatus)
        assertFalse(vm.state.value.canSave)
        vm.setNewSetName("")
        assertEquals(SetNameStatus.Empty, vm.state.value.newSetNameStatus, "prázdné = vybere Telegram")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun keywordsGoWithStickerAndLimitSave() = runTest {
        val stickers = FakeStickers(listOf(set(2, 3)))
        val vm = viewModel(stickers)
        advanceUntilIdle()
        vm.setEmoji("👍")
        vm.setKeywords("a".repeat(65))
        assertFalse(vm.state.value.canSave, "přes 64 znaků se neuloží")
        vm.setKeywords("pes, štěně")
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("pes", "štěně"), stickers.added.single().second.keywords)
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
    fun outlineIsOnByDefaultAndRemakesOnlyCutOutSticker() = runTest {
        val maker = FakeMaker()
        val vm = viewModel(FakeStickers(emptyList()), maker)
        advanceUntilIdle()
        assertTrue(vm.state.value.outline)
        assertEquals(listOf(true), maker.outlines, "výchozí samolepka má obrys")
        vm.setOutline(false)
        advanceUntilIdle()
        assertEquals(listOf(true, false), maker.outlines, "vypnutý obrys = nová samolepka")
        vm.setRemoveBackground(false)
        advanceUntilIdle()
        val made = maker.calls.size
        vm.setOutline(true)
        advanceUntilIdle()
        assertEquals(made, maker.calls.size, "bez odstranění pozadí se kvůli obrysu nic nepřepočítává")
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
