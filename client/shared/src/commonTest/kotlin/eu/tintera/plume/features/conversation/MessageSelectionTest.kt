package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.SendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Výběr víc zpráv: co smí výběr jako celek a co se z vybraných bloků rozbalí na id zpráv.
 *
 * Obojí se uvnitř obrazovky neotestuje a přitom je to samý hraniční případ — album, práva,
 * která dorazí až po chvíli, a pořadí, ve kterém se kopíruje i přeposílá.
 */
class MessageSelectionTest {

    private fun message(id: Long, album: Long = 0L, text: String = "ahoj") = ChatMessage(
        id = id,
        isOutgoing = false,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = SendState.Sent,
        replyToMessageId = null,
        isService = false,
        timestamp = id.toInt(),
        isEdited = false,
        mediaAlbumId = album,
    )

    private fun rights(
        canForward: Boolean = true,
        canCopy: Boolean = true,
        canDeleteForSelf: Boolean = true,
        canDeleteForAll: Boolean = true,
    ) = MessageActions(
        canReply = true,
        canCopy = canCopy,
        canDeleteForSelf = canDeleteForSelf,
        canDeleteForAll = canDeleteForAll,
        canEdit = false,
        canPin = false,
        canForward = canForward,
        canSave = true,
    )

    /** Seznam jde od nejnovější, jako v konverzaci. */
    private fun state(vararg messages: ChatMessage, selection: MessageSelection?) =
        ConversationState(messages = messages.sortedByDescending { it.id }, selection = selection)

    @Test
    fun dokudPravaNedoraziSeNicNenabizi() {
        val selection = MessageSelection(keys = setOf(1L, 2L), rights = mapOf(1L to rights()), withText = setOf(1L))
        assertTrue(selection.loading)
        assertFalse(selection.canForward)
        assertFalse(selection.canCopy)
        assertFalse(selection.canDelete)
    }

    @Test
    fun jednaZakazanaZpravaZakazeCelyVyber() {
        // Akce platí pro celý výběr, takže se práva skládají přes `all`: přeposlat půlku
        // vybraného a o druhé mlčet by znamenalo, že uživatel neví, co odešlo.
        val selection = MessageSelection(
            keys = setOf(1L, 2L),
            rights = mapOf(1L to rights(), 2L to rights(canForward = false)),
            withText = setOf(1L, 2L),
        )
        assertFalse(selection.loading)
        assertFalse(selection.canForward)
        assertTrue(selection.canCopy)
    }

    @Test
    fun smazatVsemJdeJenKdyzToJdeUVsech() {
        val selection = MessageSelection(
            keys = setOf(1L, 2L),
            rights = mapOf(1L to rights(), 2L to rights(canDeleteForAll = false)),
        )
        assertTrue(selection.canDelete)
        assertFalse(selection.canDeleteForAll)
        assertTrue(selection.canDeleteForSelf)
    }

    @Test
    fun bezTextuNeniCoKopirovat() {
        val selection = MessageSelection(keys = setOf(1L), rights = mapOf(1L to rights()), withText = emptySet())
        assertFalse(selection.canCopy)
    }

    @Test
    fun prazdnyVyberNicNenabizi() {
        val selection = MessageSelection()
        assertFalse(selection.canForward)
        assertFalse(selection.canDelete)
    }

    @Test
    fun albumSeVybiraCele() {
        // Klíč bloku je klíč NEJSTARŠÍ zprávy alba; ťuknout se dá na kteroukoli fotku.
        val state = state(
            message(10L, album = 7L),
            message(11L, album = 7L),
            message(12L),
            selection = null,
        )
        assertEquals(10L, state.blockKeyOf(11L))
        assertEquals(12L, state.blockKeyOf(12L))
    }

    @Test
    fun vybraneIdJdouChronologickyIcelymAlbem() {
        val state = state(
            message(10L, album = 7L),
            message(11L, album = 7L),
            message(12L),
            message(13L),
            // Vybrané je album (klíč 10) a zpráva 13; zpráva 12 vybraná není.
            selection = MessageSelection(keys = setOf(10L, 13L)),
        )
        assertEquals(listOf(10L, 11L, 13L), state.selectedMessageIds())
    }

    @Test
    fun bezVyberuNejsouZadnaId() {
        val state = state(message(10L), selection = null)
        assertEquals(emptyList(), state.selectedMessageIds())
    }
}
