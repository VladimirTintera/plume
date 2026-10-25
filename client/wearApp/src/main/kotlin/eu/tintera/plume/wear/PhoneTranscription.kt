package eu.tintera.plume.wear

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import eu.tintera.plume.core.wear.WatchLogin
import eu.tintera.plume.core.wear.WatchTranscription
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

private val logger = Logger.withTag("WearPhoneTranscription")

/**
 * Jak dlouho hodinky čekají na přepis z telefonu: Gemma přepisuje zhruba v reálném čase a
 * hlasovku nad pět minut telefon nevezme, s rezervou na probuzení procesu a stažení souboru.
 */
private val PHONE_TIMEOUT = 4.minutes

/**
 * Přepis hlasovky v telefonu ([WatchTranscription]): pošle telefonu s Plume, kterou zprávu, a
 * dočká se textu. `null`, když telefon v dosahu není, přepsat neumí, je na jiném účtu, nebo se
 * nedočká — pak přijde na řadu přepis na hodinkách.
 */
class PhoneTranscription(context: Context) {

    private val capabilities = Wearable.getCapabilityClient(context)
    private val messages = Wearable.getMessageClient(context)

    suspend fun transcribe(accountId: Long, chatId: Long, messageId: Long): String? {
        val nodes = runCatching {
            capabilities.getCapability(WatchLogin.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).awaitResult().nodes.map { it.id }
        }.getOrDefault(emptyList())
        val phone = nodes.firstOrNull() ?: return null
        // Posluchač napřed, ať odpověď nepřijde dřív, než se poslouchá.
        val response = withTimeoutOrNull(PHONE_TIMEOUT) {
            callbackFlow {
                val listener = MessageClient.OnMessageReceivedListener { event ->
                    if (event.path != WatchTranscription.PATH_RESULT) return@OnMessageReceivedListener
                    WatchTranscription.Response.decode(event.data)
                        ?.takeIf { it.chatId == chatId && it.messageId == messageId }
                        ?.let { trySend(it) }
                }
                messages.addListener(listener).awaitResult()
                val sent = runCatching {
                    messages.sendMessage(phone, WatchTranscription.PATH_REQUEST, WatchTranscription.Request(accountId, chatId, messageId).encode()).awaitResult()
                }
                if (sent.isFailure) {
                    logger.w(sent.exceptionOrNull()) { "Žádost o přepis se telefonu neposlala." }
                    close()
                }
                awaitClose { messages.removeListener(listener) }
            }.firstOrNull()
        }
        if (response == null) logger.w { "Telefon přepis zprávy $messageId nevrátil." }
        return response?.takeIf { it.status == WatchTranscription.Status.Done }?.text?.takeIf { it.isNotBlank() }
            .also { if (response != null && it == null) logger.i { "Telefon přepis nedal: ${response.status}." } }
    }
}
