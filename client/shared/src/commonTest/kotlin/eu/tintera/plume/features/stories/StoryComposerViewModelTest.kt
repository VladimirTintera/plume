package eu.tintera.plume.features.stories

import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StoryAudience
import eu.tintera.plume.core.telegram.StoryDraft
import eu.tintera.plume.core.telegram.StoryMedia
import eu.tintera.plume.core.telegram.StoryPostCheck
import eu.tintera.plume.core.telegram.StoryPostResult
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.ui.RenderedStoryVideo
import eu.tintera.plume.core.ui.StoryPhoto
import eu.tintera.plume.core.ui.StoryVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryComposerViewModelTest {

    private class Posting : StoryRepository {
        val drafts = mutableListOf<StoryDraft>()
        override val active: StateFlow<Map<Long, ActiveStories>> = MutableStateFlow(emptyMap())
        override suspend fun story(posterChatId: Long, storyId: Int): MessageMedia.Story = error("nevolá se")
        override suspend fun open(posterChatId: Long, storyId: Int) = Unit
        override suspend fun close(posterChatId: Long, storyId: Int) = Unit
        override suspend fun react(posterChatId: Long, storyId: Int, reaction: String?) = Result.success(Unit)
        override suspend fun reply(posterChatId: Long, storyId: Int, text: String) = Result.success(Unit)
        override suspend fun canPost(): StoryPostCheck = StoryPostCheck.Allowed
        override suspend fun post(draft: StoryDraft): Result<Unit> {
            drafts += draft
            return Result.success(Unit)
        }
        override val postResults: Flow<StoryPostResult> = emptyFlow()
        override suspend fun delete(posterChatId: Long, storyId: Int) = Result.success(Unit)
    }

    private class Renderer(val fail: Boolean = false) : StoryPhoto {
        val calls = mutableListOf<Boolean>()
        override suspend fun render(path: String, fill: Boolean): String {
            calls += fill
            if (fail) error("nejde dekódovat")
            return "$path.story.jpg"
        }
    }

    private class Videos(val durationMs: Long = 30_000, val audio: Boolean = true) : StoryVideo {
        val fills = mutableListOf<Boolean>()
        override val supported = true
        override suspend fun durationMs(path: String) = durationMs
        override suspend fun preview(path: String) = "$path.frame.jpg"
        override suspend fun render(path: String, fill: Boolean): RenderedStoryVideo {
            fills += fill
            return RenderedStoryVideo("$path.story.mp4", durationSeconds = minOf(durationMs, 60_000) / 1000.0, hasAudio = audio)
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun postsRenderedPhotoWithChosenSettings() = runTest {
        val repository = Posting()
        val renderer = Renderer()
        val viewModel = StoryComposerViewModel("foto.jpg", isVideo = false, repository = repository, storyPhoto = renderer, storyVideo = Videos())
        viewModel.setFill(false)
        viewModel.setCaption("Ahoj")
        viewModel.setAudience(AudienceChoice.Everyone)
        viewModel.setKeepOnProfile(true)
        viewModel.setAllowScreenshots(false)
        viewModel.post()
        advanceUntilIdle()

        assertEquals(listOf(false), renderer.calls)
        val draft = repository.drafts.single()
        assertEquals(StoryMedia.Photo("foto.jpg.story.jpg"), draft.media)
        assertEquals(StoryAudience.Everyone, draft.audience)
        assertEquals("Ahoj", draft.caption)
        assertTrue(draft.keepOnProfile)
        assertTrue(draft.protectContent, "zakázané snímky = chráněný obsah")
        assertTrue(viewModel.state.value.posted)
    }

    @Test
    fun selectedWithoutPeopleIsRefused() = runTest {
        val repository = Posting()
        val viewModel = StoryComposerViewModel("foto.jpg", isVideo = false, repository = repository, storyPhoto = Renderer(), storyVideo = Videos())
        viewModel.setAudience(AudienceChoice.Selected)
        viewModel.post()
        advanceUntilIdle()
        assertTrue(repository.drafts.isEmpty())
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun pickedPeopleSwitchAudienceToSelected() = runTest {
        val repository = Posting()
        val viewModel = StoryComposerViewModel("foto.jpg", isVideo = false, repository = repository, storyPhoto = Renderer(), storyVideo = Videos())
        viewModel.setSelected(setOf(5L, 6L))
        viewModel.post()
        advanceUntilIdle()
        assertEquals(StoryAudience.Selected(listOf(5L, 6L)), repository.drafts.single().audience)
    }

    @Test
    fun renderFailureKeepsTheScreenOpen() = runTest {
        val repository = Posting()
        val viewModel = StoryComposerViewModel("foto.jpg", isVideo = false, repository = repository, storyPhoto = Renderer(fail = true), storyVideo = Videos())
        viewModel.post()
        advanceUntilIdle()
        assertTrue(repository.drafts.isEmpty())
        assertNotNull(viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.posting)
    }

    @Test
    fun longSilentVideoIsTrimmedAndPostedAsAnimation() = runTest {
        val repository = Posting()
        val videos = Videos(durationMs = 95_000, audio = false)
        val viewModel = StoryComposerViewModel("klip.mp4", isVideo = true, repository = repository, storyPhoto = Renderer(), storyVideo = videos)
        advanceUntilIdle()
        assertEquals("klip.mp4.frame.jpg", viewModel.state.value.previewPath, "náhled je první snímek")
        assertTrue(viewModel.state.value.trimmed, "delší než minuta → hláška o zkrácení")

        viewModel.post()
        advanceUntilIdle()
        assertEquals(listOf(true), videos.fills)
        assertEquals(StoryMedia.Video("klip.mp4.story.mp4", durationSeconds = 60.0, hasAudio = false), repository.drafts.single().media)
    }
}
