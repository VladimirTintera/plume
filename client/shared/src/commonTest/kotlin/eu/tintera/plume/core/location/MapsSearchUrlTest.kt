package eu.tintera.plume.core.location

import kotlin.test.Test
import kotlin.test.assertEquals

class MapsSearchUrlTest {
    @Test
    fun adresaSeKodujePoBajtechUtf8() {
        assertEquals("Na%20P%C5%99%C3%ADkop%C4%9B%2022%2C%20Praha", encodeQueryParameter("Na Příkopě 22, Praha"))
        assertEquals("a-b._~", encodeQueryParameter("a-b._~"))
        assertEquals("%26%3D%23%2F", encodeQueryParameter("&=#/"))
    }
}
