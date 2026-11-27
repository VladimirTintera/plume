package eu.tintera.plume.core.stories

import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.StoryListKind
import eu.tintera.plume.core.telegram.StoryRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryCursorTest {

    private fun author(
        chatId: Long,
        storyIds: List<Int>,
        maxRead: Int = 0,
        order: Long = chatId,
        list: StoryListKind? = StoryListKind.Main,
    ) = ActiveStories(
        chatId = chatId,
        title = "Autor $chatId",
        avatarFileId = null,
        kind = ChatKind.Private,
        list = list,
        order = order,
        maxReadStoryId = maxRead,
        stories = storyIds.map { StoryRef(id = it, date = it) },
    )

    @Test
    fun startsAtFirstUnreadStory() {
        assertEquals(1, author(1, listOf(10, 11, 12), maxRead = 10).firstToShow)
        assertEquals(0, author(1, listOf(10, 11), maxRead = 11).firstToShow, "vše zhlédnuté → od začátku")
        assertEquals(1, author(1, listOf(10, 11, 12), maxRead = 11).unreadCount)
    }

    @Test
    fun closeFriendsRingOnlyForUnreadCloseFriendsStory() {
        val base = author(1, listOf(10, 11), maxRead = 10)
        val friendsOld = base.copy(stories = listOf(StoryRef(10, 10, isForCloseFriends = true), StoryRef(11, 11)))
        val friendsNew = base.copy(stories = listOf(StoryRef(10, 10), StoryRef(11, 11, isForCloseFriends = true)))
        assertEquals(false, friendsOld.hasUnreadForCloseFriends, "zhlédnutý příběh pro blízké barvu nemění")
        assertEquals(true, friendsNew.hasUnreadForCloseFriends)
    }

    @Test
    fun postersFollowListOrderAndStayInTheirList() {
        val all = listOf(
            author(1, listOf(1), order = 5),
            author(2, listOf(1), order = 9),
            author(3, listOf(1), order = 7, list = StoryListKind.Archive),
            author(4, listOf(1), order = 1, list = null),
        ).associateBy { it.chatId }

        assertEquals(listOf(2L, 1L), viewerPosters(all, startChatId = 1).map { it.chatId })
        assertEquals(listOf(3L), viewerPosters(all, startChatId = 3).map { it.chatId })
        assertEquals(listOf(4L), viewerPosters(all, startChatId = 4).map { it.chatId }, "bez seznamu jen on sám")
        assertTrue(viewerPosters(all, startChatId = 99).isEmpty())
    }

    @Test
    fun nextWalksStoriesThenPostersThenCloses() {
        var state = StoriesState(posters = listOf(author(1, listOf(10, 11)), author(2, listOf(20))))
        assertEquals(StoryKey(1, 10), state.current)
        state = state.next()
        assertEquals(StoryKey(1, 11), state.current)
        state = state.next()
        assertEquals(StoryKey(2, 20), state.current)
        state = state.next()
        assertTrue(state.closed)
    }

    @Test
    fun previousReturnsWhereThePreviousPosterEnded() {
        var state = StoriesState(posters = listOf(author(1, listOf(10, 11)), author(2, listOf(20, 21))))
        state = state.next().next()
        assertEquals(StoryKey(2, 20), state.current)
        state = state.previous()
        assertEquals(StoryKey(1, 11), state.current, "u předchozího autora tam, kde se skončilo")
        state = state.previous().previous()
        assertEquals(StoryKey(1, 10), state.current)
        assertEquals(1, state.restart, "u úplně prvního se příběh pustí znovu")
    }

    @Test
    fun swipingKeepsEachPostersPosition() {
        var state = StoriesState(posters = listOf(author(1, listOf(10, 11, 12), maxRead = 11), author(2, listOf(20))))
        assertEquals(StoryKey(1, 12), state.current, "otevře se na nezhlédnutém")
        state = state.showPoster(1)
        assertEquals(StoryKey(2, 20), state.current)
        state = state.showPoster(0)
        assertEquals(StoryKey(1, 12), state.current)
        assertEquals(state, state.showPoster(5), "mimo rozsah se nic nemění")
    }

    @Test
    fun deletedStoryIsRemovedAndViewerMovesOn() {
        var state = StoriesState(posters = listOf(author(1, listOf(10, 11, 12)), author(2, listOf(20))))
        state = state.next()
        assertEquals(StoryKey(1, 11), state.current)
        state = state.withoutStory(StoryKey(1, 11))
        assertEquals(StoryKey(1, 12), state.current, "na místě smazaného je další")
        state = state.withoutStory(StoryKey(1, 12))
        assertEquals(StoryKey(1, 10), state.current, "smazaný poslední → předchozí")
        state = state.withoutStory(StoryKey(1, 10))
        assertEquals(StoryKey(2, 20), state.current, "autor bez příběhů zmizí")
        state = state.withoutStory(StoryKey(2, 20))
        assertTrue(state.closed)
    }
}
