package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_delete_failed
import eu.tintera.plume.resources.conversation_schedule_failed
import eu.tintera.plume.resources.conversation_scheduled_load_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Seznam naplánovaných zpráv chatu: otevření, odeslání hned, přeplánování a smazání.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationScheduled(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val logger: Logger,
) {
    /**
     * Odešle připravené přílohy s popiskem.
     *
     * Jako odeslání textu: koncept se zahodí, citace se spotřebuje, „píšu" se zapomene. Na rozdíl od
     * textové zprávy TDLib u příloh koncept sám nesmaže, takže se maže výslovně. Bez letu ze vstupního
     * pole — přílohy z pole nevyletěly. Když odeslání selže, přílohy se vrátí nad pole, ať se nemusí
     * vybírat znovu.
     */
    /** Otevře seznam naplánovaných zpráv. */
    fun openScheduled() {
        state.update { it.copy(scheduledMessages = it.scheduledMessages ?: emptyList()) }
        loadScheduled()
    }

    fun closeScheduled() {
        state.update { it.copy(scheduledMessages = null) }
    }

    fun loadScheduled() {
        scope.launch {
            repository.scheduledMessages(chatId)
                .onSuccess { list ->
                    // Zavřený mezitím zůstane zavřený; prázdný se zavře sám.
                    state.update { state ->
                        if (state.scheduledMessages == null) state
                        else state.copy(scheduledMessages = list.takeIf { it.isNotEmpty() })
                    }
                }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_scheduled_load_failed)) }
                }
        }
    }

    /**
     * Naplánovanou zprávu odešle hned ([schedule] `null`), nebo přeplánuje. Seznam se pak čte
     * znovu — co se stalo, ví server; odeslaná zpráva dorazí do konverzace jako každá nová.
     */
    fun reschedule(messageId: Long, schedule: MessageSchedule?) {
        scope.launch {
            repository.reschedule(chatId, messageId, schedule)
                .onSuccess { loadScheduled() }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_schedule_failed)) }
                }
        }
    }

    fun deleteScheduled(messageId: Long) {
        scope.launch {
            repository.deleteMessages(chatId, listOf(messageId), revoke = true)
                .onSuccess { loadScheduled() }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_delete_failed)) }
                }
        }
    }
}
