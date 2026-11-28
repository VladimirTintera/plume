package eu.tintera.plume.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.TranscribeOffer
import eu.tintera.plume.core.telegram.transcribeOffer
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.transcribe_failed
import eu.tintera.plume.resources.transcript_failed
import eu.tintera.plume.resources.transcript_pending
import eu.tintera.plume.resources.transcript_premium_required
import eu.tintera.plume.resources.transcript_too_long
import eu.tintera.plume.resources.transcript_trial_too_long
import eu.tintera.plume.resources.transcript_used_up
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Co se u záznamu ukazuje pod ním: přepis, nebo proč přepis nejde. */
sealed interface TranscriptRequest {
    /** Přepis ze zprávy (rozpracovaný, hotový, chyba od serveru). */
    data object Shown : TranscriptRequest

    /** Server žádost o přepis vůbec nepřijal (`recognizeSpeech` selhalo). */
    data object StartFailed : TranscriptRequest

    /** Přepis se nenabízí: [offer] říká proč (došlé přepisy, moc dlouhý záznam, bez Premium). */
    data class Refused(val offer: TranscribeOffer) : TranscriptRequest

    /** Uživatel přepis schoval (i ten, který ve zprávě byl odjinud). */
    data object Hidden : TranscriptRequest
}

/**
 * Přepisy, o které si uživatel v konverzaci řekl, podle zprávy. Stav obrazovky, jako na
 * telefonu: výsledek přepisu je ve zprávě (přijde úpravou obsahu), tady jen, jestli je vidět.
 */
@Stable
class Transcripts {
    private val requests = mutableStateMapOf<Long, TranscriptRequest>()

    /**
     * Co ukázat u [message]: žádost uživatele, a když žádná není, hotový přepis, který ve zprávě
     * už je (přepsal ho někdo jiný, nebo telefon) — jako na telefonu je vidět hned.
     */
    fun of(message: ChatMessage): TranscriptRequest? =
        requests[message.id]?.takeIf { it != TranscriptRequest.Hidden }
            ?: TranscriptRequest.Shown.takeIf { requests[message.id] == null && message.speechTranscript() is SpeechTranscript.Done }

    /**
     * Přepis u [message] ukáže, nebo schová. S nabídkou [TranscribeOffer.Available] požádá
     * server — jen když ve zprávě přepis ještě není nebo minule selhal; jinak se jen ukáže.
     */
    fun toggle(state: ConversationState, message: ChatMessage, offer: TranscribeOffer, scope: CoroutineScope) {
        if (of(message) != null) {
            requests[message.id] = TranscriptRequest.Hidden
            return
        }
        if (offer != TranscribeOffer.Available) {
            requests[message.id] = TranscriptRequest.Refused(offer)
            return
        }
        requests[message.id] = TranscriptRequest.Shown
        val existing = message.speechTranscript()
        if (existing == null || existing is SpeechTranscript.Failed && existing.reason == SpeechTranscript.Failed.Reason.Other) {
            scope.launch { state.recognizeSpeech(message.id).onFailure { requests[message.id] = TranscriptRequest.StartFailed } }
        }
    }
}

/** Přepis hlasovky nebo kolečka ve zprávě; `null` u jiných zpráv a bez přepisu. */
fun ChatMessage.speechTranscript(): SpeechTranscript? = when (val media = media) {
    is MessageMedia.VoiceNote -> media.transcript
    is MessageMedia.VideoNote -> media.transcript
    else -> null
}

/** Délka záznamu, který jde přepsat (hlasovka, kolečko); `null` u jiných zpráv. */
fun ChatMessage.speechDurationSeconds(): Int? = when (val media = media) {
    is MessageMedia.VoiceNote -> media.durationSeconds
    is MessageMedia.VideoNote -> media.durationSeconds
    else -> null
}

/**
 * Jestli a jak přepis nabídnout (`transcribeOffer` z `core`, táž pravidla jako telefon): s Premium
 * vždycky, bez něj podle zkušebních přepisů. Televize přepsat v zařízení neumí, takže kde
 * server odmítne, zůstane tlačítko s vysvětlením.
 */
@Composable
fun rememberTranscribeOffer(durationSeconds: Int): TranscribeOffer {
    val chats = koinInject<ChatRepository>()
    val premium by chats.isPremium.collectAsState()
    val trial by chats.speechTrial.collectAsState()
    return transcribeOffer(isPremium = premium, trial = trial, durationSeconds = durationSeconds)
}

/** Text pod záznamem: přepis (rozpracovaný kurzívou), nebo věta, proč přepis není. */
@Composable
fun TranscriptText(message: ChatMessage, request: TranscriptRequest, color: Color, modifier: Modifier = Modifier) {
    val transcript = message.speechTranscript()
    val (text, pending) = when (request) {
        TranscriptRequest.Hidden -> return
        TranscriptRequest.StartFailed -> stringResource(Res.string.transcribe_failed) to false
        is TranscriptRequest.Refused -> when (val offer = request.offer) {
            is TranscribeOffer.TooLong -> stringResource(Res.string.transcript_trial_too_long, offer.maxSeconds.asDuration())
            is TranscribeOffer.UsedUp -> stringResource(Res.string.transcript_used_up)
            else -> stringResource(Res.string.transcript_premium_required)
        } to false
        TranscriptRequest.Shown -> when (transcript) {
            null -> stringResource(Res.string.transcript_pending) to true
            is SpeechTranscript.Pending -> transcript.partial.ifBlank { stringResource(Res.string.transcript_pending) } to true
            is SpeechTranscript.Done -> transcript.text to false
            is SpeechTranscript.Failed -> stringResource(
                when (transcript.reason) {
                    SpeechTranscript.Failed.Reason.TooLong -> Res.string.transcript_too_long
                    SpeechTranscript.Failed.Reason.PremiumRequired -> Res.string.transcript_premium_required
                    SpeechTranscript.Failed.Reason.Other -> Res.string.transcript_failed
                },
            ) to false
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = if (pending) FontStyle.Italic else FontStyle.Normal),
        color = color,
        modifier = modifier,
    )
}
