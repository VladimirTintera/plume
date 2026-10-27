package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.AddedProxy
import dev.g000sha256.tdl.dto.Proxy
import dev.g000sha256.tdl.dto.ProxyType
import dev.g000sha256.tdl.dto.ProxyTypeHttp
import dev.g000sha256.tdl.dto.ProxyTypeMtproto
import dev.g000sha256.tdl.dto.ProxyTypeSocks5
import dev.g000sha256.tdl.dto.ProxyTypeWeb
import dev.g000sha256.tdl.toResult
import kotlin.math.roundToInt

/** Proxy nad TDLib. Komentář k proxy (`comment`) aplikace nenabízí a posílá prázdný. */
class TdlProxyRepository(
    private val holder: TelegramClientHolder,
) : ProxyRepository {

    private val client: TdlClient get() = holder.client

    override suspend fun proxies(): Result<List<SavedProxy>> =
        client.getProxies().toResult().map { result -> result.proxies.mapNotNull { it.toSaved() } }

    override suspend fun add(config: ProxyConfig, enable: Boolean): Result<Unit> =
        client.addProxy(proxy = config.toTdl(), enable = enable, comment = "").toResult().map { }

    override suspend fun edit(id: Int, config: ProxyConfig, enable: Boolean): Result<Unit> =
        client.editProxy(proxyId = id, proxy = config.toTdl(), enable = enable, comment = "").toResult().map { }

    override suspend fun enable(id: Int): Result<Unit> =
        client.enableProxy(proxyId = id).toResult().map { }

    override suspend fun disable(): Result<Unit> =
        client.disableProxy().toResult().map { }

    override suspend fun remove(id: Int): Result<Unit> =
        client.removeProxy(proxyId = id).toResult().map { }

    override suspend fun ping(config: ProxyConfig): Result<Int> =
        client.pingProxy(proxy = config.toTdl()).toResult().map { (it.seconds * 1000).roundToInt() }
}

private fun AddedProxy.toSaved(): SavedProxy? = SavedProxy(
    id = id,
    config = proxy.toDomain() ?: return null,
    enabled = isEnabled,
    lastUsedDate = lastUsedDate.toLong(),
)

/**
 * `null` u webové proxy (`ProxyTypeWeb`, TDLib 1.8.69): ta jde přes přenosovou stránku ve skrytém
 * WebView, kterou musí hostit aplikace (`updateWebProxyCarrier…`), a tu Plume nemá.
 */
private fun Proxy.toDomain(): ProxyConfig? = ProxyConfig(
    server = server,
    port = port,
    kind = when (val type: ProxyType = type) {
        is ProxyTypeMtproto -> ProxyKind.Mtproto(type.secret)
        is ProxyTypeSocks5 -> ProxyKind.Socks5(type.username, type.password)
        is ProxyTypeHttp -> ProxyKind.Http(type.username, type.password, type.httpOnly)
        is ProxyTypeWeb -> return null
    },
)

internal fun ProxyConfig.toTdl() = Proxy(
    server = server.trim(),
    port = port,
    type = when (kind) {
        is ProxyKind.Mtproto -> ProxyTypeMtproto(secret = kind.secret.trim())
        is ProxyKind.Socks5 -> ProxyTypeSocks5(username = kind.username, password = kind.password)
        is ProxyKind.Http -> ProxyTypeHttp(username = kind.username, password = kind.password, httpOnly = kind.httpOnly)
    },
)

/** Proxy z odkazu (`InternalLinkTypeProxy`). */
internal fun Proxy.toProxyConfig(): ProxyConfig? = toDomain()
