package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Údaje o protějšku z pruhu nad konverzací — viz [CounterpartAccount]. */
class CounterpartAccountTest {

    @Test
    fun vsechnyUdajeJdouVPevnemPoradi() {
        val account = CounterpartAccount.of(
            month = 3,
            year = 2021,
            phoneCountryCode = "cz",
            nameChangedAt = 1_700_000_000,
            photoChangedAt = 1_700_000_100,
        )

        assertEquals(
            listOf(
                CounterpartAccountLine.Registered(RegistrationMonth(3, 2021)),
                CounterpartAccountLine.PhoneCountry("CZ"),
                CounterpartAccountLine.NameChanged(1_700_000_000),
                CounterpartAccountLine.PhotoChanged(1_700_000_100),
            ),
            account?.lines,
        )
    }

    @Test
    fun neznameHodnotySeVynechaji() {
        // TDLib neznámé posílá jako nulu nebo prázdný řetězec; řádek „od 0. 0" nesmí vzniknout.
        val account = CounterpartAccount.of(month = 0, year = 2021, phoneCountryCode = "", nameChangedAt = 0, photoChangedAt = 42)

        assertEquals(listOf<CounterpartAccountLine>(CounterpartAccountLine.PhotoChanged(42)), account?.lines)
    }

    @Test
    fun rokBezMesiceANeplatnyKodZemeSeNeukazuji() {
        val account = CounterpartAccount.of(month = 13, year = 2021, phoneCountryCode = "CZE", nameChangedAt = 0, photoChangedAt = 0)

        assertNull(account)
    }

    @Test
    fun bezJedinehoUdajeNeniCoUkazat() {
        assertNull(CounterpartAccount.of(month = 0, year = 0, phoneCountryCode = "", nameChangedAt = 0, photoChangedAt = 0))
    }

    @Test
    fun vlajkaSeSkladaZRegionalnichIndikatoru() {
        assertEquals("🇨🇿", flagEmoji("CZ"))
        assertEquals("🇺🇸", flagEmoji("us"))
        assertNull(flagEmoji("C1"))
        assertNull(flagEmoji(""))
    }
}
