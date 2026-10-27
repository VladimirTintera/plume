package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.report_failed
import eu.tintera.plume.resources.report_done
import eu.tintera.plume.resources.reactions_delete_failed
import eu.tintera.plume.resources.reactions_deleted
import eu.tintera.plume.resources.conversation_reaction_failed
import eu.tintera.plume.resources.reactions_recent_clear_failed
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
     * Vymaže nedávno použité reakce (potvrzené ve výběru). Tlačítko z otevřeného výběru zmizí
     * hned; pořadí emoji se srovná až při příštím otevření (TDLib je řadí znovu). Při selhání
     * se tlačítko vrátí.
     */
    fun clearRecentReactions() {
        state.update { it.copy(messageMenu = it.messageMenu?.copy(hasRecentReactions = false)) }
        scope.launch {
            repository.clearRecentReactions().onFailure { failure ->
                state.update {
                    it.copy(
                        messageMenu = it.messageMenu?.copy(hasRecentReactions = true),
                        error = failure.reported(logger, Res.string.reactions_recent_clear_failed),
                    )
                }
            }
        }
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
            if (reaction in available.emoji) toggleReaction(messageId, reaction)
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
        state.update { it.copy(sheet = ReactionDetails(messageId = messageId, counts = counts)) }
        loadReactionDetails(messageId, filter = null)
        scope.launch {
            val moderation = repository.reactionModeration(chatId, messageId)
            state.update { current ->
                val open = current.sheet<ReactionDetails>()?.takeIf { it.messageId == messageId } ?: return@update current
                current.copy(sheet = open.copy(moderation = moderation))
            }
        }
    }

    /**
     * Správce smaže všechny reakce člena [senderId] u otevřené zprávy. Seznam se pak přečte
     * znovu; čipy pod zprávou srovná update reakcí od serveru.
     */
    fun deleteReactionsFrom(senderId: Long) {
        val open = state.value.sheet<ReactionDetails>() ?: return
        scope.launch {
            repository.deleteReactionsFrom(chatId, open.messageId, senderId)
                .onSuccess {
                    // Byl-li to poslední reagující, prázdný přehled nemá co ukazovat a zavře se. Pod
                    // filtrem to platit nemusí (jiné reakce zbýt můžou), takže se filtr zruší.
                    val remaining = open.items.filter { it.sender.id != senderId }
                    val close = remaining.isEmpty() && open.filter == null
                    val filter = open.filter.takeIf { remaining.isNotEmpty() }
                    state.update {
                        it.updatingSheet<ReactionDetails> { details -> details.takeUnless { close }?.copy(items = remaining, filter = filter) }
                            .copy(notice = UiText(Res.string.reactions_deleted))
                    }
                    if (!close) loadReactionDetails(open.messageId, filter)
                }
                .onFailure { failure -> state.update { it.copy(error = failure.reported(logger, Res.string.reactions_delete_failed)) } }
        }
    }

    /** Nahlásí reakce člena [senderId] moderátorům Telegramu (bez průvodce — server důvod nechce). */
    fun reportReactionsFrom(senderId: Long) {
        val open = state.value.sheet<ReactionDetails>() ?: return
        scope.launch {
            repository.reportReactionsFrom(chatId, open.messageId, senderId)
                .onSuccess { state.update { it.copy(notice = UiText(Res.string.report_done)) } }
                .onFailure { failure -> state.update { it.copy(error = failure.reported(logger, Res.string.report_failed)) } }
        }
    }

    /**
     * Zúží detail na jednu reakci ([emoji]), `null` vrátí všechny. Dosavadní seznam zůstává, dokud
     * nedorazí nový — výměna za indikátor by sheet pod prstem smrštila a zase natáhla.
     */
    fun filterReactionDetails(emoji: String?) {
        val open = state.value.sheet<ReactionDetails>() ?: return
        if (open.filter == emoji) return
        state.update { it.updatingSheet<ReactionDetails> { details -> details.copy(filter = emoji) } }
        loadReactionDetails(open.messageId, emoji)
    }

    private fun loadReactionDetails(messageId: Long, filter: String?) {
        scope.launch {
            repository.reactionDetails(chatId, messageId, filter)
                .onSuccess { details ->
                    state.update { current ->
                        // Mezitím mohl uživatel sheet zavřít, otevřít u jiné zprávy nebo přepnout filtr.
                        val open = current.sheet<ReactionDetails>()?.takeIf { it.messageId == messageId && it.filter == filter }
                            ?: return@update current
                        current.copy(sheet = open.copy(loading = false, items = details))
                    }
                }
                .onFailure { failure ->
                    state.update {
                        it.closingSheet<ReactionDetails>().copy(error = failure.reported(logger, Res.string.conversation_reaction_failed))
                    }
                }
        }
    }

    fun dismissReactionDetails() {
        state.update { it.closingSheet<ReactionDetails>() }
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
