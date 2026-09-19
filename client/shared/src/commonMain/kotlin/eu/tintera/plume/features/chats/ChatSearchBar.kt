package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.SideEffect
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.resources.chats_search_empty_hint
import eu.tintera.plume.resources.search_off_24dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import eu.tintera.plume.core.telegram.FoundMessage
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.chats_search_section_global
import eu.tintera.plume.resources.chats_search_section_messages
import eu.tintera.plume.resources.chats_search_show_all
import eu.tintera.plume.resources.chats_search_show_less
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.resources.chats_search_archived
import eu.tintera.plume.resources.chats_search_clear_recent
import eu.tintera.plume.resources.chats_search_people
import eu.tintera.plume.resources.chats_search_recent
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_search
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.close_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hlavička seznamu chatů: vyhledávací pilulka a vedle ní [actions].
 *
 * Nahradila `LargeFlexibleTopAppBar`. Ten byl hezký jen rozbalený: sbalený přešel na
 * `surfaceContainer`, tedy pruh přes celou šířku v roli, která jinde v seznamu není, a na
 * rozloženém Foldu stál vedle 64dp hlavičky konverzace bar jiné výšky i barvy. Pilulka je
 * zaoblený blok jako skupiny chatů, bar kolem ní má barvu stránky i po odscrollování a je
 * vysoký přesně jako hlavička konverzace.
 *
 * **Při scrollu dolů bar odjede ([scrollBehavior], `enterAlways`), ale stav spojení ho přivolá
 * zpátky.** Odjetý bar by jinak schoval jediné vysvětlení, proč nic nechodí. Pás pod stavovou
 * lištou kreslí seznam chatů.
 *
 * **Stav spojení je pilulka UVNITŘ hledání** ([connection]), ne nadpis. Skoro celou dobu je
 * `null` a pak není vidět nic; nadpis „Chaty" by jen opakoval, co říká navigace.
 *
 * **Hledá se ve všech známých chatech** ([ChatSearch]): i v archivu a v chatech, které
 * v žádném seznamu nejsou; pod nimi veřejné chaty a zprávy. Pod prázdným dotazem jsou
 * nejčastější lidé a historie hledání.
 * Uložené zprávy se najdou podle přeloženého názvu — TDLib je zná pod jménem účtu.
 */
@Composable
internal fun ChatSearchBar(
    connection: StringResource?,
    search: ChatSearchState,
    savedMessagesChatId: Long?,
    scrollBehavior: SearchBarScrollBehavior,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onChatClick: (Long) -> Unit,
    onMessageClick: (chatId: Long, messageId: Long) -> Unit,
    onLoadMoreMessages: () -> Unit,
    onClearRecent: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val expanded = searchBarState.currentValue == SearchBarValue.Expanded

    fun collapse() {
        scope.launch {
            searchBarState.animateToCollapsed()
        }
    }

    // Bar má barvu stránky i nad odscrollovaným seznamem — tónovaný pruh přes celou šířku byl
    // to, co vadilo na sbaleném velkém baru. Odstup od obsahu nese pilulka.
    val colors = SearchBarDefaults.appBarWithSearchColors(
        scrolledAppBarContainerColor = MaterialTheme.colorScheme.surface,
    )

    // Výpadek spojení bar vrátí, i když je odjetý.
    LaunchedEffect(connection != null) {
        if (connection != null && scrollBehavior.scrollOffset != 0f) {
            animate(scrollBehavior.scrollOffset, 0f, animationSpec = spatial) { value, _ ->
                scrollBehavior.scrollOffset = value
            }
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = {},
            colors = colors.searchBarColors.inputFieldColors,
            placeholder = {
                Text(
                    stringResource(Res.string.chats_search),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = ::collapse) {
                        Icon(
                            painterResource(Res.drawable.arrow_back_24dp),
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                } else {
                    Icon(painterResource(Res.drawable.search_24dp), contentDescription = null)
                }
            },
            trailingIcon = {
                when {
                    expanded && textFieldState.text.isNotEmpty() -> IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(
                            painterResource(Res.drawable.close_24dp),
                            contentDescription = stringResource(Res.string.chats_search_clear),
                        )
                    }
                    // V rozbaleném hledání stav nepatří — uživatel se dívá na výsledky.
                    !expanded -> ConnectionPill(connection)
                }
            },
        )
    }

    // Hledání zavřené gestem zpět jde mimo `collapse()`; dotaz by jinak zůstal v pilulce.
    LaunchedEffect(expanded) {
        if (!expanded) textFieldState.clearText()
        // Návrhy (nejčastější, historie) se načítají jen při otevřeném hledání.
        onExpandedChange(expanded)
    }

    val onQueryChangeLatest by rememberUpdatedState(onQueryChange)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { onQueryChangeLatest(it) }
    }

    AppBarWithSearch(
        state = searchBarState,
        inputField = inputField,
        colors = colors,
        // Barva stránky výslovně. Bar ji má mít sám (`appBarContainerColor`), ale bez scroll
        // behavior ji v téhle alphě nenakreslil a chaty pod ním prosvítaly.
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        scrollBehavior = scrollBehavior,
        actions = actions,
    )

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
        colors = colors.searchBarColors,
        /*
         * Spodní inset si berou seznamy výsledků (`resultsPadding`), ne hledání. Výchozí
         * `safeDrawing` dává obsahu spodek jako padding, takže výsledky končily useknuté nad
         * navigační lištou na holé ploše. Takhle jedou pod lištu a poslední položka se nad ni
         * doroluje. Klávesnice je ve `safeDrawing` taky, takže nic nezakryje.
         */
        windowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) },
    ) {
        SearchResults(
            query = textFieldState.text.toString(),
            search = search,
            savedMessagesChatId = savedMessagesChatId,
            onPick = { chatId ->
                collapse()
                onChatClick(chatId)
            },
            onMessageClick = { chatId, messageId ->
                collapse()
                onMessageClick(chatId, messageId)
            },
            onLoadMoreMessages = onLoadMoreMessages,
            onClearRecent = onClearRecent,
        )
    }
}

/**
 * Stav spojení jako tonální pilulka na konci hledání.
 *
 * Při odjezdu kreslí poslední známý text — v okamžiku skrytí je hodnota už `null` a
 * `AnimatedVisibility` by dobíhala s prázdnou pilulkou (tatáž past jako u pruhu připnutých).
 */
@Composable
private fun ConnectionPill(connection: StringResource?) {
    val last = remember { LastValue<StringResource>() }
    connection?.let { last.value = it }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    AnimatedVisibility(
        visible = connection != null,
        enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.8f),
        exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.8f),
    ) {
        val label = last.value ?: return@AnimatedVisibility
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Obyčejné pole, ne stav — čte se jen při skládání obsahu, který se kvůli němu nepřekresluje. */
private class LastValue<T> {
    var value: T? = null
}

/**
 * Obsah rozbaleného hledání. Bez dotazu návrhy (řádek nejčastějších lidí, historie hledání),
 * s dotazem sekce: chaty (Uložené zprávy podle přeloženého názvu vždy první), veřejné chaty,
 * které uživatel nesleduje, a zprávy napříč chaty.
 *
 * Chaty a veřejné výsledky jsou sbalené na pár řádků ([COLLAPSED_ROWS]) — u krátkého dotazu
 * jich je desítky a zprávy by skončily daleko pod nimi. Zprávy se stránkují, dokud se listuje.
 */
@Composable
private fun SearchResults(
    query: String,
    search: ChatSearchState,
    savedMessagesChatId: Long?,
    onPick: (Long) -> Unit,
    onMessageClick: (chatId: Long, messageId: Long) -> Unit,
    onLoadMoreMessages: () -> Unit,
    onClearRecent: () -> Unit,
) {
    val trimmed = query.trim()
    val onPickLatest by rememberUpdatedState(onPick)
    val onMessageClickLatest by rememberUpdatedState(onMessageClick)
    val onLoadMoreLatest by rememberUpdatedState(onLoadMoreMessages)

    if (trimmed.isEmpty()) {
        Suggestions(search, onPick = { onPickLatest(it) }, onClearRecent = onClearRecent)
        return
    }

    val savedTitle = stringResource(Res.string.saved_messages)
    val showSaved = savedMessagesChatId != null && savedTitle.contains(trimmed, ignoreCase = true)
    // Výsledky patří k dotazu, na který TDLib odpovídal. Dokud nedorazí nové, ukazují se
    // staré jen pro tentýž dotaz — jinak by pod „jana" chvíli visely výsledky pro „ja".
    val current = search.query == trimmed
    val chats = if (current) search.results.filter { it.id != savedMessagesChatId } else emptyList()
    val global = if (current) search.globalOnly else emptyList()
    val messages = if (current) search.messages else emptyList()
    val settled = current && search.settled

    /*
     * Jednou ukázaný prázdný stav vydrží i během hledání dalšího znaku. Kdyby ho mezitím
     * vystřídal indikátor, úvodní animace prázdného stavu by se pouštěla při každém znaku.
     */
    var emptyShown by remember { mutableStateOf(false) }
    val nothing = chats.isEmpty() && global.isEmpty() && messages.isEmpty() && !showSaved
    SideEffect { emptyShown = nothing && (settled || emptyShown) }

    if (nothing) {
        if (settled || emptyShown) {
            // Stejný prázdný stav jako placeholder detailu a prázdný archiv. Spodek je nad
            // klávesnicí a lištou (viz `resultsPadding`), jinak by se centroval pod klávesnici.
            IntroEmptyState(
                icon = Res.drawable.search_off_24dp,
                title = stringResource(Res.string.chats_search_empty),
                text = stringResource(Res.string.chats_search_empty_hint),
                containerColor = Color.Transparent,
                modifier = Modifier.padding(bottom = resultsPadding().calculateBottomPadding()),
            )
        } else {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        return
    }

    // Rozbalení sekcí patří k dotazu; nový dotaz je zase sbalí.
    var chatsExpanded by rememberSaveable(trimmed) { mutableStateOf(false) }
    var globalExpanded by rememberSaveable(trimmed) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Další stránka zpráv, jakmile se listuje ke konci.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }.collect { nearEnd -> if (nearEnd) onLoadMoreLatest() }
    }

    val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = resultsPadding()) {
        val chatCount = chats.size + if (showSaved) 1 else 0
        /*
         * Nadpis chatů je tu vždycky, když jsou chaty — ne až když přibudou další sekce.
         * Seznam drží pozici podle první viditelné položky, takže nadpis vložený nad chaty
         * později odjel nad horní hranu a nebylo ho vidět.
         */
        if (chatCount > 0) {
            item(key = "chats-header") {
                SectionHeader(stringResource(Res.string.chats_search_section_chats)) {
                    ExpandToggle(chatCount, chatsExpanded) { chatsExpanded = it }
                }
            }
        }
        savedMessagesChatId?.takeIf { showSaved }?.let { savedId ->
            item(key = savedId) {
                ChatResultRow(
                    chatId = savedId,
                    title = savedTitle,
                    avatarFileId = null,
                    archived = false,
                    onClick = { onPickLatest(savedId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        val shownChats = if (chatsExpanded || global.isEmpty() && messages.isEmpty()) {
            chats
        } else {
            chats.take((COLLAPSED_ROWS - if (showSaved) 1 else 0).coerceAtLeast(0))
        }
        items(shownChats, key = { it.id }) { chat ->
            ChatResultRow(
                chatId = chat.id,
                title = chat.title,
                avatarFileId = chat.avatarFileId,
                archived = chat.folder == ChatFolder.Archive,
                onClick = { onPickLatest(chat.id) },
                // Serverové výsledky přibývají za lokálními — plynule, ne skokem.
                modifier = Modifier.animateItem(),
            )
        }

        if (global.isNotEmpty()) {
            item(key = "global-header") {
                SectionHeader(stringResource(Res.string.chats_search_section_global)) {
                    ExpandToggle(global.size, globalExpanded) { globalExpanded = it }
                }
            }
            items(if (globalExpanded) global else global.take(COLLAPSED_ROWS), key = { "global-${it.id}" }) { chat ->
                ChatResultRow(
                    chatId = chat.id,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    archived = false,
                    onClick = { onPickLatest(chat.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (messages.isNotEmpty()) {
            item(key = "messages-header") { SectionHeader(stringResource(Res.string.chats_search_section_messages)) }
            items(messages, key = { "message-${it.chat.id}-${it.messageId}" }) { found ->
                MessageResultRow(
                    found = found,
                    query = trimmed,
                    highlight = highlight,
                    onClick = { onMessageClickLatest(found.chat.id, found.messageId) },
                )
            }
        }
        if (current && search.messagesLoading && (messages.isNotEmpty() || chatCount > 0)) {
            item(key = "messages-loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** „Zobrazit vše" / „Zobrazit méně" v nadpisu sekce — jen když se sekce do sbalené podoby nevejde. */
@Composable
private fun ExpandToggle(count: Int, expanded: Boolean, onChange: (Boolean) -> Unit) {
    if (count <= COLLAPSED_ROWS) return
    TextButton(onClick = { onChange(!expanded) }) {
        Text(stringResource(if (expanded) Res.string.chats_search_show_less else Res.string.chats_search_show_all))
    }
}

/**
 * Nalezená zpráva: chat, kdy přišla a text se zvýrazněným dotazem. Dva řádky textu — nalezené
 * slovo bývá uprostřed a jeden řádek by ho často usekl.
 */
@Composable
private fun MessageResultRow(
    found: FoundMessage,
    query: String,
    highlight: SpanStyle,
    onClick: () -> Unit,
) {
    val text = remember(found.text, query, highlight) {
        val snippet = snippetAround(found.text.replace('\n', ' '), query)
        buildAnnotatedString {
            append(snippet)
            highlightRanges(snippet, query).forEach { addStyle(highlight, it.first, it.last + 1) }
        }
    }
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chatTitle(found.chat.id, found.chat.title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    found.date.asRelativeTime(),
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        supportingContent = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Avatar(
                chatId = found.chat.id,
                title = found.chat.title,
                avatarFileId = found.chat.avatarFileId,
                size = 40.dp,
                isSavedMessages = isSavedMessages(found.chat.id),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Suggestions(
    search: ChatSearchState,
    onPick: (Long) -> Unit,
    onClearRecent: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = resultsPadding()) {
        if (search.top.isNotEmpty()) {
            item(key = "top-header") { SectionHeader(stringResource(Res.string.chats_search_people)) }
            item(key = "top") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(search.top, key = { it.id }) { chat ->
                        TopChat(chat, onClick = { onPick(chat.id) })
                    }
                }
            }
        }

        if (search.recent.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeader(stringResource(Res.string.chats_search_recent)) {
                    TextButton(onClick = onClearRecent) { Text(stringResource(Res.string.chats_search_clear_recent)) }
                }
            }
            items(search.recent, key = { "recent-${it.id}" }) { chat ->
                ChatResultRow(
                    chatId = chat.id,
                    title = chatTitle(chat.id, chat.title),
                    avatarFileId = chat.avatarFileId,
                    archived = chat.folder == ChatFolder.Archive,
                    onClick = { onPick(chat.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

/** Nejčastější člověk: avatar a pod ním křestní jméno, jako řádek „lidí" v oficiálních klientech. */
@Composable
private fun TopChat(chat: ChatSummary, onClick: () -> Unit) {
    val title = chatTitle(chat.id, chat.title)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(TOP_CHAT_WIDTH)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Avatar(
            chatId = chat.id,
            title = chat.title,
            avatarFileId = chat.avatarFileId,
            size = 52.dp,
            isSavedMessages = isSavedMessages(chat.id),
        )
        Text(
            // Jen první slovo — celé jméno se pod avatar nevejde a useknuté nic neřekne.
            title.substringBefore(' '),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatResultRow(
    chatId: Long,
    title: String,
    avatarFileId: Int?,
    archived: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // Archiv se v hlavním seznamu nevidí, takže nalezený archivovaný chat by jinak
        // vypadal, jako by se odněkud vynořil.
        supportingContent = if (archived) {
            { Text(stringResource(Res.string.chats_search_archived)) }
        } else {
            null
        },
        leadingContent = {
            Avatar(chatId = chatId, title = title, avatarFileId = avatarFileId, size = 40.dp, isSavedMessages = isSavedMessages(chatId))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

private val TOP_CHAT_WIDTH = 76.dp

/** Odsazení seznamů v hledání: spodní systémové lišty a klávesnice (viz `windowInsets` hledání). */
@Composable
private fun resultsPadding(): PaddingValues {
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    return PaddingValues(top = 8.dp, bottom = 8.dp + bottom)
}

/** Kolik řádků ukáže sbalená sekce chatů a veřejných výsledků. */
private const val COLLAPSED_ROWS = 4

/** Kolik položek před koncem už spustí další stránku zpráv. */
private const val LOAD_MORE_THRESHOLD = 5

/**
 * Úseky [text], kde stojí [query], bez překryvů. Pro zvýraznění nalezeného slova ve výsledku
 * hledání zpráv.
 *
 * **Bez ohledu na velikost písmen i diakritiku** — tak hledá TDLib: „obslehl" najde „Obšlehl"
 * a zvýraznění se s tím musí shodnout, jinak by nalezená zpráva nic zvýrazněného neměla.
 * Porovnává se po znacích ([foldDiacritics] délku nemění), takže pozice sedí na původní text.
 */
internal fun highlightRanges(text: String, query: String): List<IntRange> {
    val needle = foldDiacritics(query.trim())
    if (needle.isEmpty()) return emptyList()
    val haystack = foldDiacritics(text)
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val index = haystack.indexOf(needle, from, ignoreCase = true)
        if (index < 0) break
        ranges += index until index + needle.length
        from = index + needle.length
    }
    return ranges
}

/**
 * Text zprávy tak, aby nalezené slovo bylo vidět: začíná kousek ([SNIPPET_CONTEXT] znaků) před
 * prvním výskytem, na začátku slova, se „…" na začátku. Výsledek má dva řádky a slovo z konce
 * dlouhé zprávy by jinak skončilo za výpustkou — řádek by pak nevysvětlil, proč se našel.
 */
internal fun snippetAround(text: String, query: String): String {
    val first = highlightRanges(text, query).firstOrNull()?.first ?: return text
    if (first <= SNIPPET_CONTEXT) return text
    val space = text.lastIndexOf(' ', first - SNIPPET_CONTEXT)
    val start = if (space in 0 until first) space + 1 else first - SNIPPET_CONTEXT
    return "…" + text.substring(start)
}

/** Kolik znaků před nalezeným slovem zůstane ve výsledku vidět. */
private const val SNIPPET_CONTEXT = 24

/**
 * Písmena s diakritikou na základní, znak za znak (délka textu se nemění). Pokrývá latinku
 * střední a západní Evropy; Kotlin v commonMain `Normalizer` nemá a pro zvýraznění to stačí —
 * nepokrytý znak se jen nezvýrazní.
 */
internal fun foldDiacritics(text: String): String = buildString(text.length) {
    text.forEach { append(DIACRITICS[it] ?: it) }
}

private val DIACRITICS: Map<Char, Char> = buildMap {
    fun map(from: String, to: Char) = from.forEach { put(it, to) }
    // Znaky jsou zapsané escape sekvencemi: `verifyNoHardcodedText` hlídá diakritiku v literálech
    // (text pro uživatele patří do prostředků) a tahle tabulka text pro uživatele není.
    // áàâäãåą → a, ÁÀÂÄÃÅĄ → A
    map("\u00e1\u00e0\u00e2\u00e4\u00e3\u00e5\u0105", 'a'); map("\u00c1\u00c0\u00c2\u00c4\u00c3\u00c5\u0104", 'A')
    // čćç → c, ČĆÇ → C
    map("\u010d\u0107\u00e7", 'c'); map("\u010c\u0106\u00c7", 'C')
    // ď → d, Ď → D
    map("\u010f", 'd'); map("\u010e", 'D')
    // éěèêëę → e, ÉĚÈÊËĘ → E
    map("\u00e9\u011b\u00e8\u00ea\u00eb\u0119", 'e'); map("\u00c9\u011a\u00c8\u00ca\u00cb\u0118", 'E')
    // íìîï → i, ÍÌÎÏ → I
    map("\u00ed\u00ec\u00ee\u00ef", 'i'); map("\u00cd\u00cc\u00ce\u00cf", 'I')
    // ľĺł → l, ĽĹŁ → L
    map("\u013e\u013a\u0142", 'l'); map("\u013d\u0139\u0141", 'L')
    // ňńñ → n, ŇŃÑ → N
    map("\u0148\u0144\u00f1", 'n'); map("\u0147\u0143\u00d1", 'N')
    // óòôöõő → o, ÓÒÔÖÕŐ → O
    map("\u00f3\u00f2\u00f4\u00f6\u00f5\u0151", 'o'); map("\u00d3\u00d2\u00d4\u00d6\u00d5\u0150", 'O')
    // řŕ → r, ŘŔ → R
    map("\u0159\u0155", 'r'); map("\u0158\u0154", 'R')
    // šś → s, ŠŚ → S
    map("\u0161\u015b", 's'); map("\u0160\u015a", 'S')
    // ť → t, Ť → T
    map("\u0165", 't'); map("\u0164", 'T')
    // úůùûüű → u, ÚŮÙÛÜŰ → U
    map("\u00fa\u016f\u00f9\u00fb\u00fc\u0171", 'u'); map("\u00da\u016e\u00d9\u00db\u00dc\u0170", 'U')
    // ýÿ → y, ÝŸ → Y
    map("\u00fd\u00ff", 'y'); map("\u00dd\u0178", 'Y')
    // žźż → z, ŽŹŻ → Z
    map("\u017e\u017a\u017c", 'z'); map("\u017d\u0179\u017b", 'Z')
}
