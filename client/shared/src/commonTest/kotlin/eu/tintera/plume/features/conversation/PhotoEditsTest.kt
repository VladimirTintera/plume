package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.CropBox
import eu.tintera.plume.core.ui.PhotoMarkup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Výměna upravené fotky v přílohách — společná pro konverzaci i sdílení. */
class PhotoEditsTest {

    private val markup = PhotoMarkup(quarterTurns = 1, crop = CropBox(0f, 0f, 1f, 0.5f))

    /** Výměna zachová režim odeslání a ostatní přílohy; záznam se přestěhuje pod novou cestu. */
    @Test
    fun vymenaFotky() {
        val photo = OutgoingAttachment.Photo("/a.jpg", 2000, 1000, AttachmentMode.Spoiler)
        val other = OutgoingAttachment.Photo("/b.jpg", 10, 10)
        val edited = EditedPhoto("/a-edit.jpg", 1000, 1000, PhotoEdit("/a.jpg", markup, 2000, 1000))

        val attachments = listOf(photo, other).withEditedPhoto("/a.jpg", edited)
        assertEquals(listOf(OutgoingAttachment.Photo("/a-edit.jpg", 1000, 1000, AttachmentMode.Spoiler), other), attachments)

        val edits = emptyMap<String, PhotoEdit>().withEditedPhoto("/a.jpg", edited)
        assertEquals(setOf("/a-edit.jpg"), edits.keys)
        assertEquals("/a.jpg", edits.galleryPath("/a-edit.jpg"))
        assertEquals("/b.jpg", edits.galleryPath("/b.jpg"))
    }

    /** Návrat k originálu vrátí jeho cestu i rozměry a záznam úpravy zahodí. */
    @Test
    fun navratKOriginalu() {
        val edit = PhotoEdit("/a.jpg", markup, 2000, 1000)
        val reverted = revertedPhoto(OutgoingAttachment.Photo("/a-edit.jpg", 1000, 1000), edit)
        assertEquals(EditedPhoto("/a.jpg", 2000, 1000, null), reverted)

        val edits = mapOf("/a-edit.jpg" to edit).withEditedPhoto("/a-edit.jpg", reverted)
        assertTrue(edits.isEmpty())
    }

    /** Galerie zná upravenou fotku pod původní cestou, ostatní přílohy pod jejich vlastní. */
    @Test
    fun galerieZnaPuvodniCestu() {
        val state = ConversationState(photoEdits = mapOf("/edited.jpg" to PhotoEdit("/original.jpg", PhotoMarkup(), 100, 100)))
        assertEquals("/original.jpg", state.galleryPath("/edited.jpg"))
        assertEquals("/other.jpg", state.galleryPath("/other.jpg"))
    }
}
