package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.core.telegram.UnreadMarks
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Nepřečtené zmínky, reakce a hlasy v anketě: odkud se skáče a které zprávy se hlásí jako zhlédnuté.
 *
 * Reakce jsou na VLASTNÍCH zprávách, a ty se jako zhlédnuté dřív nehlásily vůbec — tlačítko
 * „❤" by pak nezhaslo ani po doskočení na zprávu.
 */
class UnreadMarksTest {

    private fun message(
        id: Long,
        outgoing: Boolean,
        unreadReactions: Boolean = false,
        unreadPollVotes: Boolean = false,
        album: Long = 0L,
    ) = ChatMessage(
        id = id,
        isOutgoing = outgoing,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = "ahoj",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = if (outgoing) SendState.Sent else null,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
        mediaAlbumId = album,
        hasUnreadReactions = unreadReactions,
        hasUnreadPollVotes = unreadPollVotes,
    )

    @Test
    fun pocetPodleDruhu() {
        val marks = UnreadMarks(mentions = 2, reactions = 5, pollVotes = 7)
        assertEquals(2, marks.count(UnreadMarkKind.Mention))
        assertEquals(5, marks.count(UnreadMarkKind.Reaction))
        assertEquals(7, marks.count(UnreadMarkKind.PollVote))
    }

    @Test
    fun vlastniZpravaSNeprectenouReakciSeHlasi() {
        val state = ConversationState(
            messages = listOf(
                message(3, outgoing = true, unreadReactions = true),
                message(2, outgoing = true),
                message(1, outgoing = false),
            ),
        )
        assertEquals(listOf(3L, 1L), state.messagesToView(setOf(1L, 2L, 3L)))
    }

    @Test
    fun vlastniAnketaSNeprectenymiHlasySeHlasi() {
        // Hlasy ve vlastní anketě jsou taky na odchozí zprávě — bez hlášení by tlačítko
        // s anketou nezhaslo ani po doskočení na ni.
        val state = ConversationState(
            messages = listOf(
                message(3, outgoing = true, unreadPollVotes = true),
                message(2, outgoing = true),
            ),
        )
        assertEquals(listOf(3L), state.messagesToView(setOf(2L, 3L)))
    }

    @Test
    fun neviditelnaZpravaSeNehlasi() {
        val state = ConversationState(
            messages = listOf(
                message(3, outgoing = true, unreadReactions = true),
                message(1, outgoing = false),
            ),
        )
        assertEquals(listOf(1L), state.messagesToView(setOf(1L)))
    }

    @Test
    fun albumSeHlasiCele() {
        // Album je jedna položka s klíčem nejstarší zprávy.
        val state = ConversationState(
            messages = listOf(
                message(12, outgoing = false, album = 7L),
                message(11, outgoing = false, album = 7L),
                message(10, outgoing = false, album = 7L),
            ),
        )
        assertEquals(listOf(12L, 11L, 10L), state.messagesToView(setOf(10L)))
    }

    @Test
    fun vlastniZpravaNadHraniciNeprectenychSeHlasi() {
        // Téma s nepřečtenými, nad hranicí jen vlastní zprávy — bez jejich hlášení by se
        // hranice nepohnula a počet nepřečtených by zůstal napořád.
        val state = ConversationState(
            unreadBoundaryId = 12,
            messages = listOf(
                message(14, outgoing = true),
                message(13, outgoing = true),
                message(12, outgoing = false),
                message(11, outgoing = true),
            ),
        )
        assertEquals(listOf(14L, 13L, 12L), state.messagesToView(setOf(11L, 12L, 13L, 14L)))
    }

    @Test
    fun bezHraniceSeVlastniZpravaNehlasi() {
        val state = ConversationState(messages = listOf(message(14, outgoing = true), message(12, outgoing = false)))
        assertEquals(listOf(12L), state.messagesToView(setOf(12L, 14L)))
    }

    @Test
    fun zpravySNeprectenouReakciNeboHlasem() {
        val state = ConversationState(
            messages = listOf(
                message(3, outgoing = true, unreadReactions = true),
                message(2, outgoing = true, unreadPollVotes = true),
                message(1, outgoing = false),
            ),
        )
        assertEquals(setOf(3L, 2L), state.unreadMarkedIds())
    }
}
