package eu.tintera.plume.features.webapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun druheTlacitkoSPolohou() {
        val event = parseWebAppEvent(
            "web_app_setup_secondary_button",
            """{"is_visible":true,"text":"Zpět do menu","position":"top","has_shine_effect":true}""",
        )
        assertEquals(
            WebAppEvent.SecondaryButton(WebAppButton(visible = true, text = "Zpět do menu"), WebAppButtonPosition.Top),
            event,
        )
    }

    /** Bez polohy (nebo s neznámou) stojí druhé tlačítko vlevo, jako u Telegramu. */
    @Test
    fun druheTlacitkoBezPolohyJeVlevo() {
        val missing = parseWebAppEvent("web_app_setup_secondary_button", """{"is_visible":false}""")
        val unknown = parseWebAppEvent("web_app_setup_secondary_button", """{"position":"middle"}""")
        assertEquals(WebAppButtonPosition.Left, (missing as WebAppEvent.SecondaryButton).position)
        assertEquals(WebAppButtonPosition.Left, (unknown as WebAppEvent.SecondaryButton).position)
    }

    /** Barva lišty je v jednom poli buď hex, nebo klíč z motivu. */
    @Test
    fun barvaSpodniListy() {
        assertEquals(
            WebAppEvent.BottomBarColor(key = null, color = 0x1D2733),
            parseWebAppEvent("web_app_set_bottom_bar_color", """{"color":"#1d2733"}"""),
        )
        assertEquals(
            WebAppEvent.BottomBarColor(key = "secondary_bg_color", color = null),
            parseWebAppEvent("web_app_set_bottom_bar_color", """{"color":"secondary_bg_color"}"""),
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
        // Bez úvodního lomítka by z `https://t.me@zly.example` byl host zly.example.
        assertFalse(parseWebAppEvent("web_app_open_tg_link", """{"path_full":"@zly.example/x"}""") is WebAppEvent.OpenTelegramLink)
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

    @Test
    fun ulozisteVZarizeni() {
        assertEquals(
            WebAppEvent.DeviceStorageSave("r1", "skore", "42"),
            parseWebAppEvent("web_app_device_storage_save_key", """{"req_id":"r1","key":"skore","value":"42"}"""),
        )
        // `removeItem` posílá null.
        assertEquals(
            WebAppEvent.DeviceStorageSave("r2", "skore", null),
            parseWebAppEvent("web_app_device_storage_save_key", """{"req_id":"r2","key":"skore","value":null}"""),
        )
        assertEquals(
            WebAppEvent.DeviceStorageGet("r3", "skore"),
            parseWebAppEvent("web_app_device_storage_get_key", """{"req_id":"r3","key":"skore"}"""),
        )
        assertEquals(WebAppEvent.DeviceStorageClear("r4"), parseWebAppEvent("web_app_device_storage_clear", """{"req_id":"r4"}"""))
        assertEquals(
            WebAppOutgoing("device_storage_key_received", """{"req_id":"r3","value":null}"""),
            WebAppEvents.deviceStorageReceived("r3", null),
        )
        assertEquals(
            WebAppOutgoing("device_storage_failed", """{"req_id":"r1","error":"QUOTA_EXCEEDED"}"""),
            WebAppEvents.deviceStorageFailed("r1", WebAppErrors.QUOTA_EXCEEDED),
        )
    }

    @Test
    fun zabezpeceneUloziste() {
        assertEquals(
            WebAppEvent.SecureStorageSave("s1", "token", "abc"),
            parseWebAppEvent("web_app_secure_storage_save_key", """{"req_id":"s1","key":"token","value":"abc"}"""),
        )
        assertEquals(
            WebAppEvent.SecureStorageSave("s2", "token", null),
            parseWebAppEvent("web_app_secure_storage_save_key", """{"req_id":"s2","key":"token","value":null}"""),
        )
        assertEquals(
            WebAppEvent.SecureStorageGet("s3", "token"),
            parseWebAppEvent("web_app_secure_storage_get_key", """{"req_id":"s3","key":"token"}"""),
        )
        assertEquals(
            WebAppEvent.SecureStorageRestore("s4"),
            parseWebAppEvent("web_app_secure_storage_restore_key", """{"req_id":"s4","key":"token"}"""),
        )
        assertEquals(WebAppEvent.SecureStorageClear("s5"), parseWebAppEvent("web_app_secure_storage_clear", """{"req_id":"s5"}"""))
        // Bez klíče je požadavek neplatný; neznámá událost ho aspoň zaloguje.
        assertEquals(WebAppEvent.Unknown("web_app_secure_storage_get_key"), parseWebAppEvent("web_app_secure_storage_get_key", """{"req_id":"s6"}"""))
        assertEquals(
            WebAppOutgoing("secure_storage_key_received", """{"req_id":"s3","value":"abc","can_restore":false}"""),
            WebAppEvents.secureStorageReceived("s3", "abc"),
        )
        assertEquals(
            WebAppOutgoing("secure_storage_failed", """{"req_id":"s4","error":"RESTORE_UNAVAILABLE"}"""),
            WebAppEvents.secureStorageFailed("s4", WebAppErrors.RESTORE_UNAVAILABLE),
        )
    }

    @Test
    fun polohaCelaPlochaAZastupce() {
        assertEquals(WebAppEvent.CheckLocation, parseWebAppEvent("web_app_check_location", null))
        assertEquals(WebAppEvent.RequestLocation, parseWebAppEvent("web_app_request_location", "{}"))
        assertEquals(WebAppEvent.ExitFullscreen, parseWebAppEvent("web_app_exit_fullscreen", null))
        assertEquals(WebAppEvent.AddToHomeScreen, parseWebAppEvent("web_app_add_to_home_screen", null))
        assertEquals(WebAppOutgoing("location_requested", """{"available":false}"""), WebAppEvents.locationRequested(null, null))
        assertTrue(WebAppEvents.locationRequested(50.08, 14.42).data.contains(""""latitude":50.08"""))
        assertEquals(WebAppOutgoing("fullscreen_changed", """{"is_fullscreen":true}"""), WebAppEvents.fullscreenChanged(true))
        assertEquals(WebAppOutgoing("home_screen_checked", """{"status":"missing"}"""), WebAppEvents.homeScreenChecked("missing"))
    }
}
