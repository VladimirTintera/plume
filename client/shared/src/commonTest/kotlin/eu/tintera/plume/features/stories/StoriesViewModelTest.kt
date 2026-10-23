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
import eu.tintera.plume.core.telegram.ReportOption
import eu.tintera.plume.core.telegram.ReportOptionId
import eu.tintera.plume.core.telegram.ReportRequest
import eu.tintera.plume.core.telegram.ReportStep
import eu.tintera.plume.core.telegram.StoryAlbum
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.telegram.pinnedAfter
import eu.tintera.plume.core.telegram.StoryAudience
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

    private class FakeStories(
        var failReaction: Boolean = false,
        /** Příběh je vlastní a dá se spravovat (popisek, viditelnost, profil). */
        val own: Boolean = false,
        var failManage: Boolean = false,
        val storyReactions: List<String> = emptyList(),
    ) : StoryRepository {
        override suspend fun availableReactions() = Result.success(storyReactions)
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
            canBeReplied = !own,
            isOwn = own,
            canBeEdited = own,
            audience = StoryAudience.Contacts.takeIf { own },
            canToggleOnProfile = own,
        )

        val audiences = mutableListOf<StoryAudience>()
        val onProfile = mutableListOf<Boolean>()
        var pinnedIds = listOf(9)
        val captions = mutableListOf<String>()

        private fun manage(): Result<Unit> =
            if (failManage) Result.failure(IllegalStateException("STORY_ERROR")) else Result.success(Unit)

        override suspend fun setAudience(storyId: Int, audience: StoryAudience): Result<Unit> {
            audiences += audience
            return manage()
        }

        override suspend fun setOnProfile(posterChatId: Long, storyId: Int, onProfile: Boolean): Result<Unit> {
            this.onProfile += onProfile
            return manage()
        }

        override suspend fun pinnedStoryIds(chatId: Long): Result<List<Int>> = Result.success(pinnedIds)

        override suspend fun setPinned(chatId: Long, storyId: Int, pinned: Boolean): Result<Unit> =
            manage().onSuccess { pinnedIds = pinnedAfter(pinnedIds, storyId, pinned) }

        override suspend fun editCaption(posterChatId: Long, storyId: Int, caption: String): Result<Unit> {
            captions += caption
            return manage()
        }

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

        val reports = mutableListOf<Pair<Int, ReportRequest>>()

        /** Napřed důvod, po výběru hotovo — jako server. */
        override suspend fun report(posterChatId: Long, storyId: Int, request: ReportRequest): Result<ReportStep> {
            reports += storyId to request
            return Result.success(
                if (request.optionId == null) {
                    ReportStep.ChooseOption("Proč?", listOf(ReportOption(ReportOptionId(byteArrayOf(1)), "Spam")))
                } else {
                    ReportStep.Done
                },
            )
        }

        var failAlbums = false
        val albumCalls = mutableListOf<String>()

        override suspend fun albums(chatId: Long) = Result.success(listOf(StoryAlbum(1, "Léto"), StoryAlbum(2, "Hory")))

        override suspend fun createAlbum(posterChatId: Long, name: String, storyIds: List<Int>): Result<StoryAlbum> {
            albumCalls += "create $name $storyIds"
            return Result.success(StoryAlbum(3, name))
        }

        override suspend fun setInAlbum(chatId: Long, albumId: Int, storyId: Int, inAlbum: Boolean): Result<Unit> {
            albumCalls += "set $albumId $storyId $inAlbum"
            return if (failAlbums) Result.failure(IllegalStateException("STORY_ID_INVALID")) else Result.success(Unit)
        }

        override suspend fun renameAlbum(chatId: Long, albumId: Int, name: String) = Result.success(StoryAlbum(albumId, name))

        override suspend fun reorderAlbums(chatId: Long, albumIds: List<Int>): Result<Unit> {
            albumCalls += "reorder $albumIds"
            return if (failAlbums) Result.failure(IllegalStateException("ALBUM_ID_INVALID")) else Result.success(Unit)
        }

        override suspend fun deleteAlbum(chatId: Long, albumId: Int): Result<Unit> {
            albumCalls += "delete $albumId"
            return Result.success(Unit)
        }

        /** Album o třech příbězích, server je dává po dvou. */
        override suspend fun albumStories(chatId: Long, albumId: Int, offset: Int): Result<ProfileStoriesPage> {
            if (failAlbums) return Result.failure(IllegalStateException("ALBUM_ID_INVALID"))
            val all = listOf(10, 11, 12).map { story(chatId, it) }
            return Result.success(ProfileStoriesPage(all.drop(offset).take(2), all.size, emptySet()))
        }

        override suspend fun reorderAlbumStories(chatId: Long, albumId: Int, storyIds: List<Int>): Result<Unit> {
            albumCalls += "order $albumId $storyIds"
            return if (failAlbums) Result.failure(IllegalStateException("STORY_ID_INVALID")) else Result.success(Unit)
        }

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
    fun reportingAsksForReasonThenConfirms() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.reportStory(key)
        advanceUntilIdle()
        val step = viewModel.report.wizard.value?.step as? ReportStep.ChooseOption
        assertNotNull(step, "napřed důvod od serveru")
        viewModel.report.choose(step.options.single())
        advanceUntilIdle()
        assertNull(viewModel.report.wizard.value, "po nahlášení se průvodce zavře")
        assertNotNull(viewModel.notice.value, "výsledek jde do pilulky")
        assertEquals(listOf(1, 1), repository.reports.map { it.first }, "hlásí se příběh, ne chat")
    }

    @Test
    fun albumsToggleCreateAndDelete() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.openAlbums(key)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), viewModel.albums.value?.albums?.map { it.id })
        viewModel.toggleAlbum(1)
        assertEquals(setOf(1), viewModel.albums.value?.inAlbums, "přepne se hned")
        advanceUntilIdle()
        assertEquals(listOf(1), viewModel.state.value.pages[key]?.albumIds, "a zapíše se i k příběhu")
        viewModel.createAlbum("  Výlety s dlouhým názvem  ")
        advanceUntilIdle()
        assertEquals("create Výlety s dlo [1]", repository.albumCalls[1], "název oříznutý na 12 znaků, rovnou s příběhem")
        assertEquals(setOf(1, 3), viewModel.albums.value?.inAlbums)
        viewModel.deleteAlbum(1)
        advanceUntilIdle()
        assertEquals(listOf(2, 3), viewModel.albums.value?.albums?.map { it.id })
        assertEquals(setOf(3), viewModel.albums.value?.inAlbums)
    }

    @Test
    fun albumsReorderAndRevertOnFailure() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.openAlbums(key)
        advanceUntilIdle()
        viewModel.reorderAlbums(listOf(2, 1))
        assertEquals(listOf(2, 1), viewModel.albums.value?.albums?.map { it.id }, "přeskládá se hned")
        advanceUntilIdle()
        assertEquals(listOf("reorder [2, 1]"), repository.albumCalls)
        viewModel.reorderAlbums(listOf(2, 1))
        advanceUntilIdle()
        assertEquals(1, repository.albumCalls.size, "beze změny se neposílá")

        repository.failAlbums = true
        viewModel.reorderAlbums(listOf(1, 2))
        advanceUntilIdle()
        assertEquals(listOf(2, 1), viewModel.albums.value?.albums?.map { it.id }, "při chybě zpátky")
        assertNotNull(viewModel.albums.value?.error)
    }

    @Test
    fun albumStoriesLoadAllPagesAndReorder() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.openAlbums(key)
        advanceUntilIdle()
        viewModel.openAlbumStories(2)
        assertEquals(AlbumStoriesOrder(2), viewModel.albums.value?.storiesOrder, "napřed načítání")
        advanceUntilIdle()
        assertEquals(listOf(10, 11, 12), viewModel.albums.value?.storiesOrder?.stories?.map { it.storyId }, "všechny stránky")

        viewModel.reorderAlbumStories(listOf(10, 11, 12))
        advanceUntilIdle()
        assertNull(viewModel.albums.value?.storiesOrder)
        assertEquals(emptyList(), repository.albumCalls, "beze změny se neposílá")

        viewModel.openAlbumStories(2)
        advanceUntilIdle()
        viewModel.reorderAlbumStories(listOf(12, 10, 11))
        advanceUntilIdle()
        assertEquals(listOf("order 2 [12, 10, 11]"), repository.albumCalls)

        viewModel.openAlbumStories(2)
        advanceUntilIdle()
        repository.failAlbums = true
        viewModel.reorderAlbumStories(listOf(11, 12, 10))
        advanceUntilIdle()
        assertNotNull(viewModel.albums.value?.error, "chyba v panelu")
    }

    @Test
    fun failedAlbumStoriesLoadClosesOrder() = runTest {
        val repository = FakeStories()
        val viewModel = viewModel(repository)
        viewModel.openAlbums(key)
        advanceUntilIdle()
        repository.failAlbums = true
        viewModel.openAlbumStories(1)
        advanceUntilIdle()
        assertNull(viewModel.albums.value?.storiesOrder)
        assertNotNull(viewModel.albums.value?.error)
    }

    @Test
    fun failedAlbumToggleIsReverted() = runTest {
        val repository = FakeStories().apply { failAlbums = true }
        val viewModel = viewModel(repository)
        viewModel.openAlbums(key)
        advanceUntilIdle()
        viewModel.toggleAlbum(2)
        advanceUntilIdle()
        assertEquals(emptySet(), viewModel.albums.value?.inAlbums)
        assertNotNull(viewModel.albums.value?.error, "chyba v panelu — pilulku by panel zakryl")
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

    @Test
    fun ownStoryLoadsPinnedAndTogglesProfileAndPin() = runTest {
        val repository = FakeStories(own = true)
        val viewModel = viewModel(repository)
        assertEquals(listOf(9), viewModel.pinned.value[7], "připnuté vlastního autora se načtou")

        viewModel.setOnProfile(key, true)
        assertEquals(true, viewModel.state.value.pages[key]?.isOnProfile, "přepne se hned")
        advanceUntilIdle()
        assertEquals(listOf(true), repository.onProfile)

        viewModel.setPinned(key, true)
        assertEquals(listOf(1, 9), viewModel.pinned.value[7], "nově připnutý je první")
        advanceUntilIdle()
        assertEquals(listOf(1, 9), repository.pinnedIds)

        // Odebrání z profilu ho vyřadí i z připnutých.
        viewModel.setOnProfile(key, false)
        assertEquals(listOf(9), viewModel.pinned.value[7])
    }

    @Test
    fun failedProfileToggleIsRevertedWithPins() = runTest {
        val repository = FakeStories(own = true)
        val viewModel = viewModel(repository)
        viewModel.setOnProfile(key, true)
        advanceUntilIdle()
        viewModel.setPinned(key, true)
        advanceUntilIdle()

        repository.failManage = true
        viewModel.setOnProfile(key, false)
        advanceUntilIdle()
        assertEquals(true, viewModel.state.value.pages[key]?.isOnProfile)
        assertEquals(listOf(1, 9), viewModel.pinned.value[7])
        assertNotNull(viewModel.notice.value)
    }

    @Test
    fun audienceChangesAndRevertsOnFailure() = runTest {
        val repository = FakeStories(own = true)
        val viewModel = viewModel(repository)
        viewModel.openAudience(key)
        advanceUntilIdle()
        assertEquals(StoryAudience.Contacts, viewModel.audience.value?.audience)
        assertEquals(0, viewModel.audience.value?.closeFriends)

        viewModel.setAudience(StoryAudience.Everyone)
        assertNull(viewModel.audience.value, "výběr se zavře")
        advanceUntilIdle()
        assertEquals(StoryAudience.Everyone, viewModel.state.value.pages[key]?.audience)
        assertEquals(listOf<StoryAudience>(StoryAudience.Everyone), repository.audiences)

        repository.failManage = true
        viewModel.openAudience(key)
        viewModel.setAudience(StoryAudience.Contacts)
        advanceUntilIdle()
        assertEquals(StoryAudience.Everyone, viewModel.state.value.pages[key]?.audience)
    }

    /** Nabídka reakcí u příběhu je od TDLib (`getStoryAvailableReactions`), i s vlastními emoji. */
    @Test
    fun reactionChoicesComeFromStoryReactions() = runTest {
        val repository = FakeStories(storyReactions = listOf("👍", "🔥", "custom:42"))
        val viewModel = viewModel(repository)
        assertEquals(listOf("👍", "🔥", "custom:42"), viewModel.reactionChoices.value)
    }

    /** Bez reakcí od TDLib (selhání, prázdné) zůstává nabídka rychlé reakce — tady prázdná. */
    @Test
    fun emptyStoryReactionsFallBack() = runTest {
        val viewModel = viewModel(FakeStories())
        assertEquals(emptyList(), viewModel.reactionChoices.value)
    }

    /** „Vybraní lidé" přes výběr lidí; prázdný výběr nic nezmění. */
    @Test
    fun selectedPeopleFromPicker() = runTest {
        val repository = FakeStories(own = true)
        val viewModel = viewModel(repository)
        viewModel.openAudience(key)
        advanceUntilIdle()
        viewModel.pickPeople()
        assertNull(viewModel.audience.value, "sheet ustoupí výběru lidí")
        assertNotNull(viewModel.pickingPeople.value)

        viewModel.confirmPeople(emptySet())
        advanceUntilIdle()
        assertEquals(emptyList(), repository.audiences)

        viewModel.openAudience(key)
        viewModel.pickPeople()
        viewModel.confirmPeople(setOf(11L, 12L))
        advanceUntilIdle()
        assertNull(viewModel.pickingPeople.value)
        assertEquals(listOf<StoryAudience>(StoryAudience.Selected(listOf(11L, 12L))), repository.audiences)
    }

    /** Stejná volba nic neposílá. */
    @Test
    fun sameAudienceSendsNothing() = runTest {
        val repository = FakeStories(own = true)
        val viewModel = viewModel(repository)
        viewModel.openAudience(key)
        viewModel.setAudience(StoryAudience.Contacts)
        advanceUntilIdle()
        assertEquals(emptyList(), repository.audiences)
    }

    @Test
    fun captionIsTrimmedSavedAndRevertedOnFailure() = runTest {
        val repository = FakeStories(own = true)
        val viewModel = viewModel(repository)
        viewModel.openCaptionEdit(key)
        viewModel.editCaption("  Na horách  ")
        advanceUntilIdle()
        assertEquals("Na horách", viewModel.state.value.pages[key]?.caption)
        assertEquals(listOf("Na horách"), repository.captions)
        assertNull(viewModel.captionEdit.value)

        repository.failManage = true
        viewModel.openCaptionEdit(key)
        viewModel.editCaption("")
        advanceUntilIdle()
        assertEquals("Na horách", viewModel.state.value.pages[key]?.caption)
    }

    /** Cizí příběh spravovat nejde — dialogy se neotevřou. */
    @Test
    fun foreignStoryCannotBeManaged() = runTest {
        val viewModel = viewModel(FakeStories())
        viewModel.openCaptionEdit(key)
        viewModel.openAudience(key)
        assertNull(viewModel.captionEdit.value)
        assertNull(viewModel.audience.value)
        assertNull(viewModel.pinned.value[7])
    }
}
