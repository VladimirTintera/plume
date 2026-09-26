package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.core.telegram.UnreadMarks
import eu.tintera.plume.core.telegram.oldestOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun skaceSeNaNejstarsi() {
        // TDLib vrací od nejnovější, skáče se ale od nejstarší — jako u nepřečtených zpráv.
        assertEquals(10L, oldestOf(listOf(30L, 20L, 10L)))
    }

    @Test
    fun bezVysledkuNeniKamSkocit() {
        assertNull(oldestOf(emptyList()))
    }

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
}
