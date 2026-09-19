package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pořadí v pruhu připnutých: ťuknutí vede ke starší, za nejstarší zase nejnovější. */
class PinnedMessagesBarTest {

    private fun message(id: Long) = ChatMessage(
        id = id,
        isOutgoing = false,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = "připnuto",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = SendState.Sent,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
        mediaAlbumId = 0L,
    )

    /** Od nejnovější, jak je vrací repository. */
    private val pinned = listOf(message(30), message(20), message(10))

    @Test
    fun bezVybraneZpravyJeVidetNejnovejsi() {
        assertEquals(0, pinnedIndex(pinned, null))
    }

    @Test
    fun odepnutaVybranaZpravaSpadneNaNejnovejsi() {
        assertEquals(0, pinnedIndex(pinned, 99))
    }

    @Test
    fun novaPripnutaNeposuneVybranou() {
        val withNewer = listOf(message(40)) + pinned
        assertEquals(2, pinnedIndex(withNewer, 20))
    }

    @Test
    fun tuknutiVedeKeStarsiADokola() {
        assertEquals(20L, nextPinnedId(pinned, 30))
        assertEquals(10L, nextPinnedId(pinned, 20))
        assertEquals(30L, nextPinnedId(pinned, 10))
    }

    @Test
    fun jedinaZpravaZustava() {
        assertEquals(30L, nextPinnedId(listOf(message(30)), 30))
    }

    @Test
    fun prazdnySeznamNemaDalsi() {
        assertNull(nextPinnedId(emptyList(), 30))
    }
}
