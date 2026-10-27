package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Odkaz pod názvem v [LinkQrSheet]: bez schématu, jinak beze změny. */
class LinkQrSheetTest {

    @Test
    fun odkazBezSchematu() {
        assertEquals("t.me/plume", "https://t.me/plume".withoutScheme())
        assertEquals("t.me/+AbC", "http://t.me/+AbC".withoutScheme())
        assertEquals("t.me/plume", "t.me/plume".withoutScheme())
    }
}
