package eu.tintera.plume.core.ui

import eu.tintera.plume.features.conversation.ConversationState
import eu.tintera.plume.features.conversation.PhotoEdit
import eu.tintera.plume.features.conversation.galleryPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Tahy do fotky: šířka a barva podle nástroje, rovnost tahů a cesta upravené fotky pro galerii. */
class PhotoMarkupTest {

    /** Šířka se měří kratší stranou — svislá i vodorovná fotka mají stejně silné začernění. */
    @Test
    fun sirkaTahuJeZKratsiStrany() {
        assertEquals(MarkupTool.Redact.strokeWidth(2560, 1440), MarkupTool.Redact.strokeWidth(1440, 2560))
        assertEquals(0.06f * 1440, MarkupTool.Redact.strokeWidth(2560, 1440))
    }

    /** Barvu volí jen pero; zvýrazňovač a začernění mají vlastní, ať je zvolené cokoli. */
    @Test
    fun barvaPodleNastroje() {
        val red = MARKUP_PEN_COLORS.first()
        assertEquals(red, MarkupTool.Pen.strokeColor(red))
        assertEquals(MARKUP_HIGHLIGHTER_COLOR, MarkupTool.Highlighter.strokeColor(red))
        assertEquals(MARKUP_REDACT_COLOR, MarkupTool.Redact.strokeColor(red))
    }

    /**
     * Tahy se porovnávají obsahem bodů, ne identitou pole: editor otevřený a zavřený bez změny
     * vrátí nové instance a úprava se pak nemá vykreslovat znovu.
     */
    @Test
    fun tahySePorovnavajiObsahem() {
        val a = MarkupStroke(MarkupTool.Pen, red(), floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))
        assertEquals(a, a.copy(points = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)))
        assertEquals(listOf(a), listOf(a.copy(points = a.points.copyOf())))
        assertNotEquals(a, a.copy(points = floatArrayOf(0.1f, 0.2f)))
    }

    /** Galerie zná upravenou fotku pod původní cestou, ostatní přílohy pod jejich vlastní. */
    @Test
    fun galerieZnaPuvodniCestu() {
        val state = ConversationState(photoEdits = mapOf("/edited.jpg" to PhotoEdit("/original.jpg", PhotoMarkup(), 100, 100)))
        assertEquals("/original.jpg", state.galleryPath("/edited.jpg"))
        assertEquals("/other.jpg", state.galleryPath("/other.jpg"))
    }

    private fun red() = MARKUP_PEN_COLORS.first()
}
