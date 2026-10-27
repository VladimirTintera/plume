package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StagedAttachmentsStoreTest {

    private val photo = OutgoingAttachment.Photo(path = "/cache/outgoing/a/photo.jpg", width = 10, height = 10)
    private val chat = StagedAttachmentsKey(chatId = 1)

    @Test
    fun prilohyPockajiNaNavrat() {
        val store = StagedAttachmentsStore()
        store.put(chat, StagedAttachmentsEntry(listOf(photo)))
        assertEquals(listOf(photo), store.take(chat)?.attachments)
    }

    @Test
    fun vyzvednutimZaznamZmizi() {
        // Od vyzvednutí je drží konverzace; druhá konverzace téhož chatu je dostat nemá.
        val store = StagedAttachmentsStore()
        store.put(chat, StagedAttachmentsEntry(listOf(photo)))
        store.take(chat)
        assertNull(store.take(chat))
    }

    @Test
    fun prazdnePrilohyZaznamSmazou() {
        // Odeslané přílohy: konverzace odchází s prázdným polem a nesmí se vrátit ty staré.
        val store = StagedAttachmentsStore()
        store.put(chat, StagedAttachmentsEntry(listOf(photo)))
        store.put(chat, StagedAttachmentsEntry(emptyList()))
        assertNull(store.take(chat))
    }

    @Test
    fun temaJeJineMistoNezCelaSkupina() {
        val store = StagedAttachmentsStore()
        store.put(StagedAttachmentsKey(chatId = 1, forumTopicId = 7), StagedAttachmentsEntry(listOf(photo)))
        assertNull(store.take(chat))
        assertEquals(listOf(photo), store.take(StagedAttachmentsKey(chatId = 1, forumTopicId = 7))?.attachments)
    }
}
