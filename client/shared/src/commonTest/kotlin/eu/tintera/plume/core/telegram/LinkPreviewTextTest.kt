package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkPreviewTextTest {
    @Test
    fun prazdnyTextNeniNic() {
        assertNull(previewText(""))
        assertNull(previewText("   "))
    }

    @Test
    fun doslovneNullOdServeruNeniNic() {
        assertNull(previewText("null"))
        assertNull(previewText(" NULL "))
    }

    @Test
    fun beznyTextZustava() {
        assertEquals("Nullová hypotéza", previewText("Nullová hypotéza"))
        assertEquals("Titulek", previewText("  Titulek "))
    }
}
