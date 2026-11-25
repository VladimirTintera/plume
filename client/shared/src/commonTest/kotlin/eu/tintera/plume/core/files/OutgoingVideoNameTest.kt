package eu.tintera.plume.core.files

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class OutgoingVideoNameTest {

    private val prague = TimeZone.of("Europe/Prague")

    // 25. 11. 2026 18:29:00 UTC = 19:29:00 v Praze (zimní čas).
    private val recorded = "20261125T182900.000Z"

    @Test
    fun meaningfulNameStays() {
        assertEquals("PXL_20261125_192900.mp4", outgoingVideoName("PXL_20261125_192900.mp4", recorded, prague))
        assertEquals("dovolena 2026.mov", outgoingVideoName("dovolena 2026.mov", recorded, prague))
    }

    @Test
    fun numericNameFromGooglePhotosGetsRecordingDate() {
        assertEquals("VID_20261125_192900.mp4", outgoingVideoName("2143.mp4", recorded, prague))
        assertEquals("VID_20261125_192900.mov", outgoingVideoName("2143.mov", recorded, prague))
        assertEquals("VID_20261125_192900.mp4", outgoingVideoName("2143", recorded, prague))
    }

    @Test
    fun missingNameGetsRecordingDate() {
        assertEquals("VID_20261125_192900.mp4", outgoingVideoName(null, recorded, prague))
        assertEquals("VID_20261125_192900.mp4", outgoingVideoName("  ", recorded, prague))
    }

    @Test
    fun summerTimeAndDateWithoutMillis() {
        assertEquals("VID_20260701_140000.mp4", outgoingVideoName("7.mp4", "20260701T120000", prague))
    }

    @Test
    fun withoutUsableDateNameStays() {
        assertEquals("2143.mp4", outgoingVideoName("2143.mp4", null, prague))
        // Počátek epochy QuickTime = video bez data.
        assertEquals("2143.mp4", outgoingVideoName("2143.mp4", "19040101T000000.000Z", prague))
        assertEquals("2143.mp4", outgoingVideoName("2143.mp4", "0", prague))
        assertEquals("2143.mp4", outgoingVideoName("2143.mp4", "20261345T999999.000Z", prague))
        assertEquals("video.mp4", outgoingVideoName(null, null, prague))
    }
}
