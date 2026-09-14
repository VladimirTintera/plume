package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageReaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stav a stránkování jedné konverzace.
 *
 * Zprávy se drží **seřazené od nejnovější po nejstarší**, protože stejně je vykresluje
 * `LazyColumn(reverseLayout = true)` — nejnovější dole, stránkování nahoru. Kdyby se držely
 * opačně, každé přidání staré stránky by znamenalo přepočet celého seznamu.
 */
class ConversationViewModel(
    private val chatId: Long,
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val logger = Logger.withTag("Conversation")

    /** Serializuje stránkování — bez toho by dva rychlé scrolly načetly tutéž stránku dvakrát. */
    private val loadMutex = Mutex()

    init {
        viewModelScope.launch {
            repository.chatInfo(chatId).onSuccess { info ->
                _state.update { it.copy(info = info) }
            }
        }

        loadOlder()
        observeUpdates()
    }

    /**
     * Načte další stránku směrem do minulosti.
     *
     * Konec historie se pozná jen podle prázdné odpovědi — TDLib žádný příznak „jsi na začátku"
     * nedává. Proto [ConversationState.reachedStart].
     */
    fun loadOlder() {
        val current = _state.value
        if (current.loading || current.reachedStart) return

        viewModelScope.launch {
            loadMutex.withLock {
                if (_state.value.loading || _state.value.reachedStart) return@withLock
                _state.update { it.copy(loading = true) }

                // Nula znamená „od nejnovější"; jinak navazujeme pod nejstarší už načtenou zprávou.
                val from = _state.value.messages.lastOrNull()?.id ?: 0L

                repository.history(chatId = chatId, fromMessageId = from)
                    .onSuccess { page ->
                        _state.update { state ->
                            state.copy(
                                messages = merge(state.messages, page),
                                loading = false,
                                reachedStart = page.isEmpty(),
                                error = null,
                            )
                        }
                        resolveMissingQuotes()
                    }
                    .onFailure { failure ->
                        _state.update {
                            it.copy(loading = false, error = failure.message ?: "Historii se nepodařilo načíst.")
                        }
                    }
            }
        }
    }

    private fun observeUpdates() {
        viewModelScope.launch {
            repository.newMessages(chatId).collect { message ->
                _state.update { it.copy(messages = merge(it.messages, listOf(message))) }
                resolveMissingQuotes()
            }
        }

        viewModelScope.launch {
            repository.editedMessages(chatId).collect { message ->
                _state.update { it.copy(messages = merge(it.messages, listOf(message))) }
            }
        }

        viewModelScope.launch {
            repository.reactionUpdates(chatId).collect { update ->
                _state.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id == update.messageId) {
                                message.copy(reactions = update.reactions)
                            } else {
                                message
                            }
                        },
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.deletedMessages(chatId).collect { ids ->
                val removed = ids.toSet()
                _state.update { state ->
                    state.copy(messages = state.messages.filterNot { it.id in removed })
                }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val replyTo = _state.value.replyingTo?.id
        // Rozepsaná odpověď se zruší hned, ne až po potvrzení — jinak by při pomalé síti šlo
        // odeslat druhou zprávu se stejnou citací.
        clearReply()

        viewModelScope.launch {
            // Odeslaná zpráva se do seznamu nepřidává ručně — TDLib ji vrátí přes `newMessages`
            // včetně skutečného id a času. Ruční přidání by znamenalo duplicitu.
            repository.send(chatId, trimmed, replyTo).onFailure { failure ->
                _state.update { it.copy(error = failure.message ?: "Zprávu se nepodařilo odeslat.") }
            }
        }
    }

    /** Nastaví zprávu, na kterou se odpovídá. Zobrazí se nad vstupním polem. */
    fun replyTo(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        _state.update { it.copy(replyingTo = message, reactionPicker = null) }
    }

    fun clearReply() {
        _state.update { it.copy(replyingTo = null) }
    }

    /**
     * Doplní citované zprávy, které nejsou v načtené historii.
     *
     * Odpovídá se skoro vždy na něco nedávného, takže většina citací se najde v už načtených
     * zprávách zadarmo. Tohle řeší zbytek — typicky odpověď na starou zprávu hluboko v historii.
     */
    private fun resolveMissingQuotes() {
        val loaded = _state.value.messages.associateBy { it.id }
        val missing = _state.value.messages
            .mapNotNull { it.replyToMessageId }
            .filter { it !in loaded && it !in _state.value.quotes }
            .distinct()

        if (missing.isEmpty()) return

        viewModelScope.launch {
            missing.forEach { messageId ->
                repository.message(chatId, messageId).onSuccess { quoted ->
                    _state.update { it.copy(quotes = it.quotes + (messageId to quoted)) }
                }
            }
        }
    }

    /** Označí viditelné zprávy za přečtené. Volá obrazovka podle toho, co je reálně na displeji. */
    fun markRead(messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch { repository.markRead(chatId, messageIds) }
    }

    /**
     * Přepne reakci.
     *
     * Stav se **mění lokálně hned**, ještě než dorazí potvrzení ze serveru — jinak by mezi ťuknutím
     * a odpovědí byla viditelná prodleva a gesto by působilo rozbitě. Když volání selže, změna se
     * vrátí zpět. Server pak stejně pošle autoritativní stav přes `reactionUpdates`.
     */
    fun toggleReaction(messageId: Long, emoji: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val existing = message.reactions.firstOrNull { it.emoji == emoji }
        val add = existing?.isChosen != true

        val before = message.reactions
        applyReactions(messageId, before.toggled(emoji, add))

        viewModelScope.launch {
            repository.setReaction(chatId, messageId, emoji, add).onFailure { failure ->
                applyReactions(messageId, before)
                _state.update { it.copy(error = failure.message ?: "Reakci se nepodařilo uložit.") }
            }
        }
        hideReactionPicker()
    }

    /** Načte nabídku emoji pro danou zprávu a otevře výběr. */
    fun showReactionPicker(messageId: Long) {
        viewModelScope.launch {
            val emoji = repository.availableReactions(chatId, messageId).getOrDefault(emptyList())
            if (emoji.isEmpty()) {
                // Typicky Uložené zprávy: tam Telegram místo reakcí používá tagy a
                // `getMessageAvailableReactions` vrátí prázdný seznam.
                logger.d { "Chat $chatId nenabízí žádné reakce." }
                _state.update { it.copy(error = "V tomhle chatu nejdou reakce použít.") }
                return@launch
            }
            _state.update { it.copy(reactionPicker = ReactionPicker(messageId, emoji)) }
        }
    }

    fun hideReactionPicker() {
        _state.update { it.copy(reactionPicker = null) }
    }

    private fun applyReactions(messageId: Long, reactions: List<MessageReaction>) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(reactions = reactions) else it
                },
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}

/**
 * Sloučí novou dávku do stávajícího seznamu.
 *
 * Deduplikuje podle id (překryv stránek i editace přijdou jako tatáž zpráva) a udrží řazení
 * od nejnovější. Novější verze téže zprávy přebíjí starší.
 */
private fun merge(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
    if (incoming.isEmpty()) return current

    val byId = LinkedHashMap<Long, ChatMessage>(current.size + incoming.size)
    current.forEach { byId[it.id] = it }
    incoming.forEach { byId[it.id] = it }

    return byId.values.sortedByDescending { it.id }
}

/**
 * Spočítá, jak budou reakce vypadat po přepnutí. Reakce, která klesne na nulu, ze seznamu zmizí —
 * čip s nulou by vypadal jako chyba.
 */
private fun List<MessageReaction>.toggled(emoji: String, add: Boolean): List<MessageReaction> {
    val existing = firstOrNull { it.emoji == emoji }

    return when {
        // Vlastní avatar u nově přidané reakce zatím neznáme — dorazí se serverovým updatem
        // o chvíli později. Optimisticky se ukáže jen počet, což je lepší než čekat na data.
        existing == null && add ->
            this + MessageReaction(emoji, count = 1, isChosen = true, recentSenders = emptyList())

        existing == null -> this

        add -> map {
            if (it.emoji == emoji) it.copy(count = it.count + 1, isChosen = true) else it
        }

        else -> mapNotNull {
            when {
                it.emoji != emoji -> it
                it.count <= 1 -> null
                else -> it.copy(count = it.count - 1, isChosen = false)
            }
        }
    }
}

/** Otevřený výběr reakcí nad konkrétní zprávou. */
data class ReactionPicker(
    val messageId: Long,
    val emoji: List<String>,
)

data class ConversationState(
    val info: ChatInfo? = null,
    val reactionPicker: ReactionPicker? = null,
    /** Zpráva, na kterou se právě chystá odpověď. Zobrazuje se nad vstupním polem. */
    val replyingTo: ChatMessage? = null,
    /**
     * Citované zprávy, které nejsou v [messages] — doplněné jednotlivě.
     * Náhled odpovědi se hledá nejdřív v [messages], teprve pak tady.
     */
    val quotes: Map<Long, ChatMessage> = emptyMap(),
    /** Od nejnovější po nejstarší. */
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val reachedStart: Boolean = false,
    val error: String? = null,
)
