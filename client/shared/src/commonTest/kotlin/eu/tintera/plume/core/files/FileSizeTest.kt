package eu.tintera.plume.core.files

import kotlin.test.Test
import kotlin.test.assertEquals

class FileSizeTest {

    @Test
    fun bytesStayWhole() {
        assertEquals(FileSize("0", FileSizeUnit.Bytes), formatFileSize(0))
        assertEquals(FileSize("512", FileSizeUnit.Bytes), formatFileSize(512))
    }

    @Test
    fun smallValuesKeepOneDecimal() {
        assertEquals(FileSize("1", FileSizeUnit.Kilobytes), formatFileSize(1024))
        assertEquals(FileSize("1.5", FileSizeUnit.Kilobytes), formatFileSize(1536))
        assertEquals(FileSize("1.4", FileSizeUnit.Megabytes), formatFileSize(1_468_006))
    }

    @Test
    fun largeValuesAreWhole() {
        assertEquals(FileSize("137", FileSizeUnit.Megabytes), formatFileSize(143_654_912))
        assertEquals(FileSize("2.5", FileSizeUnit.Gigabytes), formatFileSize(2_684_354_560))
    }
}
