package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.MessageMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactPickerTest {

    private val contacts = listOf(contact(1, "Jana Nováková"), contact(2, "Petr Novák"), contact(3, "Eva"))

    @Test
    fun bezDotazuVsechnyKontakty() {
        assertEquals(contacts, ContactPicker(contacts = contacts).shown)
    }

    @Test
    fun dotazFiltrujePodleJmenaBezOhleduNaVelikost() {
        assertEquals(listOf(1L, 2L), ContactPicker(contacts = contacts, query = "NOVÁK").shown.map { it.id })
    }

    @Test
    fun mezeryKolemDotazuNevadi() {
        assertEquals(listOf(3L), ContactPicker(contacts = contacts, query = "  eva ").shown.map { it.id })
    }

    @Test
    fun samotneMezeryJsouPrazdnyDotaz() {
        assertEquals(contacts, ContactPicker(contacts = contacts, query = "   ").shown)
    }

    private val me = MessageMedia.Contact(name = "Já", phone = "+420111222333", userId = 99)

    @Test
    fun vlastniKontaktBezDotazu() {
        assertTrue(ContactPicker(contacts = contacts, me = me).showsMe)
    }

    @Test
    fun vlastniKontaktSePriHledaniSchova() {
        assertFalse(ContactPicker(contacts = contacts, me = me, query = "no").showsMe)
    }

    @Test
    fun vlastniKontaktJenKdyzSeNacetl() {
        assertFalse(ContactPicker(contacts = contacts).showsMe)
    }

    private fun contact(id: Long, title: String) = ChatSummary(
        id = id,
        title = title,
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )
}
