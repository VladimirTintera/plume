package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.new_chat_load_failed
import eu.tintera.plume.resources.settings_blocked_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Koho výběr nabídne: kontakty a při hledání i ostatní známé chaty — jen soukromé (lidé a boti),
 * bez Uložených zpráv a bez těch, kdo už blokovaní jsou.
 *
 * **Jen soukromé chaty**: blokovat jde i kanál nebo skupinu jako ODESÍLATELE zpráv, jenže to se
 * dělá u jejich zprávy ve skupině; v seznamu k zablokování by skupina slibovala, že přestane
 * posílat zprávy, a to blokování neumí. Kontakty se hledají lokálně bez ohledu na diakritiku,
 * jako seznam blokovaných.
 */
internal fun blockCandidates(
    query: String,
    contacts: List<ChatSummary>,
    searchResults: List<ChatSummary>,
    savedMessagesChatId: Long?,
    blockedIds: Set<Long>,
): Pair<List<ChatSummary>, List<ChatSummary>> {
    fun ChatSummary.offered() = kind == ChatKind.Private && id != savedMessagesChatId && id !in blockedIds
    val needle = foldDiacritics(query.trim())
    val shownContacts = contacts
        .filter { it.offered() }
        .filter { needle.isEmpty() || foldDiacritics(it.title).contains(needle, ignoreCase = true) }
    if (needle.isEmpty()) return shownContacts to emptyList()
    val contactIds = contacts.mapTo(HashSet()) { it.id }
    val others = searchResults.filter { it.offered() && it.id !in contactIds }.distinctBy { it.id }
    return shownContacts to others
}

data class BlockSenderState(
    val query: String = "",
    val loading: Boolean = true,
    val contacts: List<ChatSummary> = emptyList(),
    val others: List<ChatSummary> = emptyList(),
    /** Vybraný, čeká na potvrzení; `null` = dialog zavřený. */
    val pending: ChatSummary? = null,
    val blocking: Boolean = false,
    /** Zablokováno — obrazovka odchází zpátky do seznamu blokovaných. */
    val done: Boolean = false,
    val error: UiText? = null,
)

/**
 * Výběr, koho zablokovat, ze seznamu blokovaných. Jediná cesta k zablokování někoho, s kým
 * uživatel žádný chat nemá otevřený — ostatní vedou přes nabídku nad chatem nebo profil.
 * Blokuje se přes [ChatRepository.setBlocked], tutéž cestu jako všude jinde.
 */
class BlockSenderViewModel(private val chats: ChatRepository) : ViewModel() {

    private val logger = Logger.withTag("BlockSender")

    private val query = MutableStateFlow("")
    private val contacts = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val blockedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val local = MutableStateFlow(BlockSenderState())
    private val search = ChatSearch(chats, viewModelScope)

    val state: StateFlow<BlockSenderState> = combine(
        combine(query, contacts, blockedIds) { q, c, b -> Triple(q, c, b) },
        search.state,
        chats.savedMessagesChatId,
        local,
    ) { (text, allContacts, blocked), searchState, savedId, base ->
        val (shownContacts, others) = blockCandidates(text, allContacts, searchState.results, savedId, blocked)
        base.copy(query = text, contacts = shownContacts, others = others)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockSenderState())

    init {
        viewModelScope.launch {
            chats.contacts()
                .onSuccess { contacts.value = it }
                .onFailure { failure ->
                    logger.w(failure) { "Kontakty k zablokování se nepodařilo načíst." }
                    local.update { it.copy(error = UiText(Res.string.new_chat_load_failed)) }
                }
            local.update { it.copy(loading = false) }
        }
        viewModelScope.launch {
            // Kdo už blokovaný je, se nenabízí. Když se seznam nepřečte, nabídne se každý —
            // zablokovat blokovaného znovu nic nerozbije.
            collectBlockedSenders { offset -> chats.blockedSenders(offset = offset, limit = BLOCKED_PAGE_SIZE) }
                .onSuccess { list -> blockedIds.value = list.mapTo(HashSet()) { it.id } }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    fun choose(chatId: Long) {
        val chat = state.value.let { s -> (s.contacts + s.others).firstOrNull { it.id == chatId } } ?: return
        local.update { it.copy(pending = chat) }
    }

    fun cancel() {
        local.update { it.copy(pending = null) }
    }

    /** Zablokuje vybraného; po úspěchu obrazovka odejde a seznam blokovaných ho ukáže. */
    fun confirm() {
        val chat = local.value.pending ?: return
        if (local.value.blocking) return
        local.update { it.copy(pending = null, blocking = true) }
        viewModelScope.launch {
            chats.setBlocked(chat.id, blocked = true)
                .onSuccess { local.update { it.copy(blocking = false, done = true) } }
                .onFailure { failure ->
                    logger.w(failure) { "Zablokování ${chat.id} se nepodařilo." }
                    local.update { it.copy(blocking = false, error = failure.userMessage(Res.string.settings_blocked_failed)) }
                }
        }
    }

    fun consumeError() {
        local.update { it.copy(error = null) }
    }
}
