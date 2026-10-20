package eu.tintera.plume.features.stories

import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StoryListKind
import eu.tintera.plume.core.telegram.StoryRef
import eu.tintera.plume.core.telegram.StoryDraft
import eu.tintera.plume.core.telegram.StoryPostCheck
import eu.tintera.plume.core.telegram.StoryPostResult
import eu.tintera.plume.core.telegram.ProfileStoriesPage
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.telegram.StoryViewer
import eu.tintera.plume.core.telegram.StoryViewersPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StoriesViewModelTest {

    private class FakeStories(var failReaction: Boolean = false) : StoryRepository {
        override val active: StateFlow<Map<Long, ActiveStories>> = MutableStateFlow(
            mapOf(
                7L to ActiveStories(
                    chatId = 7,
                    title = "Autor",
                    avatarFileId = null,
                    kind = ChatKind.Private,
                    list = StoryListKind.Main,
                    order = 1,
                    maxReadStoryId = 0,
                    stories = listOf(StoryRef(id = 1, date = 1)),
                ),
            ),
        )
        val opened = mutableListOf<Int>()
        val reactions = mutableListOf<String?>()
        val replies = mutableListOf<String>()

        override suspend fun story(posterChatId: Long, storyId: Int) = MessageMedia.Story(
            posterChatId = posterChatId,
            storyId = storyId,
            posterName = "Autor",
            content = null,
            caption = "",
            isLive = false,
            isAvailable = true,
            viaMention = false,
            canBeReplied = true,
        )

        override suspend fun open(posterChatId: Long, storyId: Int) {
            opened += storyId
        }

        override suspend fun close(posterChatId: Long, storyId: Int) = Unit

        override suspend fun react(posterChatId: Long, storyId: Int, reaction: String?): Result<Unit> {
            reactions += reaction
            return if (failReaction) Result.failure(IllegalStateException("REACTION_INVALID")) else Result.success(Unit)
        }

        override suspend fun reply(posterChatId: Long, storyId: Int, text: String): Result<Unit> {
            replies += text
            return Result.success(Unit)
        }

        val deleted = mutableListOf<Int>()

        override suspend fun canPost(): StoryPostCheck = StoryPostCheck.Allowed

        override suspend fun post(draft: StoryDraft): Result<Unit> = Result.success(Unit)

        override val postResults: Flow<StoryPostResult> = emptyFlow()

        override suspend fun delete(posterChatId: Long, storyId: Int): Result<Unit> {
            deleted += storyId
            return Result.success(Unit)
        }

        override suspend fun closeFriends(): Result<List<Long>> = Result.success(emptyList())

        override suspend fun setCloseFriends(userIds: List<Long>): Result<Unit> = Result.success(Unit)

        override suspend fun profileStories(chatId: Long, fromStoryId: Int) =
            Result.success(ProfileStoriesPage(emptyList(), 0, emptySet()))

        override suspend fun author(chatId: Long): ActiveStories? = active.value[chatId]?.copy(stories = emptyList())

        val hidden = mutableListOf<Pair<Long, Boolean>>()

        override suspend fun setHidden(chatId: Long, hidden: Boolean): Result<Unit> {
            this.hidden += chatId to hidden
            return Result.success(Unit)
        }

        val offsets = mutableListOf<String>()

        /** Dvě stránky; druhá opakuje diváka z první (server to nevylučuje). */
        override suspend fun viewers(storyId: Int, offset: String): Result<StoryViewersPage> {
            offsets += offset
            fun viewer(id: Long, reaction: String? = null) =
                StoryViewer(chatId = id, title = "Divák $id", avatarFileId = null, kind = ChatKind.Private, date = 1, reaction = reaction)
            return Result.success(
                if (offset.isEmpty()) {
                    StoryViewersPage(listOf(viewer(1, "❤"), viewer(2)), totalCount = 3, nextOffset = "dalsi")
                } else {
                    StoryViewersPage(listOf(viewer(2), viewer(3)), totalCount = 3, nextOffset = null)
                },
            )
        }
    }

    private val key = StoryKey(7, 1)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(repository: FakeStories) =
        StoriesViewModel(startChatId = 7, repository = repository, appScope = this).also { advanceUntilIdle() }

    @Test
    fun openingCountsTheViewAndLoadsTheStory() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        assertEquals(listOf(1), repository.opened)
        assertNotNull(viewModel.state.value.pages[key])
    }

    @Test
    fun sameReactionTwiceRemovesIt() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.react(key, "❤")
        assertEquals("❤", viewModel.state.value.pages[key]?.chosenReaction, "přepne se hned")
        advanceUntilIdle()
        viewModel.react(key, "❤")
        advanceUntilIdle()
        assertNull(viewModel.state.value.pages[key]?.chosenReaction)
        assertEquals(listOf("❤", null), repository.reactions)
    }

    @Test
    fun failedReactionIsRevertedAndReported() = runTest {
        val repository = FakeStories(failReaction = true)
        val viewModel = viewModel(repository)
        viewModel.react(key, "👍")
        advanceUntilIdle()
        assertNull(viewModel.state.value.pages[key]?.chosenReaction)
        assertNotNull(viewModel.notice.value)
    }

    @Test
    fun deletingTheOnlyStoryClosesTheViewer() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.delete(key)
        advanceUntilIdle()
        assertEquals(listOf(1), repository.deleted)
        assertEquals(true, viewModel.state.value.closed)
    }

    @Test
    fun viewersArePagedWithoutDuplicates() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.openViewers(key)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.viewers.value?.viewers?.map { it.chatId })
        viewModel.loadMoreViewers()
        advanceUntilIdle()
        val state = viewModel.viewers.value
        assertEquals(listOf(1L, 2L, 3L), state?.viewers?.map { it.chatId }, "divák 2 jen jednou")
        assertNull(state?.nextOffset)
        viewModel.loadMoreViewers()
        advanceUntilIdle()
        assertEquals(listOf("", "dalsi"), repository.offsets, "po poslední stránce se už neptá")
        viewModel.closeViewers()
        assertNull(viewModel.viewers.value)
    }

    @Test
    fun hidingFlipsTheAuthorBetweenLists() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.toggleHidden(7)
        advanceUntilIdle()
        assertEquals(listOf(7L to true), repository.hidden)
        assertEquals(true, viewModel.state.value.posters.single().isHidden, "položka nabídky se přepne")
        assertNotNull(viewModel.notice.value)
        viewModel.toggleHidden(7)
        advanceUntilIdle()
        assertEquals(listOf(7L to true, 7L to false), repository.hidden)
        assertEquals(false, viewModel.state.value.posters.single().isHidden)
    }

    @Test
    fun profileStoriesOpenAtTheTappedOneWithoutArchiveMenu() = runTest {
        val repository = FakeStories()
        val viewModel = StoriesViewModel(startChatId = 7, storyIds = listOf(30, 20, 10), startStoryId = 20, repository = repository, appScope = this)
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(StoryKey(7, 20), state.current)
        assertEquals(listOf(20), repository.opened, "počítá se zhlédnutí jen otevřeného")
        val author = state.posters.single()
        assertEquals(false, author.canBeArchived || author.isHidden, "z profilu se do archivu neskrývá")
        assertEquals(0, author.unreadCount)
        viewModel.next()
        viewModel.next()
        assertEquals(true, viewModel.state.value.closed, "za posledním z mřížky konec, žádný další autor")
    }

    @Test
    fun replyIsTrimmedAndConfirmed() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        var result: Boolean? = null
        viewModel.reply(key, "  ahoj  ") { result = it }
        advanceUntilIdle()
        assertEquals(listOf("ahoj"), repository.replies)
        assertEquals(true, result)
        assertNotNull(viewModel.notice.value)

        viewModel.reply(key, "   ") { result = false }
        advanceUntilIdle()
        assertEquals(listOf("ahoj"), repository.replies, "prázdná odpověď se neposílá")
    }
}
