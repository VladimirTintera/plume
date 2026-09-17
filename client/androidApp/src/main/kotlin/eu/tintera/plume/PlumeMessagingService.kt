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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Příjem push notifikací z FCM.
 *
 * Obsah pushe je **zašifrovaný** — Telegram posílá jen blob v poli `p` a rozšifrovat ho umí
 * jedině TDLib. Služba tedy nic nevykresluje; předá payload TDLib a ten vydá
 * `UpdateNotificationGroup`, který zobrazí `NotificationPresenter`. Tím je zobrazení notifikace
 * stejné bez ohledu na to, jestli přišla pushem, nebo z běžícího spojení.
 *
 * **[onMessageReceived] musí blokovat, dokud notifikace není na liště.** Proces v pozadí
 * (neukončený, jen odsunutý) je v cache a systém ho zmrazí zhruba deset vteřin poté, co přestane
 * běžet cokoli, co ho drží naživu — a služba pushe ho drží jen po dobu téhle metody. Dokud se
 * zpracování jen spustilo v korutině a metoda se hned vrátila, proces zamrzl uprostřed skládání
 * notifikace a ta se ukázala až při příštím probuzení, pokud vůbec. Metoda běží na vlákně
 * Firebase, ne na hlavním, takže `runBlocking` tu nic nezasekne.
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

        // Telegram posílá s vysokou prioritou. Když aplikace po takových pushích opakovaně
        // neukáže notifikaci, FCM jí prioritu sníží a v dozu pak zprávy čekají na údržbové okno.
        if (message.priority != message.originalPriority) {
            logger.w { "Push dorazil se sníženou prioritou (${message.originalPriority} → ${message.priority})." }
        }

        val started = TimeSource.Monotonic.markNow()
        runBlocking {
            // Strop platí pro CELÉ zpracování: při studeném startu čeká už `processPush`,
            // než TDLib dostane parametry a otevře databázi.
            val processed = withTimeoutOrNull(DELIVERY_TIMEOUT) { repository.processPush(payload) }
            if (processed?.isFailure == true) return@runBlocking
            val remaining = DELIVERY_TIMEOUT - started.elapsedNow()
            val delivered = processed != null && repository.awaitDelivered(remaining)
            if (delivered) {
                logger.d { "Push vyřízen za ${started.elapsedNow().inWholeMilliseconds} ms." }
            } else {
                logger.w { "Push se nestihl vyřídit do $DELIVERY_TIMEOUT." }
            }
        }
    }

    private fun registerToken(token: String) {
        val repository = repository ?: return
        scope.launch { repository.registerDevice(PushToken.Firebase(token)) }
    }

    private companion object {
        const val PAYLOAD_KEY = "p"

        /**
         * Jak dlouho se na notifikaci čeká. FCM dává zpracování pushe jen omezené okno; nad
         * ním aplikaci nic nezaručuje a opakované přetahování může vést ke snížení priority.
         */
        val DELIVERY_TIMEOUT = 8.seconds
    }
}
