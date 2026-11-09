package eu.tintera.plume

import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import eu.tintera.plume.core.accounts.primaryAccount
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.wear.WatchTranscription
import eu.tintera.plume.features.conversation.OnDeviceTranscription
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("WatchTranscription")

/** Jak dlouho se po probuzení procesu čeká na TDLib a na id účtu. */
private val READY_TIMEOUT = 10.seconds

/** Strop přepisu; delší hlasovku telefon stejně nepřepíše (`MAX_ON_DEVICE_SECONDS` je 5 minut). */
private val TRANSCRIBE_TIMEOUT = 3.minutes

/**
 * Přepis hlasovky na žádost hodinek ([WatchTranscription]): telefon zprávu sám stáhne, přepíše
 * Gemmou nebo systémem ([OnDeviceTranscription] — tentýž jako v konverzaci) a text pošle zpět.
 * Přepis zůstane i v telefonu (`LocalTranscripts`), takže ho uživatel uvidí i tam.
 *
 * - **Jen pro stejný účet.** Id zprávy platí jen v rámci účtu: žádost nese účet hodinek a
 *   telefon přihlášený jinam odpoví `WrongAccount`, místo aby přepisoval cizí zprávu.
 * - **Bez potvrzení**, na rozdíl od přihlášení: přepis nic nemění, je to jen čtení vlastní zprávy
 *   a zvuk telefon neopouští.
 * - **Probuzený proces:** zpráva umí spustit mrtvou aplikaci, na TDLib se čeká. `onMessageReceived`
 *   běží mimo hlavní vlákno a služba žije, dokud nevrátí — přepis se proto čeká uvnitř (blokující
 *   `runBlocking` se stropem), ne v korutině, která by po návratu mohla zaniknout s procesem.
 */
class WatchTranscriptionService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchTranscription.PATH_REQUEST) return
        val request = WatchTranscription.Request.decode(event.data) ?: return
        val koin = GlobalContext.getOrNull()?.primaryAccount() ?: return
        val response = runBlocking { respond(koin.get(), koin.get(), koin.get(), koin.get(), request) }
        Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, WatchTranscription.PATH_RESULT, response.encode())
            .addOnFailureListener { logger.w(it) { "Přepis se hodinkám ${event.sourceNodeId} neposlal." } }
    }

    private suspend fun respond(
        session: TelegramSession,
        chats: ChatRepository,
        conversations: ConversationRepository,
        transcription: OnDeviceTranscription,
        request: WatchTranscription.Request,
    ): WatchTranscription.Response {
        fun answer(status: WatchTranscription.Status, text: String = "") =
            WatchTranscription.Response(request.chatId, request.messageId, status, text)

        val ready = withTimeoutOrNull(READY_TIMEOUT) { session.authStage.first { it == AuthStage.Ready } } != null
        val account = if (ready) withTimeoutOrNull(READY_TIMEOUT) { chats.savedMessagesChatId.filterNotNull().first() } else null
        if (account != request.accountId) return answer(WatchTranscription.Status.WrongAccount)
        val check = transcription.check()
        // Co zapnout, hodinky ukážou: na nich se to nastavit nedá a bez toho by viděly jen „nepovedlo se".
        if (!check.available) return WatchTranscription.Response.unavailable(request.chatId, request.messageId, check.fix)

        val message = conversations.message(request.chatId, request.messageId).getOrNull()
            ?: return answer(WatchTranscription.Status.Failed)
        val fileId = when (val media = message.media) {
            is MessageMedia.VoiceNote -> media.fileId
            is MessageMedia.VideoNote -> media.fileId
            else -> return answer(WatchTranscription.Status.Failed)
        }
        val result = withTimeoutOrNull(TRANSCRIBE_TIMEOUT) { transcription.transcribe(request.chatId, request.messageId, fileId) }
        return when (result) {
            is SpeechTranscript.Done -> answer(WatchTranscription.Status.Done, result.text)
            else -> {
                logger.w { "Přepis zprávy ${request.messageId} pro hodinky se nepovedl ($result)." }
                answer(WatchTranscription.Status.Failed)
            }
        }
    }
}
