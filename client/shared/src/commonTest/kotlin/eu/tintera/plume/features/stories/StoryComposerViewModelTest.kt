package eu.tintera.plume.features.stories

import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StoryAudience
import eu.tintera.plume.core.telegram.StoryDraft
import eu.tintera.plume.core.telegram.StoryMedia
import eu.tintera.plume.core.telegram.StoryPostCheck
import eu.tintera.plume.core.telegram.StoryPostResult
import eu.tintera.plume.core.telegram.ProfileStoriesPage
import eu.tintera.plume.core.telegram.ReportRequest
import eu.tintera.plume.core.telegram.ReportStep
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.telegram.StoryViewersPage
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

    private class Posting(
        var friends: List<Long> = emptyList(),
        val failSaving: Boolean = false,
    ) : StoryRepository {
        val drafts = mutableListOf<StoryDraft>()
        val savedFriends = mutableListOf<List<Long>>()
        override suspend fun closeFriends(): Result<List<Long>> = Result.success(friends)
        override suspend fun setHidden(chatId: Long, hidden: Boolean) = Result.success(Unit)
        override suspend fun profileStories(chatId: Long, fromStoryId: Int) = Result.success(ProfileStoriesPage(emptyList(), 0, emptySet()))
        override suspend fun author(chatId: Long): ActiveStories? = null
        override suspend fun albums(chatId: Long) = Result.success(emptyList<eu.tintera.plume.core.telegram.StoryAlbum>())
        override suspend fun albumStories(chatId: Long, albumId: Int, offset: Int) = Result.success(ProfileStoriesPage(emptyList(), 0, emptySet()))
        override suspend fun report(posterChatId: Long, storyId: Int, request: ReportRequest) = Result.success<ReportStep>(ReportStep.Done)
        override suspend fun setCloseFriends(userIds: List<Long>): Result<Unit> {
            savedFriends += userIds
            return if (failSaving) Result.failure(IllegalStateException("USER_NOT_MUTUAL_CONTACT")) else Result.success(Unit)
        }
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
        override suspend fun viewers(storyId: Int, offset: String) = Result.success(StoryViewersPage(emptyList(), 0, null))
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
        val starts = mutableListOf<Long>()
        override val supported = true
        override suspend fun durationMs(path: String) = durationMs
        override suspend fun frame(path: String, atMs: Long, edge: Int) = if (atMs == 0L) "$path.frame.jpg" else "$path.frame-$atMs.jpg"
        override suspend fun render(path: String, fill: Boolean, startMs: Long): RenderedStoryVideo {
            fills += fill
            starts += startMs
            return RenderedStoryVideo("$path.story.mp4", durationSeconds = minOf(durationMs - startMs, 60_000) / 1000.0, hasAudio = audio)
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

    @Test
    fun chosenClipAndCoverGoToTheStory() = runTest {
        val repository = Posting()
        val videos = Videos(durationMs = 95_000)
        val viewModel = StoryComposerViewModel("klip.mp4", isVideo = true, repository = repository, storyPhoto = Renderer(), storyVideo = videos)
        advanceUntilIdle()
        val control = assertNotNull(viewModel.video, "u videa je pás úseku")
        assertEquals(60_000, control.trim.segmentMs, "příběh smí minutu, ne 10 s jako profilovka")
        control.moveTo(30_000)
        assertEquals(30_000, control.trim.startMs, "úsek posunutý na 30 s")
        control.chooseCover(45_000)
        advanceUntilIdle()
        assertEquals("klip.mp4.frame-45000.jpg", viewModel.state.value.previewPath, "náhled ukazuje obálku")
        viewModel.post()
        advanceUntilIdle()
        assertEquals(listOf(30_000L), videos.starts)
        val media = repository.drafts.single().media as StoryMedia.Video
        assertEquals(15.0, media.coverSeconds, "obálka od začátku převedeného videa")
    }

    @Test
    fun closeFriendsArePostedOnlyWithSomeoneOnTheList() = runTest {
        val repository = Posting(friends = emptyList())
        val viewModel = StoryComposerViewModel("foto.jpg", isVideo = false, repository = repository, storyPhoto = Renderer(), storyVideo = Videos())
        advanceUntilIdle()
        assertEquals(emptySet(), viewModel.state.value.closeFriends)
        viewModel.setAudience(AudienceChoice.CloseFriends)
        viewModel.post()
        advanceUntilIdle()
        assertTrue(repository.drafts.isEmpty(), "prázdný seznam = příběh nikdo neuvidí")
        assertNotNull(viewModel.state.value.error)

        viewModel.errorShown()
        viewModel.saveCloseFriends(setOf(7L, 8L))
        advanceUntilIdle()
        assertEquals(listOf(listOf(7L, 8L)), repository.savedFriends)
        assertEquals(AudienceChoice.CloseFriends, viewModel.state.value.audience)
        viewModel.post()
        advanceUntilIdle()
        assertEquals(StoryAudience.CloseFriends, repository.drafts.single().audience)
    }

    @Test
    fun failedCloseFriendsSaveIsReverted() = runTest {
        val repository = Posting(friends = listOf(1L), failSaving = true)
        val viewModel = StoryComposerViewModel("foto.jpg", isVideo = false, repository = repository, storyPhoto = Renderer(), storyVideo = Videos())
        advanceUntilIdle()
        viewModel.saveCloseFriends(setOf(1L, 2L))
        advanceUntilIdle()
        assertEquals(setOf(1L), viewModel.state.value.closeFriends)
        assertNotNull(viewModel.state.value.error)
    }
}
