package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.REPLY_CONTEXT_MESSAGES
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kdy se nabízí návrh odpovědi a co dostane model jako kontext. */
class ReplySuggestionsTest {

    private fun message(
        id: Long,
        outgoing: Boolean = false,
        text: String = "zprava$id",
        service: Boolean = false,
    ) = ChatMessage(
        id = id,
        isOutgoing = outgoing,
        senderName = "Jana",
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = SendState.Sent,
        replyToMessageId = null,
        isService = service,
        timestamp = id.toInt(),
        isEdited = false,
    )

    /** Stav konverzace drží zprávy od nejnovější. */
    private fun newestFirst(vararg ids: Long) = ids.sortedDescending().map { message(it) }

    private fun target(
        messages: List<ChatMessage>,
        draft: String = "",
        editing: ChatMessage? = null,
        canWrite: Boolean = true,
    ) = replySuggestionTarget(messages, draft, editing, canWrite)

    @Test
    fun naPrichoziZpravuSeNabizi() {
        assertEquals(3L, target(newestFirst(1, 2, 3)))
    }

    @Test
    fun naVlastniZpravuNe() {
        // Poslední je moje — odpovídat sám sobě nedává smysl.
        val messages = listOf(message(3, outgoing = true), message(2))
        assertNull(target(messages))
    }

    @Test
    fun naServisniOznameniNe() {
        // „X se přidal do skupiny" není nic, na co by šlo odpovědět.
        val messages = listOf(message(3, service = true), message(2))
        assertNull(target(messages))
    }

    @Test
    fun sRozepsanymTextemNe() {
        assertNull(target(newestFirst(1, 2), draft = "už píšu"))
        // Samé mezery se za rozepsaný text nepočítají.
        assertEquals(2L, target(newestFirst(1, 2), draft = "   "))
    }

    @Test
    fun priUpraveZpravyNe() {
        assertNull(target(newestFirst(1, 2), editing = message(1)))
    }

    @Test
    fun kamSeNesmiPsatNe() {
        assertNull(target(newestFirst(1, 2), canWrite = false))
    }

    @Test
    fun prazdnaHistorieNe() {
        assertNull(target(emptyList()))
    }

    @Test
    fun kontextJeChronologickyABezServisnich() {
        val messages = listOf(message(4), message(3, service = true), message(2), message(1))
        assertEquals(listOf(1L, 2L, 4L), replyContext(messages).map { it.id })
    }

    @Test
    fun kontextJeOmezenyPoctem() {
        val many = (1..(REPLY_CONTEXT_MESSAGES + 5)).map { message(it.toLong()) }.reversed()
        val context = replyContext(many)
        assertEquals(REPLY_CONTEXT_MESSAGES, context.size)
        // Bere se KONEC rozhovoru, ne jeho začátek.
        assertEquals((REPLY_CONTEXT_MESSAGES + 5).toLong(), context.last().id)
    }
}
