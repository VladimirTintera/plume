package eu.tintera.plume

import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessaging
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.PushToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Registruje zařízení pro push u Telegramu, za jeden účet.
 *
 * **Registruje se po každém startu**, ne jen při změně tokenu. `FirebaseMessagingService.onNewToken`
 * se volá jen při první instalaci a při rotaci tokenu; Telegram ale registraci považuje za
 * dočasnou a doporučuje ji obnovovat. Bez toho by push po čase tiše přestal chodit.
 *
 * Čeká se na [AuthStage.Ready] — `registerDevice` u nepřihlášeného účtu nemá kam token uložit.
 *
 * **S víc účty má každý účet registraci vlastní** a s ní id příjemce (`PushReceiverId`), podle
 * kterého se pak pozná, komu push patří (`Accounts.pushTarget`). Ostatní účty se serveru
 * jmenují (`other_user_ids`).
 */
class PushRegistrar(
    private val accounts: Accounts,
    private val account: AccountSession,
) {

    private val logger = Logger.withTag("Push")

    fun start() {
        // Scope účtu: s odebraným účtem registrace skončí.
        account.koin.get<CoroutineScope>(named(APP_SCOPE)).launch {
            account.session.authStage.collect { stage ->
                if (stage == AuthStage.Ready) currentToken()?.let { register(it) } ?: logger.w { "FCM token není k dispozici — push nebude fungovat." }
            }
        }
    }

    suspend fun register(token: String) {
        account.get<NotificationRepository>()
            .registerDevice(PushToken.Firebase(token), otherUserIds = accounts.otherUserIds(account.slot))
            .onSuccess { receiverId ->
                accounts.rememberPushReceiver(account.slot, receiverId)
                logger.d { "Zařízení zaregistrováno pro push (účet ${account.slot.id})." }
            }
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
