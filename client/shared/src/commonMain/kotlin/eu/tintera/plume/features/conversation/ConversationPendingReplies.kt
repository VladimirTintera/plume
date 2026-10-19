package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.PendingBotMessage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_pending_stop_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Rozepsané odpovědi botů (TDLib `updatePendingMessage`) a tlačítko Zastavit.
 *
 * Pravidla předepisuje TDLib a drží se doslova:
 * - **ukazuje se nejdéle `pending_text_message_period` sekund** od posledního updatu,
 * - **update se stejným `draftId` předchozí nahradí** (obrazovka změnu animuje),
 * - **příchozí zpráva nebo odpověď s jiným `draftId` ji smaže** — ve vlákně je tedy nejvýš jedna.
 *   Příchozí zprávu hlídá ViewModel ve stejné změně stavu, ve které zprávu vkládá
 *   ([afterNewMessage]), jinak by na snímek byla vidět obojí.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationPendingReplies(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val logger: Logger,
) {
    /** `pending_text_message_period`; čte se jednou, při první odpovědi. */
    private var period: Duration? = null

    private var expiry: Job? = null

    /**
     * Zastavené odpovědi. Update, který bot poslal těsně před zastavením, může dorazit až po něm
     * — zastavená odpověď bez `keepOnStop` by se jím vrátila na obrazovku.
     */
    private val stopped = mutableSetOf<Long>()

    fun observe() {
        scope.launch { repository.pendingMessages(chatId).collect(::show) }
        scope.launch { repository.stoppedPendingMessages(chatId).collect(::markStopped) }
    }

    private suspend fun show(message: PendingBotMessage) {
        val wasStopped = message.draftId in stopped
        if (wasStopped && !message.keepOnStop) return
        val period = period ?: repository.pendingMessagePeriodSeconds().seconds.also { period = it }

        val reply = PendingReply(message, stopped = wasStopped)
        state.update { it.copy(pendingReply = reply) }

        // Odpočet běží od POSLEDNÍHO updatu: bot, který dál píše, odpověď drží na obrazovce.
        expiry?.cancel()
        expiry = scope.launch {
            delay(period)
            // Jen pokud je na obrazovce pořád tahle verze; mezitím ji mohla smazat zpráva.
            state.update { if (it.pendingReply === reply) it.copy(pendingReply = null) else it }
        }
    }

    /** Tlačítko Zastavit. */
    fun stop() {
        val reply = state.value.pendingReply?.takeIf { it.canStop } ?: return
        val draftId = reply.message.draftId
        markStopped(draftId)
        scope.launch {
            repository.stopPendingMessage(chatId, draftId).onFailure { failure ->
                // Bot generuje dál, takže další update odpověď zase ukáže (i s tlačítkem).
                stopped -= draftId
                state.update { it.copy(error = failure.reported(logger, Res.string.conversation_pending_stop_failed)) }
            }
        }
    }

    /** Zastavení tady i odjinud (`updateStopMessageDraft`): tlačítko zmizí, bez `keepOnStop` i text. */
    private fun markStopped(draftId: Long) {
        stopped += draftId
        state.update { current ->
            val reply = current.pendingReply?.takeIf { it.message.draftId == draftId } ?: return@update current
            current.copy(pendingReply = if (reply.message.keepOnStop) reply.copy(stopped = true) else null)
        }
    }
}

/**
 * Co zbude z rozepsané odpovědi, když do vlákna přijde [message]: příchozí ji smaže (bývá to
 * právě dopsaná odpověď bota), vlastní zpráva ji nechá.
 */
internal fun PendingReply?.afterNewMessage(message: ChatMessage): PendingReply? =
    this?.takeIf { message.isOutgoing }
