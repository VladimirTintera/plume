package eu.tintera.plume.features.chats

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * Hledání chatů podle názvu a uživatelského jména. Sdílí ho hledání v seznamu chatů,
 * přeposílání i sdílení ze systému — všechno je „najdi chat".
 *
 * **Hledá se ve VŠECH známých chatech, ne jen v načteném seznamu**: archiv, chaty mimo složky
 * i ty, které lokálně ještě nejsou. Nejdřív lokální databáze (výsledek je hned), pak server;
 * serverové výsledky se připojí za lokální ([mergeChatResults]).
 *
 * S prázdným dotazem ukazuje návrhy — nejčastější lidi a historii hledání —, ale jen když
 * o ně volající požádá ([setSuggestionsWanted]). Výběr pro přeposílání má pod prázdným hledáním
 * celý seznam chatů a návrhy by tam byly navíc.
 *
 * Běží v rozsahu volajícího (`viewModelScope`) a hledá, jen dokud někdo stav čte.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSearch(
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("ChatSearch")

    private val query = MutableStateFlow("")
    private val suggestionsWanted = MutableStateFlow(false)

    /** Zvedá se po vymazání historie — návrhy se přečtou znovu. */
    private val suggestionsVersion = MutableStateFlow(0)

    val state: StateFlow<ChatSearchState> =
        combine(query, suggestionsWanted, suggestionsVersion) { text, wanted, _ -> text.trim() to wanted }
            // `transformLatest`: rozepsané hledání se dalším znakem zahodí, takže pomalá odpověď
            // serveru na „ja" nepřepíše výsledky pro „jana".
            .transformLatest { (text, wanted) ->
                if (text.isEmpty()) {
                    if (!wanted) {
                        emit(ChatSearchState())
                        return@transformLatest
                    }
                    emit(
                        ChatSearchState(
                            top = repository.topChats().logged("topChats").orEmpty(),
                            recent = repository.recentlyFoundChats().logged("historie").orEmpty(),
                        ),
                    )
                    return@transformLatest
                }

                // Krátká pauza: lokální hledání je rychlé, ale dotaz na server na každý znak ne.
                delay(TYPING_DEBOUNCE_MS)
                val local = repository.searchChats(text).logged("searchChats").orEmpty()
                emit(ChatSearchState(query = text, results = local, searching = true))

                val server = repository.searchChats(text, onServer = true).logged("searchChatsOnServer").orEmpty()
                emit(ChatSearchState(query = text, results = mergeChatResults(local, server)))
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ChatSearchState())

    fun setQuery(text: String) {
        query.value = text
    }

    /** Hledání je otevřené a má pod prázdným dotazem ukázat návrhy. */
    fun setSuggestionsWanted(wanted: Boolean) {
        suggestionsWanted.value = wanted
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

    private companion object {
        const val TYPING_DEBOUNCE_MS = 150L
    }
}

/**
 * Stav hledání. S prázdným [query] nese návrhy ([top], [recent]), jinak výsledky.
 * [searching] = lokální výsledky jsou, serverové ještě ne.
 */
data class ChatSearchState(
    val query: String = "",
    val results: List<ChatSummary> = emptyList(),
    val searching: Boolean = false,
    val top: List<ChatSummary> = emptyList(),
    val recent: List<ChatSummary> = emptyList(),
)

/**
 * Serverové výsledky za lokální, bez duplicit. Lokální pořadí zůstává — výsledky, které
 * uživatel už vidí, se nesmí po odpovědi serveru přeskládat pod prstem.
 */
fun mergeChatResults(local: List<ChatSummary>, server: List<ChatSummary>): List<ChatSummary> {
    val seen = local.mapTo(HashSet()) { it.id }
    return local + server.filter { seen.add(it.id) }
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
