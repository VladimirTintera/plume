package eu.tintera.plume.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.QrScanFailure
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.qrScannerSupported
import eu.tintera.plume.core.ui.rememberQrScanner
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.devices_qr_permission
import eu.tintera.plume.resources.devices_qr_unavailable
import eu.tintera.plume.resources.new_chat_scan_qr
import eu.tintera.plume.resources.qr_code_scanner_24dp
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.new_chat_new_channel
import eu.tintera.plume.resources.new_chat_new_contact
import eu.tintera.plume.resources.new_chat_new_group
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.chats_search_section_global
import eu.tintera.plume.resources.new_chat_contacts
import eu.tintera.plume.resources.new_chat_empty
import eu.tintera.plume.resources.new_chat_empty_text
import eu.tintera.plume.resources.new_chat_search
import eu.tintera.plume.resources.new_chat_title
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.person_add_24dp
import eu.tintera.plume.resources.search_off_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
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
 *
 * **Nad kontakty je založení skupiny, kanálu a kontaktu**, jako u oficiálních klientů: „Nová
 * zpráva" je jediné místo, odkud se cokoli nového zakládá. Při hledání se schovají — hledá se chat.
 */
@Composable
fun NewChatScreen(
    viewModel: NewChatViewModel,
    onBack: () -> Unit,
    onChatSelected: (chatId: Long) -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onNewContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val blankQuery = state.query.isBlank()
    val startScan = rememberQrScanner(
        onScanned = viewModel::onScanned,
        onFailure = { failure ->
            viewModel.onScanFailed(
                UiText(
                    when (failure) {
                        QrScanFailure.Unavailable -> Res.string.devices_qr_unavailable
                        QrScanFailure.PermissionDenied -> Res.string.devices_qr_permission
                    },
                ),
            )
        },
    )

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
        header = {
            if (blankQuery) {
                CreateChatRows(
                    onNewGroup = onNewGroup,
                    onNewChannel = onNewChannel,
                    onNewContact = onNewContact,
                    onScanQr = startScan.takeIf { qrScannerSupported },
                )
            }
        },
        modifier = modifier,
    )
}

/**
 * Řádky jako jeden blok, stejným tvarem jako „Zablokovat uživatele" nad seznamem blokovaných.
 * „Naskenovat QR kód" ([onScanQr]) je hned pod novým kontaktem — obojí přidává člověka; bez
 * čtečky (desktop) chybí.
 */
@Composable
private fun CreateChatRows(onNewGroup: () -> Unit, onNewChannel: () -> Unit, onNewContact: () -> Unit, onScanQr: (() -> Unit)?) {
    val count = if (onScanQr != null) 4 else 3
    Column(
        verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        modifier = Modifier.fillMaxWidth().padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
    ) {
        CreateChatRow(Res.drawable.group_24dp, Res.string.new_chat_new_group, index = 0, count = count, onClick = onNewGroup)
        CreateChatRow(Res.drawable.campaign_24dp, Res.string.new_chat_new_channel, index = 1, count = count, onClick = onNewChannel)
        CreateChatRow(Res.drawable.person_add_24dp, Res.string.new_chat_new_contact, index = 2, count = count, onClick = onNewContact)
        if (onScanQr != null) {
            CreateChatRow(Res.drawable.qr_code_scanner_24dp, Res.string.new_chat_scan_qr, index = 3, count = count, onClick = onScanQr)
        }
    }
}

@Composable
private fun CreateChatRow(icon: DrawableResource, title: StringResource, index: Int, count: Int, onClick: () -> Unit) {
    Surface(
        shape = groupItemShape(index, count = count),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                leadingContentColor = MaterialTheme.colorScheme.primary,
            ),
            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
            headlineContent = { Text(stringResource(title)) },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

