package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSearchFilterTest {

    private val now = 1_800_000_000L
    private val day = 86_400L

    @Test
    fun bezObdobiJeBezMezi() {
        assertEquals(0 to 0, null.dateRange(now))
    }

    @Test
    fun predvolbyMajiJenDolniMez() {
        assertEquals((now - 7 * day).toInt() to 0, SearchPeriod.Week.dateRange(now))
        assertEquals((now - 30 * day).toInt() to 0, SearchPeriod.Month.dateRange(now))
        assertEquals((now - 365 * day).toInt() to 0, SearchPeriod.Year.dateRange(now))
    }

    /** „Do 20. 9." musí zahrnout i zprávy z 20. 9. večer — horní mez je konec dne. */
    @Test
    fun vlastniRozsahKonciNaKonciDne() {
        val from = 1_790_000_000L
        val to = 1_790_864_000L
        assertEquals(from.toInt() to (to + day - 1).toInt(), SearchPeriod.Custom(from, to).dateRange(now))
    }
}
