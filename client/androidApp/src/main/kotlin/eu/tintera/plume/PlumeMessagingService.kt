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
import org.json.JSONObject
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
        // Zašifrovaný obsah je v datové části pod klíčem `p`; bez něj to není push od Telegramu.
        if (message.data[PAYLOAD_KEY] == null) {
            logger.w { "Push bez payloadu — ignoruji." }
            return
        }
        val payload = message.toTdlibPayload()

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
                // Typicky odložená notifikace (až 30 s, když byl účet nedávno online jinde) —
                // na tu se čeká v úloze, ve které systém proces nezmrazí.
                logger.i { "Push se nestihl vyřídit do $DELIVERY_TIMEOUT, dočkám se v úloze." }
                NotificationDeliveryWorker.enqueue(applicationContext)
            }
        }
    }

    /**
     * Payload pro `processPushNotification`: **CELÁ datová část jako JSON**, ne jen pole `p`,
     * a k ní `google.sent_time` a `google.notification.sound`, jak chce dokumentace TDLib.
     * Dokud se předávalo jen `p`, TDLib každý push odmítl („Failed to parse payload as JSON
     * object") a push nefungoval nikdy — ani u zabité aplikace.
     */
    private fun RemoteMessage.toTdlibPayload(): String {
        val json = JSONObject()
        data.forEach { (key, value) -> json.put(key, value) }
        json.put("google.sent_time", sentTime)
        notification?.sound?.let { json.put("google.notification.sound", it) }
        return json.toString()
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
