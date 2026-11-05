package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Session
import dev.g000sha256.tdl.dto.SessionDeviceType
import dev.g000sha256.tdl.dto.SessionDeviceTypeAndroid
import dev.g000sha256.tdl.dto.SessionDeviceTypeApple
import dev.g000sha256.tdl.dto.SessionDeviceTypeBrave
import dev.g000sha256.tdl.dto.SessionDeviceTypeChrome
import dev.g000sha256.tdl.dto.SessionDeviceTypeEdge
import dev.g000sha256.tdl.dto.SessionDeviceTypeFirefox
import dev.g000sha256.tdl.dto.SessionDeviceTypeIpad
import dev.g000sha256.tdl.dto.SessionDeviceTypeIphone
import dev.g000sha256.tdl.dto.SessionDeviceTypeLinux
import dev.g000sha256.tdl.dto.SessionDeviceTypeMac
import dev.g000sha256.tdl.dto.SessionDeviceTypeOpera
import dev.g000sha256.tdl.dto.SessionDeviceTypeSafari
import dev.g000sha256.tdl.dto.SessionDeviceTypeUbuntu
import dev.g000sha256.tdl.dto.SessionDeviceTypeUnknown
import dev.g000sha256.tdl.dto.SessionDeviceTypeVivaldi
import dev.g000sha256.tdl.dto.SessionDeviceTypeWindows
import dev.g000sha256.tdl.dto.SessionDeviceTypeXbox
import dev.g000sha256.tdl.dto.SessionTypeConnectedBot
import dev.g000sha256.tdl.dto.SessionTypeDevice
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dev.g000sha256.tdl.dto.ConnectedWebsite as TdlConnectedWebsite

/**
 * [DeviceRepository] nad TDLib: `getActiveSessions`, `terminateSession`, `getConnectedWebsites`,
 * `confirmQrCodeAuthentication` a update `UpdateUnconfirmedSession`.
 *
 * Vzniká eagerně (`createdAtStart`): nepotvrzené přihlášení TDLib ohlásí jednou, hned po startu,
 * a kdyby odběr vznikl až s první obrazovkou, karta „Byli jste to vy?" by se neukázala.
 */
class TdlDeviceRepository(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : DeviceRepository {

    private val logger = Logger.withTag("Telegram")

    private val client: TdlClient get() = holder.client

    private val _unconfirmed = MutableStateFlow<UnconfirmedDevice?>(null)
    override val unconfirmed: StateFlow<UnconfirmedDevice?> = _unconfirmed.asStateFlow()

    init {
        scope.launch {
            holder.updates { unconfirmedSessionUpdates }.collect { update ->
                val session = update.session
                // Připojený bot (Telegram Business) se potvrzuje jinde; `confirmSession` bere
                // jen id relace zařízení.
                val type = session?.type as? SessionTypeDevice
                _unconfirmed.value = if (session != null && type != null) {
                    UnconfirmedDevice(
                        sessionId = type.sessionId,
                        deviceModel = session.deviceModel,
                        location = session.location,
                        date = session.date,
                    )
                } else {
                    if (session?.type is SessionTypeConnectedBot) logger.i { "Nepotvrzený připojený bot — nezobrazuje se." }
                    null
                }
            }
        }
        // Nepotvrzené přihlášení patří účtu; po odhlášení nemá co viset.
        scope.launch { holder.replaced.collect { _unconfirmed.value = null } }
    }

    override suspend fun devices(): Result<Devices> =
        client.getActiveSessions().toResult().map { sessions ->
            val all = sessions.sessions.filterNotNull().map { it.toDomain() }
            Devices(
                current = all.firstOrNull { it.isCurrent },
                others = all.filterNot { it.isCurrent }.sortedByDescending { it.lastActiveDate },
                inactiveTtlDays = sessions.inactiveSessionTtlDays,
            )
        }

    override suspend fun terminate(sessionId: Long): Result<Unit> =
        client.terminateSession(sessionId = sessionId).toResult().map { forget(sessionId) }

    override suspend fun terminateOthers(): Result<Unit> =
        client.terminateAllOtherSessions().toResult().map { _unconfirmed.value = null }

    override suspend fun setInactiveTtl(days: Int): Result<Unit> =
        client.setInactiveSessionTtl(inactiveSessionTtlDays = days).toResult().map { }

    override suspend fun setCanAcceptCalls(sessionId: Long, value: Boolean): Result<Unit> =
        client.toggleSessionCanAcceptCalls(sessionId = sessionId, canAcceptCalls = value).toResult().map { }

    override suspend fun setCanAcceptSecretChats(sessionId: Long, value: Boolean): Result<Unit> =
        client.toggleSessionCanAcceptSecretChats(sessionId = sessionId, canAcceptSecretChats = value)
            .toResult()
            .map { }

    override suspend fun websites(): Result<List<ConnectedWebsite>> =
        client.getConnectedWebsites().toResult().map { found ->
            found.websites.filterNotNull().map { it.toDomain() }.sortedByDescending { it.lastActiveDate }
        }

    override suspend fun disconnectWebsite(websiteId: Long): Result<Unit> =
        client.disconnectWebsite(websiteId = websiteId).toResult().map { }

    override suspend fun disconnectAllWebsites(): Result<Unit> =
        client.disconnectAllWebsites().toResult().map { }

    override suspend fun confirmQrLogin(link: String): Result<Device> =
        client.confirmQrCodeAuthentication(link = link).toResult().map { it.toDomain() }

    override suspend fun confirm(sessionId: Long): Result<Unit> =
        client.confirmSession(sessionId = sessionId).toResult().map { forget(sessionId) }

    /**
     * Karta „Byli jste to vy?" zmizí hned po rozhodnutí. TDLib pošle update s `null` taky,
     * ale čekat na něj by znamenalo nechat kartu viset s tlačítky, na která už se ťuklo.
     */
    private fun forget(sessionId: Long) {
        if (_unconfirmed.value?.sessionId == sessionId) _unconfirmed.value = null
    }
}

private fun Session.toDomain() = Device(
    id = id,
    isCurrent = isCurrent,
    isPasswordPending = isPasswordPending,
    isUnconfirmed = isUnconfirmed,
    canAcceptCalls = canAcceptCalls,
    canAcceptSecretChats = canAcceptSecretChats,
    kind = refinedDeviceKind(deviceType.toKind(), deviceModel, systemVersion),
    applicationName = applicationName,
    applicationVersion = applicationVersion,
    isOfficialApplication = isOfficialApplication,
    deviceModel = deviceModel,
    platform = platform,
    systemVersion = systemVersion,
    logInDate = logInDate,
    lastActiveDate = lastActiveDate,
    ipAddress = ipAddress,
    location = location,
)

// Bez `else`: nový druh zařízení v novější TDLib má rozbít překlad, ne tiše spadnout na „neznámé".
private fun SessionDeviceType.toKind(): DeviceKind = when (this) {
    is SessionDeviceTypeAndroid, is SessionDeviceTypeIphone -> DeviceKind.Phone
    is SessionDeviceTypeIpad -> DeviceKind.Tablet
    is SessionDeviceTypeApple, is SessionDeviceTypeMac, is SessionDeviceTypeWindows,
    is SessionDeviceTypeLinux, is SessionDeviceTypeUbuntu, is SessionDeviceTypeXbox,
    -> DeviceKind.Desktop
    is SessionDeviceTypeBrave, is SessionDeviceTypeChrome, is SessionDeviceTypeEdge,
    is SessionDeviceTypeFirefox, is SessionDeviceTypeOpera, is SessionDeviceTypeSafari,
    is SessionDeviceTypeVivaldi,
    -> DeviceKind.Browser
    is SessionDeviceTypeUnknown -> DeviceKind.Unknown
}

private fun TdlConnectedWebsite.toDomain() = ConnectedWebsite(
    id = id,
    domain = domainName,
    browser = browser,
    platform = platform,
    logInDate = logInDate,
    lastActiveDate = lastActiveDate,
    ipAddress = ipAddress,
    location = location,
)
