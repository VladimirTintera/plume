package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageSenderOption
import eu.tintera.plume.core.telegram.messageSenderChoice
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.send_as_failed
import eu.tintera.plume.resources.send_as_premium
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Kým se do skupiny píše: vlastním účtem, kanálem, který uživatel spravuje, nebo jménem skupiny
 * jako anonymní správce (`ConversationState.messageSender`, avatar u vstupního pole).
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 *
 * - **Vybraného hlásí chat sám** ([ConversationRepository.messageSender]), i po změně z jiného
 *   zařízení. Nabídka se čte jen tam, kde odesílatele měnit jde, a znovu, když vybraný v ní chybí
 *   nebo se otevře výběr — kanál mohl mezitím přibýt.
 * - **Volba se ukáže hned** a server ji potvrdí updatem; když selže, vrátí se předchozí.
 * - **Zamčenou volbu (Premium) nevybere ani ViewModel**: výběr ji nabídne jen jako nedostupnou,
 *   tohle je pojistka, kdyby se k ní došlo jinudy.
 */
internal class ConversationSendAs(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val logger: Logger,
) {
    private var options: List<MessageSenderOption> = emptyList()
    private var currentId: Long? = null

    /** Sleduje, kým se píše. Volá se jednou, při startu ViewModelu. */
    fun observe() {
        scope.launch {
            repository.messageSender(chatId).collect { id ->
                currentId = id
                if (id != null && options.none { it.id == id }) load()
                publish()
            }
        }
    }

    /** Nabídka znovu ze serveru — při otevření výběru. */
    fun refresh() {
        if (currentId == null) return
        scope.launch {
            load()
            publish()
        }
    }

    fun choose(option: MessageSenderOption) {
        val before = state.value.messageSender ?: return
        if (option.id == before.current.id) return
        if (option.locked) {
            state.update { it.copy(notice = UiText(Res.string.send_as_premium)) }
            return
        }
        currentId = option.id
        publish()
        scope.launch {
            repository.setMessageSender(chatId, option.id).onFailure { failure ->
                // Jen když mezitím nepřišla jiná změna; ta je čerstvější než stav před ťuknutím.
                if (currentId == option.id) currentId = before.current.id
                publish()
                state.update { it.copy(error = failure.reported(logger, Res.string.send_as_failed)) }
            }
        }
    }

    private suspend fun load() {
        repository.availableMessageSenders(chatId)
            .onSuccess { options = it }
            .onFailure { failure -> logger.i { "Nabídku odesílatelů se nepodařilo načíst: ${failure.message}" } }
    }

    private fun publish() {
        val choice = messageSenderChoice(currentId, options)
        state.update { it.copy(messageSender = choice) }
    }
}
