package eu.tintera.plume.features.chats

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FoundMessage
import eu.tintera.plume.core.telegram.MessageSearchFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Hledání chatů podle názvu a uživatelského jména. Sdílí ho hledání v seznamu chatů,
 * přeposílání i sdílení ze systému — všechno je „najdi chat".
 *
 * **Hledá se ve VŠECH známých chatech, ne jen v načteném seznamu**: archiv, chaty mimo složky
 * i ty, které lokálně ještě nejsou. Nejdřív lokální databáze (výsledek je hned), pak server;
 * serverové výsledky se připojí za lokální ([mergeChatResults]).
 *
 * S [everywhere] hledá navíc veřejné chaty, které uživatel nesleduje, a zprávy napříč chaty
 * (stránkované, [loadMoreMessages]). To chce jen hledání v seznamu chatů; výběr pro přeposílání
 * hledá jen chaty.
 *
 * S prázdným dotazem ukazuje návrhy — nejčastější lidi a historii hledání —, ale jen když
 * o ně volající požádá ([setSuggestionsWanted]). Výběr pro přeposílání má pod prázdným hledáním
 * celý seznam chatů a návrhy by tam byly navíc.
 *
 * Běží v rozsahu volajícího (`viewModelScope`); bez dotazu nedělá nic.
 */
class ChatSearch(
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
    private val everywhere: Boolean = false,
) {
    private val logger = Logger.withTag("ChatSearch")

    private val query = MutableStateFlow("")
    private val suggestionsWanted = MutableStateFlow(false)
    private val filter = MutableStateFlow(MessageSearchFilter())

    /** Zvedá se po vymazání historie — návrhy se přečtou znovu. */
    private val suggestionsVersion = MutableStateFlow(0)

    private val _state = MutableStateFlow(ChatSearchState())
    val state: StateFlow<ChatSearchState> = _state.asStateFlow()

    /** Dotažení další stránky zpráv. Nový dotaz ho ruší. */
    private var moreMessages: Job? = null

    init {
        scope.launch {
            combine(query, suggestionsWanted, suggestionsVersion, filter) { text, wanted, version, filter ->
                SearchInput(text.trim(), wanted, version, filter)
            }
                .distinctUntilChanged()
                // `collectLatest`: rozepsané hledání se dalším znakem zahodí, takže pomalá odpověď
                // serveru na „ja" nepřepíše výsledky pro „jana".
                .collectLatest { input ->
                    moreMessages?.cancel()
                    if (input.text.isEmpty()) suggest(input.suggestionsWanted) else search(input.text, input.filter)
                }
        }
    }

    private suspend fun suggest(wanted: Boolean) {
        if (!wanted) {
            _state.value = ChatSearchState()
            return
        }
        _state.value = ChatSearchState(
            top = repository.topChats().logged("topChats").orEmpty(),
            recent = repository.recentlyFoundChats().logged("recentlyFoundChats").orEmpty(),
        )
    }

    private suspend fun search(text: String, filter: MessageSearchFilter) {
        /*
         * Změnil se jen filtr zpráv: chaty pro tentýž dotaz zůstanou, jak jsou, a znovu se hledají
         * jen zprávy. Hledat znovu i chaty by seznam při každém ťuknutí na čip přeskládalo.
         */
        val previous = _state.value
        if (everywhere && previous.query == text && previous.filter != filter && !previous.searching) {
            _state.update { it.copy(filter = filter, messages = emptyList(), messagesNextOffset = null, messagesLoading = true) }
            val page = messagePage(text, "", filter)
            _state.update { it.copy(messages = page?.messages.orEmpty(), messagesNextOffset = page?.nextOffset, messagesLoading = false) }
            return
        }

        // Krátká pauza: lokální hledání je rychlé, ale dotaz na server na každý znak ne.
        delay(TYPING_DEBOUNCE_MS)

        /*
         * `#tag` je hledání zpráv s tagem (ťuknutí na hashtag v konverzaci). Hledání chatů
         * mřížku zahodí a vrátí chaty se slovem v názvu — „#plume" našlo kanály „Plumex"
         * a „plume", tedy šum nad tím, co uživatel hledal.
         */
        if (everywhere && isHashtagQuery(text)) {
            _state.value = ChatSearchState(query = text, filter = filter, messagesLoading = true)
            val page = messagePage(text, "", filter)
            _state.update {
                it.copy(messages = page?.messages.orEmpty(), messagesNextOffset = page?.nextOffset, messagesLoading = false)
            }
            return
        }

        val local = repository.searchChats(text).logged("searchChats").orEmpty()
        _state.value = ChatSearchState(
            query = text,
            filter = filter,
            results = local,
            searching = true,
            messagesLoading = everywhere,
        )

        coroutineScope {
            launch {
                val server = repository.searchChats(text, onServer = true).logged("searchChatsOnServer").orEmpty()
                _state.update { it.copy(results = mergeChatResults(it.results, server), searching = false) }
            }
            if (everywhere) {
                launch {
                    val global = repository.searchPublicChats(text).logged("searchPublicChats").orEmpty()
                    _state.update { it.copy(global = global) }
                }
                launch {
                    val page = messagePage(text, "", filter)
                    _state.update {
                        it.copy(
                            messages = page?.messages.orEmpty(),
                            messagesNextOffset = page?.nextOffset,
                            messagesLoading = false,
                        )
                    }
                }
            }
        }
    }

    /** Stránka zpráv: z vlastních chatů, nebo u hashtagu s [MessageSearchFilter.publicPosts] veřejné příspěvky. */
    private suspend fun messagePage(text: String, offset: String, filter: MessageSearchFilter) =
        if (searchesPublicPosts(text, filter)) {
            repository.searchPublicPosts(text, offset).logged("searchPublicPosts")
        } else {
            repository.searchMessages(text, offset, filter).logged("searchMessages")
        }

    fun setQuery(text: String) {
        query.value = text
    }

    /** Zúžení hledaných zpráv (druh chatu, období). Nový dotaz ho nemaže — platí, dokud se nezmění. */
    fun setFilter(value: MessageSearchFilter) {
        filter.value = value
    }

    /** Hledání je otevřené a má pod prázdným dotazem ukázat návrhy. */
    fun setSuggestionsWanted(wanted: Boolean) {
        suggestionsWanted.value = wanted
    }

    /** Další stránka zpráv; volá se, když se seznam blíží ke konci. */
    fun loadMoreMessages() {
        val current = _state.value
        val offset = current.messagesNextOffset ?: return
        if (current.messagesLoading || moreMessages?.isActive == true) return

        _state.update { it.copy(messagesLoading = true) }
        moreMessages = scope.launch {
            val page = messagePage(current.query, offset, current.filter)
            _state.update {
                // Mezitím se změnil dotaz nebo filtr — stránka patří k jinému hledání.
                if (it.query != current.query || it.filter != current.filter) return@update it
                it.copy(
                    messages = mergeFoundMessages(it.messages, page?.messages.orEmpty()),
                    // Selhání stránkování zastaví: jinak by se na konci seznamu zkoušelo pořád dokola.
                    messagesNextOffset = page?.nextOffset,
                    messagesLoading = false,
                )
            }
        }
    }

    /** Ťuknutí na výsledek — chat se zapíše do historie hledání. */
    fun recordFound(chatId: Long) {
        scope.launch { repository.addRecentlyFound(chatId).logged("addRecentlyFound") }
    }

    fun clearRecent() {
        scope.launch {
            repository.clearRecentlyFound().logged("clearRecentlyFound")
            suggestionsVersion.value++
        }
    }

    // Selhání hledání uživateli nehlásíme: prázdný výsledek („nic nenalezeno") je u hledání
    // přirozený stav a lokální výsledky zůstávají, i když server neodpoví.
    private fun <T> Result<T>.logged(what: String): T? =
        onFailure { logger.w(it) { "Hledání chatů ($what) selhalo." } }.getOrNull()

    private data class SearchInput(val text: String, val suggestionsWanted: Boolean, val version: Int, val filter: MessageSearchFilter)

    private companion object {
        const val TYPING_DEBOUNCE_MS = 150L
    }
}

/**
 * Stav hledání. S prázdným [query] nese návrhy ([top], [recent]), jinak výsledky.
 * [searching] = lokální výsledky jsou, serverové ještě ne; [messagesLoading] = načítá se
 * (první nebo další) stránka zpráv.
 */
data class ChatSearchState(
    val query: String = "",
    /** Filtr, se kterým se hledaly [messages]. */
    val filter: MessageSearchFilter = MessageSearchFilter(),
    val results: List<ChatSummary> = emptyList(),
    val searching: Boolean = false,
    val top: List<ChatSummary> = emptyList(),
    val recent: List<ChatSummary> = emptyList(),
    val global: List<ChatSummary> = emptyList(),
    val messages: List<FoundMessage> = emptyList(),
    val messagesNextOffset: String? = null,
    val messagesLoading: Boolean = false,
) {
    /**
     * Veřejné výsledky bez chatů, které jsou už mezi [results] — server je vynechává jen
     * u chatů v seznamu, ne u všech známých.
     */
    val globalOnly: List<ChatSummary>
        get() {
            val known = results.mapTo(HashSet()) { it.id }
            return global.filter { it.id !in known }
        }

    /** Hledání pro [query] doběhlo celé — prázdný výsledek teď znamená „nic nenalezeno". */
    val settled: Boolean get() = !searching && !messagesLoading
}

/** Hledají se veřejné příspěvky kanálů? Jen u hashtagu — obyčejný dotaz je API nebere. */
fun searchesPublicPosts(text: String, filter: MessageSearchFilter): Boolean =
    filter.publicPosts && isHashtagQuery(text)

/** Dotaz je hashtag (`#tag`, jedno slovo) — hledají se jen zprávy. */
fun isHashtagQuery(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.length > 1 && trimmed.startsWith('#') && trimmed.none { it.isWhitespace() }
}

/**
 * Serverové výsledky za lokální, bez duplicit. Lokální pořadí zůstává — výsledky, které
 * uživatel už vidí, se nesmí po odpovědi serveru přeskládat pod prstem.
 */
fun mergeChatResults(local: List<ChatSummary>, server: List<ChatSummary>): List<ChatSummary> {
    val seen = local.mapTo(HashSet()) { it.id }
    return local + server.filter { seen.add(it.id) }
}

/** Další stránka zpráv za předchozí. TDLib se na hranici stránek může opakovat. */
fun mergeFoundMessages(loaded: List<FoundMessage>, page: List<FoundMessage>): List<FoundMessage> {
    val seen = loaded.mapTo(HashSet()) { it.chat.id to it.messageId }
    return loaded + page.filter { seen.add(it.chat.id to it.messageId) }
}

/**
 * Co ukázat ve výběru chatu (přeposílání, sdílení): bez dotazu celý hlavní seznam, s dotazem
 * výsledky hledání — z nich i archivované chaty, které v hlavním seznamu nejsou.
 *
 * Během psaní zůstávají výsledky předchozího dotazu, dokud nedorazí nové. Kdyby se výběr
 * mezitím přepnul na načítání, problikl by při každém znaku; ťuknutí na zastaralý výsledek
 * přitom pořád míří na chat, který uživatel vidí. Načítá se jen, když není co ukázat.
 */
fun pickerChats(query: String, list: ChatListState, search: ChatSearchState): PickerChats {
    if (query.isBlank()) {
        return PickerChats(
            chats = (list as? ChatListState.Ready)?.chats.orEmpty(),
            loading = list is ChatListState.Loading,
        )
    }
    val settled = search.query == query.trim() && !search.searching
    return PickerChats(chats = search.results, loading = search.results.isEmpty() && !settled)
}

data class PickerChats(val chats: List<ChatSummary>, val loading: Boolean)
