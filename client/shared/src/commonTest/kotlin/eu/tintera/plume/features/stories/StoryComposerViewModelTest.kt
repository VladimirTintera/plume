package eu.tintera.plume.features.stories

import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StoryAudience
import eu.tintera.plume.core.telegram.StoryDraft
import eu.tintera.plume.core.telegram.StoryMedia
import eu.tintera.plume.core.telegram.StoryPostCheck
import eu.tintera.plume.core.telegram.StoryPoster
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.ui.UiText
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
        val targets: List<StoryPoster> = emptyList(),
        val channelCheck: StoryPostCheck = StoryPostCheck.Allowed,
    ) : StoryRepository {
        val drafts = mutableListOf<StoryDraft>()
        val replaced = mutableListOf<Triple<Int, StoryMedia, String>>()
        val covers = mutableListOf<Pair<Int, Double>>()
        override suspend fun editCover(posterChatId: Long, storyId: Int, coverSeconds: Double): Result<Unit> {
            covers += storyId to coverSeconds
            return Result.success(Unit)
        }
        override suspend fun postTargets() = Result.success(targets)
        override suspend fun canPostAs(chatId: Long) = channelCheck
        override suspend fun replaceMedia(posterChatId: Long, storyId: Int, media: StoryMedia, caption: String): Result<Unit> {
            replaced += Triple(storyId, media, caption)
            return Result.success(Unit)
        }
        val savedFriends = mutableListOf<List<Long>>()
        override suspend fun closeFriends(): Result<List<Long>> = Result.success(friends)
        override suspend fun setHidden(chatId: Long, hidden: Boolean) = Result.success(Unit)
        override suspend fun profileStories(chatId: Long, fromStoryId: Int) = Result.success(ProfileStoriesPage(emptyList(), 0, emptySet()))
        override suspend fun author(chatId: Long): ActiveStories? = null
        override suspend fun albums(chatId: Long) = Result.success(emptyList<eu.tintera.plume.core.telegram.StoryAlbum>())
        override suspend fun albumStories(chatId: Long, albumId: Int, offset: Int) = Result.success(ProfileStoriesPage(emptyList(), 0, emptySet()))
        override suspend fun createAlbum(posterChatId: Long, name: String, storyIds: List<Int>) = Result.success(eu.tintera.plume.core.telegram.StoryAlbum(1, name))
        override suspend fun setInAlbum(chatId: Long, albumId: Int, storyId: Int, inAlbum: Boolean) = Result.success(Unit)
        override suspend fun renameAlbum(chatId: Long, albumId: Int, name: String) = Result.success(eu.tintera.plume.core.telegram.StoryAlbum(albumId, name))
        override suspend fun deleteAlbum(chatId: Long, albumId: Int) = Result.success(Unit)
        override suspend fun reorderAlbums(chatId: Long, albumIds: List<Int>) = Result.success(Unit)
        override suspend fun reorderAlbumStories(chatId: Long, albumId: Int, storyIds: List<Int>) = Result.success(Unit)
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

    /** Úprava vymění médium a popisek, nic nového nezveřejní. */
    @Test
    fun editReplacesMediaInsteadOfPosting() = runTest {
        val repository = Posting()
        val viewModel = StoryComposerViewModel(
            "nova.jpg",
            isVideo = false,
            repository = repository,
            storyPhoto = Renderer(),
            storyVideo = Videos(),
            edit = StoryEditTarget(posterChatId = 7, storyId = 42, caption = "Starý popisek"),
        )
        assertEquals("Starý popisek", viewModel.state.value.caption)
        viewModel.setCaption("Nový")
        viewModel.post()
        advanceUntilIdle()

        assertTrue(repository.drafts.isEmpty())
        assertEquals(listOf(Triple(42, StoryMedia.Photo("nova.jpg.story.jpg") as StoryMedia, "Nový")), repository.replaced)
        assertTrue(viewModel.state.value.posted)
    }

    /** Jen obálka: nic se nepřevádí ani nezveřejňuje, jde čas vybraného snímku. */
    @Test
    fun coverOnlySavesCoverTimestamp() = runTest {
        val repository = Posting()
        val videos = Videos(durationMs = 20_000)
        val viewModel = StoryComposerViewModel(
            "pribeh.mp4",
            isVideo = true,
            repository = repository,
            storyPhoto = Renderer(),
            storyVideo = videos,
            edit = StoryEditTarget(posterChatId = 7, storyId = 42, caption = "", coverOnly = true),
        )
        advanceUntilIdle()
        val control = assertNotNull(viewModel.video)
        control.chooseCover(5_000)
        viewModel.post()
        advanceUntilIdle()

        assertEquals(listOf(42 to 5.0), repository.covers)
        assertTrue(repository.drafts.isEmpty())
        assertTrue(repository.replaced.isEmpty())
        assertTrue(videos.fills.isEmpty(), "video se nepřevádí")
        assertTrue(viewModel.state.value.posted)
    }

    /** Za kanál: draft nese kanál a viditelnost se nenastavuje (vidí ho odběratelé). */
    @Test
    fun postsAsChannel() = runTest {
        val channel = StoryPoster(chatId = -100, title = "Kanál", avatarFileId = null, kind = ChatKind.Channel)
        val repository = Posting(targets = listOf(channel))
        val viewModel = StoryComposerViewModel("foto.jpg", isVideo = false, repository = repository, storyPhoto = Renderer(), storyVideo = Videos())
        advanceUntilIdle()
        assertEquals(listOf(channel), viewModel.state.value.posters)
        viewModel.setAudience(AudienceChoice.Selected)
        viewModel.setPoster(channel)
        advanceUntilIdle()
        viewModel.post()
        advanceUntilIdle()

        val draft = repository.drafts.single()
        assertEquals(-100L, draft.posterChatId)
        assertEquals(StoryAudience.Everyone, draft.audience)
    }

    /** Kanál bez boostů: zveřejnit nejde a obrazovka řekne proč. */
    @Test
    fun deniedChannelIsRefused() = runTest {
        val channel = StoryPoster(chatId = -100, title = "Kanál", avatarFileId = null, kind = ChatKind.Channel)
        val reason = UiText.Raw("Chce to boosty")
        val repository = Posting(targets = listOf(channel), channelCheck = StoryPostCheck.Denied(reason))
        val viewModel = StoryComposerViewModel("foto.jpg", isVideo = false, repository = repository, storyPhoto = Renderer(), storyVideo = Videos())
        advanceUntilIdle()
        viewModel.setPoster(channel)
        advanceUntilIdle()
        assertEquals(reason, viewModel.state.value.posterDenied)
        viewModel.post()
        advanceUntilIdle()

        assertTrue(repository.drafts.isEmpty())
        assertEquals(reason, viewModel.state.value.error)
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
