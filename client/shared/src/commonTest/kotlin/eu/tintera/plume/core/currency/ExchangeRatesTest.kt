package eu.tintera.plume.core.currency

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeRatesTest {
    private val sample = """
        09 Oct 2026 #195
        Country|Currency|Amount|Code|Rate
        Australia|dollar|1|AUD|15.181
        EMU|euro|1|EUR|24.365
        Hungary|forint|100|HUF|6.512
        USA|dollar|1|USD|20.900
    """.trimIndent()

    @Test
    fun listekSeCte() {
        val table = parseCnbRates(sample)!!
        assertEquals(LocalDate(2026, 10, 9), table.date)
        assertEquals(24.365, table.czkPerUnit["EUR"])
        // Kurz forintu je za 100 jednotek.
        assertEquals(0.06512, table.czkPerUnit["HUF"]!!, 1e-9)
    }

    @Test
    fun prevodPresKorunu() {
        val table = parseCnbRates(sample)!!
        assertEquals(2436.5, table.convert(100.0, "EUR", "CZK")!!, 1e-9)
        assertEquals(100.0, table.convert(2436.5, "CZK", "EUR")!!, 1e-9)
        assertEquals(24.365 / 20.9 * 10, table.convert(10.0, "EUR", "USD")!!, 1e-9)
        assertNull(table.convert(10.0, "XYZ", "CZK"))
    }

    @Test
    fun rozbityListek() {
        assertNull(parseCnbRates(""))
        assertNull(parseCnbRates("09 Oct 2026 #195\nhlavicka\nnesmysl"))
    }
}
