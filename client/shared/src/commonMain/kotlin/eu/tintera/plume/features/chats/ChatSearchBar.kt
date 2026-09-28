package eu.tintera.plume.features.chats

import eu.tintera.plume.resources.sender_you
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Shape
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.highlightRanges
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.SideEffect
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.resources.chats_search_empty_hint
import eu.tintera.plume.resources.search_filter_empty_hint
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
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.FoundMessage
import eu.tintera.plume.core.telegram.MessageSearchFilter
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.chats_search_section_global
import eu.tintera.plume.resources.chats_search_section_messages
import eu.tintera.plume.resources.search_source_public
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
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
 * Hlavička seznamu chatů: vyhledávací pilulka přes celou šířku, na jejím konci [trailing]
 * (fotka účtu s menu — vzor Gmailu a Google Messages; dřív tu bylo ⋮ vedle pilulky).
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
 * **Stav spojení je nápověda pilulky** ([connection]) a prstenec kolem fotky účtu (ten dodá
 * [trailing]), ne nadpis ani vlastní prvek. Skoro celou dobu je `null` a pak je vidět jen
 * „Hledat v chatech"; nadpis „Chaty" by jen opakoval, co říká navigace.
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
    onFilterChange: (MessageSearchFilter) -> Unit,
    /** Dotaz zvenku (hashtag z konverzace): hledání se s ním rozbalí. */
    request: String?,
    onRequestHandled: () -> Unit,
    /** Konec sbalené pilulky (menu účtu). V rozbaleném hledání není — tam je ✕ dotazu. */
    trailing: @Composable () -> Unit,
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
            /*
             * Ve sbalené pilulce nese nápověda stav spojení (prstenec kolem fotky účtu ukazuje,
             * ŽE něco není v pořádku, text CO). Rozbalené hledání má nápovědu vždycky svoji —
             * uživatel se tam dívá na výsledky, ne na spojení.
             *
             * Výměna je fade through, ne `Crossfade`: dva texty na témže místě napůl průhledné
             * přes sebe („Čekání na síť" přes „Připojování") se nedají přečíst a vypadají jako
             * chyba vykreslení — tatáž úvaha jako u přechodů mezi obrazovkami.
             */
            placeholder = {
                val hint = connection.takeUnless { expanded } ?: Res.string.chats_search
                AnimatedContent(
                    targetState = hint,
                    transitionSpec = {
                        fadeIn(tween(HINT_FADE_IN_MS, delayMillis = HINT_FADE_OUT_MS, easing = LinearOutSlowInEasing)) togetherWith
                            fadeOut(tween(HINT_FADE_OUT_MS, easing = FastOutLinearInEasing))
                    },
                ) {
                    Text(
                        stringResource(it),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                    // V rozbaleném hledání stav ani účet nepatří — uživatel se dívá na výsledky.
                    !expanded -> trailing()
                }
            },
        )
    }

    // Hledání zavřené gestem zpět jde mimo `collapse()`; dotaz by jinak zůstal v pilulce.
    LaunchedEffect(expanded) {
        if (!expanded) {
            textFieldState.clearText()
            // Filtr patří k jednomu hledání, jako dotaz; příště se začíná bez něj.
            onFilterChange(MessageSearchFilter())
        }
        // Návrhy (nejčastější, historie) se načítají jen při otevřeném hledání.
        onExpandedChange(expanded)
    }

    /*
     * Dotaz zvenku. Napřed rozbalit, pak vepsat text: efekt nad `expanded` při sbalení text
     * maže, a kdyby text přišel dřív, smazal by ho ještě přechod ze sbaleného stavu.
     * Požadavek se nuluje AŽ NAKONEC — vynulování mění klíč efektu, a hned na začátku by
     * efekt zrušilo i s rozbalováním (tatáž past jako „Vrátit" u odepnuté zprávy).
     */
    LaunchedEffect(request) {
        val query = request ?: return@LaunchedEffect
        searchBarState.animateToExpanded()
        textFieldState.setTextAndPlaceCursorAtEnd(query)
        onRequestHandled()
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
            onFilterChange = onFilterChange,
        )
    }
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
    onFilterChange: (MessageSearchFilter) -> Unit,
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
    val showSaved = savedMessagesChatId != null && savedTitle.contains(trimmed, ignoreCase = true) &&
        search.filter.period == null && (search.filter.chatKind == null || search.filter.chatKind == ChatKind.Private)
    // Výsledky patří k dotazu, na který TDLib odpovídal. Dokud nedorazí nové, ukazují se
    // staré jen pro tentýž dotaz — jinak by pod „jana" chvíli visely výsledky pro „ja".
    val current = search.query == trimmed
    val filter = search.filter
    // Druh chatu zúží i chaty; období se týká jen zpráv, takže s ním chaty nemají co ukázat.
    val chatsShown = filter.period == null
    val chats = if (current && chatsShown) {
        search.results.filter { it.id != savedMessagesChatId && (filter.chatKind == null || it.kind == filter.chatKind) }
    } else {
        emptyList()
    }
    val global = if (current && chatsShown) search.globalOnly.filter { filter.chatKind == null || it.kind == filter.chatKind } else emptyList()
    val messages = if (current) search.messages else emptyList()
    val settled = current && search.settled

    /*
     * Jednou ukázaný prázdný stav vydrží i během hledání dalšího znaku. Kdyby ho mezitím
     * vystřídal indikátor, úvodní animace prázdného stavu by se pouštěla při každém znaku.
     */
    var emptyShown by remember { mutableStateOf(false) }
    val nothing = chats.isEmpty() && global.isEmpty() && messages.isEmpty() && !showSaved
    SideEffect { emptyShown = nothing && (settled || emptyShown) }

    Column(Modifier.fillMaxSize()) {
        // Čipy nad výsledky, ne jako položka seznamu: filtr musí zůstat po ruce i odscrollovaný.
        // Hashtag hledá jen zprávy, druh chatu se tam nabízí taky; období dává smysl vždycky.
        // U hashtagu nejdřív odkud (vlastní chaty / veřejné příspěvky); veřejné příspěvky druh
        // chatu ani období neberou, takže s nimi filtry nejsou.
        if (isHashtagQuery(trimmed)) HashtagSourceToggle(filter = search.filter, onChange = onFilterChange)
        if (!searchesPublicPosts(trimmed, search.filter)) {
            MessageSearchFilters(filter = search.filter, onChange = onFilterChange)
        }
        if (nothing) {
            if (settled || emptyShown) {
                // Stejný prázdný stav jako placeholder detailu a prázdný archiv. Spodek je nad
                // klávesnicí a lištou (viz `resultsPadding`), jinak by se centroval pod klávesnici.
                IntroEmptyState(
                    icon = Res.drawable.search_off_24dp,
                    title = stringResource(Res.string.chats_search_empty),
                    // S filtrem je nejčastější příčina právě filtr — věta to má říct.
                    text = stringResource(if (search.filter.isEmpty) Res.string.chats_search_empty_hint else Res.string.search_filter_empty_hint),
                    containerColor = Color.Transparent,
                    modifier = Modifier.padding(bottom = resultsPadding().calculateBottomPadding()),
                )
            } else {
                // Expressive indikátor jako při načítání seznamu chatů — kroužek by tu byl jediný
                // prvek starého jazyka.
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    LoadingIndicator()
                }
            }
            return@Column
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
                    avatarShape = chat.kind.avatarShape(),
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
                        avatarShape = chat.kind.avatarShape(),
                    )
                }
            }

            if (messages.isNotEmpty()) {
                item(key = "messages-header") {
                    SectionHeader(
                        stringResource(
                            if (searchesPublicPosts(trimmed, filter)) Res.string.search_source_public else Res.string.chats_search_section_messages,
                        ),
                    )
                }
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
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
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
    // Kdo zprávu napsal, jako v seznamu zpráv oficiálních klientů: ve skupině jméno, u vlastní
    // zprávy „Vy". Kanál ani soukromý chat jméno nepotřebují — odesílatele říká název chatu.
    val sender = found.senderName
        ?: stringResource(Res.string.sender_you).takeIf { found.isOutgoing && found.chat.kind != ChatKind.Channel }
    val senderColor = MaterialTheme.colorScheme.onSurface
    val text = remember(found.text, query, highlight, sender, senderColor) {
        val snippet = snippetAround(found.text.replace('\n', ' '), query)
        buildAnnotatedString {
            sender?.let {
                withStyle(SpanStyle(color = senderColor)) { append(it) }
                append(": ")
            }
            val start = length
            append(snippet)
            highlightRanges(snippet, query).forEach { addStyle(highlight, start + it.first, start + it.last + 1) }
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
                shape = found.chat.kind.avatarShape(),
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
                    avatarShape = chat.kind.avatarShape(),
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
            shape = chat.kind.avatarShape(),
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
    avatarShape: Shape = CircleShape,
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
            Avatar(chatId = chatId, title = title, avatarFileId = avatarFileId, size = 40.dp, isSavedMessages = isSavedMessages(chatId), shape = avatarShape)
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

/** Výměna nápovědy pilulky: stará zmizí, nová se objeví až potom (stejná čísla jako fade through obrazovek). */
private const val HINT_FADE_OUT_MS = 90
private const val HINT_FADE_IN_MS = 210
