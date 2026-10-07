package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.PrivacyBase
import eu.tintera.plume.core.telegram.PrivacyChoice
import eu.tintera.plume.core.telegram.PrivacyMember
import eu.tintera.plume.core.telegram.PrivacyRepository
import eu.tintera.plume.core.telegram.PrivacyRule
import eu.tintera.plume.core.telegram.PrivacySetting
import eu.tintera.plume.core.telegram.mergeHiddenExceptions
import eu.tintera.plume.core.telegram.privacyChoiceOf
import eu.tintera.plume.core.telegram.privacyRulesOf
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.privacy_save_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Výjimka v mapě jmen: člověk a skupina můžou mít stejné číslo. */
data class MemberKey(val id: Long, val isGroup: Boolean)

/**
 * Výběr výjimek nad detailem. [always] říká, do kterého seznamu se přidává; [selected] jsou id
 * CHATŮ z výběru — na lidi a skupiny se převádějí až při potvrzení ([PrivacyRepository.memberOf]).
 */
data class ExceptionPicker(
    val always: Boolean,
    val query: String = "",
    val selected: Set<Long> = emptySet(),
    val adding: Boolean = false,
)

data class PrivacySettingState(
    val setting: PrivacySetting,
    /** `null` = ještě se načítá. */
    val choice: PrivacyChoice? = null,
    val loadFailed: Boolean = false,
    val members: Map<MemberKey, PrivacyMember> = emptyMap(),
    /** `false` u hlasových zpráv bez Premium: stav se jen ukazuje. */
    val editable: Boolean = true,
    val picker: ExceptionPicker? = null,
    val error: UiText? = null,
)

/**
 * Koho výběr výjimek nabídne: kontakty a skupiny ze seznamu chatů, při hledání i ostatní lidi
 * a skupiny, které TDLib zná. Kanál výjimkou být nemůže, Uložené zprávy jsou účet sám, a kdo
 * už v cílovém seznamu je, se nenabízí podruhé.
 *
 * Vrací kontakty, skupiny a ostatní (jen při hledání).
 */
internal fun exceptionCandidates(
    query: String,
    contacts: List<ChatSummary>,
    groups: List<ChatSummary>,
    searchResults: List<ChatSummary>,
    savedMessagesChatId: Long?,
    excluded: Set<Long>,
): Triple<List<ChatSummary>, List<ChatSummary>, List<ChatSummary>> {
    fun ChatSummary.offered() = kind != ChatKind.Channel && id != savedMessagesChatId && id !in excluded
    val needle = foldDiacritics(query.trim())
    fun ChatSummary.matches() = needle.isEmpty() || foldDiacritics(title).contains(needle, ignoreCase = true)
    val shownContacts = contacts.filter { it.kind == ChatKind.Private && it.offered() && it.matches() }
    val shownGroups = groups.filter { it.kind == ChatKind.Group && it.offered() && it.matches() }
    if (needle.isEmpty()) return Triple(shownContacts, shownGroups, emptyList())
    val known = (contacts.map { it.id } + groups.map { it.id }).toSet()
    val others = searchResults.filter { it.offered() && it.id !in known }.distinctBy { it.id }
    return Triple(shownContacts, shownGroups, others)
}

/**
 * Detail jednoho nastavení soukromí: základní volba a výjimky.
 *
 * **Každá změna se zapíše hned** a volba se pohne dřív, než server odpoví; když zápis selže,
 * vrátí se a obrazovka řekne proč. Zapisují se pravidla CELÁ ([privacyRulesOf]) — co obrazovka
 * neumí (boti, Premium), se přenese z naposledy přečtených pravidel.
 */
class PrivacySettingViewModel(
    private val setting: PrivacySetting,
    private val privacy: PrivacyRepository,
    private val chats: ChatRepository,
) : ViewModel() {

    private val logger = Logger.withTag("Privacy")

    private val _state = MutableStateFlow(PrivacySettingState(setting = setting))
    val state: StateFlow<PrivacySettingState> = _state.asStateFlow()

    /** Naposledy přečtená pravidla — z nich se přenáší, co obrazovka neumí. */
    private var rules: List<PrivacyRule> = emptyList()

    private val contacts = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val search = ChatSearch(chats, viewModelScope)

    /** Kontakty, skupiny a ostatní pro otevřený výběr výjimek. */
    val candidates: StateFlow<Triple<List<ChatSummary>, List<ChatSummary>, List<ChatSummary>>> = combine(
        _state,
        contacts,
        chats.chats(),
        search.state,
        chats.savedMessagesChatId,
    ) { s, allContacts, list, searchState, savedId ->
        val picker = s.picker
        val choice = s.choice
        if (picker == null || choice == null) return@combine NO_CANDIDATES
        val excluded = if (picker.always) choice.alwaysUsers + choice.alwaysChats else choice.neverUsers + choice.neverChats
        val groups = (list as? ChatListState.Ready)?.chats.orEmpty()
        exceptionCandidates(picker.query, allContacts, groups, searchState.results, savedId, excluded.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NO_CANDIDATES)

    init {
        viewModelScope.launch {
            privacy.rules(setting).collect { result ->
                result
                    .onSuccess { read ->
                        rules = read
                        _state.update {
                            it.copy(choice = mergeHiddenExceptions(privacyChoiceOf(read), it.choice), loadFailed = false)
                        }
                        loadMembers()
                    }
                    .onFailure { if (_state.value.choice == null) _state.update { it.copy(loadFailed = true) } }
            }
        }
        if (setting == PrivacySetting.VoiceMessages) {
            viewModelScope.launch { chats.isPremium.collect { premium -> _state.update { it.copy(editable = premium) } } }
        }
    }

    fun chooseBase(base: PrivacyBase) {
        val choice = _state.value.choice ?: return
        if (choice.base == base || base !in setting.bases) return
        apply(choice.withBase(base))
    }

    fun removeException(always: Boolean, member: PrivacyMember) {
        val choice = _state.value.choice ?: return
        apply(choice.withRemoved(always, member.id, member.isGroup))
    }

    fun openPicker(always: Boolean) {
        if (!_state.value.editable) return
        _state.update { it.copy(picker = ExceptionPicker(always = always)) }
        search.setQuery("")
        if (contacts.value.isEmpty()) {
            viewModelScope.launch {
                chats.contacts()
                    .onSuccess { contacts.value = it }
                    .onFailure { logger.w(it) { "Kontakty pro výjimky se nepodařilo načíst." } }
            }
        }
    }

    fun closePicker() {
        _state.update { it.copy(picker = null) }
        search.setQuery("")
    }

    fun onPickerQuery(text: String) {
        _state.update { s -> s.copy(picker = s.picker?.copy(query = text)) }
        search.setQuery(text)
    }

    fun togglePicked(chatId: Long) {
        _state.update { s ->
            val picker = s.picker ?: return@update s
            val selected = if (chatId in picker.selected) picker.selected - chatId else picker.selected + chatId
            s.copy(picker = picker.copy(selected = selected))
        }
    }

    /** Vybrané chaty převede na lidi a skupiny a přidá je jako výjimky jedním zápisem. */
    fun confirmPicker() {
        val picker = _state.value.picker ?: return
        if (picker.adding || picker.selected.isEmpty()) return
        _state.update { it.copy(picker = picker.copy(adding = true)) }
        viewModelScope.launch {
            val members = picker.selected.mapNotNull { chatId ->
                privacy.memberOf(chatId)
                    .onFailure { logger.w(it) { "Chat $chatId nejde přidat jako výjimku." } }
                    .getOrNull()
            }
            _state.update { s ->
                s.copy(
                    picker = null,
                    members = s.members + members.associateBy { MemberKey(it.id, it.isGroup) },
                )
            }
            search.setQuery("")
            val choice = _state.value.choice ?: return@launch
            if (members.isEmpty()) return@launch
            apply(
                choice.withAdded(
                    always = picker.always,
                    users = members.filterNot { it.isGroup }.map { it.id },
                    chats = members.filter { it.isGroup }.map { it.id },
                ),
            )
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Změna se ukáže hned a zapíše; když zápis selže, vrátí se a obrazovka řekne proč.
     *
     * **Zapsaná pravidla se berou za platná HNED**, ne až s updatem od serveru. Jinak by rychlé
     * přepnutí tam a zpátky (A → B → A) porovnalo návrat se starým stavem A, nic by neposlalo —
     * a na serveru by zůstalo B.
     */
    private fun apply(updated: PrivacyChoice) {
        if (!_state.value.editable) return
        val previous = _state.value.choice ?: return
        val before = rules
        val write = privacyRulesOf(updated, before, setting.exceptions)
        _state.update { it.copy(choice = updated) }
        if (write == before) return
        rules = write
        viewModelScope.launch {
            privacy.setRules(setting, write).onFailure {
                if (rules == write) rules = before
                _state.update { it.copy(choice = previous, error = UiText(Res.string.privacy_save_failed)) }
            }
        }
    }

    private suspend fun loadMembers() {
        val choice = _state.value.choice ?: return
        val known = _state.value.members.keys
        val users = (choice.alwaysUsers + choice.neverUsers).distinct().filter { MemberKey(it, false) !in known }
        val groups = (choice.alwaysChats + choice.neverChats).distinct().filter { MemberKey(it, true) !in known }
        if (users.isEmpty() && groups.isEmpty()) return
        val loaded = privacy.members(users, groups)
        _state.update { s -> s.copy(members = s.members + loaded.associateBy { MemberKey(it.id, it.isGroup) }) }
    }
}

private val NO_CANDIDATES = Triple(emptyList<ChatSummary>(), emptyList<ChatSummary>(), emptyList<ChatSummary>())
