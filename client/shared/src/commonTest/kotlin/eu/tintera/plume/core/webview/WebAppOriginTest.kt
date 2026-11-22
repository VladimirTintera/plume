package eu.tintera.plume.core.webview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebAppOriginTest {

    @Test
    fun originIsSchemeHostAndNonDefaultPort() {
        assertEquals("https://app.example", webOrigin("https://App.Example/path?q=1#tgWebAppData=x"))
        assertEquals("https://app.example", webOrigin("https://app.example:443/"))
        assertEquals("http://app.example:8080", webOrigin("http://app.example:8080/a"))
        assertEquals("https://[::1]:8443", webOrigin("https://[::1]:8443/"))
        assertEquals("https://evil.example", webOrigin("https://user:pw@evil.example/"), "userinfo nepatří k originu")
        assertEquals("https://evil.example", webOrigin("https://evil.example\\@app.example/"), "zpětné lomítko je v http(s) lomítko")
        assertNull(webOrigin("about:blank"))
        assertNull(webOrigin("intent://x#Intent;end"))
        assertNull(webOrigin("https:///nohost"))
        assertNull(webOrigin(null))
    }

    @Test
    fun beforeFirstLoadOnlyMainFrameTalksAndNavigationIsFree() {
        val guard = WebAppOriginGuard()
        assertTrue(guard.accepts("https://redirect.example", isMainFrame = true))
        assertFalse(guard.accepts("https://ads.example", isMainFrame = false))
        assertTrue(guard.allowsNavigation("https://login.example/redirect"))
        assertFalse(guard.allowsNavigation("intent://x"), "jen http(s)")
    }

    @Test
    fun afterLoadOnlyAppOriginTalksAndMainFrameStaysThere() {
        val guard = WebAppOriginGuard()
        guard.pageLoaded("https://app.example/index.html#tgWebAppData=x")
        assertEquals("https://app.example", guard.appOrigin)

        assertTrue(guard.accepts("https://app.example", isMainFrame = true))
        assertTrue(guard.accepts("https://app.example", isMainFrame = false), "vlastní vložený rámec (hra)")
        assertFalse(guard.accepts("https://ads.example", isMainFrame = false))
        assertFalse(guard.accepts("https://other.example/page", isMainFrame = true), "cizí web po odkazu")

        assertTrue(guard.allowsNavigation("https://app.example/other"))
        assertFalse(guard.allowsNavigation("https://other.example/"))
        assertFalse(guard.allowsNavigation("http://app.example/"), "jiné schéma = jiný origin")
    }

    @Test
    fun firstRealPageFixesOriginLaterLoadsDoNot() {
        val guard = WebAppOriginGuard()
        guard.pageLoaded("about:blank")
        assertNull(guard.appOrigin)
        guard.pageLoaded("https://app.example/")
        guard.pageLoaded("https://other.example/")
        assertEquals("https://app.example", guard.appOrigin)
    }

    @Test
    fun profilesDifferPerUserAndAreStable() {
        assertEquals("user-42", webAppProfileName(42))
        val uuid = webAppProfileUuid("user-42")
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(uuid))
        assertEquals(uuid, webAppProfileUuid("user-42"))
        assertNotEquals(uuid, webAppProfileUuid("user-43"))
    }

    @Test
    fun bridgeMessageCarriesTypeAndOptionalData() {
        assertEquals("web_app_ready" to null, parseBridgeMessage("""["web_app_ready",null]"""))
        assertEquals("web_app_open_link" to """{"url":"https://x"}""", parseBridgeMessage("""["web_app_open_link","{\"url\":\"https://x\"}"]"""))
        assertNull(parseBridgeMessage("""{"eventType":"x"}"""))
        assertNull(parseBridgeMessage("not json"))
        assertNull(parseBridgeMessage("""[1,null]"""))
    }
}
