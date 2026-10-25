package eu.tintera.plume.wear

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import eu.tintera.plume.core.wear.WatchLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

private val logger = Logger.withTag("WearPhoneLogin")

/**
 * Hodinková strana přihlášení potvrzeného z telefonu ([WatchLogin]): najde telefon s Plume,
 * pošle mu odkaz z QR kódu a poslouchá, jak to dopadlo. Potvrzení samo (a tedy `Ready`) přijde
 * jako u naskenovaného kódu — změnou stavu přihlášení v TDLib.
 */
class PhoneLogin(context: Context) {

    private val capabilities = Wearable.getCapabilityClient(context)
    private val messages = Wearable.getMessageClient(context)

    /**
     * Je v dosahu telefon s Plume? Telefon bez aplikace (nebo se starou verzí bez schopnosti)
     * odkaz nepřijme, tlačítko by tam jen klamalo.
     */
    suspend fun phoneAvailable(): Boolean = phoneNodes().isNotEmpty()

    /** Pošle odkaz všem telefonům s Plume v dosahu (bývá jeden); `false`, když není komu. */
    suspend fun send(link: String): Boolean {
        val nodes = phoneNodes()
        nodes.forEach { node ->
            runCatching { messages.sendMessage(node, WatchLogin.PATH_REQUEST, link.encodeToByteArray()).awaitResult() }
                .onFailure { logger.w(it) { "Odkaz k přihlášení se telefonu $node neposlal." } }
        }
        return nodes.isNotEmpty()
    }

    /** Výsledky od telefonu, dokud je kdo sbírá (posluchač se odhlásí s koncem sběru). */
    fun results(): Flow<WatchLogin.Result> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == WatchLogin.PATH_RESULT) {
                WatchLogin.Result.fromWire(event.data.decodeToString())?.let { trySend(it) }
            }
        }
        messages.addListener(listener)
        awaitClose { messages.removeListener(listener) }
    }

    private suspend fun phoneNodes(): List<String> = runCatching {
        capabilities.getCapability(WatchLogin.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).awaitResult()
            .nodes.map { it.id }
    }.onFailure { logger.w(it) { "Telefon s Plume se nepodařilo zjistit." } }.getOrDefault(emptyList())
}

/** Task API bez `coroutines-play-services`: dočká se mimo hlavní vlákno (blokující `Tasks.await`). */
internal suspend fun <T> Task<T>.awaitResult(): T = withContext(Dispatchers.IO) { Tasks.await(this@awaitResult) }
