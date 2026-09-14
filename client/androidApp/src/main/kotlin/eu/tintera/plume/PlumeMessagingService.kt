package eu.tintera.plume

import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.PushToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Příjem push notifikací z FCM.
 *
 * Obsah pushe je **zašifrovaný** — Telegram posílá jen blob v poli `p` a rozšifrovat ho umí
 * jedině TDLib. Služba tedy nic nevykresluje; předá payload TDLib a ten vydá
 * `UpdateNotificationGroup`, který zobrazí `NotificationPresenter`. Tím je zobrazení notifikace
 * stejné bez ohledu na to, jestli přišla pushem, nebo z běžícího spojení.
 */
class PlumeMessagingService : FirebaseMessagingService() {

    private val logger = Logger.withTag("Push")

    // Vlastní scope: služba může být systémem zrušená dřív, než TDLib dokončí zpracování.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repository: NotificationRepository?
        get() = GlobalContext.getOrNull()?.get()

    override fun onNewToken(token: String) {
        logger.d { "Nový FCM token." }
        registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Telegram posílá payload v datové části pod klíčem `p`. Notifikační část nepoužívá,
        // protože obsah musí projít dešifrováním.
        val payload = message.data[PAYLOAD_KEY]
        if (payload == null) {
            logger.w { "Push bez payloadu — ignoruji." }
            return
        }

        val repository = repository ?: run {
            logger.w { "Push dorazil dřív, než se nastartoval Koin — ignoruji." }
            return
        }

        scope.launch { repository.processPush(payload) }
    }

    private fun registerToken(token: String) {
        val repository = repository ?: return
        scope.launch { repository.registerDevice(PushToken.Firebase(token)) }
    }

    private companion object {
        const val PAYLOAD_KEY = "p"
    }
}
