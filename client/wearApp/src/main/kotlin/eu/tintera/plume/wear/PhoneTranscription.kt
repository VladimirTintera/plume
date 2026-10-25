package eu.tintera.plume.wear

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import eu.tintera.plume.core.telegram.OnDeviceFix
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
 * Proč přepis na hodinkách nevyšel. Hodinky ho ukážou místo obecného „nepovedlo se": důvod leží
 * většinou v telefonu (vybraná Gemini Nano, jiný účet), kde ho uživatel z hodinek nepozná.
 */
sealed interface TranscribeFailure {
    /** Telefon s Plume není v dosahu (nebo Plume nemá). */
    data object NoPhone : TranscribeFailure

    /** Telefon žádost dostal, ale nedočkali jsme se odpovědi. */
    data object NoAnswer : TranscribeFailure

    /** Plume v telefonu je přihlášené k jinému účtu (nebo vůbec). */
    data object WrongAccount : TranscribeFailure

    /** Telefon přepisovat neumí; [fix] říká, co v něm zapnout, `null` = nic nepomůže. */
    data class PhoneCantTranscribe(val fix: OnDeviceFix?) : TranscribeFailure

    /** Přepis se rozběhl a nepovedl se (soubor, model, prázdný výsledek). */
    data object Other : TranscribeFailure
}

/** Výsledek [PhoneTranscription.transcribe]. */
sealed interface PhoneResult {
    data class Done(val text: String) : PhoneResult

    data class Failed(val reason: TranscribeFailure) : PhoneResult
}

/**
 * Přepis hlasovky v telefonu ([WatchTranscription]): pošle telefonu s Plume, kterou zprávu, a
 * dočká se textu. Když telefon v dosahu není, přepsat neumí, je na jiném účtu, nebo se nedočká,
 * vrátí proč — pak přijde na řadu přepis na hodinkách a důvod se ukáže, když nevyjde ani ten.
 */
class PhoneTranscription(context: Context) {

    private val capabilities = Wearable.getCapabilityClient(context)
    private val messages = Wearable.getMessageClient(context)

    suspend fun transcribe(accountId: Long, chatId: Long, messageId: Long): PhoneResult {
        val nodes = runCatching {
            capabilities.getCapability(WatchLogin.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).awaitResult().nodes.map { it.id }
        }.getOrDefault(emptyList())
        val phone = nodes.firstOrNull() ?: return PhoneResult.Failed(TranscribeFailure.NoPhone)
        var unsent = false
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
                    unsent = true
                    close()
                }
                awaitClose { messages.removeListener(listener) }
            }.firstOrNull()
        }
        if (response == null) {
            logger.w { "Telefon přepis zprávy $messageId nevrátil." }
            return PhoneResult.Failed(if (unsent) TranscribeFailure.NoPhone else TranscribeFailure.NoAnswer)
        }
        if (response.status != WatchTranscription.Status.Done) logger.i { "Telefon přepis nedal: ${response.status}." }
        return when (response.status) {
            WatchTranscription.Status.Done ->
                response.text.takeIf { it.isNotBlank() }?.let { PhoneResult.Done(it) } ?: PhoneResult.Failed(TranscribeFailure.Other)
            WatchTranscription.Status.Unavailable -> PhoneResult.Failed(TranscribeFailure.PhoneCantTranscribe(response.fix))
            WatchTranscription.Status.WrongAccount -> PhoneResult.Failed(TranscribeFailure.WrongAccount)
            WatchTranscription.Status.Failed -> PhoneResult.Failed(TranscribeFailure.Other)
        }
    }
}
