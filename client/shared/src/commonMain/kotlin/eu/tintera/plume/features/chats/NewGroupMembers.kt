package eu.tintera.plume.features.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSearch
import eu.tintera.plume.core.ui.PickerSelection
import eu.tintera.plume.core.ui.PickerEmpty
import eu.tintera.plume.core.ui.PickerError
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_forward_24dp
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.chats_search_section_global
import eu.tintera.plume.resources.new_chat_contacts
import eu.tintera.plume.resources.new_chat_empty
import eu.tintera.plume.resources.new_chat_empty_text
import eu.tintera.plume.resources.new_chat_search
import eu.tintera.plume.resources.new_group_members_title
import eu.tintera.plume.resources.new_group_next
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.search_off_24dp
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr členů nové skupiny — tentýž [ChatPicker] i tentýž [NewChatViewModel] jako „Nová
 * zpráva", jen s výběrem víc lidí a tlačítkem „Další".
 *
 * **Nabízí se jen soukromé chaty** (lidé a boti, `ChatKind.Private`) bez Uložených zpráv:
 * do skupiny se přidává uživatel, ne chat.
 *
 * **Pokračovat jde i bez členů** (`confirmWhenEmpty`): skupina smí vzniknout prázdná a lidi
 * pozvat odkazem — oficiální klienti to dovolují taky.
 *
 * **Výběr drží obrazovka** (`rememberSaveable`), ne ViewModel: je to jen seznam id, který jde
 * dál routou. Pořadí výběru zůstává, tak jako u cílů přeposlání.
 */
@Composable
fun NewGroupMembersScreen(
    viewModel: NewChatViewModel,
    onBack: () -> Unit,
    onNext: (memberChatIds: List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val blankQuery = state.query.isBlank()

    ChatPicker(
        title = stringResource(Res.string.new_group_members_title),
        search = PickerSearch(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(Res.string.new_chat_search),
        ),
        loading = state.loading && state.contacts.isEmpty(),
        sections = listOf(
            PickerSection(key = "contacts", chats = groupMemberCandidates(state.contacts, state.savedMessagesChatId), title = stringResource(Res.string.new_chat_contacts)),
            PickerSection(key = "chats", chats = groupMemberCandidates(state.chatResults, state.savedMessagesChatId), title = stringResource(Res.string.chats_search_section_chats)),
            PickerSection(key = "global", chats = groupMemberCandidates(state.globalResults, state.savedMessagesChatId), title = stringResource(Res.string.chats_search_section_global)),
        ),
        enabled = true,
        onPick = { chatId -> selected = if (chatId in selected) selected - chatId else selected + chatId },
        selection = PickerSelection(
            selected = selected.toSet(),
            onConfirm = { onNext(selected) },
            confirmLabel = Res.string.new_group_next,
            confirmIcon = Res.drawable.arrow_forward_24dp,
            confirmWhenEmpty = true,
        ),
        showPresence = true,
        empty = PickerEmpty(
            icon = if (blankQuery) Res.drawable.person_24dp else Res.drawable.search_off_24dp,
            title = if (blankQuery) stringResource(Res.string.new_chat_empty) else null,
            text = if (blankQuery) stringResource(Res.string.new_chat_empty_text) else null,
        ),
        onBack = onBack,
        error = state.error?.let { PickerError(it, viewModel::consumeError) },
        modifier = modifier,
    )
}

/**
 * Do skupiny jdou lidé a boti (soukromé chaty), ne skupiny, kanály ani Uložené zprávy. Ani tajné
 * chaty: jejich id není id člověka, takže by je server jako člena odmítl.
 */
internal fun groupMemberCandidates(chats: List<ChatSummary>, savedMessagesChatId: Long?): List<ChatSummary> =
    chats.filter { it.kind == ChatKind.Private && !it.isSecret && it.id != savedMessagesChatId }
