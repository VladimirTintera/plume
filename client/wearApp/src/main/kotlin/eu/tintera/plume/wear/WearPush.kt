package eu.tintera.plume.wear

import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.tintera.plume.core.accounts.primaryAccount
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.PushToken
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("WearPush")

/** Kolik času má push na rozšifrování a zobrazení, než systém službu ukončí. */
private val DELIVERY_TIMEOUT = 8.seconds

/**
 * Po přihlášení zaregistruje FCM token hodinek u TDLib — hodinky mají vlastní sezení, takže
 * i vlastní push, nezávisle na telefonu. Jako `PushRegistrar` telefonu, bez hovorů.
 */
class WearPushRegistrar(
    private val session: TelegramSession,
    private val notifications: NotificationRepository,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            session.authStage.collect { stage -> if (stage == AuthStage.Ready) register() }
        }
    }

    private suspend fun register() {
        val token = currentToken() ?: run {
            logger.w { "FCM token na hodinkách není — push nebude." }
            return
        }
        notifications.registerDevice(PushToken.Firebase(token))
            .onSuccess { logger.d { "Hodinky zaregistrované pro push." } }
    }

    private suspend fun currentToken(): String? = suspendCancellableCoroutine { continuation ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(task.result?.takeIf { task.isSuccessful })
            }
        }.onFailure {
            logger.w(it) { "Firebase na hodinkách není nastavený." }
            continuation.resume(null)
        }
    }
}

/**
 * Push od Telegramu: celý JSON jde do TDLib (`processPush`), notifikaci pak vydá TDLib a nakreslí
 * [WearNotifications]. Služba čeká, dokud se nevyřídí — po návratu z `onMessageReceived` smí
 * systém proces uspat. Hovory hodinky nemají, ty jen TDLib odmítne.
 */
class WearMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repository: NotificationRepository?
        get() = GlobalContext.getOrNull()?.primaryAccount()?.getOrNull()

    override fun onNewToken(token: String) {
        val repository = repository ?: return
        scope.launch { repository.registerDevice(PushToken.Firebase(token)) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val repository = repository ?: run {
            logger.w { "Push dorazil dřív, než se nastartoval Koin." }
            return
        }
        val payload = JSONObject().apply {
            message.data.forEach { (key, value) -> put(key, value) }
            put("google.sent_time", message.sentTime)
        }.toString()
        runBlocking {
            val processed = withTimeoutOrNull(DELIVERY_TIMEOUT) { repository.processPush(payload) }
            if (processed?.isSuccess == true) repository.awaitDelivered(DELIVERY_TIMEOUT)
            logger.d { "Push na hodinkách: ${if (processed == null) "vypršel čas" else if (processed.isSuccess) "vyřízen" else "odmítnut"}." }
        }
    }
}
