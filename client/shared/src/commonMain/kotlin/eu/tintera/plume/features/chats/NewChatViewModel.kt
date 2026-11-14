package eu.tintera.plume.features.chats

import eu.tintera.plume.core.accounts.AccountSlot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.navigation.isTelegramLink
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.telegram.isPrivate
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.my_qr_not_telegram
import eu.tintera.plume.resources.new_chat_load_failed
import eu.tintera.plume.resources.secret_chat_start_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewChatState(
    val query: String = "",
    val loading: Boolean = true,
    val contacts: List<ChatSummary> = emptyList(),
    val chatResults: List<ChatSummary> = emptyList(),
    val globalResults: List<ChatSummary> = emptyList(),
    val savedMessagesChatId: Long? = null,
    val error: UiText? = null,
    /**
     * Vybírá se, s kým začít tajný chat: jen lidé (kontakty a známé soukromé chaty), výběr
     * chat nezakládá, ale vytvoří tajný.
     */
    val secretMode: Boolean = false,
)

class NewChatViewModel(
    private val chatRepository: ChatRepository,
    private val linkRequests: LinkOpenRequests = LinkOpenRequests(),
    /** Účet nové konverzace: naskenovaný odkaz se otevře v něm, bez ptaní na účet. */
    private val slot: AccountSlot? = null,
) : ViewModel() {

    private val logger = Logger.withTag("NewChatViewModel")

    private val query = MutableStateFlow("")
    private val contacts = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<UiText?>(null)
    private val secretMode = MutableStateFlow(false)

    /** Založený tajný chat — obrazovka ho otevře a hodnotu spotřebuje. */
    private val _openSecretChat = MutableStateFlow<Long?>(null)
    val openSecretChat: StateFlow<Long?> = _openSecretChat

    private val search = ChatSearch(chatRepository, viewModelScope, everywhere = true)

    val state: StateFlow<NewChatState> = combine(
        combine(loading, error, contacts) { l, e, c -> Triple(l, e, c) },
        combine(query, search.state, chatRepository.savedMessagesChatId) { q, s, id -> Triple(q, s, id) },
        secretMode,
    ) { (isLoading, err, allContacts), (text, searchState, savedId), secret ->
        newChatState(isLoading, err, allContacts, text, searchState, savedId).let { state ->
            if (!secret) {
                state
            } else {
                // Tajný chat jde jen s člověkem: bez Uložených zpráv, skupin, kanálů a botů,
                // které server stejně odmítne. Veřejné výsledky hledání jsou kanály a skupiny.
                state.copy(
                    secretMode = true,
                    savedMessagesChatId = null,
                    chatResults = state.chatResults.filter { it.isPrivate && !it.isSecret && it.id != savedId },
                    globalResults = emptyList(),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewChatState())

    private fun newChatState(
        isLoading: Boolean,
        err: UiText?,
        allContacts: List<ChatSummary>,
        text: String,
        searchState: ChatSearchState,
        savedId: Long?,
    ): NewChatState {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) {
            NewChatState(
                query = text,
                loading = isLoading,
                contacts = allContacts,
                savedMessagesChatId = savedId,
                error = err,
            )
        } else {
            val filteredContacts = allContacts.filter { it.title.contains(trimmed, ignoreCase = true) }
            val contactIds = allContacts.mapTo(HashSet()) { it.id }
            val otherKnown = searchState.results.filter { it.id !in contactIds && it.id != savedId }
            NewChatState(
                query = text,
                loading = isLoading || searchState.searching,
                contacts = filteredContacts,
                chatResults = otherKnown,
                globalResults = searchState.globalOnly.filter { it.id !in contactIds },
                savedMessagesChatId = savedId,
                error = err,
            )
        }
    }

    fun startSecretMode() {
        secretMode.value = true
    }

    /** Zpět z výběru pro tajný chat do obyčejné Nové zprávy. */
    fun leaveSecretMode(): Boolean {
        if (!secretMode.value) return false
        secretMode.value = false
        return true
    }

    /**
     * Vybraný člověk v režimu tajného chatu. Soukromý chat má id uživatele, takže stačí ho
     * předat; nový tajný chat se otevře, chyba se ukáže jako hláška.
     */
    fun pickForSecretChat(chatId: Long) {
        // Dvojí ťuknutí by založilo dva tajné chaty — každý je u protějšku vidět zvlášť.
        if (startingSecretChat) return
        startingSecretChat = true
        viewModelScope.launch {
            chatRepository.createSecretChat(userId = chatId)
                .onSuccess { _openSecretChat.value = it }
                .onFailure { failure ->
                    logger.w(failure) { "Tajný chat se nepodařilo začít." }
                    error.value = failure.userMessage(Res.string.secret_chat_start_failed)
                }
            startingSecretChat = false
        }
    }

    private var startingSecretChat = false

    fun consumeOpenSecretChat() {
        _openSecretChat.value = null
    }

    init {
        loadContacts()
    }

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    fun loadContacts() {
        loading.value = true
        viewModelScope.launch {
            chatRepository.contacts()
                .onSuccess { list ->
                    contacts.value = list
                    loading.value = false
                }
                .onFailure { failure ->
                    logger.w(failure) { "Načtení kontaktů selhalo." }
                    error.value = UiText(Res.string.new_chat_load_failed)
                    loading.value = false
                }
        }
    }

    fun consumeError() {
        error.value = null
    }

    /**
     * Kód naskenovaný z řádku „Naskenovat QR kód": odkaz na Telegram otevře shell
     * ([LinkOpenRequests]) — kontaktní odkaz chat s tím člověkem, a „Nová zpráva" tím odejde.
     * Cokoli jiného jen ohlásí, že to kód Telegramu není.
     */
    fun onScanned(content: String) {
        val text = content.trim()
        if (isTelegramLink(text)) linkRequests.request(text, slot) else error.value = UiText(Res.string.my_qr_not_telegram)
    }

    fun onScanFailed(message: UiText) {
        error.value = message
    }
}
