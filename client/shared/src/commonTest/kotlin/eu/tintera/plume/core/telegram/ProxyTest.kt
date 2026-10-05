package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyTest {

    @Test
    fun odkazMtprotoNeseServerPortAKlic() {
        val config = ProxyConfig("proxy.example.com", 443, ProxyKind.Mtproto("ee0123abcd"))
        assertEquals("https://t.me/proxy?server=proxy.example.com&port=443&secret=ee0123abcd", config.shareLink())
    }

    @Test
    fun odkazSocksNeseJmenoAHesloJenKdyzJsou() {
        assertEquals(
            "https://t.me/socks?server=10.0.0.1&port=1080",
            ProxyConfig("10.0.0.1", 1080, ProxyKind.Socks5()).shareLink(),
        )
        assertEquals(
            "https://t.me/socks?server=10.0.0.1&port=1080&user=jana&pass=a%20b%26c%3D%C5%99",
            ProxyConfig("10.0.0.1", 1080, ProxyKind.Socks5("jana", "a b&c=ř")).shareLink(),
        )
    }

    @Test
    fun httpProxyOdkazNema() {
        assertNull(ProxyConfig("h", 8080, ProxyKind.Http()).shareLink())
    }

    @Test
    fun coChybi() {
        assertEquals(ProxyProblem.MissingServer, ProxyConfig(" ", 443, ProxyKind.Socks5()).problem())
        assertEquals(ProxyProblem.InvalidPort, ProxyConfig("h", 0, ProxyKind.Socks5()).problem())
        assertEquals(ProxyProblem.InvalidPort, ProxyConfig("h", 70000, ProxyKind.Socks5()).problem())
        assertEquals(ProxyProblem.MissingSecret, ProxyConfig("h", 443, ProxyKind.Mtproto("")).problem())
        assertNull(ProxyConfig("h", 443, ProxyKind.Mtproto("ee")).problem())
        assertNull(ProxyConfig("h", 1080, ProxyKind.Socks5()).problem())
    }
}
