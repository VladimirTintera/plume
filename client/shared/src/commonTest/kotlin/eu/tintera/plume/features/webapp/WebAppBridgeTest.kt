package eu.tintera.plume.features.webapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebAppBridgeTest {

    @Test
    fun hlavniTlacitko() {
        val event = parseWebAppEvent(
            "web_app_setup_main_button",
            """{"is_visible":true,"is_active":false,"text":"Zaplatit","color":"#2481cc","text_color":"#fff","is_progress_visible":true}""",
        )
        assertEquals(
            WebAppEvent.MainButton(WebAppButton(visible = true, active = false, text = "Zaplatit", color = 0x2481CC, textColor = 0xFFFFFF, progress = true)),
            event,
        )
    }

    /** Stránka posílá data jako řetězec s JSON, i prázdná nebo `null` — to nesmí shodit most. */
    @Test
    fun prazdnaANecitelnaDataNejsouPad() {
        assertEquals(WebAppEvent.Ready, parseWebAppEvent("web_app_ready", null))
        assertEquals(WebAppEvent.Ready, parseWebAppEvent("web_app_ready", "null"))
        assertEquals(WebAppEvent.BackButton(false), parseWebAppEvent("web_app_setup_back_button", "{rozbité"))
        assertEquals(WebAppEvent.Unknown("web_app_neco"), parseWebAppEvent("web_app_neco", "{}"))
    }

    @Test
    fun odkazDoTelegramuDostaneAdresu() {
        assertEquals(
            WebAppEvent.OpenTelegramLink("https://t.me/durov?start=x"),
            parseWebAppEvent("web_app_open_tg_link", """{"path_full":"/durov?start=x"}"""),
        )
    }

    @Test
    fun popupSTlacitky() {
        val event = parseWebAppEvent(
            "web_app_open_popup",
            """{"title":"Smazat?","message":"Opravdu","buttons":[{"id":"ok","type":"destructive","text":"Smazat"},{"id":"no","type":"cancel"}]}""",
        )
        assertEquals(
            WebAppEvent.Popup(
                WebAppPopup(
                    title = "Smazat?",
                    message = "Opravdu",
                    buttons = listOf(WebAppPopupButton("ok", "destructive", "Smazat"), WebAppPopupButton("no", "cancel", "")),
                ),
            ),
            event,
        )
    }

    @Test
    fun vlastniMetodaNesePametryJakoJson() {
        assertEquals(
            WebAppEvent.CustomMethod("7", "getStorageValues", """{"keys":["a"]}"""),
            parseWebAppEvent("web_app_invoke_custom_method", """{"req_id":"7","method":"getStorageValues","params":{"keys":["a"]}}"""),
        )
    }

    @Test
    fun haptika() {
        assertEquals(WebAppEvent.Haptic(WebAppHaptic.Success), parseWebAppEvent("web_app_trigger_haptic_feedback", """{"type":"notification","notification_type":"success"}"""))
        assertEquals(WebAppEvent.Haptic(WebAppHaptic.Heavy), parseWebAppEvent("web_app_trigger_haptic_feedback", """{"type":"impact","impact_style":"heavy"}"""))
        assertEquals(WebAppEvent.Haptic(WebAppHaptic.Selection), parseWebAppEvent("web_app_trigger_haptic_feedback", """{"type":"selection_change"}"""))
    }

    /** Typ i data jdou do JavaScriptu jako JSON literály — uvozovka v typu stránku nerozbije. */
    @Test
    fun skriptEscapujeTyp() {
        val script = WebAppOutgoing("a\"b", """{"x":1}""").script()
        assertTrue(script.contains("""receiveEvent("a\"b", {"x":1})"""), script)
    }

    @Test
    fun vysledekVlastniMetodyJeJson() {
        assertEquals("""{"req_id":"7","result":{"a":"1"}}""", WebAppEvents.customMethodInvoked("7", """{"a":"1"}""", null).data)
        assertEquals("""{"req_id":"7","error":"BOT_ERROR"}""", WebAppEvents.customMethodInvoked("7", null, "BOT_ERROR").data)
    }

    @Test
    fun barvy() {
        assertEquals("#0a0b0c", hex(0x0A0B0C))
        assertEquals(0xAABBCC, parseHexColor("#abc"))
        assertEquals(null, parseHexColor("red"))
    }
}
