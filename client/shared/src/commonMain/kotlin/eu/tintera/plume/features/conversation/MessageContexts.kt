package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.proactiveVariant
import eu.tintera.plume.core.ai.NoDeviceLoad
import eu.tintera.plume.core.ai.DeviceLoad
import eu.tintera.plume.core.ai.AiTask
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.eventDetailsOf
import eu.tintera.plume.core.ai.generateAnswer
import eu.tintera.plume.core.ai.EventAnswer
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.eventPrompt
import eu.tintera.plume.core.ai.parseEventDetails
import eu.tintera.plume.core.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime

/**
 * Posudky termínů ze zpráv modelem v zařízení — druhý krok za pravidly ([findMessageContext]).
 *
 * - **Model se ptá jen na termín, který našla pravidla.** Běžná zpráva se k němu nedostane,
 *   stejně jako u varování před podvodem (`ScamWarnings`); účet a zásilku ověřuje kontrolní
 *   součet, tam model nemá co dodat.
 * - **Jen se zapnutou AI a modelem, který je PŘIPRAVENÝ.** Kvůli tomu se nic nestahuje; bez
 *   modelu platí pravidla ([EventVerdict.Unavailable]).
 * - **Fronta po jedné zprávě v [appScope]**, výsledek v paměti procesu podle (chat, zpráva, text):
 *   upravená zpráva je jiný klíč, takže se posoudí znovu. Rozjeté posouzení doběhne i po zavření
 *   konverzace.
 */
class MessageContexts(
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
    appScope: CoroutineScope,
    /** Úsporný režim a teplota: Gemma pod nimi sama od sebe nic nepočítá ([proactiveVariant]). */
    private val load: DeviceLoad = NoDeviceLoad,
) {
    private val logger = Logger.withTag("MessageContexts")

    private val _verdicts = MutableStateFlow<Map<Key, EventVerdict>>(emptyMap())

    /** Posudky termínů podle (chat, zpráva, text). Zpráva, která tu není, se modelu nedávala. */
    val verdicts: StateFlow<Map<Key, EventVerdict>> = _verdicts.asStateFlow()

    private val queue = Channel<Request>(Channel.UNLIMITED)

    data class Key(val chatId: Long, val messageId: Long, val textHash: Int)

    private class Request(val key: Key, val text: String, val sentAt: LocalDateTime, val start: LocalDateTime)

    init {
        appScope.launch {
            for (request in queue) judge(request)
        }
    }

    /**
     * Zařadí termín k posouzení, pokud ještě posouzený není. Levné; bez zapnuté AI nedělá nic
     * a termín pak nabízejí pravidla sama.
     */
    fun check(chatId: Long, messageId: Long, text: String, sentAt: LocalDateTime, start: LocalDateTime) {
        if (!enabled) return
        val key = Key(chatId, messageId, text.hashCode())
        var added = false
        _verdicts.update { current ->
            added = key !in current
            if (added) current + (key to EventVerdict.Pending) else current
        }
        if (added) queue.trySend(Request(key, text, sentAt, start))
    }

    /**
     * Otevřený chat: termíny, které se minule posoudit nepodařilo (model nebyl připravený,
     * selhal), dostanou novou šanci.
     */
    fun reopen(chatId: Long) {
        _verdicts.update { current -> current.filterNot { (key, verdict) -> key.chatId == chatId && verdict == EventVerdict.Unavailable } }
    }

    /**
     * Posuzuje se vůbec? Bez zapnuté AI [check] nic nedělá — kdo by kvůli němu hledal termín
     * pravidly ([findMessageContext]), udělá drahou práci zbytečně.
     */
    val enabled: Boolean get() = settings.messageContextEnabled.value && settings.onDeviceAiEnabled.value

    private suspend fun judge(request: Request) {
        val variant = ai.proactiveVariant(AiTask.Dates, settings, load)
        val ready = variant != null && enabled && runCatching { ai.status(variant) == OnDeviceAiStatus.Ready }.getOrDefault(false)
        if (variant == null || !ready) {
            // Model zatím není: termín nabídnou pravidla, posoudí se až s dalším otevřením chatu
            // ([reopen]). Odebrat klíč by znamenalo zařadit zprávu znovu s každou změnou stavu.
            _verdicts.update { it + (request.key to EventVerdict.Unavailable) }
            return
        }
        val verdict = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                ai.generateAnswer(
                    variant,
                    EventAnswer::class,
                    prompt = { structured -> eventPrompt(request.text, request.sentAt, request.start, structured) },
                    fromStructured = { eventDetailsOf(it, request.text) },
                    fromText = { parseEventDetails(it, request.text) },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.i(e) { "Termín ve zprávě se nepodařilo posoudit" }
            null
        }
        _verdicts.update {
            it + (
                request.key to when {
                    verdict == null -> EventVerdict.Unavailable
                    verdict.isEvent -> EventVerdict.Confirmed(verdict.title, verdict.place)
                    else -> EventVerdict.Rejected
                }
                )
        }
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
