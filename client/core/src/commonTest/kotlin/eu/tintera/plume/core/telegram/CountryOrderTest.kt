package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class CountryOrderTest {

    private fun country(name: String) =
        Country(code = name.take(2).uppercase(), name = name, englishName = name, flagEmoji = "", callingCode = "+1")

    @Test
    fun radiSeBezOhleduNaDiakritikuAVelikostPismen() {
        val sorted = listOf("Zambie", "Čad", "Ekvádor", "egypt", "Albánie", "Česko")
            .map(::country)
            .sortedByName()

        assertEquals(listOf("Albánie", "Čad", "Česko", "egypt", "Ekvádor", "Zambie"), sorted.map { it.name })
    }

    @Test
    fun stejnyKlicNechavaPuvodniPoradi() {
        // TDLib posílá podle kódu; při shodě názvu ho řazení nemá přeházet.
        val first = Country(code = "AA", name = "Kongo", englishName = "Congo", flagEmoji = "", callingCode = "+242")
        val second = Country(code = "CD", name = "Kongo", englishName = "DR Congo", flagEmoji = "", callingCode = "+243")

        assertEquals(listOf(first, second), listOf(first, second).sortedByName())
    }
}
