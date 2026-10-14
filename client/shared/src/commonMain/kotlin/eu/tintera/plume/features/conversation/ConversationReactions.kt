package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_reaction_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reakce na zprávy: přepnutí reakce (hned v seznamu, potvrzení ze serveru dorazí updatem),
 * rychlá reakce dvojitým ťuknutím a přehled, kdo jak reagoval.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationReactions(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    /** Reakce na dvojité ťuknutí; nastavení účtu, ne chatu. */
    private val quickReaction: QuickReaction,
    private val closeMenus: () -> Unit,
    private val logger: Logger,
) {
    /**
     * Přepne reakci.
     *
     * Stav se **mění lokálně hned**, ještě než dorazí potvrzení ze serveru — jinak by mezi ťuknutím
     * a odpovědí byla viditelná prodleva a gesto by působilo rozbitě. Když volání selže, změna se
     * vrátí zpět. Server pak stejně pošle autoritativní stav přes `interactionUpdates`.
     */
    fun toggleReaction(messageId: Long, emoji: String) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val existing = message.reactions.firstOrNull { it.emoji == emoji }
        val add = existing?.isChosen != true

        val before = message.reactions
        applyReactions(messageId, before.toggled(emoji, add))

        scope.launch {
            repository.setReaction(chatId, messageId, emoji, add).onFailure { failure ->
                applyReactions(messageId, before)
                state.update { it.copy(error = failure.reported(logger, Res.string.conversation_reaction_failed)) }
            }
        }
        closeMenus()
    }

    /**
     * Rychlá reakce dvojitým ťuknutím: přepne reakci účtu ([QuickReaction]) jako ťuknutí na ni
     * v nabídce — už přidanou tedy odebere.
     *
     * **Dostupnost se ověřuje dřív, než se cokoli změní.** Kanál si sadu reakcí omezuje,
     * v Uložených zprávách reakce nejsou vůbec, a vlastní emoji může chtít Premium. Optimisticky
     * přidaná reakce, kterou server odmítne, by problikla a skončila chybovou hláškou — u gesta,
     * o kterém uživatel nemusí ani vědět, že ho udělal. Nedostupná reakce proto neudělá nic.
     * Odebrání vlastní reakce se neověřuje: ta zjevně dostupná byla.
     */
    fun quickReact(messageId: Long) {
        val reaction = quickReaction.reaction.value ?: return
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.reactions.any { it.emoji == reaction && it.isChosen }) {
            toggleReaction(messageId, reaction)
            return
        }
        scope.launch {
            val available = repository.availableReactions(chatId, messageId).getOrNull() ?: return@launch
            if (reaction in available) toggleReaction(messageId, reaction)
        }
    }

    /**
     * Ťuknutí na čip reakce: otevře detail a dotáhne, kdo reagoval.
     *
     * Reakci to **nepřepíná**. Přidat i odebrat jde z nabídky nad zprávou, kde je vybraná reakce
     * zvýrazněná a ťuknutí na ni ji sundá — jinak by se ťuknutí na čip pralo o význam
     * s otevřením detailu.
     */
    fun showReactionDetails(messageId: Long) {
        val counts = state.value.messages.firstOrNull { it.id == messageId }?.reactions.orEmpty()
        state.update { it.copy(reactionDetails = ReactionDetails(messageId = messageId, counts = counts)) }
        loadReactionDetails(messageId, filter = null)
    }

    /**
     * Zúží detail na jednu reakci ([emoji]), `null` vrátí všechny. Dosavadní seznam zůstává, dokud
     * nedorazí nový — výměna za indikátor by sheet pod prstem smrštila a zase natáhla.
     */
    fun filterReactionDetails(emoji: String?) {
        val open = state.value.reactionDetails ?: return
        if (open.filter == emoji) return
        state.update { it.copy(reactionDetails = open.copy(filter = emoji)) }
        loadReactionDetails(open.messageId, emoji)
    }

    private fun loadReactionDetails(messageId: Long, filter: String?) {
        scope.launch {
            repository.reactionDetails(chatId, messageId, filter)
                .onSuccess { details ->
                    state.update { current ->
                        // Mezitím mohl uživatel sheet zavřít, otevřít u jiné zprávy nebo přepnout filtr.
                        val open = current.reactionDetails?.takeIf { it.messageId == messageId && it.filter == filter }
                            ?: return@update current
                        current.copy(reactionDetails = open.copy(loading = false, items = details))
                    }
                }
                .onFailure { failure ->
                    state.update {
                        it.copy(
                            reactionDetails = null,
                            error = failure.reported(logger, Res.string.conversation_reaction_failed),
                        )
                    }
                }
        }
    }

    fun dismissReactionDetails() {
        state.update { it.copy(reactionDetails = null) }
    }

    private fun applyReactions(messageId: Long, reactions: List<MessageReaction>) {
        state.update { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id == messageId) it.copy(reactions = reactions) else it
                },
            )
        }
    }
}
