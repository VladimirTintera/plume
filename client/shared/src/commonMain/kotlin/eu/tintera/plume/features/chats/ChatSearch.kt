package eu.tintera.plume.features.chats

import eu.tintera.plume.core.accounts.isSharedHistoryChat
import eu.tintera.plume.core.accounts.AccountSlot

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FoundMessage
import eu.tintera.plume.core.telegram.MessageSearchFilter
import eu.tintera.plume.core.telegram.SponsoredSearchChat
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
 * S [sponsored] k tomu sponzorované chaty, které Telegram k dotazu přimíchá (`getSearchSponsoredChats`)
 * — jen hlavní hledání v seznamu chatů, kde je specifikace reklam chce a kde se i hlásí jejich
 * zhlédnutí. Nová konverzace hledá veřejné chaty taky, ale reklama tam nepatří.
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
    private val sponsored: Boolean = false,
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
                    // Prázdný dotaz s druhem obsahu („fotky z léta" z hledání přirozeným jazykem)
                    // je hledání zpráv, ne návrhy.
                    if (input.text.isEmpty() && input.filter.content == null) {
                        suggest(input.suggestionsWanted)
                    } else {
                        search(input.text, input.filter)
                    }
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
        // Doporučení až po lidech a historii: jde se pro ně na server, a ty dva bloky jsou to,
        // pro co uživatel hledání otevírá. Jen v hlavním hledání, ne v archivu ani výběru chatu.
        if (everywhere) {
            // Hashtagy jen v hlavním hledání: hledat podle tagu jde jen tam.
            val tags = repository.searchedTags().logged("searchedTags").orEmpty()
            _state.update { if (it.query.isEmpty()) it.copy(tags = tags) else it }
            val recommended = repository.recommendedChannels().logged("recommendedChannels").orEmpty()
            _state.update { if (it.query.isEmpty()) it.copy(recommended = recommended) else it }
        }
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
         * a „plume", tedy šum nad tím, co uživatel hledal. Prázdný dotaz s druhem obsahu
         * hledá taky jen zprávy: chaty bez slova najít nejde.
         */
        if (everywhere && (isHashtagQuery(text) || text.isEmpty())) {
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
                if (sponsored) {
                    launch {
                        val ads = repository.searchSponsoredChats(text).logged("searchSponsoredChats").orEmpty()
                        _state.update { it.copy(sponsored = ads) }
                    }
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

    /** Jeden chat z historie hledání pryč; návrhy se pak přečtou znovu. */
    fun removeRecent(chatId: Long) {
        scope.launch {
            repository.removeRecentlyFound(chatId).logged("removeRecentlyFound")
            suggestionsVersion.value++
        }
    }

    /** Člověk z nejčastějších pryč (podržení v řádku „Lidé"); návrhy se pak přečtou znovu. */
    fun removeTop(chatId: Long) {
        scope.launch {
            repository.removeTopChat(chatId).logged("removeTopChat")
            suggestionsVersion.value++
        }
    }

    /** Nahlášený sponzorovaný chat z výsledků zmizí, jako reklama v kanálu. */
    fun dropSponsored(uniqueId: Long) {
        _state.update { state -> state.copy(sponsored = state.sponsored.filterNot { it.uniqueId == uniqueId }) }
    }

    /** Hashtag z historie hledání pryč; návrhy se pak přečtou znovu. */
    fun removeTag(tag: String) {
        scope.launch {
            repository.removeSearchedTag(tag).logged("removeSearchedTag")
            suggestionsVersion.value++
        }
    }

    /** Všechny hashtagy z historie pryč (Vymazat u sekce Hashtagy), jako u nedávných chatů. */
    fun clearTags() {
        scope.launch {
            repository.clearSearchedTags().logged("clearSearchedForTags")
            suggestionsVersion.value++
        }
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
    /** Naposledy hledané hashtagy v návrzích (jen hlavní hledání). */
    val tags: List<String> = emptyList(),
    /** Doporučené kanály v návrzích (jen hledání v hlavním seznamu); bez těch, které už odebírá. */
    val recommended: List<ChatSummary> = emptyList(),
    val global: List<ChatSummary> = emptyList(),
    /** Sponzorované chaty k dotazu (jen hlavní hledání); patří mezi [results] a [global]. */
    val sponsored: List<SponsoredSearchChat> = emptyList(),
    val messages: List<FoundMessage> = emptyList(),
    val messagesNextOffset: String? = null,
    val messagesLoading: Boolean = false,
    /**
     * Hledání přes víc účtů: účty spojeného výsledku mezi [results] (`mergedSearchState`), podle
     * účtu a id výsledku. První je účet výsledku, pak ostatní. Výsledek jen jednoho účtu tu není.
     */
    val resultAccounts: Map<Pair<AccountSlot, Long>, List<AccountSlot>> = emptyMap(),
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

/**
 * Hledání přes víc účtů (režim Všechny účty): výsledky hledání každého účtu ([sources], v pořadí
 * účtů) v jednom stavu, každý chat se slotem svého účtu ([ChatSummary.slot]). Návrhy pod
 * prázdným dotazem (lidé, historie, hashtagy, doporučené kanály) a reklamy jsou z hledání účtu
 * obrazovky ([screen]); patří jednomu účtu a míchat je nemá smysl.
 *
 * - **Vlastní chaty** účet po účtu; chat, který má víc účtů, je jednou jako ve společném seznamu
 *   (`mergeKey`: společná skupina nebo kanál, soukromý chat; ne obyčejná skupina ani chat
 *   s vlastním účtem). Stojí na místě prvního výskytu, otevře se účtem z [preferred] (kterým se do
 *   něj psalo naposledy), jinak tím prvním, a účty nese [ChatSearchState.resultAccounts].
 * - **Veřejné chaty** jen jednou: kanál najdou oba účty a otevřít ho jde kterýmkoli, bere se
 *   účet, který je v pořadí první.
 * - **Zprávy** podle data, nejnovější nahoře, napříč účty. Zpráva ve společné skupině nebo kanálu
 *   ([isSharedHistoryChat]) je jednou — najdou ji oba účty pod týmž id.
 * - **Uložené zprávy** ([savedMessages], id podle účtu) mezi vlastními chaty nejsou: TDLib je
 *   najde pod jménem účtu, takže by hledání „Jan" vrátilo Uložené zprávy každého Janova účtu.
 *   Řádek Uložených zpráv přidává hledání samo, jako u jednoho účtu. Vyřazuje je merge, ne
 *   obrazovka — ta zná jen id účtu obrazovky a pod ním je v ostatních účtech chat s ním.
 */
fun mergedSearchState(
    screen: ChatSearchState,
    sources: List<Pair<AccountSlot, ChatSearchState>>,
    savedMessages: Map<AccountSlot, Long> = emptyMap(),
    preferred: Map<Long, AccountSlot> = emptyMap(),
): ChatSearchState {
    val first = sources.firstOrNull()?.second ?: return screen
    val global = LinkedHashMap<Long, ChatSummary>()
    sources.forEach { (slot, state) -> state.global.forEach { chat -> global.getOrPut(chat.id) { chat.copy(slot = slot) } } }

    // Vlastní chaty: výskyty téhož chatu v různých účtech pod jedním klíčem, v pořadí účtů.
    val ownIds = savedMessages.values.toSet()
    val ownIdsKnown = sources.all { savedMessages[it.first] != null }
    val instances = LinkedHashMap<String, MutableList<ChatSummary>>()
    sources.forEach { (slot, state) ->
        state.results.filter { it.id != savedMessages[slot] }.forEach { chat ->
            val key = mergeKey(chat, savedMessages[slot], ownIds, ownIdsKnown) ?: "${slot.id}:${chat.id}"
            instances.getOrPut(key) { mutableListOf() } += chat.copy(slot = slot)
        }
    }
    val results = mutableListOf<ChatSummary>()
    val resultAccounts = mutableMapOf<Pair<AccountSlot, Long>, List<AccountSlot>>()
    instances.values.forEach { found ->
        val opener = found.firstOrNull { it.slot == preferred[it.id] } ?: found.first()
        results += opener
        val slot = opener.slot
        if (found.size > 1 && slot != null) {
            resultAccounts[slot to opener.id] = listOf(slot) + found.filter { it !== opener }.mapNotNull { it.slot }
        }
    }

    return screen.copy(
        query = first.query,
        filter = first.filter,
        results = results,
        resultAccounts = resultAccounts,
        searching = sources.any { it.second.searching },
        global = global.values.toList(),
        messages = sources
            .flatMap { (slot, state) -> state.messages.map { found -> found.copy(chat = found.chat.copy(slot = slot)) } }
            .sortedByDescending { it.date }
            // Táž zpráva ve společné skupině z obou účtů: stačí jednou (první účet v pořadí).
            .distinctBy { found ->
                if (isSharedHistoryChat(found.chat.id)) "${found.chat.id}:${found.messageId}" else "${found.chat.slot?.id}:${found.chat.id}:${found.messageId}"
            },
        // Další stránka je, má-li ji kterýkoli účet; dotažení jde všem a kdo nemá, nic neudělá.
        messagesNextOffset = sources.firstNotNullOfOrNull { it.second.messagesNextOffset },
        messagesLoading = sources.any { it.second.messagesLoading },
    )
}
