package eu.tintera.plume.features.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.chats_search_section_global
import eu.tintera.plume.resources.new_chat_contacts
import eu.tintera.plume.resources.new_chat_empty
import eu.tintera.plume.resources.new_chat_empty_text
import eu.tintera.plume.resources.new_chat_search
import eu.tintera.plume.resources.new_chat_title
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.search_off_24dp
import org.jetbrains.compose.resources.stringResource

/**
 * Zahájení nové konverzace: kontakty, existující chaty a veřejné chaty na Telegramu (@jméno),
 * nad nimi karta Uložených zpráv.
 *
 * Je to **tentýž [ChatPicker] jako přeposílání a sdílení** — všechno je to „vyber chat", jen se
 * pak pokračuje jinam. Vlastní seznam tu byl dřív a zůstal u `TextField` s podtržením a holých
 * `ListItem`ů na pozadí, zatímco přeposílání už mluvilo jazykem seznamu chatů; dva vzhledy pro
 * totéž znamenaly, že uživatel poznává výběr chatu na každé obrazovce znovu.
 *
 * Co přibylo proti přeposílání: **sekce s nadpisy** (odkud výsledek je) a **dostupnost pod
 * jménem** — tady se vybírá člověk, ne cíl pro obsah.
 */
@Composable
fun NewChatScreen(
    viewModel: NewChatViewModel,
    onBack: () -> Unit,
    onChatSelected: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val blankQuery = state.query.isBlank()

    ChatPicker(
        title = stringResource(Res.string.new_chat_title),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.new_chat_search),
        // Kontakty se načtou jednou; dokud nějaké jsou, hledání je jen zúží a načítání
        // by pod prstem vyměnilo seznam za kolečko.
        loading = state.loading && state.contacts.isEmpty(),
        sections = listOf(
            PickerSection(key = "contacts", chats = state.contacts, title = stringResource(Res.string.new_chat_contacts)),
            PickerSection(key = "chats", chats = state.chatResults, title = stringResource(Res.string.chats_search_section_chats)),
            PickerSection(key = "global", chats = state.globalResults, title = stringResource(Res.string.chats_search_section_global)),
        ),
        enabled = true,
        // Jeden cíl: ťuknutí rovnou otevře konverzaci, není co potvrzovat.
        onPick = onChatSelected,
        showPresence = true,
        savedMessagesChatId = state.savedMessagesChatId,
        // Bez dotazu je prázdno „nemáš kontakty", s dotazem „nic nenalezeno" — dvě různé
        // situace, a rada „hledej podle @jména" dává smysl jen u té první.
        emptyIcon = if (blankQuery) Res.drawable.person_24dp else Res.drawable.search_off_24dp,
        emptyTitle = if (blankQuery) stringResource(Res.string.new_chat_empty) else null,
        emptyText = if (blankQuery) stringResource(Res.string.new_chat_empty_text) else null,
        onBack = onBack,
        error = state.error,
        onErrorShown = viewModel::consumeError,
        modifier = modifier,
    )
}
