package eu.tintera.plume

import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessaging
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.PushToken
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Registruje zařízení pro push u Telegramu.
 *
 * **Registruje se po každém startu**, ne jen při změně tokenu. `FirebaseMessagingService.onNewToken`
 * se volá jen při první instalaci a při rotaci tokenu; Telegram ale registraci považuje za
 * dočasnou a doporučuje ji obnovovat. Bez toho by push po čase tiše přestal chodit.
 *
 * Čeká se na [AuthStage.Ready] — `registerDevice` u nepřihlášeného účtu nemá kam token uložit.
 */
class PushRegistrar(
    private val session: TelegramSession,
    private val repository: NotificationRepository,
) {

    private val logger = Logger.withTag("Push")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            session.authStage.collect { stage ->
                if (stage == AuthStage.Ready) register()
            }
        }
    }

    private suspend fun register() {
        val token = currentToken()
        if (token == null) {
            logger.w { "FCM token není k dispozici — push nebude fungovat." }
            return
        }

        repository.registerDevice(PushToken.Firebase(token))
            .onSuccess { logger.d { "Zařízení zaregistrováno pro push." } }
    }

    /** Firebase vrací token jako `Task`; převod na suspend, ať se nemusí tahat další knihovna. */
    private suspend fun currentToken(): String? = suspendCancellableCoroutine { continuation ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(task.result?.takeIf { task.isSuccessful })
            }
        }.onFailure {
            // Bez google-services.json se Firebase neinicializuje a getInstance vyhodí výjimku.
            logger.w(it) { "Firebase není nastavený." }
            continuation.resume(null)
        }
    }
}
