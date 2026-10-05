package eu.tintera.plume.features.proxy

import eu.tintera.plume.core.telegram.ProxyConfig
import eu.tintera.plume.core.telegram.ProxyKind
import eu.tintera.plume.core.telegram.SavedProxy
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyStatusTest {

    private fun proxy(id: Int, server: String, enabled: Boolean) = SavedProxy(
        id = id,
        config = ProxyConfig(server = server, port = 443, kind = ProxyKind.Mtproto("dd00")),
        enabled = enabled,
        lastUsedDate = 0,
    )

    @Test
    fun bezProxyJePrimePripojeni() {
        assertEquals(ProxyStatus.Direct, proxyStatus(emptyList()))
    }

    @Test
    fun ulozeneAleVypnuteJsouPrimePripojeni() {
        assertEquals(ProxyStatus.Direct, proxyStatus(listOf(proxy(1, "a.example", enabled = false))))
    }

    @Test
    fun zapnutaProxyPredstavujeStav() {
        val status = proxyStatus(
            listOf(proxy(1, "a.example", enabled = false), proxy(2, "b.example", enabled = true)),
        )
        assertEquals("b.example", (status as ProxyStatus.Via).config.server)
    }
}
