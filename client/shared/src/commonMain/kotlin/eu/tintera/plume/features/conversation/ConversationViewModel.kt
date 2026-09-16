package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.SentMessageUpdate
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UrlOpener
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
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
    private val urlOpener: UrlOpener,
    private val clipboard: TextClipboard,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val logger = Logger.withTag("Conversation")

    /** Serializuje stránkování — bez toho by dva rychlé scrolly načetly tutéž stránku dvakrát. */
    private val loadMutex = Mutex()

    /** Rozlišuje jednotlivé lety textu do bubliny, když uživatel odešle dvě zprávy rychle po sobě. */
    private var flightCounter = 0L

    /** Už nahlášená zhlédnutí reklam — každé se hlásí jen jednou. */
    private val reportedSponsoredViews = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            repository.chatInfo(chatId).onSuccess { info ->
                _state.update {
                    it.copy(info = info, lastReadOutboxMessageId = info.lastReadOutboxMessageId)
                }
            }
        }

        viewModelScope.launch {
            // Mimo kanály vrací TDLib prázdný seznam, takže se nemusí ptát na typ chatu.
            repository.sponsoredMessages(chatId).onSuccess { sponsored ->
                _state.update { it.copy(sponsored = sponsored) }
            }
        }

        loadOlder()
        observeUpdates()
    }

    /**
     * Nahlásí zhlédnutí reklamy. Volá obrazovka, teprve až je vidět celá.
     *
     * Hlásí se **jen jednou za relaci** — opakované hlášení téhož zhlédnutí při každém scrollu
     * by nafouklo statistiku inzerenta.
     */
    fun reportSponsoredSeen(messageId: Long) {
        if (!reportedSponsoredViews.add(messageId)) return
        viewModelScope.launch { repository.reportSponsoredView(chatId, messageId) }
    }

    /**
     * Zpracuje kliknutí na reklamu.
     *
     * Hlášení odchází **před** otevřením odkazu — jakmile se otevře prohlížeč, aplikace jde do
     * pozadí a doručení by už nebylo jisté.
     */
    fun onSponsoredClick(sponsored: SponsoredMessage) {
        viewModelScope.launch {
            repository.reportSponsoredClick(chatId, sponsored.messageId)
            urlOpener.open(sponsored.url)
        }
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
            repository.sentMessageUpdates(chatId).collect { update ->
                // TDLib po potvrzení zprávě ZMĚNÍ id, takže ji nestačí aktualizovat — původní
                // se musí ze seznamu odebrat, jinak by tam zůstala dvakrát.
                val (oldId, message) = when (update) {
                    is SentMessageUpdate.Succeeded -> update.oldMessageId to update.message
                    is SentMessageUpdate.Failed -> update.oldMessageId to update.message
                }

                _state.update { state ->
                    val without = state.messages.filterNot { it.id == oldId }
                    state.copy(messages = merge(without, listOf(message)))
                }
            }
        }

        viewModelScope.launch {
            repository.readOutboxUpdates(chatId).collect { lastReadId ->
                _state.update { it.copy(lastReadOutboxMessageId = lastReadId) }
            }
        }

        viewModelScope.launch {
            repository.presenceUpdates(chatId).collect { presence ->
                _state.update { it.copy(presence = presence) }
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

        // Text „vyletí" ze vstupního pole do bubliny. Musí se zapamatovat hned při odeslání,
        // protože pole se vyprázdní dřív, než dorazí zpráva — a bez toho by nebylo co animovat.
        val flight = MessageFlight(id = ++flightCounter, text = trimmed, messageId = null)
        _state.update { it.copy(flight = flight) }

        viewModelScope.launch {
            repository.send(chatId, trimmed, replyTo)
                // TDLib vrací rozepsanou zprávu okamžitě, takže bublina naskočí bez čekání
                // na server. Skutečné id dorazí potvrzením a zprávu v seznamu nahradí.
                .onSuccess { pending ->
                    _state.update {
                        it.copy(
                            messages = merge(it.messages, listOf(pending)),
                            // Teprve teď ví let, kam míří — bublina si podle id vezme klíč.
                            flight = it.flight?.takeIf { f -> f.id == flight.id }
                                ?.copy(messageId = pending.id),
                        )
                    }

                    // Let se uklidí až po doběhnutí animace. Kdyby se zrušil dřív, zmizel by
                    // klíč sdíleného prvku uprostřed přechodu a text by skokem zmizel.
                    delay(FLIGHT_DURATION_MS)
                    _state.update { state ->
                        state.copy(flight = state.flight?.takeIf { it.id != flight.id })
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            error = failure.message ?: "Zprávu se nepodařilo odeslat.",
                            flight = it.flight?.takeIf { f -> f.id != flight.id },
                        )
                    }
                }
        }
    }

    /**
     * Otevře odkaz ze zprávy.
     *
     * Vede přes ViewModel, ne rovnou z bubliny do `UrlOpener`: až budeme rozlišovat interní
     * `t.me` odkazy (pozvánka do skupiny, odkaz na zprávu), rozhodne se to tady a obrazovka
     * se měnit nebude.
     */
    fun openLink(url: String) {
        urlOpener.open(url)
    }

    /** Nastaví zprávu, na kterou se odpovídá. Zobrazí se nad vstupním polem. */
    fun replyTo(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        _state.update { it.copy(replyingTo = message, messageMenu = null) }
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

    /**
     * Označí viditelné zprávy za přečtené. Volá obrazovka podle toho, co je reálně na displeji.
     *
     * Vlastní zprávy se odfiltrují **tady**, ne v obrazovce: ta zná jen id viditelných položek
     * a dohledávat si k nim zprávy by znamenalo držet je kvůli tomu v kompozici.
     */
    fun markRead(messageIds: List<Long>) {
        if (messageIds.isEmpty()) return

        val visible = messageIds.toSet()
        val incoming = _state.value.messages
            .filter { !it.isOutgoing && it.id in visible }
            .map { it.id }

        if (incoming.isEmpty()) return
        viewModelScope.launch { repository.markRead(chatId, incoming) }
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
        closeMenus()
    }

    /**
     * Otevře nabídku nad zprávou.
     *
     * Emoji a příznaky akcí se načítají **současně** — jsou to dva nezávislé dotazy a nabídka
     * čeká na oba, takže sériově by se čekání zdvojnásobilo.
     *
     * Prázdná nabídka emoji **není chyba**: v Uložených zprávách Telegram místo reakcí používá
     * tagy a `getMessageAvailableReactions` vrátí prázdný seznam. Dřív se kvůli tomu nabídka
     * neotevřela vůbec a nešlo ani odpovědět.
     */
    fun showMessageMenu(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return

        viewModelScope.launch {
            val (emoji, actions) = coroutineScope {
                val emojiTask = async { repository.availableReactions(chatId, messageId) }
                val actionsTask = async { repository.messageActions(chatId, messageId) }
                emojiTask.await().getOrDefault(emptyList()) to
                    actionsTask.await().getOrElse { failure ->
                        logger.w(failure) { "Příznaky zprávy $messageId se nepodařilo zjistit." }
                        MessageActions.Fallback
                    }
            }

            _state.update {
                it.copy(
                    messageMenu = MessageMenu(
                        messageId = messageId,
                        isOutgoing = message.isOutgoing,
                        emoji = emoji,
                        chosenEmoji = message.reactions.filter { r -> r.isChosen }.map { r -> r.emoji }.toSet(),
                        actions = actions,
                        hasText = message.text.isNotBlank(),
                    ),
                )
            }
        }
    }

    /** Přepne z nabídky na plný seznam emoji. Ten se listuje, takže zůstává bottom sheetem. */
    fun showAllReactions() {
        val menu = _state.value.messageMenu ?: return
        _state.update {
            it.copy(
                messageMenu = null,
                reactionSheet = ReactionPicker(menu.messageId, menu.emoji),
            )
        }
    }

    fun closeMenus() {
        _state.update { it.copy(messageMenu = null, reactionSheet = null) }
    }

    /**
     * Zkopíruje text zprávy do schránky.
     *
     * Kopíruje se text, jak ho vidí uživatel — bez entit. Formátování má smysl v Telegramu,
     * ale do cizí aplikace se stejně přenese jen holý řetězec.
     */
    fun copyMessage(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        clipboard.copy(message.text)
        closeMenus()
    }

    /** Otevře potvrzení smazání. Nevratná akce se nedělá na jedno ťuknutí. */
    fun requestDelete(messageId: Long) {
        val menu = _state.value.messageMenu ?: return
        _state.update {
            it.copy(
                messageMenu = null,
                pendingDelete = PendingDelete(
                    messageId = messageId,
                    canDeleteForAll = menu.actions.canDeleteForAll,
                    canDeleteForSelf = menu.actions.canDeleteForSelf,
                ),
            )
        }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    /**
     * Smaže zprávu.
     *
     * Ze seznamu se **neodstraňuje ručně** — TDLib pošle `UpdateDeleteMessages` a ten se už
     * zpracovává. Předběhnout server by znamenalo riskovat, že zpráva zmizí z obrazovky
     * a na serveru zůstane.
     */
    fun confirmDelete(revoke: Boolean) {
        val pending = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }

        viewModelScope.launch {
            repository.deleteMessages(chatId, listOf(pending.messageId), revoke)
                .onFailure { failure ->
                    _state.update {
                        it.copy(error = failure.message ?: "Zprávu se nepodařilo smazat.")
                    }
                }
        }
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

/**
 * Text na cestě ze vstupního pole do bubliny.
 *
 * Existuje jen po dobu animace. [messageId] je `null`, dokud TDLib nevrátí rozepsanou zprávu —
 * do té doby se text kreslí u vstupního pole, pak ho převezme bublina.
 *
 * [id] rozlišuje jednotlivé lety: bez něj by dvě rychle po sobě odeslané zprávy sdílely jeden
 * klíč a animace by se popraly.
 */
data class MessageFlight(
    val id: Long,
    val text: String,
    val messageId: Long?,
) {
    /** Klíč sdíleného prvku. Musí být na obou stranách stejný, jinak se prvky nespárují. */
    val sharedKey: String get() = "message-flight-$id"
}

/** Doba, po kterou se drží klíč sdíleného prvku po přistání v bublině. */
internal const val FLIGHT_DURATION_MS = 450L

/** Otevřený plný seznam reakcí (bottom sheet za tlačítkem „+"). */
data class ReactionPicker(
    val messageId: Long,
    val emoji: List<String>,
)

/**
 * Otevřená nabídka nad zprávou.
 *
 * Jsou v ní dvě různé věci: **rychlé reakce** (pár emoji na jedno ťuknutí) a **akce** nad zprávou.
 * Proto se kreslí jako dva samostatné kusy, ne jako jeden seznam — reagovat a odpovědět nejsou
 * varianty téhož.
 *
 * Nese jen data, **žádné souřadnice**. Kde přesně bublina na obrazovce leží, ví layout, ne
 * ViewModel; ten by se tím navázal na rozvržení, které se mění s velikostí okna.
 */
data class MessageMenu(
    val messageId: Long,
    /** Podle strany bubliny se nabídka zarovná, aby vyrůstala od zprávy, ne od kraje obrazovky. */
    val isOutgoing: Boolean,
    /** Nabídnutá emoji. Prázdné je běžný stav (Uložené zprávy), ne chyba. */
    val emoji: List<String>,
    /** Emoji, která uživatel u téhle zprávy už dal — v rychlém řádku se zvýrazní. */
    val chosenEmoji: Set<String>,
    val actions: MessageActions,
    /** Bez textu není co kopírovat, i kdyby to server dovolil. */
    val hasText: Boolean,
) {
    /** Do rychlého řádku se vejde jen pár; zbytek je za „+". */
    val quickEmoji: List<String> get() = emoji.take(MAX_QUICK_REACTIONS)

    /** „+" má smysl jen tehdy, když za ním něco navíc je. */
    val hasMoreEmoji: Boolean get() = emoji.size > MAX_QUICK_REACTIONS
}

/**
 * Zpráva čekající na potvrzení smazání.
 *
 * Drží se **zvlášť od [MessageMenu]**: mezi zavřením nabídky a potvrzením nesmí zůstat viset
 * obojí. Stejný důvod jako u `target`/`pendingDelete` v akcích nad chatem.
 */
data class PendingDelete(
    val messageId: Long,
    val canDeleteForSelf: Boolean,
    val canDeleteForAll: Boolean,
)

/**
 * Kolik emoji se vejde do rychlého řádku.
 *
 * Šest jako v Google Messages — na jeden řádek se vejdou i na úzkém telefonu vedle tlačítka „+".
 */
private const val MAX_QUICK_REACTIONS = 6

data class ConversationState(
    val info: ChatInfo? = null,
    /** Dostupnost protějšku pro podtitul hlavičky. `null` u skupin a kanálů. */
    val presence: Presence? = null,
    /** Otevřená nabídka nad zprávou (rychlé reakce + akce). */
    val messageMenu: MessageMenu? = null,
    /** Plný seznam reakcí za tlačítkem „+". */
    val reactionSheet: ReactionPicker? = null,
    /** Zpráva čekající na potvrzení smazání. */
    val pendingDelete: PendingDelete? = null,
    /** Zpráva, na kterou se právě chystá odpověď. Zobrazuje se nad vstupním polem. */
    val replyingTo: ChatMessage? = null,
    /**
     * Citované zprávy, které nejsou v [messages] — doplněné jednotlivě.
     * Náhled odpovědi se hledá nejdřív v [messages], teprve pak tady.
     */
    val quotes: Map<Long, ChatMessage> = emptyMap(),
    /** Protějšek přečetl odchozí zprávy až po tohle id včetně. */
    val lastReadOutboxMessageId: Long = 0L,
    /** Reklamy k zobrazení. Podle Telegram ToS se nesmí filtrovat ani skrývat. */
    val sponsored: List<SponsoredMessage> = emptyList(),
    /** Text právě letící ze vstupního pole do bubliny; `null` když se nic neodesílá. */
    val flight: MessageFlight? = null,
    /** Od nejnovější po nejstarší. */
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val reachedStart: Boolean = false,
    val error: String? = null,
)
