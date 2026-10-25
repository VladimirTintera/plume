package eu.tintera.plume.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.search_off_24dp
import eu.tintera.plume.resources.wear_search_button
import eu.tintera.plume.resources.wear_search_contact
import eu.tintera.plume.resources.wear_search_contacts
import eu.tintera.plume.resources.wear_search_label
import eu.tintera.plume.resources.wear_search_none
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.text.Normalizer

private val logger = Logger.withTag("WearSearch")

/** Výsledek hledání: chat, a jestli je to kontakt, se kterým se ještě nepsalo (podtitul). */
private class Found(val chat: ChatSummary, val isNewContact: Boolean)

/**
 * Hledání chatu a nová konverzace: rovnou se otevře diktování (klávesnice taky jde), pak
 * výsledky — chaty podle názvu (lokálně i na serveru, i archivované, `searchChats`) a kontakty,
 * se kterými se ještě nepsalo. Ťuknutí otevře konverzaci; kontakty mají soukromý chat zajištěný
 * (`contacts`), takže se dá psát i komukoli z adresáře, kdo v seznamu chatů není.
 *
 * Zavření diktování bez textu ukáže kontakty podle jména — to je cesta k nové konverzaci bez
 * hledání. Hledat znovu jde tlačítkem dole.
 */
@Composable
fun SearchScreen(onOpen: (Long) -> Unit) {
    val chats = koinInject<ChatRepository>()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Found>?>(null) }
    var contacts by remember { mutableStateOf<List<ChatSummary>?>(null) }
    val savedMessagesId = savedMessagesChatId()
    val savedTitle = stringResource(Res.string.saved_messages)

    suspend fun loadContacts(): List<ChatSummary> = contacts ?: chats.contacts()
        .onFailure { logger.w(it) { "Kontakty se na hodinkách nenačetly." } }
        .getOrDefault(emptyList())
        .also { contacts = it }

    fun search(text: String) {
        query = text
        results = null
        scope.launch {
            val local = async { chats.searchChats(text).getOrDefault(emptyList()) }
            val remote = async { chats.searchChats(text, onServer = true).getOrDefault(emptyList()) }
            val people = async { loadContacts() }
            // Uložené zprávy TDLib najde jen pod jménem účtu, ne pod jejich názvem (ChatRepository).
            val saved = savedMessagesId
                ?.takeIf { normalized(savedTitle).contains(normalized(text)) }
                ?.let { chats.summaries(listOf(it)) }
                .orEmpty()
            val found = (saved + local.await() + remote.await()).distinctBy { it.id }
            val known = found.map { it.id }.toSet()
            val newContacts = people.await().filter { it.id !in known && normalized(it.title).contains(normalized(text)) }
            results = found.map { Found(it, isNewContact = false) } + newContacts.map { Found(it, isNewContact = true) }
        }
    }

    val openInput = rememberReplyInput(
        label = Res.string.wear_search_label,
        quickReplies = false,
        // Bez textu: kontakty podle jména, ať jde začít konverzaci i bez hledání.
        onCancel = { if (query == null) scope.launch { loadContacts() } },
        onReply = ::search,
    )
    val inputTitle = stringResource(Res.string.wear_search_button)
    LaunchedEffect(Unit) { if (query == null && contacts == null) openInput(inputTitle) }

    val currentQuery = query
    val shown = if (currentQuery != null) results else contacts?.map { Found(it, isNewContact = false) }
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = state,
        edgeButton = {
            EdgeButton(onClick = { openInput(inputTitle) }) {
                Icon(painterResource(Res.drawable.search_24dp), contentDescription = stringResource(Res.string.wear_search_button))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(currentQuery ?: stringResource(Res.string.wear_search_contacts), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            when {
                // Čeká se na diktování nebo na výsledky.
                shown == null && (currentQuery != null || contacts == null) -> if (currentQuery != null) {
                    item { LoadingRow(Modifier.transformedItem(this, spec)) }
                }
                shown.isNullOrEmpty() && currentQuery != null -> item {
                    NoResults(stringResource(Res.string.wear_search_none, currentQuery), Modifier.transformedItem(this, spec))
                }
                else -> items(shown.orEmpty()) { found ->
                    val chat = found.chat
                    val title = chatTitle(chat.id, chat.title, savedMessagesId)
                    Button(
                        onClick = { onOpen(chat.id) },
                        icon = { ChatAvatar(fileId = chat.avatarFileId, title = title, size = AVATAR, savedMessages = chat.id == savedMessagesId) },
                        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = when {
                            found.isNewContact -> { { Text(stringResource(Res.string.wear_search_contact)) } }
                            else -> chat.lastMessagePreview?.let { preview -> { Text(preview.resolve(), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
        }
    }
}

private val AVATAR = 24.dp

/** Hledá se: kolečko uprostřed seznamu, nadpis s dotazem zůstává nad ním. */
@Composable
private fun LoadingRow(modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun NoResults(text: String, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(Res.drawable.search_off_24dp), contentDescription = null)
        Text(text, textAlign = TextAlign.Center)
    }
}

/**
 * Text pro porovnání bez ohledu na velikost písmen a diakritiku: diktování i klávesnice hodinek
 * háčky a čárky často vynechají („Kuchynka" má najít „Kuchyňka").
 */
private fun normalized(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
