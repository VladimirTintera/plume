package eu.tintera.plume.core.files

import kotlin.test.Test
import kotlin.test.assertEquals

class FileSizeTest {

    @Test
    fun bytesStayWhole() {
        assertEquals(FileSize("0", null, FileSizeUnit.Bytes), formatFileSize(0))
        assertEquals(FileSize("512", null, FileSizeUnit.Bytes), formatFileSize(512))
    }

    @Test
    fun smallValuesKeepOneDecimal() {
        assertEquals(FileSize("1", null, FileSizeUnit.Kilobytes), formatFileSize(1024))
        assertEquals(FileSize("1", 5, FileSizeUnit.Kilobytes), formatFileSize(1536))
        assertEquals(FileSize("1", 4, FileSizeUnit.Megabytes), formatFileSize(1_468_006))
    }

    @Test
    fun largeValuesAreWhole() {
        assertEquals(FileSize("137", null, FileSizeUnit.Megabytes), formatFileSize(143_654_912))
        assertEquals(FileSize("2", 5, FileSizeUnit.Gigabytes), formatFileSize(2_684_354_560))
    }

    @Test
    fun desetinnyOddelovacNeniVCisle() {
        // Oddělovač dodá jazyk (česky čárka), takže v datech nesmí být ani tečka, ani čárka.
        val size = formatFileSize(2_588_147_712)
        assertEquals(FileSize("2", 4, FileSizeUnit.Gigabytes), size)
    }

    @Test
    fun zaokrouhleniNaCeleNenechaNuluZaCarkou() {
        // 9,96 MB se zaokrouhlí na 10,0 — píše se „10", ne „10,0".
        assertEquals(FileSize("10", null, FileSizeUnit.Megabytes), formatFileSize(10_443_000))
    }
}
