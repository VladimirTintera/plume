package eu.tintera.plume.core.notifications

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.PushToken
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.core.Koin
import platform.Foundation.NSData
import platform.Foundation.NSError

/**
 * Registrace pro APNs push u Telegramu.
 *
 * Token vydá iOS do `AppDelegate` (Swift, `iOSApp.swift`), který ho předá sem ([didRegister]);
 * zaregistruje se u TDLib, až je účet přihlášený — `registerDevice` u nepřihlášeného účtu nemá
 * kam token uložit. Po odhlášení a novém přihlášení se registruje znovu, stejně jako
 * `PushRegistrar` na Androidu.
 *
 * **Registruje se po každém startu**, ne jen při změně tokenu: Telegram registraci považuje za
 * dočasnou a iOS token může vyměnit kdykoli (obnova zálohy, přeinstalace).
 *
 * **[sandbox] musí odpovídat prostředí, ze kterého APNs token vydal** — build z Xcode dostane
 * token vývojového prostředí, TestFlight a App Store produkčního. Při nesouladu Telegram push
 * odešle do špatného prostředí a APNs ho tiše zahodí. Rozhoduje Swift (`#if DEBUG`).
 */
object IosPush {

    private val logger = Logger.withTag("Push")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val token = MutableStateFlow<PushToken.ApplePush?>(null)

    internal fun start(koin: Koin) {
        val session = koin.get<TelegramSession>()
        val repository = koin.get<NotificationRepository>()
        scope.launch {
            combine(token.filterNotNull(), session.authStage) { token, stage -> token.takeIf { stage == AuthStage.Ready } }
                .filterNotNull()
                .collect { token ->
                    repository.registerDevice(token)
                        .onSuccess { logger.i { "Zařízení zaregistrováno pro APNs (sandbox=${token.sandbox})." } }
                }
        }
        // Token dostane jen aplikace, která smí notifikace; o oprávnění se ptá až přihlášená část.
        scope.launch {
            if (koin.get<NotificationPermission>().currentStatus().isGranted()) registerForRemoteNotifications()
        }
    }

    /** Z `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`. */
    fun didRegister(deviceToken: NSData, sandbox: Boolean) {
        token.value = PushToken.ApplePush(token = deviceToken.hex(), sandbox = sandbox)
    }

    /** Na simulátoru bez APNs i v buildu bez entitlementu `aps-environment`. */
    fun didFailToRegister(error: NSError) {
        logger.w { "Registrace u APNs selhala: ${error.localizedDescription}" }
    }

    /** Telegram (a TDLib) chce token jako hex řetězec, ne base64 ani `description` z NSData. */
    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.hex(): String {
        val bytes = this.bytes?.reinterpret<UByteVar>() ?: return ""
        return buildString(length.toInt() * 2) {
            for (i in 0 until length.toInt()) {
                val byte = bytes[i].toInt()
                append(HEX[byte shr 4])
                append(HEX[byte and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789abcdef"
}
