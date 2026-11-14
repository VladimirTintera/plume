package eu.tintera.plume

import android.app.ActivityManager
import android.net.ConnectivityManager
import android.os.PowerManager
import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.notifications.BackgroundDataWarning
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.PushKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import kotlin.time.Duration
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
 *
 * **Push hovoru je zvláštní případ**: TDLib ho rozšifrovat neumí a hovor se dozví až od serveru.
 * Služba proto otevře spojení znovu a čeká na hovor; jakmile zvoní, drží proces naživu Telecom
 * (delegovaná služba v popředí). Dřív služba při odmítnutém pushi rovnou skončila a hovor na
 * spící aplikaci nezazvonil vůbec.
 */
class PlumeMessagingService : FirebaseMessagingService() {

    private val logger = Logger.withTag("Push")

    // Vlastní scope: služba může být systémem zrušená dřív, než TDLib dokončí zpracování.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val accounts: Accounts?
        get() = GlobalContext.getOrNull()?.getOrNull()

    private val journal: CallJournal?
        get() = GlobalContext.getOrNull()?.getOrNull()

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

        val accounts = accounts ?: run {
            logger.w { "Push dorazil dřív, než se nastartoval Koin — ignoruji." }
            return
        }
        // Komu push patří: s víc účty podle příjemce, se kterým se účet registroval.
        val account = runBlocking { accounts.pushTarget(accounts.default.value.get<NotificationRepository>().pushReceiverId(payload)) } ?: run {
            logger.i { "Push nepatří žádnému přihlášenému účtu — ignoruji." }
            return
        }
        val repository = account.get<NotificationRepository>()

        // Telegram posílá s vysokou prioritou. Když aplikace po takových pushích opakovaně
        // neukáže notifikaci, FCM jí prioritu sníží a v dozu pak zprávy čekají na údržbové okno.
        if (message.priority != message.originalPriority) {
            logger.w { "Push dorazil se sníženou prioritou (${message.originalPriority} → ${message.priority})." }
        }

        val started = TimeSource.Monotonic.markNow()
        runBlocking {
            // Každý push v pozadí prozradí, jestli aplikace na mobilních datech má síť. Bez ní
            // hovor nezazvoní — a o tom se uživatel musí dozvědět dřív, než nějaký propadne.
            checkBackgroundData()
            // Strop platí pro CELÉ zpracování: při studeném startu čeká už `processPush`,
            // než TDLib dostane parametry a otevře databázi.
            val processed = withTimeoutOrNull(DELIVERY_TIMEOUT) { repository.processPush(payload) }
            if (processed?.isFailure == true) return@runBlocking
            val call = processed?.getOrNull() == PushKind.Call
            // Push hovoru do deníku i s tím, jak dlouho cestoval: zmeškaný hovor se pak pozná
            // od pushe, který přišel pozdě, nebo vůbec (`docs/poznatky/notifikace.md`).
            if (call) {
                journal?.record("Call push, sent ${System.currentTimeMillis() - message.sentTime} ms ago, priority ${message.priority}/${message.originalPriority}; ${environment()}")
                watchConnection(account)
            }
            // Hovor zvoní jen do zvednutí (desítky vteřin) a dozvědět se o něm jde jen od serveru:
            // spojení se otevře hned, ne až na timeout mrtvých soketů z doby před zmrazením.
            if (call && !repository.awaitIncomingCall(Duration.ZERO)) {
                journal?.record("Call not known yet, reconnecting")
                reconnect(account)
            }
            val callKnown = !call || repository.awaitIncomingCall(DELIVERY_TIMEOUT - started.elapsedNow())
            if (call) {
                journal?.record(
                    if (callKnown) "Call known ${started.elapsedNow().inWholeMilliseconds} ms after the push"
                    else "No call within $DELIVERY_TIMEOUT, waiting in a background job",
                )
            }
            val delivered = processed != null && callKnown && repository.awaitDelivered(DELIVERY_TIMEOUT - started.elapsedNow())
            if (delivered) {
                logger.d { "Push${if (call) " hovoru" else ""} vyřízen za ${started.elapsedNow().inWholeMilliseconds} ms." }
            } else {
                // Typicky odložená notifikace (až 30 s, když byl účet nedávno online jinde), nebo
                // hovor, ke kterému se spojení ještě neotevřelo — na obojí se čeká v úloze, ve
                // které systém proces nezmrazí.
                logger.i { "Push${if (call) " hovoru" else ""} se nestihl vyřídit do $DELIVERY_TIMEOUT, dočkám se v úloze." }
                NotificationDeliveryWorker.enqueue(applicationContext, account.slot, call = call && !callKnown)
            }
        }
    }

    /**
     * Co může aplikaci v pozadí zavřít síť: Doze, úspora baterie, omezená data na pozadí, omezení
     * aplikace v nastavení baterie — a jestli systém aplikaci síť zrovna nepouští (`BLOCKED`).
     * Zmeškaný hovor se pak pozná od hovoru, ke kterému se TDLib nemohl připojit.
     */
    private fun environment(): String = runCatching {
        val power = getSystemService(PowerManager::class.java)
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val activity = getSystemService(ActivityManager::class.java)
        @Suppress("DEPRECATION")
        val net = connectivity.activeNetworkInfo
        val dataSaver = when (connectivity.restrictBackgroundStatus) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "on"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "on, app exempt"
            else -> "off"
        }
        @Suppress("DEPRECATION")
        "idle ${power.isDeviceIdleMode}, battery saver ${power.isPowerSaveMode}, data saver $dataSaver, " +
            "background restricted ${activity.isBackgroundRestricted}, network ${net?.typeName ?: "none"} ${net?.detailedState ?: ""}".trimEnd()
    }.getOrElse { "environment unknown" }

    /**
     * Stav spojení TDLib do deníku po dobu, kdy se na hovor z pushe čeká (služba, pak úloha) —
     * ať je u zmeškaného hovoru vidět, jestli se TDLib k serveru vůbec dostal.
     */
    private fun watchConnection(account: AccountSession) {
        val journal = journal ?: return
        val connection = account.get<ConnectionRepository>()
        scope.launch {
            withTimeoutOrNull(CONNECTION_WATCH) {
                connection.state.collect { journal.record("TDLib connection: $it") }
            }
        }
    }

    private suspend fun checkBackgroundData() {
        val blocked = BackgroundDataWarning.check(applicationContext) ?: return
        val permission = GlobalContext.getOrNull()?.getOrNull<NotificationPermission>() ?: return
        if (blocked) journal?.record("Background data blocked on a metered network: calls on mobile data cannot ring")
        if (BackgroundDataWarning.report(applicationContext, permission, blocked)) logger.i { "Upozornění na zablokovaná data na pozadí ukázané." }
    }

    private suspend fun reconnect(account: AccountSession) {
        account.get<ConnectionController>().reconnect("Push hovoru")
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

    /** Nový token pro všechny účty; každý s vlastní registrací ([PushRegistrar]). */
    private fun registerToken(token: String) {
        val accounts = accounts ?: return
        accounts.all.value.forEach { account -> scope.launch { PushRegistrar(accounts, account).register(token) } }
    }

    private companion object {
        const val PAYLOAD_KEY = "p"

        /**
         * Jak dlouho se na notifikaci čeká. FCM dává zpracování pushe jen omezené okno; nad
         * ním aplikaci nic nezaručuje a opakované přetahování může vést ke snížení priority.
         */
        val DELIVERY_TIMEOUT = 8.seconds

        /** Jak dlouho po pushi hovoru zapisovat stav spojení: služba a úloha na pozadí (1 min). */
        val CONNECTION_WATCH = 70.seconds
    }
}
