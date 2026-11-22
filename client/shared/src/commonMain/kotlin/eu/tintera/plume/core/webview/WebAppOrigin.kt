package eu.tintera.plume.core.webview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Origin webové adresy (`https://host[:port]`, malými písmeny, bez výchozího portu), nebo `null`
 * pro cokoli jiného než http(s) — `about:blank`, `data:`, chybová stránka WebView.
 */
internal fun webOrigin(url: String?): String? {
    val match = url?.let(ORIGIN::find) ?: return null
    val scheme = match.groupValues[1].lowercase()
    val authority = match.groupValues[2].substringAfterLast('@').lowercase()
    if (authority.isEmpty()) return null
    val (host, port) = if (authority.startsWith('[')) {
        val end = authority.indexOf(']').takeIf { it > 0 } ?: return null
        authority.substring(0, end + 1) to authority.substring(end + 1).removePrefix(":")
    } else {
        authority.substringBefore(':') to authority.substringAfter(':', "")
    }
    if (host.isEmpty()) return null
    val defaultPort = if (scheme == "https") "443" else "80"
    return if (port.isEmpty() || port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
}

private val ORIGIN = Regex("""^(https?)://([^/?#\\]*)""", RegexOption.IGNORE_CASE)

/**
 * Komu smí WebView mini aplikace dát most (`TelegramWebviewProxy`) a kam ho nechat odejít.
 *
 * Mini aplikace je web bota, a ten na stránce má odkazy kamkoli. Dokud WebView pouštělo každou
 * http(s) navigaci a most vkládalo do všech rámců, dostal cizí web po obyčejném odkazu (nebo
 * vložený rámec třetí strany, reklama) celé API mini aplikace: SecureStorage a DeviceStorage
 * bota, polohu bez dialogu, `invoke_custom_method` jménem uživatele.
 *
 * - **Origin mini aplikace se určí při prvním dokončeném načtení** ([pageLoaded]), ne z adresy
 *   od TDLib: mini aplikace se smí na začátku přesměrovat (přihlášení, kratší odkaz) a teprve
 *   stránka, na které skončí, je ona. Totéž dělá oficiální klient.
 * - **Potom hlavní rámec na jiný origin nesmí** ([allowsNavigation]) — odkaz se otevře mimo
 *   mini aplikaci, v prohlížeči.
 * - **Události se přijímají jen z originu mini aplikace** ([accepts]): z hlavního rámce
 *   i z vlastních vložených rámců (hry v `<iframe>`), z cizích ne. Před prvním načtením jen
 *   z hlavního rámce — cokoli jiného by byl vložený rámec stránky, kterou ještě neznáme.
 */
internal class WebAppOriginGuard {

    var appOrigin: String? = null
        private set

    fun pageLoaded(url: String?) {
        if (appOrigin == null) appOrigin = webOrigin(url)
    }

    fun allowsNavigation(url: String?): Boolean {
        val target = webOrigin(url) ?: return false
        return appOrigin == null || target == appOrigin
    }

    fun accepts(origin: String?, isMainFrame: Boolean): Boolean {
        val app = appOrigin ?: return isMainFrame
        return webOrigin(origin) == app
    }
}

/**
 * Data WebView (cookies, `localStorage`, IndexedDB) patří účtu Telegramu, ne celé aplikaci.
 * Společné úložiště dovolilo botovi poznat, že dva účty v Plume patří témuž člověku (náhodné id
 * v `localStorage` viděly oba), a session mini aplikace z jednoho účtu přihlásila druhý.
 *
 * Klíčem je id uživatele (jako u DeviceStorage, `MiniAppStorage`), ne slot účtu: kdo se do
 * slotu přihlásí po odhlášení, dostane prázdné úložiště, a vlastník po návratu to svoje.
 */
internal fun webAppProfileName(userId: Long): String = "user-$userId"

/**
 * Stálé UUID pro [profile] (iOS `WKWebsiteDataStore(forIdentifier:)` chce UUID, ne jméno):
 * dvě nezávislá 64bitová FNV-1a z textu. Nejde o tajemství, jen o stabilní a odlišné id.
 */
internal fun webAppProfileUuid(profile: String): String {
    fun fnv(seed: ULong): ULong = profile.encodeToByteArray().fold(seed) { hash, byte ->
        (hash xor byte.toUByte().toULong()) * 0x100000001b3uL
    }
    val hex = fnv(0xcbf29ce484222325uL).toString(16).padStart(16, '0') +
        fnv(0x84222325cbf29ce4uL).toString(16).padStart(16, '0')
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}

/** Zpráva mostu z posluchače (Android): `JSON.stringify([typ, data])` ze skriptu [BRIDGE_SHIM]. */
internal fun parseBridgeMessage(message: String?): Pair<String, String?>? {
    val array = runCatching { Json.parseToJsonElement(message ?: return null) }.getOrNull() as? JsonArray ?: return null
    val type = (array.getOrNull(0) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    val data = (array.getOrNull(1) as? JsonPrimitive)?.takeIf { it.isString }?.content
    return type to data
}

/** Jméno objektu posluchače mostu (Android `addWebMessageListener`). */
internal const val BRIDGE_OBJECT = "plumeWebAppBridge"

/**
 * `TelegramWebviewProxy.postEvent(typ, data)`, jak ho volá `telegram-web-app.js`, nad
 * posluchačem zpráv. Data posílá knihovna už jako JSON řetězec; neřetězec (nic) jde jako `null`.
 */
internal const val BRIDGE_SHIM =
    "(function () { var bridge = window.$BRIDGE_OBJECT; if (!bridge) return; " +
        "window.TelegramWebviewProxy = { postEvent: function (eventType, eventData) { " +
        "bridge.postMessage(JSON.stringify([String(eventType), typeof eventData === 'string' ? eventData : null])); } }; })();"
