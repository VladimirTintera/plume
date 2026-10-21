package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Přihlášení na webu nad TDLib — viz [OauthLogin]. */
internal class TdlOauthLogin(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : OauthLogin {

    private val logger = Logger.withTag("OauthLogin")
    private val client: TdlClient get() = holder.client

    private val _pending = MutableStateFlow<List<String>>(emptyList())
    override val pending: StateFlow<List<String>> = _pending.asStateFlow()

    init {
        scope.launch {
            holder.updates { newOauthRequestUpdates }.collect { update ->
                logger.i { "Žádost o přihlášení na webu: ${update.domain}" }
                open(update.url)
            }
        }
        // Žádosti patří účtu: po odhlášení by se pod novým klientem ptaly za starý.
        scope.launch { holder.replaced.collect { _pending.value = emptyList() } }
    }

    override fun open(url: String) = _pending.update { if (url in it) it else it + url }

    override fun dismiss(url: String) = _pending.update { it - url }

    override suspend fun requestInfo(url: String): Result<OauthRequest> =
        client.getOauthLinkInfo(url = url, inAppOrigin = "").toResult().map { info ->
            OauthRequest(
                url = url,
                domain = info.domain,
                appName = info.verifiedAppName.ifBlank { null },
                fromApp = info.fromApp,
                botName = client.getUser(userId = info.botUserId).toResult().getOrNull()?.let { user ->
                    user.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" }
                        ?: listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
                }.orEmpty(),
                requestWriteAccess = info.requestWriteAccess,
                requestPhoneNumber = info.requestPhoneNumberAccess,
                browser = info.browser,
                platform = info.platform,
                ipAddress = info.ipAddress,
                location = info.location,
                matchCodeFirst = info.matchCodeFirst,
                matchCodes = info.matchCodes.filterNotNull(),
            )
        }.onFailure { logger.w(it) { "Žádost o přihlášení nejde načíst." } }

    override suspend fun checkMatchCode(url: String, code: String): Result<Unit> =
        client.checkOauthRequestMatchCode(url = url, matchCode = code).toResult().map { }

    override suspend fun accept(url: String, matchCode: String, allowWriteAccess: Boolean, allowPhoneNumber: Boolean): Result<String> =
        client.acceptOauthRequest(
            url = url,
            matchCode = matchCode,
            allowWriteAccess = allowWriteAccess,
            allowPhoneNumberAccess = allowPhoneNumber,
        ).toResult().map { it.url }

    override suspend fun decline(url: String): Result<Unit> =
        client.declineOauthRequest(url = url).toResult().map { }
}
