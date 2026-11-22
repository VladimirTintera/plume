package eu.tintera.plume.features.webapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Požadavky mini aplikace, na které čeká odpověď: zápis, sdílení zprávy, výběr chatu, stažení. */
class WebAppRequestsTest {

    @Test
    fun sdileniPripraveneZpravy() {
        assertEquals(WebAppEvent.SharePreparedMessage("abc"), parseWebAppEvent("web_app_send_prepared_message", """{"id":"abc"}"""))
        // Bez id není co načíst; stránka to sama nepošle, ale nečitelná data nesmí nic spustit.
        assertEquals(WebAppEvent.Unknown("web_app_send_prepared_message"), parseWebAppEvent("web_app_send_prepared_message", "{}"))
    }

    @Test
    fun vyberChatu() {
        assertEquals(WebAppEvent.RequestChat("r1"), parseWebAppEvent("web_app_request_chat", """{"req_id":"r1"}"""))
        assertEquals(WebAppEvent.Unknown("web_app_request_chat"), parseWebAppEvent("web_app_request_chat", """{"req_id":""}"""))
    }

    @Test
    fun stazeniJenHttpsSeJmenem() {
        assertEquals(
            WebAppEvent.DownloadFile("https://example.com/a.pdf", "a.pdf"),
            parseWebAppEvent("web_app_request_file_download", """{"url":"https://example.com/a.pdf","file_name":"a.pdf"}"""),
        )
        // Skript Telegramu jiné schéma nepustí; kdyby přece, nestahuje se.
        assertEquals(
            WebAppEvent.Unknown("web_app_request_file_download"),
            parseWebAppEvent("web_app_request_file_download", """{"url":"http://example.com/a.pdf","file_name":"a.pdf"}"""),
        )
        assertEquals(
            WebAppEvent.Unknown("web_app_request_file_download"),
            parseWebAppEvent("web_app_request_file_download", """{"url":"https://example.com/a.pdf","file_name":" "}"""),
        )
    }

    /** Tvar odpovědí podle `telegram-web-app.js` — stránka čte právě tato pole. */
    @Test
    fun odpovedi() {
        assertEquals(WebAppOutgoing("write_access_requested", """{"status":"allowed"}"""), WebAppEvents.writeAccessRequested(true))
        assertEquals(WebAppOutgoing("write_access_requested", """{"status":"cancelled"}"""), WebAppEvents.writeAccessRequested(false))
        assertEquals(WebAppOutgoing("prepared_message_sent", "{}"), WebAppEvents.preparedMessageSent())
        assertEquals(
            WebAppOutgoing("prepared_message_failed", """{"error":"USER_DECLINED"}"""),
            WebAppEvents.preparedMessageFailed(WebAppErrors.USER_DECLINED),
        )
        assertEquals(WebAppOutgoing("requested_chat_sent", """{"req_id":"r1"}"""), WebAppEvents.requestedChatSent("r1"))
        assertEquals(
            WebAppOutgoing("requested_chat_failed", """{"req_id":"r1","error":"UNSUPPORTED"}"""),
            WebAppEvents.requestedChatFailed("r1", WebAppErrors.UNSUPPORTED),
        )
        assertEquals(WebAppOutgoing("file_download_requested", """{"status":"downloading"}"""), WebAppEvents.fileDownloadRequested(true))
        assertEquals(WebAppOutgoing("file_download_requested", """{"status":"cancelled"}"""), WebAppEvents.fileDownloadRequested(false))
    }

    @Test
    fun domenaVeteUStahovani() {
        assertEquals("files.example.com", downloadHost("https://files.example.com/a/b.pdf?x=1"))
        assertEquals("example.com", downloadHost("https://example.com?download=1"))
        assertEquals("example.com:8443", downloadHost("https://Example.com:8443/a"))
    }

    @Test
    fun nejednoznacnaAdresaSeNestahuje() {
        // Dřív dialog ukázal „docs.google.com" a stahovalo se ze zly.example; teď ukáže pravdu.
        assertEquals("zly.example", downloadHost("https://zly.example#@docs.google.com/x.apk"))
        assertNull(downloadHost("https://docs.google.com@zly.example/x.apk"), "přihlašovací údaje v adrese")
        assertNull(downloadHost("https://zly.example\\@docs.google.com/x.apk"))
        assertNull(downloadHost("https://zly.example/a b.apk"))
        assertNull(downloadHost("http://example.com/a.pdf"), "jen https")
        assertNull(downloadHost("javascript:alert(1)"))
    }

    @Test
    fun strankaDostaneJenKonstantuOdServeru() {
        assertEquals("BOT_METHOD_INVALID", serverError("BOT_METHOD_INVALID"))
        assertEquals(WebAppErrors.UNKNOWN_ERROR, serverError("Have no write access to the chat"))
        assertEquals(WebAppErrors.UNKNOWN_ERROR, serverError(null))
    }
}
