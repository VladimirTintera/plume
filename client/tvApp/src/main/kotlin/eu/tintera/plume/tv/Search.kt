package eu.tintera.plume.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FoundMessage
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.chats_search_recent
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.chats_search_section_global
import eu.tintera.plume.resources.chats_search_section_messages
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.tv_search_hint
import eu.tintera.plume.resources.tv_search_prompt
import eu.tintera.plume.resources.tv_search_start
import eu.tintera.plume.resources.tv_search_voice
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Výsledky hledání po částech; každá přijde, jak doběhne její dotaz. */
@Immutable
data class SearchResults(
    val chats: List<ChatSummary>? = null,
    val global: List<ChatSummary>? = null,
    val messages: List<FoundMessage>? = null,
) {
    val loading: Boolean get() = chats == null || global == null || messages == null
    val isEmpty: Boolean get() = chats.isNullOrEmpty() && global.isNullOrEmpty() && messages.isNullOrEmpty()
}

/**
 * Stav hledání drží navigace, ne obrazovka: z výsledku se otevírá chat, seznam (a s ním
 * obrazovka hledání) odchází z kompozice a po návratu musí stát dotaz, výsledky i fokus na
 * položce, ze které uživatel odešel — bez nového dotazu na server.
 */
@Stable
class TvSearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<SearchResults?>(null)

    /** Pro který dotaz [results] jsou; jiný dotaz se hledá znovu. */
    var resultsFor by mutableStateOf<String?>(null)

    /** Klíč položky, ze které se naposledy otevíral chat. */
    var lastKey by mutableStateOf<String?>(null)

    /** Roste s každým stiskem tlačítka hledání na ovladači: obrazovka pak spustí hlasové zadání. */
    var voiceRequests by mutableIntStateOf(0)
}

/**
 * Hledání: pole nahoře (systémová klávesnice televize, na Google TV i s diktováním), vedle
 * tlačítko hlasového zadání, pod ním výsledky — chaty, veřejné chaty a zprávy napříč chaty,
 * stejné dotazy jako hledání na telefonu (`searchChats`, `searchPublicChats`, `searchMessages`
 * z `core`). Vpravo náhled zaostřeného chatu jako v seznamu chatů. Bez dotazu nedávno nalezené
 * chaty (`recentlyFoundChats`, historii vede TDLib).
 *
 * Zpráva otevře svůj chat rovnou na sobě (okno historie kolem ní, `ConversationState.jumpTo`).
 */
@Composable
fun SearchPane(
    state: TvSearchState,
    takeFocus: Boolean,
    /** Zaostřený chat a jeho náhled drží seznam chatů: pozadí jde i pod záložky. */
    focusedChat: ChatSummary?,
    preview: ChatPreviewData?,
    onFocusChat: (ChatSummary) -> Unit,
    onOpen: (Long) -> Unit,
    /** Nalezená zpráva otevře svůj chat rovnou na sobě. */
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
) {
    val chats = koinInject<ChatRepository>()
    var recent by remember { mutableStateOf<List<ChatSummary>?>(null) }
    LaunchedEffect(Unit) { recent = chats.recentlyFoundChats().getOrNull().orEmpty() }

    val query = state.query.trim()
    LaunchedEffect(query) {
        if (query.isEmpty() || query == state.resultsFor) return@LaunchedEffect
        // Prodleva: dotaz se netrhá s každým písmenem z klávesnice.
        delay(SEARCH_DEBOUNCE_MS)
        state.resultsFor = query
        state.results = SearchResults()
        // Část výsledku se zapíše, jen když dotaz pořád platí: volání TDLib nemusí na zrušení
        // reagovat a výsledek staršího dotazu by jinak přepsal novější.
        fun update(change: (SearchResults) -> SearchResults) {
            if (state.resultsFor == query) state.results = change(state.results ?: SearchResults())
        }
        coroutineScope {
            launch {
                // Napřed lokálně (hned), pak i ze serveru, který zná chaty, jež lokálně nejsou.
                val local = chats.searchChats(query).getOrNull().orEmpty()
                update { it.copy(chats = local) }
                val server = chats.searchChats(query, onServer = true).getOrNull().orEmpty()
                update { it.copy(chats = (local + server).distinctBy { chat -> chat.id }) }
            }
            launch {
                val global = chats.searchPublicChats(query).getOrNull().orEmpty()
                update { it.copy(global = global) }
            }
            launch {
                val messages = chats.searchMessages(query).getOrNull()?.messages.orEmpty()
                update { it.copy(messages = messages) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxHeight().fillMaxWidth(SEARCH_LIST_FRACTION)) {
                SearchField(state, Modifier.padding(start = SAFE_HORIZONTAL, end = 12.dp, top = 8.dp, bottom = 12.dp))
                val results = state.results.takeIf { query.isNotEmpty() && state.resultsFor == query }
                when {
                    query.isEmpty() && recent.isNullOrEmpty() ->
                        MessageScreen(stringResource(Res.string.tv_search_start), Res.drawable.search_24dp)
                    query.isEmpty() -> SearchList(
                        sections = listOf(SearchSection(stringResource(Res.string.chats_search_recent), recent.orEmpty().map { SearchItem.Chat(it, "recent") })),
                        state = state,
                        takeFocus = takeFocus,
                        onFocusChat = onFocusChat,
                        onOpen = onOpen,
                        onOpenMessage = onOpenMessage,
                    )
                    results == null || (results.isEmpty && results.loading) ->
                        LoadingScreen(stringResource(Res.string.connection_connecting))
                    results.isEmpty -> MessageScreen(stringResource(Res.string.chats_search_empty), Res.drawable.search_24dp)
                    else -> SearchList(
                        sections = listOfNotNull(
                            results.chats?.takeIf { it.isNotEmpty() }?.let { found ->
                                SearchSection(stringResource(Res.string.chats_search_section_chats), found.map { SearchItem.Chat(it, "chat") })
                            },
                            results.global?.takeIf { it.isNotEmpty() }?.let { found ->
                                SearchSection(stringResource(Res.string.chats_search_section_global), found.map { SearchItem.Chat(it, "global") })
                            },
                            results.messages?.takeIf { it.isNotEmpty() }?.let { found ->
                                SearchSection(stringResource(Res.string.chats_search_section_messages), found.map { SearchItem.Message(it) })
                            },
                        ),
                        state = state,
                        takeFocus = takeFocus,
                        onFocusChat = onFocusChat,
                        onOpen = onOpen,
                        onOpenMessage = onOpenMessage,
                    )
                }
            }
            ChatPreview(
                chat = focusedChat,
                preview = preview,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 32.dp, end = SAFE_HORIZONTAL, bottom = SAFE_VERTICAL),
            )
        }
    }
}

/**
 * Pole a mikrofon. Hlasové zadání je systémové ([rememberVoiceInput]) a výsledek se vrátí jako
 * text dotazu. Kde ho nic neobslouží, tlačítko není.
 */
@Composable
private fun SearchField(state: TvSearchState, modifier: Modifier) {
    val voice = rememberVoiceInput(stringResource(Res.string.tv_search_prompt)) { state.query = it }
    // Tlačítko hledání na ovladači (KEYCODE_SEARCH) rovnou spustí hlasové zadání. Fokus jde na
    // mikrofon: obrazovka, odkud se přišlo, z kompozice odešla a fokus by zůstal na záložce —
    // šipka vpravo by pak místo pole přepnula složku.
    val micFocus = remember { FocusRequester() }
    LaunchedEffect(state.voiceRequests) {
        if (state.voiceRequests == 0 || voice == null) return@LaunchedEffect
        runCatching { micFocus.requestFocus() }
        voice.launch()
    }
    val focusManager = LocalFocusManager.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (voice != null) {
            MicButton(voice, stringResource(Res.string.tv_search_voice), Modifier.focusRequester(micFocus))
        }
        var focused by remember { mutableStateOf(false) }
        val colors = MaterialTheme.colorScheme
        // Hodnota s kurzorem: šipka vlevo na začátku textu má z pole odejít na mikrofon.
        var value by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
        LaunchedEffect(state.query) {
            if (value.text != state.query) value = TextFieldValue(state.query, TextRange(state.query.length))
        }
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                state.query = it.text
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
                // Jednořádkové pole šipku dolů nepustí (viz odpověď v konverzaci): pošle se výslovně.
                // Šipku vlevo pole bere na posun kurzoru; na začátku textu z něj odejde na mikrofon.
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        event.key == Key.DirectionLeft && value.selection.collapsed && value.selection.start == 0 ->
                            focusManager.moveFocus(FocusDirection.Left)
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (focused) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(Res.drawable.search_24dp), contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.text.isEmpty()) {
                            Text(stringResource(Res.string.tv_search_hint), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                        }
                        inner()
                    }
                }
            },
        )
    }
}

private class SearchSection(val title: String, val items: List<SearchItem>)

private sealed interface SearchItem {
    val key: String
    val chat: ChatSummary

    class Chat(override val chat: ChatSummary, kind: String) : SearchItem {
        override val key = "$kind:${chat.id}"
    }

    class Message(val found: FoundMessage) : SearchItem {
        override val chat: ChatSummary get() = found.chat
        override val key = "message:${found.chat.id}:${found.messageId}"
    }
}

@Composable
private fun SearchList(
    sections: List<SearchSection>,
    state: TvSearchState,
    takeFocus: Boolean,
    onFocusChat: (ChatSummary) -> Unit,
    onOpen: (Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
) {
    val savedMessagesId = savedMessagesChatId()
    val returnFocus = remember { FocusRequester() }
    val keys = sections.flatMap { section -> section.items.map { it.key } }
    val returnKey = state.lastKey?.takeIf { it in keys }
    // Po návratu z otevřeného chatu fokus na položku, ze které se odcházelo.
    LaunchedEffect(returnKey, takeFocus) {
        if (returnKey != null && takeFocus) runCatching { returnFocus.requestFocus() }
    }
    // Části výsledků chodí, jak doběhnou dotazy, a chaty (první část) bývají až po veřejných.
    // Seznam drží první viditelnou položku podle klíče, takže by chaty i s nadpisem zůstaly nad
    // horním okrajem (ověřeno na emulátoru). Dokud v seznamu není fokus, drží se na začátku.
    val listState = rememberLazyListState()
    var listFocused by remember { mutableStateOf(false) }
    LaunchedEffect(keys.firstOrNull()) {
        if (!listFocused && returnKey == null) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = SAFE_HORIZONTAL - 12.dp, end = 12.dp, bottom = SAFE_VERTICAL + audioDockInset()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().onFocusChanged { listFocused = it.hasFocus },
    ) {
        sections.forEach { section ->
            item(key = "header:${section.title}") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(section.items, key = { it.key }) { item ->
                SearchRow(
                    item = item,
                    savedMessagesId = savedMessagesId,
                    onClick = {
                        state.lastKey = item.key
                        when (item) {
                            is SearchItem.Message -> onOpenMessage(item.chat.id, item.found.messageId)
                            is SearchItem.Chat -> onOpen(item.chat.id)
                        }
                    },
                    modifier = (if (item.key == returnKey) Modifier.focusRequester(returnFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocusChat(item.chat) },
                )
            }
        }
    }
}

@Composable
private fun SearchRow(item: SearchItem, savedMessagesId: Long?, onClick: () -> Unit, modifier: Modifier) {
    val chat = item.chat
    val title = chatTitle(chat.id, chat.title, savedMessagesId)
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            ChatAvatar(fileId = chat.avatarFileId, title = title, size = SEARCH_AVATAR, savedMessages = chat.id == savedMessagesId)
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = when (item) {
            is SearchItem.Message -> {
                { Text(item.found.text.resolve(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            is SearchItem.Chat -> chat.lastMessagePreview?.let { last -> { Text(last.resolve(), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        },
        trailingContent = (item as? SearchItem.Message)?.let { message ->
            {
                val date = message.found.date
                Text(
                    if (date.isSameDayAs(nowSeconds())) date.asTimeOfDay() else date.asDate(),
                    style = MaterialTheme.typography.labelMedium.tabular(),
                )
            }
        },
    )
}

private val SEARCH_AVATAR = 40.dp

/** Podíl šířky pro pole a výsledky; zbytek je náhled zaostřeného chatu (jako v seznamu chatů). */
private const val SEARCH_LIST_FRACTION = 0.44f

/** Jak dlouho se po posledním písmenu čeká, než se hledá. */
private const val SEARCH_DEBOUNCE_MS = 400L
