package eu.tintera.plume.features.webapp

import eu.tintera.plume.core.telegram.WebAppTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Most mezi mini aplikací a Plume: stránka posílá `(typ, JSON)` přes `TelegramWebviewProxy.postEvent`
 * (Android) nebo `webkit.messageHandlers.performAction` (iOS), aplikace odpovídá voláním
 * `Telegram.WebView.receiveEvent(typ, data)`. Tady je jen překlad — čistý a testovaný
 * (`WebAppBridgeTest`), WebView data jen přenáší.
 *
 * Názvy a tvar událostí jsou z dokumentace Telegramu (core.telegram.org/api/web-events)
 * a ze skriptu `telegram-web-app.js` (u novějších, které dokumentace událostí ještě nemá).
 * **Neznámá událost se ignoruje** ([WebAppEvent.Unknown]); na ty, na které stránka čeká
 * odpověď a Plume je neumí (telefon, faktura…), se odpovídá „zrušeno", jinak by visela.
 */
sealed interface WebAppEvent {
    data object Ready : WebAppEvent
    data object Close : WebAppEvent
    data object RequestTheme : WebAppEvent
    data object RequestViewport : WebAppEvent
    data object RequestSafeArea : WebAppEvent
    data object RequestContentSafeArea : WebAppEvent
    data class MainButton(val button: WebAppButton) : WebAppEvent
    /** Druhé tlačítko vedle hlavního; [position] říká, ze které strany (`web_app_setup_secondary_button`). */
    data class SecondaryButton(val button: WebAppButton, val position: WebAppButtonPosition) : WebAppEvent
    /** Barva spodní lišty s tlačítky: klíč z motivu, nebo barva (`web_app_set_bottom_bar_color`). */
    data class BottomBarColor(val key: String?, val color: Int?) : WebAppEvent
    data class BackButton(val visible: Boolean) : WebAppEvent
    data class ClosingConfirmation(val needed: Boolean) : WebAppEvent
    data class OpenLink(val url: String) : WebAppEvent
    data class OpenTelegramLink(val url: String) : WebAppEvent
    data class SendData(val data: String) : WebAppEvent
    data class Haptic(val kind: WebAppHaptic) : WebAppEvent
    /** Barva hlavičky: buď klíč z motivu (`bg_color`, `secondary_bg_color`), nebo barva. */
    data class HeaderColor(val key: String?, val color: Int?) : WebAppEvent
    data class BackgroundColor(val color: Int?) : WebAppEvent
    data class Popup(val popup: WebAppPopup) : WebAppEvent
    data class CustomMethod(val requestId: String, val method: String, val parameters: String) : WebAppEvent
    data class ReadClipboard(val requestId: String) : WebAppEvent
    data object RequestPhone : WebAppEvent
    data object RequestWriteAccess : WebAppEvent
    /** Sdílet zprávu připravenou botem (`shareMessage`, `web_app_send_prepared_message`). */
    data class SharePreparedMessage(val id: String) : WebAppEvent
    /** Vybrat chat nebo lidi pro tlačítko připravené botem (`requestChat`, `web_app_request_chat`). */
    data class RequestChat(val requestId: String) : WebAppEvent
    /** Stáhnout soubor z adresy https (`downloadFile`, `web_app_request_file_download`). */
    data class DownloadFile(val url: String, val fileName: String) : WebAppEvent
    data class OpenInvoice(val slug: String) : WebAppEvent
    data object RequestFullscreen : WebAppEvent
    data object CheckHomeScreen : WebAppEvent
    data class Unknown(val type: String) : WebAppEvent
}

/** Kde stojí druhé tlačítko vůči hlavnímu. Neznámá hodnota = vlevo (výchozí podle Telegramu). */
enum class WebAppButtonPosition { Left, Right, Top, Bottom }

/** Tlačítko mini aplikace (hlavní i druhé), jak si ho stránka nastavila. */
data class WebAppButton(
    val visible: Boolean = false,
    val active: Boolean = true,
    val text: String = "",
    val color: Int? = null,
    val textColor: Int? = null,
    val progress: Boolean = false,
)

enum class WebAppHaptic { Light, Heavy, Success, Error, Selection }

data class WebAppPopup(val title: String, val message: String, val buttons: List<WebAppPopupButton>)

/** Tlačítko popupu; [type] je `default`, `ok`, `close`, `cancel` nebo `destructive`. */
data class WebAppPopupButton(val id: String, val type: String, val text: String)

private val json = Json { ignoreUnknownKeys = true }

/** Událost od stránky. Nečitelná data = jako by žádná nepřišla, ne pád. */
fun parseWebAppEvent(type: String, data: String?): WebAppEvent {
    val body: JsonObject = data?.takeIf { it.isNotBlank() && it != "null" }
        ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        ?: JsonObject(emptyMap())
    fun string(key: String) = (body[key] as? JsonPrimitive)?.contentOrNull
    fun bool(key: String) = (body[key] as? JsonPrimitive)?.booleanOrNull

    fun button() = WebAppButton(
        visible = bool("is_visible") ?: false,
        active = bool("is_active") ?: true,
        text = string("text").orEmpty(),
        color = string("color")?.let(::parseHexColor),
        textColor = string("text_color")?.let(::parseHexColor),
        progress = bool("is_progress_visible") ?: false,
    )

    return when (type) {
        "web_app_ready" -> WebAppEvent.Ready
        "web_app_close" -> WebAppEvent.Close
        "web_app_request_theme" -> WebAppEvent.RequestTheme
        "web_app_request_viewport", "web_app_expand" -> WebAppEvent.RequestViewport
        "web_app_request_safe_area" -> WebAppEvent.RequestSafeArea
        "web_app_request_content_safe_area" -> WebAppEvent.RequestContentSafeArea
        "web_app_setup_main_button" -> WebAppEvent.MainButton(button())
        "web_app_setup_secondary_button" -> WebAppEvent.SecondaryButton(
            button(),
            when (string("position")) {
                "right" -> WebAppButtonPosition.Right
                "top" -> WebAppButtonPosition.Top
                "bottom" -> WebAppButtonPosition.Bottom
                else -> WebAppButtonPosition.Left
            },
        )
        // Barva je buď hex, nebo klíč z motivu — stejné pole, tak se rozliší podle „#".
        "web_app_set_bottom_bar_color" -> string("color").let { value ->
            WebAppEvent.BottomBarColor(key = value?.takeUnless { it.startsWith("#") }, color = value?.let(::parseHexColor))
        }
        "web_app_setup_back_button" -> WebAppEvent.BackButton(bool("is_visible") ?: false)
        "web_app_setup_closing_behavior" -> WebAppEvent.ClosingConfirmation(bool("need_confirmation") ?: false)
        "web_app_open_link" -> string("url")?.let(WebAppEvent::OpenLink) ?: WebAppEvent.Unknown(type)
        "web_app_open_tg_link" -> string("path_full")?.let { WebAppEvent.OpenTelegramLink("https://t.me$it") }
            ?: WebAppEvent.Unknown(type)
        "web_app_data_send" -> string("data")?.let(WebAppEvent::SendData) ?: WebAppEvent.Unknown(type)
        "web_app_trigger_haptic_feedback" -> WebAppEvent.Haptic(
            when (string("type")) {
                "notification" -> if (string("notification_type") == "success") WebAppHaptic.Success else WebAppHaptic.Error
                "selection_change" -> WebAppHaptic.Selection
                else -> when (string("impact_style")) {
                    "medium", "heavy", "rigid" -> WebAppHaptic.Heavy
                    else -> WebAppHaptic.Light
                }
            },
        )
        "web_app_set_header_color" -> WebAppEvent.HeaderColor(key = string("color_key"), color = string("color")?.let(::parseHexColor))
        "web_app_set_background_color" -> WebAppEvent.BackgroundColor(string("color")?.let(::parseHexColor))
        "web_app_open_popup" -> WebAppEvent.Popup(
            WebAppPopup(
                title = string("title").orEmpty(),
                message = string("message").orEmpty(),
                buttons = (body["buttons"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val button = element as? JsonObject ?: return@mapNotNull null
                    val id = (button["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    WebAppPopupButton(
                        id = id,
                        type = (button["type"] as? JsonPrimitive)?.contentOrNull ?: "default",
                        text = (button["text"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    )
                },
            ),
        )
        "web_app_invoke_custom_method" -> {
            val requestId = string("req_id")
            val method = string("method")
            if (requestId == null || method == null) {
                WebAppEvent.Unknown(type)
            } else {
                WebAppEvent.CustomMethod(requestId, method, body["params"]?.toString() ?: "{}")
            }
        }
        "web_app_read_text_from_clipboard" -> string("req_id")?.let(WebAppEvent::ReadClipboard) ?: WebAppEvent.Unknown(type)
        "web_app_request_phone" -> WebAppEvent.RequestPhone
        "web_app_request_write_access" -> WebAppEvent.RequestWriteAccess
        "web_app_send_prepared_message" -> string("id")?.takeIf { it.isNotEmpty() }
            ?.let(WebAppEvent::SharePreparedMessage) ?: WebAppEvent.Unknown(type)
        "web_app_request_chat" -> string("req_id")?.takeIf { it.isNotEmpty() }
            ?.let(WebAppEvent::RequestChat) ?: WebAppEvent.Unknown(type)
        "web_app_request_file_download" -> {
            val url = string("url")
            val name = string("file_name")
            // Jen https, jako kontroluje skript Telegramu; bez jména není pod čím uložit.
            if (url != null && url.startsWith("https://") && !name.isNullOrBlank()) {
                WebAppEvent.DownloadFile(url, name)
            } else {
                WebAppEvent.Unknown(type)
            }
        }
        "web_app_open_invoice" -> WebAppEvent.OpenInvoice(string("slug").orEmpty())
        "web_app_request_fullscreen" -> WebAppEvent.RequestFullscreen
        "web_app_check_home_screen" -> WebAppEvent.CheckHomeScreen
        else -> WebAppEvent.Unknown(type)
    }
}

/**
 * Kódy chyb pro stránku — z dokumentace Web Apps (`shareMessageFailed`) a toho, co posílají
 * oficiální klienti u `requestChat`. Stránka je porovnává, takže se nepřekládají.
 */
object WebAppErrors {
    const val USER_DECLINED = "USER_DECLINED"
    const val MESSAGE_EXPIRED = "MESSAGE_EXPIRED"
    const val MESSAGE_SEND_FAILED = "MESSAGE_SEND_FAILED"
    const val UNSUPPORTED = "UNSUPPORTED"
    const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
}

/** Událost pro stránku: typ a JSON data (objekt), jak je čeká `Telegram.WebView.receiveEvent`. */
data class WebAppOutgoing(val type: String, val data: String)

object WebAppEvents {
    fun themeChanged(theme: WebAppTheme) = WebAppOutgoing(
        "theme_changed",
        buildJsonObject { put("theme_params", theme.toJson()) }.toString(),
    )

    /** Výška je v CSS pixelech (= dp). Mini aplikace je vždycky rozbalená a výška stabilní. */
    fun viewportChanged(heightDp: Int) = WebAppOutgoing(
        "viewport_changed",
        buildJsonObject {
            put("height", heightDp)
            put("is_expanded", true)
            put("is_state_stable", true)
        }.toString(),
    )

    /** Okraje řeší aplikace (hlavička a tlačítko jsou mimo WebView), takže nuly. */
    fun safeAreaChanged(type: String) = WebAppOutgoing(
        type,
        buildJsonObject {
            put("top", 0)
            put("bottom", 0)
            put("left", 0)
            put("right", 0)
        }.toString(),
    )

    fun mainButtonPressed() = WebAppOutgoing("main_button_pressed", "{}")

    fun secondaryButtonPressed() = WebAppOutgoing("secondary_button_pressed", "{}")

    fun backButtonPressed() = WebAppOutgoing("back_button_pressed", "{}")

    /** Zavřený popup; bez tlačítka (zavřen zpět / ťuknutím vedle) je `button_id` prázdné. */
    fun popupClosed(buttonId: String?) = WebAppOutgoing(
        "popup_closed",
        buildJsonObject { buttonId?.let { put("button_id", it) } }.toString(),
    )

    /** Výsledek vlastní metody: JSON od bota, nebo chyba. */
    fun customMethodInvoked(requestId: String, result: String?, error: String?) = WebAppOutgoing(
        "custom_method_invoked",
        buildJsonObject {
            put("req_id", requestId)
            when {
                result != null -> put("result", runCatching { json.parseToJsonElement(result) }.getOrElse { JsonPrimitive(result) })
                else -> put("error", error ?: "UNKNOWN_ERROR")
            }
        }.toString(),
    )

    /** Schránku mini aplikacím nedáváme — `data: null` je v protokolu „nic". */
    fun clipboardTextReceived(requestId: String) = WebAppOutgoing(
        "clipboard_text_received",
        buildJsonObject {
            put("req_id", requestId)
            put("data", JsonNull)
        }.toString(),
    )

    fun phoneRequested() = WebAppOutgoing("phone_requested", """{"status":"cancelled"}""")

    /** Odpověď na `requestWriteAccess`: [allowed] = bot smí uživateli psát. */
    fun writeAccessRequested(allowed: Boolean) = WebAppOutgoing(
        "write_access_requested",
        buildJsonObject { put("status", if (allowed) "allowed" else "cancelled") }.toString(),
    )

    fun preparedMessageSent() = WebAppOutgoing("prepared_message_sent", "{}")

    /** Sdílení nedopadlo; [error] je kód z dokumentace ([WebAppErrors]). */
    fun preparedMessageFailed(error: String) = WebAppOutgoing(
        "prepared_message_failed",
        buildJsonObject { put("error", error) }.toString(),
    )

    fun requestedChatSent(requestId: String) = WebAppOutgoing(
        "requested_chat_sent",
        buildJsonObject { put("req_id", requestId) }.toString(),
    )

    fun requestedChatFailed(requestId: String, error: String) = WebAppOutgoing(
        "requested_chat_failed",
        buildJsonObject {
            put("req_id", requestId)
            put("error", error)
        }.toString(),
    )

    /** Odpověď na `downloadFile`: [downloading] = stahování začalo, jinak ho uživatel odmítl. */
    fun fileDownloadRequested(downloading: Boolean) = WebAppOutgoing(
        "file_download_requested",
        buildJsonObject { put("status", if (downloading) "downloading" else "cancelled") }.toString(),
    )

    fun invoiceClosed(slug: String) = WebAppOutgoing(
        "invoice_closed",
        buildJsonObject {
            put("slug", slug)
            put("status", "failed")
        }.toString(),
    )

    fun fullscreenFailed() = WebAppOutgoing("fullscreen_failed", """{"error":"UNSUPPORTED"}""")

    fun homeScreenChecked() = WebAppOutgoing("home_screen_checked", """{"status":"unsupported"}""")
}

/**
 * JavaScript, kterým se událost doručí stránce. Typ i data jdou jako JSON literály, takže se
 * nic nemusí escapovat ručně. Stránka bez `telegram-web-app.js` objekt `Telegram.WebView` nemá —
 * pak se nestane nic.
 */
fun WebAppOutgoing.script(): String =
    "window.Telegram && window.Telegram.WebView && window.Telegram.WebView.receiveEvent(" +
        "${JsonPrimitive(type)}, $data);"

internal fun WebAppTheme.toJson(): JsonElement = buildJsonObject {
    put("bg_color", hex(background))
    put("secondary_bg_color", hex(secondaryBackground))
    put("header_bg_color", hex(headerBackground))
    put("bottom_bar_bg_color", hex(bottomBarBackground))
    put("section_bg_color", hex(sectionBackground))
    put("section_separator_color", hex(sectionSeparator))
    put("text_color", hex(text))
    put("accent_text_color", hex(accentText))
    put("section_header_text_color", hex(sectionHeaderText))
    put("subtitle_text_color", hex(subtitleText))
    put("destructive_text_color", hex(destructiveText))
    put("hint_color", hex(hint))
    put("link_color", hex(link))
    put("button_color", hex(button))
    put("button_text_color", hex(buttonText))
}

/** RGB jako `#rrggbb`. */
internal fun hex(rgb: Int): String = "#" + (rgb and 0xFFFFFF).toString(16).padStart(6, '0')

/** `#rgb` / `#rrggbb` → RGB; cokoli jiného `null`. */
internal fun parseHexColor(value: String): Int? {
    val digits = value.trim().removePrefix("#")
    val full = when (digits.length) {
        3 -> digits.map { "$it$it" }.joinToString("")
        6 -> digits
        else -> return null
    }
    return full.toIntOrNull(16)
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
