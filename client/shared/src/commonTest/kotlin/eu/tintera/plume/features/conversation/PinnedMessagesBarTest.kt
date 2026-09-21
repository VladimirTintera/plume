package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pruh připnutých: která zpráva se ukáže a jak se drží při změnách seznamu. */
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

    // --- Která připnutá se ukáže podle scrollu ---

    /** Od nejnovější po nejstarší, jak je drží stav konverzace. */
    private val pripnute = listOf(message(300), message(200), message(100))

    @Test
    fun bezPripnutychNeniCoUkazat() {
        assertNull(pinnedForScroll(emptyList(), topVisibleMessageId = 250))
    }

    @Test
    fun nezSeNecoVykresliPlatiNejnovejsi() {
        assertEquals(300, pinnedForScroll(pripnute, topVisibleMessageId = null))
    }

    @Test
    fun uspodkuHistorieJeToNejnovejsi() {
        // Uživatel je dole u nejnovějších zpráv: všechny připnuté leží nad ním.
        assertEquals(300, pinnedForScroll(pripnute, topVisibleMessageId = 999))
    }

    @Test
    fun uprostredSeUkazeTaNadViditelnouCasti() {
        assertEquals(200, pinnedForScroll(pripnute, topVisibleMessageId = 250))
        assertEquals(100, pinnedForScroll(pripnute, topVisibleMessageId = 150))
    }

    @Test
    fun naPripnuteSeUkazeTaStarsi() {
        // Porovnává se STRIKTNĚ: jinak by ťuknutí vedlo pořád na tutéž zprávu.
        assertEquals(200, pinnedForScroll(pripnute, topVisibleMessageId = 300))
        assertEquals(100, pinnedForScroll(pripnute, topVisibleMessageId = 200))
    }

    @Test
    fun nadNejstarsiZustavaNejstarsi() {
        // Začátek historie — výš už žádná připnutá není.
        assertEquals(100, pinnedForScroll(pripnute, topVisibleMessageId = 100))
        assertEquals(100, pinnedForScroll(pripnute, topVisibleMessageId = 50))
    }
}
