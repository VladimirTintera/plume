package eu.tintera.plume.core.files

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedFileNameTest {

    private val prague = TimeZone.of("Europe/Prague")

    // 18. 9. 2026 23:30:17 SELČ
    private val timestamp = 1_789_767_017

    @Test
    fun documentKeepsItsName() {
        assertEquals("dovolena.heic", savedFileName("dovolena.heic", "/files/documents/dovolena.heic", timestamp, prague))
    }

    @Test
    fun photoGetsNameFromMessageTime() {
        assertEquals(
            "Plume_20260918_233017.jpg",
            savedFileName(null, "/files/photos/5868681087951770588_121.jpg", timestamp, prague),
        )
    }

    @Test
    fun blankNameCountsAsMissing() {
        assertEquals("Plume_20260918_233017.mp4", savedFileName(" ", "/files/videos/12_3.mp4", timestamp, prague))
    }

    @Test
    fun pathWithoutExtension() {
        assertEquals("Plume_20260918_233017", savedFileName(null, "/files/photos.d/12", timestamp, prague))
    }
}
