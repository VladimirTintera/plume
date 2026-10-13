package eu.tintera.plume.features.instantview

import eu.tintera.plume.core.navigation.InstantViewRequests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class InstantViewTest {

    @Test
    fun hlavickaUkazeDomenuBezWww() {
        assertEquals("telegram.org", hostOf("https://telegram.org/blog/700-million?ref=x"))
        assertEquals("example.com", hostOf("http://www.example.com"))
        assertEquals("example.com", hostOf("example.com/clanek"))
    }

    @Test
    fun tataZStrankaPoZavreniOtevreZnovu() {
        val requests = InstantViewRequests()
        requests.request("https://a.cz")
        val first = requests.request.value!!
        requests.consume(first)
        assertNull(requests.request.value)
        requests.request("https://a.cz")
        assertNotEquals(first, requests.request.value)
    }

    @Test
    fun spotrebovaniStareZadostiNovouNesmaze() {
        val requests = InstantViewRequests()
        requests.request("https://a.cz")
        val first = requests.request.value!!
        requests.request("https://b.cz")
        requests.consume(first)
        assertEquals("https://b.cz", requests.request.value?.url)
    }
}
