package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.CustomEmojiSet
import eu.tintera.plume.core.telegram.CustomEmojiSetSample
import eu.tintera.plume.core.telegram.EmojiRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerSetKind
import eu.tintera.plume.core.telegram.StickerSetSample
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.telegram.UnimplementedStickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Obsah sad ve výběru se po `UpdateStickerSet` přečte znovu (`StickerRepository.changedSets`). */
@OptIn(ExperimentalCoroutinesApi::class)
class StickerPickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeRepository : UnimplementedStickerRepository() {
        val changes = MutableSharedFlow<Long>()
        val contents = mutableMapOf<Long, List<StickerItem>>()
        val requested = mutableListOf<Long>()

        override fun changedSets(): Flow<Long> = changes
        override fun installedSets(kind: StickerSetKind): Flow<List<StickerSetSummary>> = emptyFlow()
        override fun recentStickers(): Flow<List<StickerItem>> = emptyFlow()
        override fun favoriteStickers(): Flow<List<StickerItem>> = emptyFlow()
        override fun trendingSets(): Flow<List<StickerSetSample>> = emptyFlow()
        override fun customEmojiSets(): Flow<List<CustomEmojiSet>> = emptyFlow()
        override fun trendingCustomEmojiSets(): Flow<List<CustomEmojiSetSample>> = emptyFlow()

        override suspend fun stickerSet(setId: Long): Result<List<StickerItem>> {
            requested += setId
            return Result.success(contents[setId].orEmpty())
        }
    }

    private object NoEmoji : EmojiRepository {
        override suspend fun searchEmojis(text: String, languageCodes: List<String>) = emptyList<String>()
        override suspend fun keywordEmojis(word: String, languageCodes: List<String>) = emptyList<String>()
    }

    private object Settings : UnimplementedAppSettings() {
        override val recentEmojis: StateFlow<List<String>> = MutableStateFlow(emptyList())
        override val emojiSkinTones: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    }

    private fun sticker(id: Long) = StickerItem(
        id = id,
        sendFileId = id.toInt(),
        preview = MessageMedia.Sticker(
            stickerFileId = id.toInt(),
            setId = 1,
            fileId = id.toInt(),
            animationFileId = null,
            thumbnailFileId = null,
            width = 512,
            height = 512,
            emoji = "🙂",
        ),
    )

    private fun summary(id: Long) = StickerSetSummary(id = id, title = "Sada $id", cover = null)

    @Test
    fun zmenenaNactenaSadaSePreteZnovu() = runTest(dispatcher) {
        val repository = FakeRepository().apply { contents[1] = listOf(sticker(10)) }
        val viewModel = StickerPickerViewModel(repository, NoEmoji, Settings)
        advanceUntilIdle()

        viewModel.load(StickerTab.Set(summary(1)))
        advanceUntilIdle()

        repository.contents[1] = listOf(sticker(10), sticker(11))
        repository.changes.emit(1)
        // Sada, kterou výběr nenačetl, se nechá být — načte se s první návštěvou.
        repository.changes.emit(2)
        advanceUntilIdle()

        assertEquals(listOf(1L, 1L), repository.requested)
    }
}
