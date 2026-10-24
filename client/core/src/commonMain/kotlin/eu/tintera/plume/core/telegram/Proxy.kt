package eu.tintera.plume.core.telegram

/**
 * Proxy, přes kterou se TDLib připojuje k Telegramu. Druh určuje, co server potřebuje:
 * MTProto tajný klíč, SOCKS5 a HTTP volitelně jméno a heslo.
 */
data class ProxyConfig(
    val server: String,
    val port: Int,
    val kind: ProxyKind,
)

sealed interface ProxyKind {
    /** Proxy Telegramu; [secret] je hex nebo base64 klíč od provozovatele. */
    data class Mtproto(val secret: String) : ProxyKind

    data class Socks5(val username: String = "", val password: String = "") : ProxyKind

    /**
     * [httpOnly] = proxy umí jen HTTP požadavky, ne tunel (`CONNECT`). TDLib se pak k Telegramu
     * připojuje přes HTTP, pomaleji.
     */
    data class Http(val username: String = "", val password: String = "", val httpOnly: Boolean = false) : ProxyKind
}

/** Proxy uložená v TDLib. Jen jedna smí být zapnutá ([enabled]). */
data class SavedProxy(
    val id: Int,
    val config: ProxyConfig,
    val enabled: Boolean,
    /** Kdy se přes ni naposledy připojilo, Unix čas v sekundách; 0 = nikdy. */
    val lastUsedDate: Long,
)

/** Proč koncept proxy nejde uložit. */
enum class ProxyProblem { MissingServer, InvalidPort, MissingSecret }

/**
 * Co chybí, aby šla proxy uložit; `null` = nic. Hlídá jen to, co server jinak odmítne obecnou
 * chybou — jestli je tajný klíč platný, ověří až TDLib.
 */
fun ProxyConfig.problem(): ProxyProblem? = when {
    server.isBlank() -> ProxyProblem.MissingServer
    port !in 1..65535 -> ProxyProblem.InvalidPort
    kind is ProxyKind.Mtproto && kind.secret.isBlank() -> ProxyProblem.MissingSecret
    else -> null
}

/**
 * Odkaz, kterým jde proxy poslat dál (`t.me/proxy?…` pro MTProto, `t.me/socks?…` pro SOCKS5),
 * stejný, jaký ji do aplikace přidá. HTTP proxy odkaz Telegramu nemá — `null`.
 */
fun ProxyConfig.shareLink(): String? {
    val base = "https://t.me/"
    val address = "server=${server.urlEncoded()}&port=$port"
    return when (kind) {
        is ProxyKind.Mtproto -> "${base}proxy?$address&secret=${kind.secret.urlEncoded()}"
        is ProxyKind.Socks5 -> buildString {
            append("${base}socks?$address")
            if (kind.username.isNotEmpty()) append("&user=${kind.username.urlEncoded()}")
            if (kind.password.isNotEmpty()) append("&pass=${kind.password.urlEncoded()}")
        }
        is ProxyKind.Http -> null
    }
}

/** Kódování hodnoty do URL: písmena, číslice a `-._~` zůstanou, zbytek jako `%XX` z UTF-8. */
private fun String.urlEncoded(): String = buildString {
    for (byte in this@urlEncoded.encodeToByteArray()) {
        val char = byte.toInt().toChar()
        if (char.isLetterOrDigit() && byte >= 0 || char in "-._~") {
            append(char)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

private const val HEX = "0123456789ABCDEF"

/**
 * Proxy uložené v TDLib. Seznam se po každé akci ČTE ZNOVU — TDLib o změně seznamu update
 * nepošle, a co se po zapnutí jedné proxy stalo s ostatními, ví jen on.
 */
interface ProxyRepository {
    suspend fun proxies(): Result<List<SavedProxy>>

    /** Přidá proxy; s [enable] ji rovnou zapne (a vypne tu dosavadní). */
    suspend fun add(config: ProxyConfig, enable: Boolean): Result<Unit>

    suspend fun edit(id: Int, config: ProxyConfig, enable: Boolean): Result<Unit>

    suspend fun enable(id: Int): Result<Unit>

    /** Vypne proxy, ať je zapnutá kterákoli; připojí se napřímo. */
    suspend fun disable(): Result<Unit>

    suspend fun remove(id: Int): Result<Unit>

    /** Odezva Telegramu přes [config] v milisekundách. Proxy se kvůli tomu nezapíná. */
    suspend fun ping(config: ProxyConfig): Result<Int>
}
