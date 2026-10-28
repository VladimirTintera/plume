package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_jump_date_empty
import eu.tintera.plume.resources.conversation_jump_date_failed
import eu.tintera.plume.resources.conversation_search_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Hledání v chatu: dotaz s pauzou v psaní, filtr podle člena, procházení výsledků se skokem
 * na každý a dotahování dalších stránek; skok na datum z kalendáře v hledání.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationSearch(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val jumper: MessageJumper,
    private val logger: Logger,
) {
    /** Běžící hledání v chatu (dotaz s pauzou, dotažení stránky); nový dotaz ho ruší. */
    private var searchJob: Job? = null

    /** Běžící hledání členů pro filtr podle odesílatele. */
    private var memberJob: Job? = null

    /** Lupa v hlavičce: hledání v chatu. */
    fun openSearch() {
        if (state.value.search == null) state.update { it.copy(search = InChatSearch()) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        memberJob?.cancel()
        state.update { it.copy(search = null, memberPicker = null) }
    }

    /**
     * Skok na první zprávu od začátku dne [epochSeconds]. Hledání se zavře — kalendář je jeho
     * součást, ale cíl je konverzace — a skočí se jako na citaci, s kotvou nahoře. Po výměně
     * okna stojí cíl s předělem dne zhruba v půlce obrazovky a nad ním poslední zpráva před
     * vybraným dnem (ověřeno na emulátoru) — je vidět, kde den začíná.
     */
    fun jumpToDate(epochSeconds: Int) {
        scope.launch {
            repository.firstMessageFrom(chatId, epochSeconds)
                .onSuccess { messageId ->
                    if (messageId == null) {
                        state.update { it.copy(error = UiText(Res.string.conversation_jump_date_empty)) }
                    } else {
                        closeSearch()
                        jumper.jumpToMessage(messageId, anchor = ScrollAnchor.Top)
                    }
                }
                .onFailure { failure -> state.update { it.copy(error = failure.userMessage(Res.string.conversation_jump_date_failed)) } }
        }
    }

    /**
     * Dotaz v hledání. Hledá se po krátké pauze v psaní a rovnou se skočí na nejnovější
     * výsledek — kdo hledá, chce vidět, kde to je, ne seznam.
     */
    fun onSearchQuery(text: String) {
        state.update { current -> current.search?.let { current.copy(search = it.copy(query = text)) } ?: current }
        restartSearch(debounce = true)
    }

    /**
     * Hledání jen ve zprávách jednoho člena skupiny (`null` = filtr pryč). Hledá se hned, bez pauzy
     * — výběr je hotové rozhodnutí, ne psaní. S odesílatelem stačí prázdný dotaz: výsledkem jsou
     * všechny jeho zprávy.
     */
    fun setSearchSender(sender: SenderInfo?) {
        state.update { current ->
            current.search?.let { current.copy(search = it.copy(sender = sender), memberPicker = null) } ?: current
        }
        restartSearch(debounce = false)
    }

    /** Spustí hledání pro současný text a odesílatele; rozběhnuté předchozí zruší. */
    private fun restartSearch(debounce: Boolean) {
        searchJob?.cancel()
        val search = state.value.search ?: return
        if (!search.hasCriteria) {
            state.update { current -> current.search?.let { current.copy(search = it.cleared()) } ?: current }
            return
        }
        val query = search.query.trim()
        val senderId = search.sender?.id
        searchJob = scope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            state.update { current -> current.search?.let { current.copy(search = it.copy(loading = true)) } ?: current }
            repository.searchInChat(chatId, query, senderUserId = senderId)
                .onSuccess { page ->
                    state.update { current ->
                        current.search?.let { current.copy(search = it.withFirstPage(query, page, senderId)) } ?: current
                    }
                    state.value.search?.current?.let { jumper.jumpToMessage(it, fromSearch = true) }
                }
                .onFailure { failure ->
                    val message = failure.reported(logger, Res.string.conversation_search_failed)
                    state.update { current ->
                        current.copy(error = message, search = current.search?.copy(loading = false))
                    }
                }
        }
    }

    /** Výběr člena pro hledání podle odesílatele. */
    fun openMemberPicker() {
        state.update { it.copy(memberPicker = MemberPicker()) }
        onMemberQuery("")
    }

    fun closeMemberPicker() {
        memberJob?.cancel()
        state.update { it.copy(memberPicker = null) }
    }

    /** Hledání člena podle jména, s krátkou pauzou v psaní; prázdné = první členové skupiny. */
    fun onMemberQuery(text: String) {
        state.update { current -> current.memberPicker?.let { current.copy(memberPicker = it.copy(query = text, loading = true)) } ?: current }
        memberJob?.cancel()
        memberJob = scope.launch {
            if (text.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
            repository.searchMembers(chatId, text.trim())
                .onSuccess { members ->
                    state.update { current ->
                        current.memberPicker?.let { current.copy(memberPicker = it.copy(members = members, loading = false)) } ?: current
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Členy chatu se nepodařilo načíst." }
                    state.update { current ->
                        current.memberPicker?.let { current.copy(memberPicker = it.copy(members = emptyList(), loading = false)) } ?: current
                    }
                }
        }
    }

    /** Šipka nahoru: starší výsledek. Na konci načtených se dotáhne další stránka. */
    fun searchOlder() {
        val search = state.value.search ?: return
        search.older()?.let { moved ->
            state.update { it.copy(search = moved) }
            moved.current?.let { jumper.jumpToMessage(it, fromSearch = true) }
            return
        }
        loadNextPage(moveToNext = true)
    }

    /** Seznam výsledků dojel na konec načtených: další stránka, ukázaný výsledek se nemění. */
    fun loadMoreResults() = loadNextPage(moveToNext = false)

    /** Ťuknutí na výsledek v seznamu: skok na něj jako šipkami. */
    fun searchGoTo(index: Int) {
        val moved = state.value.search?.at(index) ?: return
        state.update { it.copy(search = moved) }
        moved.current?.let { jumper.jumpToMessage(it, fromSearch = true) }
    }

    private fun loadNextPage(moveToNext: Boolean) {
        val search = state.value.search ?: return
        val from = search.nextFromMessageId ?: return
        if (search.loading) return
        searchJob = scope.launch {
            state.update { current -> current.search?.let { current.copy(search = it.copy(loading = true)) } ?: current }
            repository.searchInChat(chatId, search.resultsQuery, fromMessageId = from, senderUserId = search.resultsSenderId)
                .onSuccess { page ->
                    state.update { current ->
                        val loaded = current.search?.withNextPage(search.resultsQuery, page, search.resultsSenderId)
                        current.copy(search = if (moveToNext) loaded?.older() ?: loaded else loaded)
                    }
                    if (moveToNext) {
                        state.value.search?.current?.takeIf { it != search.current }?.let { jumper.jumpToMessage(it, fromSearch = true) }
                    }
                }
                .onFailure { failure ->
                    val message = failure.reported(logger, Res.string.conversation_search_failed)
                    state.update { current -> current.copy(error = message, search = current.search?.copy(loading = false)) }
                }
        }
    }

    /** Šipka dolů: novější výsledek. */
    fun searchNewer() {
        val moved = state.value.search?.newer() ?: return
        state.update { it.copy(search = moved) }
        moved.current?.let { jumper.jumpToMessage(it, fromSearch = true) }
    }
}

/** Pauza v psaní, po které se v chatu hledá. Každý dotaz jde na server. */
internal const val SEARCH_DEBOUNCE_MS = 300L
