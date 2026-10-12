package eu.tintera.plume.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.AdminRights
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.MAX_MEMBERS_REQUEST
import eu.tintera.plume.core.telegram.ProfileKind
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_members_failed
import eu.tintera.plume.resources.add_members_invite_failed
import eu.tintera.plume.resources.new_chat_load_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Koho výběr nabídne: kontakty a při hledání i ostatní známé soukromé chaty — jako výběr
 * k zablokování. Bez Uložených zpráv a bez těch, kdo už ve skupině jsou ([memberIds]).
 *
 * **Členy zná jen první stránka** ([MAX_MEMBERS_REQUEST]): u velké skupiny tak může být nabídnut
 * i někdo, kdo už v ní je. Server ho pak prostě nepřidá podruhé; číst kvůli tomu tisíce členů
 * nestojí za to.
 */
internal fun addMemberCandidates(
    query: String,
    contacts: List<ChatSummary>,
    searchResults: List<ChatSummary>,
    savedMessagesChatId: Long?,
    memberIds: Set<Long>,
): Pair<List<ChatSummary>, List<ChatSummary>> {
    // Tajný chat má vlastní id, ne id člověka — přidat ho jako člena nejde.
    fun ChatSummary.offered() = kind == ChatKind.Private && !isSecret && id != savedMessagesChatId && id !in memberIds
    val needle = foldDiacritics(query.trim())
    val shownContacts = contacts
        .filter { it.offered() }
        .filter { needle.isEmpty() || foldDiacritics(it.title).contains(needle, ignoreCase = true) }
    if (needle.isEmpty()) return shownContacts to emptyList()
    val contactIds = contacts.mapTo(HashSet()) { it.id }
    val others = searchResults.filter { it.offered() && it.id !in contactIds }.distinctBy { it.id }
    return shownContacts to others
}

/**
 * Přidání lidí do skupiny nebo kanálu [chatId] — z pruhu „Pozvat členy" nad čerstvou skupinou
 * i z ⋮ v profilu.
 *
 * - **Víc lidí naráz**, zaškrtávají se a přidávají tlačítkem: zakládá se celá skupina, ne jeden člen.
 * - **Soukromí přidaného má přednost.** Kdo nedovoluje, aby ho někdo přidával, server vrátí
 *   (ne chybou) a obrazovka nabídne poslat mu pozvánku — přidá se sám, když bude chtít. Bez
 *   pozvánky (nemá ji kdo vidět) se jen řekne, koho přidat nešlo.
 */
class AddMembersViewModel(
    private val chatId: Long,
    private val profiles: ProfileRepository,
    chats: ChatRepository,
) : ViewModel() {

    private val logger = Logger.withTag("AddMembers")

    private val query = MutableStateFlow("")
    private val contacts = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val memberIds = MutableStateFlow<Set<Long>>(emptySet())
    private val selectedChats = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val local = MutableStateFlow(AddMembersState())
    private val search = ChatSearch(chats, viewModelScope)

    val state: StateFlow<AddMembersState> = combine(
        combine(query, contacts, memberIds, ::Triple),
        search.state,
        chats.savedMessagesChatId,
        selectedChats,
        local,
    ) { (text, allContacts, members), searchState, savedId, selected, base ->
        val (shownContacts, others) = addMemberCandidates(text, allContacts, searchState.results, savedId, members)
        base.copy(
            query = text,
            contacts = shownContacts,
            others = others,
            selected = selected.mapTo(LinkedHashSet()) { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddMembersState())

    init {
        viewModelScope.launch {
            chats.contacts()
                .onSuccess { contacts.value = it }
                .onFailure { failure ->
                    logger.w(failure) { "Kontakty k přidání se nepodařilo načíst." }
                    local.update { it.copy(error = UiText(Res.string.new_chat_load_failed)) }
                }
            local.update { it.copy(loading = false) }
        }
        viewModelScope.launch {
            profiles.profile(chatId).onSuccess { profile ->
                local.update { it.copy(isChannel = profile.kind == ProfileKind.Channel, inviteLink = profile.inviteLink) }
            }
        }
        viewModelScope.launch {
            // Kanál běžnému správci členy nevydá — pak se nevyřadí nikdo, server to srovná sám.
            profiles.members(chatId, limit = MAX_MEMBERS_REQUEST)
                .onSuccess { page -> memberIds.value = page.members.mapTo(HashSet()) { it.sender.id } }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    fun toggle(id: Long) {
        if (local.value.adding) return
        if (selectedChats.value.any { it.id == id }) {
            selectedChats.update { list -> list.filterNot { it.id == id } }
            return
        }
        val summary = state.value.let { it.contacts + it.others }.firstOrNull { it.id == id } ?: return
        selectedChats.update { it + summary }
    }

    /**
     * Přidá vybrané; [onDone] = všichni přidáni, obrazovka odchází.
     *
     * **Boti do kanálu jdou zvlášť.** Odběratelem bot být nemůže (`USER_BOT`), jen správcem —
     * a to je jiné rozhodnutí než „přidat odběratele", takže se na ně obrazovka zeptá
     * ([AddMembersState.pendingBots]), až se přidají lidé.
     */
    fun add(onDone: () -> Unit) {
        val chosen = selectedChats.value.takeIf { it.isNotEmpty() } ?: return
        if (local.value.adding) return
        local.update { it.copy(adding = true) }
        viewModelScope.launch {
            // Soukromý chat má id uživatele.
            val botIds = if (local.value.isChannel) profiles.botsAmong(chosen.map { it.id }).toSet() else emptySet()
            val bots = chosen.filter { it.id in botIds }
            val people = chosen.filterNot { it.id in botIds }
            val failedIds = if (people.isEmpty()) {
                emptyList()
            } else {
                profiles.addMembers(chatId, people.map { it.id }).getOrElse { failure ->
                    logger.w(failure) { "Přidání členů do $chatId se nepodařilo." }
                    local.update {
                        it.copy(adding = false, error = failure.userMessage(Res.string.add_members_failed))
                    }
                    return@launch
                }
            }
            val refused = people.filter { it.id in failedIds }
            if (refused.isEmpty() && bots.isEmpty()) {
                onDone()
            } else {
                local.update { it.copy(adding = false, refused = refused, pendingBots = bots) }
            }
        }
    }

    /**
     * Přidá čekající boty do kanálu jako správce s právem zveřejňovat, upravovat a mazat
     * příspěvky — to je to, kvůli čemu se bot do kanálu přidává. Další práva jdou změnit
     * u člena kanálu. Pak obrazovka pokračuje dialogem s odmítnutými lidmi, nebo odchází.
     */
    fun addBotsAsAdmins(onDone: () -> Unit) {
        val bots = local.value.pendingBots.takeIf { it.isNotEmpty() } ?: return
        local.update { it.copy(adding = true) }
        viewModelScope.launch {
            val failed = bots.filter { bot ->
                profiles.setAdminRights(chatId, bot.id, BOT_CHANNEL_RIGHTS)
                    .onFailure { logger.w(it) { "Bota ${bot.id} se do $chatId nepodařilo přidat jako správce." } }
                    .isFailure
            }
            local.update {
                it.copy(
                    adding = false,
                    pendingBots = emptyList(),
                    error = if (failed.isNotEmpty()) UiText(Res.string.add_members_failed) else null,
                )
            }
            if (failed.isEmpty() && local.value.refused.isEmpty()) onDone()
        }
    }

    /** Bota nepřidávat; když nikdo jiný nečeká na dialog, obrazovka odchází. */
    fun skipBots(onDone: () -> Unit) {
        local.update { it.copy(pendingBots = emptyList()) }
        if (local.value.refused.isEmpty()) onDone()
    }

    /** Pošle pozvánku těm, které server nepřidal; pak obrazovka odchází ([onDone]). */
    fun sendInvites(onDone: () -> Unit) {
        val link = local.value.inviteLink ?: return
        val refused = local.value.refused.takeIf { it.isNotEmpty() } ?: return
        local.update { it.copy(adding = true, refused = emptyList()) }
        viewModelScope.launch {
            profiles.sendInviteLink(refused.map { it.id }, link)
                .onSuccess { onDone() }
                .onFailure { failure ->
                    logger.w(failure) { "Pozvánku se nepodařilo poslat." }
                    local.update { it.copy(adding = false, error = UiText(Res.string.add_members_invite_failed)) }
                }
        }
    }

    fun consumeError() {
        local.update { it.copy(error = null) }
    }
}

data class AddMembersState(
    val query: String = "",
    val loading: Boolean = true,
    val contacts: List<ChatSummary> = emptyList(),
    val others: List<ChatSummary> = emptyList(),
    val selected: Set<Long> = emptySet(),
    /** Kanál: přidávají se odběratelé, ne členové (jiné texty). */
    val isChannel: Boolean = false,
    /** Pozvánka, kterou jde poslat těm, koho server nepřidal; `null` = uživatel ji nevidí. */
    val inviteLink: String? = null,
    val adding: Boolean = false,
    /** Koho server nepřidal kvůli jejich soukromí; neprázdné = dialog. */
    val refused: List<ChatSummary> = emptyList(),
    /** Boti vybraní do kanálu — přidat je jde jen jako správce; neprázdné = dialog (před [refused]). */
    val pendingBots: List<ChatSummary> = emptyList(),
    val error: UiText? = null,
)

/** Co dostane bot přidaný do kanálu: zveřejňovat, upravovat a mazat příspěvky. */
private val BOT_CHANNEL_RIGHTS = AdminRights(postMessages = true, editMessages = true, deleteMessages = true)
