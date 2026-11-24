package eu.tintera.plume.features.chats

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TooltipAnchorPosition

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import eu.tintera.plume.core.accounts.rememberShownAccount
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountBadge
import androidx.compose.foundation.interaction.DragInteraction
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.sponsored_report
import eu.tintera.plume.resources.sponsored_menu
import eu.tintera.plume.resources.sponsored_sponsored
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.adaptive.LocalListPaneToggle
import eu.tintera.plume.core.telegram.SponsoredSearchChat
import eu.tintera.plume.core.telegram.SearchContent
import org.jetbrains.compose.resources.DrawableResource
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.chats_search_section_photos
import eu.tintera.plume.core.settings.StoredPhotoDescription
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.resources.profile_bookmarks
import eu.tintera.plume.resources.bookmark_filled_24dp
import eu.tintera.plume.resources.chats_search_remove_recent
import androidx.compose.foundation.lazy.itemsIndexed
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.resources.chats_search_remove_top
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.resources.chats_search_ai
import eu.tintera.plume.resources.auto_awesome_24dp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import eu.tintera.plume.core.ui.resolve
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.FoundMessage
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.resources.chats_search_archived
import eu.tintera.plume.resources.chats_search_clear_recent
import eu.tintera.plume.resources.chats_search_people
import eu.tintera.plume.resources.chats_search_recent
import eu.tintera.plume.resources.tag_24dp
import eu.tintera.plume.resources.chats_search_tags
import eu.tintera.plume.resources.chats_search_recommended
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.SecretChatLock
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_search
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.ui.ConnectionStatusIcon
import eu.tintera.plume.core.ui.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.WindowControlsSpace
import eu.tintera.plume.core.adaptive.LocalWindowControlsClearance
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace

/**
 * Hlavička seznamu chatů: vyhledávací pilulka, na jejím konci [trailing] (hromádka příběhů)
 * a VEDLE ní [actions] (fotka účtu s menu).
 *
 * **Pilulka se nerozbaluje, jen otevře hledání** ([onOpenSearch] → `ChatSearchRoute`,
 * `ChatSearchScreen`). Hledání je vlastní obrazovka v panelu seznamu (`docs/navrh-hledani.md`):
 * dřív se z pilulky rozbalovalo na telefonu přes celou obrazovku a na široké ploše jako malý
 * panel pod pilulkou nad seznamem — popup s řadou obcházek a výsledky nacpanými do okénka.
 * Pole v pilulce proto není, jen nápověda; `AppBarWithSearch` zůstává kvůli tvaru, barvám,
 * odjíždění při scrollu a slotu pro fotku účtu.
 *
 * **Fotka účtu stojí mimo pilulku**, ve slotu `actions` baru. Dokud byla uvnitř (vzor Gmailu),
 * ťuknutí kousek vedle fotky padlo na pilulku a místo menu se otevřelo hledání: hranice
 * tlačítka uvnitř tónové plochy není vidět. Mezera mezi pilulkou a fotkou je teď mrtvá zóna
 * a fotka mohla vyrůst.
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
 * **Stav spojení je nápověda pilulky** ([connection]) a místo lupy jeho znak
 * ([ConnectionStatusIcon]), ne nadpis ani vlastní prvek. Výjimka je úzká hlavička bez pilulky
 * ([CompactChatListHeader]), tam stojí stav i příběhy samostatně. Skoro celou dobu je `null` a pak je
 * vidět jen „Hledat v chatech"; nadpis „Chaty" by jen opakoval, co říká navigace. Fotka účtu
 * stav nenese — je to identita, ne kontrolka.
 */
@Composable
internal fun ChatSearchBar(
    connection: ConnectionState?,
    scrollBehavior: SearchBarScrollBehavior,
    onOpenSearch: () -> Unit,
    /** Hromádka příběhů na konci pilulky, v úzké hlavičce samostatně; [Color] je plocha pod ní (lem). */
    stories: @Composable (rim: Color) -> Unit,
    /** Vedle pilulky, na ploše stránky (menu účtu). */
    actions: @Composable RowScope.() -> Unit,
) {
    // `AppBarWithSearch` stav chce; pole v pilulce není, takže se nikdy nerozbalí.
    val searchBarState = rememberSearchBarState()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // Bar má barvu stránky i nad odscrollovaným seznamem — tónovaný pruh přes celou šířku byl
    // to, co vadilo na sbaleném velkém baru. Odstup od obsahu nese pilulka.
    val colors = SearchBarDefaults.appBarWithSearchColors(
        scrolledAppBarContainerColor = MaterialTheme.colorScheme.surface,
    )

    // Výpadek spojení bar vrátí, i když je odjetý.
    LaunchedEffect(connection != null) {
        val scroll = scrollBehavior.scrollState
        if (connection != null && scroll.scrollOffset != 0f) {
            animate(scroll.scrollOffset, 0f, animationSpec = spatial) { value, _ ->
                scroll.scrollOffset = value
            }
        }
    }

    val searchLabel = stringResource(Res.string.chats_search)
    val morph = LocalSearchMorph.current
    // Pilulka zmizela (sbalený sloupec, jiná obrazovka): pole by jinak rostlo ze staré polohy.
    DisposableEffect(morph) { onDispose { morph?.pill = null } }
    val pill = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(SearchBarDefaults.InputFieldHeight)
                // Odtud vyroste pole hledání (`SearchMorph`).
                .onGloballyPositioned { coordinates -> morph?.pill = Rect(coordinates.positionInRoot(), coordinates.size.toSize()) }
                .clip(SearchBarDefaults.inputFieldShape)
                .clickable(onClickLabel = searchLabel, role = Role.Button, onClick = onOpenSearch),
        ) {
            /*
             * Lupa, nebo bez spojení jeho znak. Prolnutí, ne fade through jako u nápovědy: dvě
             * malé ikony přes sebe se nepletou jako dva texty. Klíč je jen „je výpadek": mezi
             * stavy výpadku se přechod nespouští (ten si řeší znak sám) a odcházející znak si
             * nechá poslední stav, ne `null`.
             */
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            Box(Modifier.padding(start = 4.dp).size(48.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = connection,
                    transitionSpec = { fadeIn(effects) togetherWith fadeOut(fastEffects) },
                    contentAlignment = Alignment.Center,
                    contentKey = { it != null },
                    label = "search-leading",
                ) { shown ->
                    if (shown != null) {
                        ConnectionStatusIcon(shown)
                    } else {
                        Icon(
                            painterResource(Res.drawable.search_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            /*
             * Nápověda nese stav spojení (znak místo lupy ukazuje, ŽE něco není v pořádku, text
             * CO). Výměna je fade through, ne `Crossfade`: dva texty na témže místě napůl
             * průhledné přes sebe („Čekání na síť" přes „Připojování") se nedají přečíst
             * a vypadají jako chyba vykreslení — tatáž úvaha jako u přechodů mezi obrazovkami.
             */
            val hint = connection?.label() ?: Res.string.chats_search
            AnimatedContent(
                targetState = hint,
                transitionSpec = {
                    fadeIn(tween(HINT_FADE_IN_MS, delayMillis = HINT_FADE_OUT_MS, easing = LinearOutSlowInEasing)) togetherWith
                        fadeOut(tween(HINT_FADE_OUT_MS, easing = FastOutLinearInEasing))
                },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            ) {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.padding(end = 4.dp)) { stories(MaterialTheme.colorScheme.surfaceContainerHigh) }
        }
    }

    // Barva stránky výslovně. Bar ji má mít sám (`appBarContainerColor`), ale bez scroll
    // behavior ji v téhle alphě nenakreslil a chaty pod ním prosvítaly.
    BoxWithConstraints(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .then(scrollBehavior.searchBarScrollBehaviorModifier),
    ) {
        // Desktop: v řádku se semafory jim pilulka nechá místo (`LocalWindowControlsClearance`).
        val clearance = LocalWindowControlsClearance.current
        val windowControls = clearance.start > 0.dp
        // Vedle konverzace přepínač sbalení seznamu před pilulkou, na Macu hned za semafory.
        val listToggle = LocalListPaneToggle.current
        /*
         * Okraj navíc, aby pilulka a fotka účtu stály na hraně bloků seznamu (16 dp), ne 8 dp od
         * kraje, jak je to v `AppBarWithSearch` výchozí. Jen na straně, kde nic není: tlačítka
         * okna a přepínač seznamu mají svůj okraj a okraj navíc by je odsunul od kraje okna.
         */
        val edgeStart = if (windowControls || listToggle != null) 0.dp else HEADER_EDGE
        val edgeEnd = if (clearance.end > 0.dp) 0.dp else HEADER_EDGE
        /*
         * Kolik by zbylo na pilulku: řádek bez tlačítek okna, přepínače, fotky účtu a okrajů baru.
         * Pod prahem je z pilulky lupa a stav spojení i příběhy stojí samostatně
         * ([CompactChatListHeader]). Na telefonu místo je, tam zůstává pilulka.
         */
        val pillRoom = maxWidth - clearance.start - clearance.end - HEADER_BUTTON * 2 -
            (if (listToggle != null) HEADER_BUTTON else 0.dp) - HEADER_PADDING - edgeStart - edgeEnd
        val compact = pillRoom < PILL_MIN_WIDTH
        val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = compact,
            transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
            label = "chat-list-header",
        ) { iconOnly ->
            if (iconOnly) {
                CompactChatListHeader(
                    connection = connection,
                    onOpenSearch = onOpenSearch,
                    stories = stories,
                    actions = actions,
                    edge = PaddingValues(start = edgeStart, end = edgeEnd),
                )
                return@AnimatedContent
            }
            AppBarWithSearch(
                state = searchBarState,
                inputField = pill,
                navigationIcon = if (windowControls || listToggle != null) {
                    { WindowControlsSpace { listToggle?.let { ListPaneToggleButton(it, collapsed = false) } } }
                } else {
                    null
                },
                actions = {
                    actions()
                    WindowControlsEndSpace()
                },
                colors = colors,
                contentPadding = PaddingValues(start = edgeStart, end = edgeEnd),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}

/**
 * Hlavička seznamu, na jehož pilulku nezbývá místo (desktop vedle konverzace: semafory, přepínač
 * a fotka účtu z 360 dp sebraly přes polovinu). Lupa jako akce, ze které pole hledání vyroste
 * stejně jako z pilulky ([SearchMorph]), a co pilulka nesla, stojí samostatně: stav spojení
 * (znak a věta, jinak by výpadek nebylo vidět) a hromádka příběhů.
 */
@Composable
private fun CompactChatListHeader(
    connection: ConnectionState?,
    onOpenSearch: () -> Unit,
    stories: @Composable (rim: Color) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    /** Okraj navíc ke kraji řádku, jako u pilulky (hrana bloků seznamu). */
    edge: PaddingValues,
) {
    val morph = LocalSearchMorph.current
    val listToggle = LocalListPaneToggle.current
    val searchLabel = stringResource(Res.string.chats_search)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(TopAppBarDefaults.TopAppBarExpandedHeight)
            .padding(edge)
            .padding(horizontal = 4.dp),
    ) {
        WindowControlsSpace { listToggle?.let { ListPaneToggleButton(it, collapsed = false) } }
        Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = connection,
                transitionSpec = { fadeIn(effects) togetherWith fadeOut(fastEffects) },
                contentKey = { it != null },
                label = "compact-connection",
            ) { shown ->
                if (shown != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionStatusIcon(shown, size = 20.dp)
                        shown.label()?.let { label ->
                            Text(
                                stringResource(label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        stories(MaterialTheme.colorScheme.surface)
        WithTooltip(label = searchLabel, position = TooltipAnchorPosition.Below) {
            IconButton(
                onClick = onOpenSearch,
                shapes = IconButtonDefaults.shapes(),
                // Odtud vyroste pole hledání, jako z pilulky.
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    morph?.pill = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
                },
            ) {
                Icon(
                    painterResource(Res.drawable.search_24dp),
                    contentDescription = searchLabel,
                    modifier = Modifier.collapseFlight("search"),
                )
            }
        }
        actions()
        WindowControlsEndSpace()
    }
}

/** Nejužší pilulka, ve které se vejde lupa, „Hledat v chatech" a hromádka příběhů. */
private val PILL_MIN_WIDTH = 220.dp

/** Tlačítko v řádku hlavičky (přepínač seznamu, fotka účtu). */
private val HEADER_BUTTON = 48.dp

/** Okraje a mezery, které si `AppBarWithSearch` dává kolem pilulky. */
private val HEADER_PADDING = 32.dp

/**
 * Okraj navíc k výchozímu odsazení `AppBarWithSearch`: pilulka (8 + 8 dp) a fotka účtu
 * (4 + 8 dp k terči, 4 dp terč k fotce) pak končí na 16 dp, na hraně bloků seznamu, lišty
 * složek a FAB. S výchozími 8 dp hlavička přečnívala seznam na obou stranách.
 */
private val HEADER_EDGE = 8.dp


/**
 * Obsah rozbaleného hledání. Bez dotazu návrhy (řádek nejčastějších lidí, historie hledání),
 * s dotazem sekce: chaty (Uložené zprávy podle přeloženého názvu vždy první), veřejné chaty,
 * které uživatel nesleduje, a zprávy napříč chaty.
 *
 * Chaty a veřejné výsledky jsou sbalené na pár řádků ([COLLAPSED_ROWS]) — u krátkého dotazu
 * jich je desítky a zprávy by skončily daleko pod nimi. Zprávy se stránkují, dokud se listuje.
 */
@Composable
internal fun SearchResults(
    query: String,
    search: ChatSearchState,
    savedMessagesChatId: Long?,
    clicks: SearchResultClicks,
    controls: ChatSearchControls,
    suggestionActions: SuggestionActions,
    sponsoredActions: SponsoredActions,
    localSources: LocalSearchSources,
) {
    val trimmed = query.trim()
    val onPickLatest by rememberUpdatedState(clicks.onPick)
    val onMessageClickLatest by rememberUpdatedState(clicks.onMessageClick)
    val onLoadMoreLatest by rememberUpdatedState(controls.onLoadMoreMessages)

    // Prázdný dotaz s druhem obsahu (z hledání přirozeným jazykem) hledá zprávy, ne návrhy.
    if (trimmed.isEmpty() && search.filter.content == null) {
        Suggestions(search, onPick = { onPickLatest(it, null) }, onPickTag = clicks.onPickTag, actions = suggestionActions)
        return
    }

    val savedTitle = stringResource(Res.string.saved_messages)
    val showSaved = savedMessagesChatId != null && trimmed.isNotEmpty() && savedTitle.contains(trimmed, ignoreCase = true) &&
        search.filter.period == null && search.filter.content == null &&
        (search.filter.chatKind == null || search.filter.chatKind == ChatKind.Private)
    // Výsledky patří k dotazu, na který TDLib odpovídal. Dokud nedorazí nové, ukazují se
    // staré jen pro tentýž dotaz — jinak by pod „jana" chvíli visely výsledky pro „ja".
    val current = search.query == trimmed
    val filter = search.filter
    // Druh chatu zúží i chaty; období a druh obsahu se týkají jen zpráv, takže s nimi chaty
    // nemají co ukázat.
    val chatsShown = filter.period == null && filter.content == null
    val chats = if (current && chatsShown) {
        // Výsledek z hledání přes víc účtů (se slotem) má Uložené zprávy vyřazené už z merge
        // (`mergedSearchState`); id účtu obrazovky je v ostatních účtech chat s ním.
        search.results.filter { (it.slot != null || it.id != savedMessagesChatId) && (filter.chatKind == null || it.kind == filter.chatKind) }
    } else {
        emptyList()
    }
    val global = if (current && chatsShown) search.globalOnly.filter { filter.chatKind == null || it.kind == filter.chatKind } else emptyList()
    // Reklama patří k dotazu, ne k filtru zpráv; druh chatu ji zúží jako veřejné výsledky.
    val sponsored = if (current && chatsShown) search.sponsored.filter { filter.chatKind == null || it.chat.kind == filter.chatKind } else emptyList()
    val messages = if (current) search.messages else emptyList()
    val settled = current && search.settled
    /*
     * Záložky se hledají lokálně, v textu uloženém se záložkou, bez diakritiky — jako zvýraznění.
     * Filtry ne: období a druh obsahu míří na zprávy od serveru, záložka je vlastní poznámka
     * „tohle si chci najít", a pod filtrem by se ztratila bez vysvětlení. Hashtag hledá zprávy.
     */
    val foundBookmarks = remember(localSources.bookmarks, trimmed, filter) {
        val key = foldDiacritics(trimmed).lowercase()
        if (key.isEmpty() || !filter.isEmpty || isHashtagQuery(trimmed)) {
            emptyList()
        } else {
            localSources.bookmarks.filter { foldDiacritics(it.text).lowercase().contains(key) }
        }
    }
    // Fotky podle popisu od modelu v zařízení — týmž lokálním hledáním jako záložky, nejnovější
    // popis první. Druh obsahu „Fotky" je nevypne: tam patří nejvíc.
    val foundPhotos = remember(localSources.describedPhotos, trimmed, filter) {
        val key = foldDiacritics(trimmed).lowercase()
        val onlyPhotos = filter.copy(content = null).isEmpty && (filter.content == null || filter.content == SearchContent.Photos)
        if (key.isEmpty() || !onlyPhotos || isHashtagQuery(trimmed)) {
            emptyList()
        } else {
            localSources.describedPhotos.filter { foldDiacritics(it.text).lowercase().contains(key) }.sortedByDescending { it.savedAt }
        }
    }

    /*
     * Jednou ukázaný prázdný stav vydrží i během hledání dalšího znaku. Kdyby ho mezitím
     * vystřídal indikátor, úvodní animace prázdného stavu by se pouštěla při každém znaku.
     */
    var emptyShown by remember { mutableStateOf(false) }
    val nothing = chats.isEmpty() && sponsored.isEmpty() && global.isEmpty() && messages.isEmpty() && foundBookmarks.isEmpty() && foundPhotos.isEmpty() && !showSaved
    SideEffect { emptyShown = nothing && (settled || emptyShown) }

    Column(Modifier.fillMaxSize()) {
        // Čipy nad výsledky, ne jako položka seznamu: filtr musí zůstat po ruce i odscrollovaný.
        // Hashtag hledá jen zprávy, druh chatu se tam nabízí taky; období dává smysl vždycky.
        // U hashtagu nejdřív odkud (vlastní chaty / veřejné příspěvky); veřejné příspěvky druh
        // chatu ani období neberou, takže s nimi filtry nejsou.
        if (isHashtagQuery(trimmed)) HashtagSourceToggle(filter = search.filter, onChange = controls.onFilterChange)
        if (!searchesPublicPosts(trimmed, search.filter)) {
            MessageSearchFilters(filter = search.filter, onChange = controls.onFilterChange)
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
        var bookmarksExpanded by rememberSaveable(trimmed) { mutableStateOf(false) }
        var photosExpanded by rememberSaveable(trimmed) { mutableStateOf(false) }
        val listState = rememberLazyListState()

        /*
         * Výsledky přicházejí po sekcích a v libovolném pořadí: zprávy bývají dřív než chaty od
         * serveru, veřejné výsledky a reklama. Seznam drží pozici podle první viditelné položky,
         * takže sekce vložené NAD ni odjely nad horní hranu — pod „games" začínal seznam až zprávami
         * a sponzorovaný chat nebyl vidět vůbec (a jeho zhlédnutí se nenahlásilo). Dokud uživatel
         * výsledky sám neposunul, drží se proto seznam u začátku.
         */
        var userScrolled by remember(trimmed) { mutableStateOf(false) }
        LaunchedEffect(listState) {
            listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) userScrolled = true }
        }
        LaunchedEffect(trimmed, chats.size, sponsored.size, global.size, showSaved) {
            if (!userScrolled && listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
        }

        // Další stránka zpráv, jakmile se listuje ke konci.
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
                last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
            }.collect { nearEnd -> if (nearEnd) onLoadMoreLatest() }
        }

        /*
         * Zhlédnutí sponzorovaného chatu se hlásí, až je řádek na obrazovce CELÝ (specifikace:
         * „when the sponsored peer is shown on screen"). Opakování hlídá ViewModel.
         */
        val onSponsoredSeenLatest by rememberUpdatedState(sponsoredActions.onSeen)
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                info.visibleItemsInfo.mapNotNull { item ->
                    sponsoredUniqueId(item.key)
                        ?.takeIf { item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset }
                }
            }.collect { seen -> seen.forEach { onSponsoredSeenLatest(it) } }
        }

        val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

        val rowColor = MaterialTheme.colorScheme.groupRow
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
            val shownChats = if (chatsExpanded || global.isEmpty() && messages.isEmpty()) {
                chats
            } else {
                chats.take((COLLAPSED_ROWS - if (showSaved) 1 else 0).coerceAtLeast(0))
            }
            // Sekce je blok zaoblených řádků jako seznam chatů; Uložené zprávy jsou jeho první řádek.
            val savedRows = if (showSaved && savedMessagesChatId != null) 1 else 0
            val chatRows = shownChats.size + savedRows
            savedMessagesChatId?.takeIf { showSaved }?.let { savedId ->
                item(key = savedId) {
                    ChatResultRow(
                        chatId = savedId,
                        title = savedTitle,
                        avatarFileId = null,
                        archived = false,
                        onClick = { onPickLatest(savedId, null) },
                        modifier = Modifier.animateItem().groupedItem(0, chatRows, rowColor),
                    )
                }
            }
            // Klíč i s účtem: při hledání přes víc účtů je společná skupina mezi výsledky dvakrát.
            itemsIndexed(shownChats, key = { _, chat -> resultKey(chat) }) { index, chat ->
                ChatResultRow(
                    chatId = chat.id,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    archived = chat.isArchived,
                    onClick = { onPickLatest(chat.id, chat.slot) },
                    slot = chat.slot,
                    // Serverové výsledky přibývají za lokálními — plynule, ne skokem.
                    modifier = Modifier.animateItem().groupedItem(index + savedRows, chatRows, rowColor),
                    avatarShape = chat.kind.avatarShape(),
                    avatarColorId = chat.avatarColorId,
                    secret = chat.isSecret,
                )
            }

            /*
             * Sponzorované chaty stojí mezi vlastními a veřejnými výsledky — tam je specifikace
             * reklam chce (`my_results` → sponzorované → `results`). Nadpis „Sponzorováno" říká,
             * že jde o reklamu; sbalit ani skrýt se nedají.
             */
            if (sponsored.isNotEmpty()) {
                item(key = "sponsored-header") {
                    SectionHeader(stringResource(Res.string.sponsored_sponsored))
                }
                itemsIndexed(sponsored, key = { _, ad -> sponsoredRowKey(ad.uniqueId) }) { index, ad ->
                    SponsoredResultRow(
                        ad = ad,
                        onClick = { clicks.onPickSponsored(ad) },
                        onReport = { sponsoredActions.onReport(ad) },
                        modifier = Modifier.animateItem().groupedItem(index, sponsored.size, rowColor),
                    )
                }
            }

            if (global.isNotEmpty()) {
                item(key = "global-header") {
                    SectionHeader(stringResource(Res.string.chats_search_section_global)) {
                        ExpandToggle(global.size, globalExpanded) { globalExpanded = it }
                    }
                }
                val shownGlobal = if (globalExpanded) global else global.take(COLLAPSED_ROWS)
                itemsIndexed(shownGlobal, key = { _, chat -> "global-${resultKey(chat)}" }) { index, chat ->
                    ChatResultRow(
                        chatId = chat.id,
                        title = chat.title,
                        avatarFileId = chat.avatarFileId,
                        archived = false,
                        onClick = { onPickLatest(chat.id, chat.slot) },
                        slot = chat.slot,
                        modifier = Modifier.animateItem().groupedItem(index, shownGlobal.size, rowColor),
                        avatarShape = chat.kind.avatarShape(),
                        avatarColorId = chat.avatarColorId,
                        secret = chat.isSecret,
                    )
                }
            }

            if (foundBookmarks.isNotEmpty()) {
                item(key = "bookmarks-header") {
                    SectionHeader(stringResource(Res.string.profile_bookmarks)) {
                        ExpandToggle(foundBookmarks.size, bookmarksExpanded) { bookmarksExpanded = it }
                    }
                }
                val shownBookmarks = if (bookmarksExpanded || messages.isEmpty()) foundBookmarks else foundBookmarks.take(COLLAPSED_ROWS)
                itemsIndexed(shownBookmarks, key = { _, bookmark -> "bookmark-${bookmark.chatId}-${bookmark.messageId}" }) { index, bookmark ->
                    val title = localSources.chatTitles[bookmark.chatId]
                    LocalResultRow(
                        title = title ?: bookmark.sender.orEmpty(),
                        // U kanálu je odesílatel tentýž jako název — dvakrát by byl šum.
                        sender = bookmark.sender?.takeIf { it != title },
                        timestamp = bookmark.timestamp,
                        text = bookmark.text,
                        icon = Res.drawable.bookmark_filled_24dp,
                        query = trimmed,
                        highlight = highlight,
                        onClick = { onMessageClickLatest(bookmark.chatId, bookmark.messageId, null, null) },
                        modifier = Modifier.animateItem().groupedItem(index, shownBookmarks.size, rowColor),
                    )
                }
            }

            if (foundPhotos.isNotEmpty()) {
                item(key = "photos-header") {
                    SectionHeader(stringResource(Res.string.chats_search_section_photos)) {
                        ExpandToggle(foundPhotos.size, photosExpanded) { photosExpanded = it }
                    }
                }
                val shownPhotos = if (photosExpanded || messages.isEmpty()) foundPhotos else foundPhotos.take(COLLAPSED_ROWS)
                itemsIndexed(shownPhotos, key = { _, photo -> "photo-${photo.chatId}-${photo.messageId}" }) { index, photo ->
                    LocalResultRow(
                        title = localSources.chatTitles[photo.chatId].orEmpty(),
                        sender = null,
                        timestamp = null,
                        text = photo.text,
                        icon = Res.drawable.image_24dp,
                        query = trimmed,
                        highlight = highlight,
                        onClick = { onMessageClickLatest(photo.chatId, photo.messageId, null, null) },
                        modifier = Modifier.animateItem().groupedItem(index, shownPhotos.size, rowColor),
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
                itemsIndexed(messages, key = { _, found -> "message-${resultKey(found.chat)}-${found.messageId}" }) { index, found ->
                    MessageResultRow(
                        found = found,
                        query = trimmed,
                        highlight = highlight,
                        onClick = { onMessageClickLatest(found.chat.id, found.messageId, found.topicId, found.chat.slot) },
                        modifier = Modifier.groupedItem(index, messages.size, rowColor),
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

/**
 * Výsledek nalezený lokálně, ne serverem — záložka nebo fotka podle popisu: název chatu, kdy
 * zpráva přišla (je-li známo) a úryvek se zvýrazněným dotazem. Ikona místo avataru říká,
 * odkud výsledek je; mezi zprávami od serveru by jinak splynul.
 */
@Composable
private fun LocalResultRow(
    title: String,
    sender: String?,
    timestamp: Int?,
    text: String,
    icon: DrawableResource,
    query: String,
    highlight: SpanStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val senderColor = MaterialTheme.colorScheme.onSurface
    val annotated = remember(text, query, highlight, sender, senderColor) {
        val snippet = snippetAround(text.replace('\n', ' '), query)
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
        supportingContent = { Text(annotated, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            timestamp?.let {
                Text(
                    it.asRelativeTime(),
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** „Zobrazit vše" / „Zobrazit méně" v nadpisu sekce — jen když se sekce do sbalené podoby nevejde. */
@Composable
private fun ExpandToggle(count: Int, expanded: Boolean, onChange: (Boolean) -> Unit) {
    if (count <= COLLAPSED_ROWS) return
    TextButton(onClick = { onChange(!expanded) }, shapes = ButtonDefaults.shapes()) {
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
    modifier: Modifier = Modifier,
) {
    // Kdo zprávu napsal, jako v seznamu zpráv oficiálních klientů: ve skupině jméno, u vlastní
    // zprávy „Vy". Kanál ani soukromý chat jméno nepotřebují — odesílatele říká název chatu.
    val sender = found.senderName
        ?: stringResource(Res.string.sender_you).takeIf { found.isOutgoing && found.chat.kind != ChatKind.Channel }
    val senderColor = MaterialTheme.colorScheme.onSurface
    val foundText = found.text.resolve()
    val (text, customEmoji) = remember(foundText, found.entities, query, highlight, sender, senderColor) {
        // Zalomení by ve dvouřádkové ukázce plýtvalo místem; mezera je stejně dlouhá, takže
        // offsety vlastních emoji sedí dál.
        val snippet = snippetOf(foundText.replace('\n', ' '), query, found.entities)
        var emoji = emptySet<Long>()
        val annotated = buildAnnotatedString {
            sender?.let {
                withStyle(SpanStyle(color = senderColor)) { append(it) }
                append(": ")
            }
            val start = length
            emoji = appendWithCustomEmoji(snippet.text, snippet.entities)
            highlightRanges(snippet.text, query).forEach { addStyle(highlight, start + it.first, start + it.last + 1) }
        }
        annotated to emoji
    }
    ResultAccountScope(found.chat.slot) {
    ListItem(
        supportingContent = {
            Text(
                text,
                inlineContent = customEmojiInlineContent(customEmoji),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            ResultAvatar(found.chat.slot) {
                Avatar(
                    chatId = found.chat.avatarColorId,
                    title = found.chat.title,
                    avatarFileId = found.chat.avatarFileId,
                    size = 40.dp,
                    isSavedMessages = isSavedMessages(found.chat.id),
                    shape = found.chat.kind.avatarShape(),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (found.chat.isSecret) SecretChatLock(modifier = Modifier.padding(end = 6.dp))
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
    }
    }
}

@Composable
private fun Suggestions(
    search: ChatSearchState,
    onPick: (Long) -> Unit,
    onPickTag: (String) -> Unit,
    actions: SuggestionActions,
) {
    var recommendedExpanded by rememberSaveable { mutableStateOf(false) }
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
                        TopChat(chat, onClick = { onPick(chat.id) }, onRemove = { actions.onRemoveTop(chat.id) })
                    }
                }
            }
        }

        if (search.recent.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeader(stringResource(Res.string.chats_search_recent)) {
                    TextButton(onClick = actions.onClear, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.chats_search_clear_recent)) }
                }
            }
            itemsIndexed(search.recent, key = { _, chat -> "recent-${chat.id}" }) { index, chat ->
                ChatResultRow(
                    chatId = chat.id,
                    title = chatTitle(chat.id, chat.title),
                    avatarFileId = chat.avatarFileId,
                    archived = chat.isArchived,
                    onClick = { onPick(chat.id) },
                    modifier = Modifier.animateItem().groupedItem(index, search.recent.size, MaterialTheme.colorScheme.groupRow),
                    avatarShape = chat.kind.avatarShape(),
                    onRemove = { actions.onRemoveRecent(chat.id) },
                )
            }
        }

        if (search.tags.isNotEmpty()) {
            item(key = "tags-header") {
                SectionHeader(stringResource(Res.string.chats_search_tags)) {
                    TextButton(onClick = actions.onClearTags, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.chats_search_clear_recent)) }
                }
            }
            itemsIndexed(search.tags, key = { _, tag -> "tag-$tag" }) { index, tag ->
                ListItem(
                    leadingContent = { Icon(painterResource(Res.drawable.tag_24dp), contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { actions.onRemoveTag(tag) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.chats_search_remove_recent))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.animateItem()
                        .groupedItem(index, search.tags.size, MaterialTheme.colorScheme.groupRow)
                        .clickable { onPickTag(tag) },
                ) { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }

        if (search.recommended.isNotEmpty()) {
            item(key = "recommended-header") {
                SectionHeader(stringResource(Res.string.chats_search_recommended)) {
                    ExpandToggle(search.recommended.size, recommendedExpanded) { recommendedExpanded = it }
                }
            }
            val shown = if (recommendedExpanded) search.recommended else search.recommended.take(COLLAPSED_ROWS)
            itemsIndexed(shown, key = { _, chat -> "recommended-${chat.id}" }) { index, chat ->
                ChatResultRow(
                    chatId = chat.id,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    archived = false,
                    onClick = { onPick(chat.id) },
                    modifier = Modifier.animateItem().groupedItem(index, shown.size, MaterialTheme.colorScheme.groupRow),
                    avatarShape = chat.kind.avatarShape(),
                    avatarColorId = chat.avatarColorId,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Nadpis lícuje s obsahem řádků v bloku (okraj bloku 16 + odsazení řádku 16), tlačítko
        // s okrajem bloku.
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 32.dp, end = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action()
    }
}

/**
 * Nejčastější člověk: avatar a pod ním křestní jméno, jako řádek „lidí" v oficiálních klientech.
 * Podržení nabídne „Odebrat z návrhů" — Telegram ho tam vrátí, až si spolu zase budou psát často.
 */
@Composable
private fun TopChat(chat: ChatSummary, onClick: () -> Unit, onRemove: () -> Unit) {
    val title = chatTitle(chat.id, chat.title)
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .width(TOP_CHAT_WIDTH)
                .clip(MaterialTheme.shapes.medium)
                // Odezvu na podržení dělá `combinedClickable` sám.
                .contextClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(vertical = 8.dp),
        ) {
            Avatar(
                chatId = chat.avatarColorId,
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
        ExpressiveMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.chats_search_remove_top), Res.drawable.close_24dp) {
                        menuOpen = false
                        onRemove()
                    },
                ),
            ),
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
    avatarColorId: Long = chatId,
    /** Tajný chat — zámek před jménem, jinak by splynul s obyčejným chatem téhož člověka. */
    secret: Boolean = false,
    /** Řádek historie hledání: křížek ho z historie odebere. `null` = křížek není. */
    onRemove: (() -> Unit)? = null,
    /** Účet chatu při hledání přes víc účtů; `null` = účet obrazovky. */
    slot: AccountSlot? = null,
) {
    ResultAccountScope(slot) {
    ListItem(
        // Archiv se v hlavním seznamu nevidí, takže nalezený archivovaný chat by jinak
        // vypadal, jako by se odněkud vynořil.
        supportingContent = if (archived) {
            { Text(stringResource(Res.string.chats_search_archived)) }
        } else {
            null
        },
        leadingContent = {
            ResultAvatar(slot) {
                Avatar(chatId = avatarColorId, title = title, avatarFileId = avatarFileId, size = 40.dp, isSavedMessages = isSavedMessages(chatId), shape = avatarShape)
            }
        },
        trailingContent = onRemove?.let { remove ->
            {
                IconButton(onClick = remove, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.chats_search_remove_recent))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (secret) SecretChatLock()
            // Až v kontejneru účtu výsledku: Uložené zprávy jiného účtu se poznají jen tady.
            Text(chatTitle(chatId, title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    }
}

/**
 * Výsledek hledání účtu [slot] (hledání přes víc účtů) se skládá v kontejneru toho účtu: fotka
 * jde z jeho souborů a „Uložené zprávy" se poznají podle jeho id. `null` = účet obrazovky;
 * účet, který mezitím zmizel, výsledek neukáže.
 */
@Composable
private fun ResultAccountScope(slot: AccountSlot?, content: @Composable () -> Unit) {
    if (slot == null) return content()
    koinInject<Accounts>().find(slot)?.let { AccountScope(it, content) }
}

/** Avatar výsledku s odznakem účtu, když je výsledek z hledání přes víc účtů. */
@Composable
private fun ResultAvatar(slot: AccountSlot?, avatar: @Composable () -> Unit) {
    Box {
        avatar()
        if (slot != null) {
            rememberShownAccount()?.let { account ->
                AccountBadge(account, rim = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.BottomStart), size = 14.dp)
            }
        }
    }
}

/** Klíč výsledku s chatem: při hledání přes víc účtů i s účtem. */
private fun resultKey(chat: ChatSummary): String = chat.slot?.let { "${it.id}-${chat.id}" } ?: chat.id.toString()

/** Co jde se sponzorovaným chatem ve výsledcích hledání (viz `ChatsViewModel`). */
class SponsoredActions(
    val onSeen: (uniqueId: Long) -> Unit,
    val onOpen: (uniqueId: Long) -> Unit,
    val onReport: (SponsoredSearchChat) -> Unit,
)

/*
 * Klíč řádku sponzorovaného chatu — podle něj se v seznamu pozná, který je vidět. Řetězec, ne
 * vlastní třída: klíč položky lazy seznamu si Android ukládá do `Bundle` a datová třída tam
 * aplikaci shodila hned při prvním výsledku s reklamou.
 */
private const val SPONSORED_KEY_PREFIX = "sponsored-"

private fun sponsoredRowKey(uniqueId: Long): String = SPONSORED_KEY_PREFIX + uniqueId

private fun sponsoredUniqueId(key: Any): Long? =
    (key as? String)?.takeIf { it.startsWith(SPONSORED_KEY_PREFIX) }?.removePrefix(SPONSORED_KEY_PREFIX)?.toLongOrNull()

/**
 * Sponzorovaný chat ve výsledcích hledání. Informace o sponzorovi a doplňující text od Telegramu
 * jsou pod jménem — TDLib je chce „ukázat spolu s chatem". ⋮ nabízí nahlášení; skrýt reklamu
 * klient nesmí (mezi důvody od serveru bývá skrytí reklam, které server podmíní Premium).
 */
@Composable
private fun SponsoredResultRow(
    ad: SponsoredSearchChat,
    onClick: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = listOf(ad.sponsorInfo, ad.additionalInfo).filter { it.isNotBlank() }.joinToString("\n")
    ListItem(
        supportingContent = if (info.isNotEmpty()) {
            // Celé, neořezané: „Ad funded by" a „This ad may be based on" je povinná průhlednost
            // reklamy (EU), ne ozdoba — useknuté za třetím řádkem ztratilo právě důvod reklamy.
            { Text(info) }
        } else {
            null
        },
        leadingContent = {
            Avatar(
                chatId = ad.chat.avatarColorId,
                title = ad.chat.title,
                avatarFileId = ad.chat.avatarFileId,
                size = 40.dp,
                shape = ad.chat.kind.avatarShape(),
            )
        },
        trailingContent = { SponsoredResultMenu(onReport) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    ) { Text(ad.chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun SponsoredResultMenu(onReport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.sponsored_menu)
    Box {
        WithTooltip(label) {
            IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = label)
            }
        }
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                listOf(
                    MenuAction(label = stringResource(Res.string.sponsored_report), icon = Res.drawable.flag_24dp) {
                        open = false
                        onReport()
                    },
                ),
            ),
        )
    }
}

/** Úklid návrhů pod prázdným dotazem; jeden objekt místo tří parametrů hledání. */
class SuggestionActions(
    val onClear: () -> Unit,
    val onRemoveRecent: (chatId: Long) -> Unit,
    val onRemoveTop: (chatId: Long) -> Unit,
    val onRemoveTag: (tag: String) -> Unit = {},
    val onClearTags: () -> Unit = {},
)

/**
 * Co pilulka a výsledky hlásí hledání ([ChatSearch]): dotaz, rozbalení, filtr a další stránku
 * zpráv.
 */
internal data class ChatSearchControls(
    val onQueryChange: (String) -> Unit,
    /** Návrhy (nejčastější, historie) se načítají jen při otevřeném hledání. */
    val onExpandedChange: (Boolean) -> Unit,
    val onFilterChange: (MessageSearchFilter) -> Unit,
    /** Další stránka zpráv, jakmile se ve výsledcích listuje ke konci. */
    val onLoadMoreMessages: () -> Unit,
)

/** Co hledání prochází v zařízení, ne na serveru: záložky zpráv a fotky popsané modelem. */
internal data class LocalSearchSources(
    /** Záložky zpráv napříč chaty; ty, jejichž text sedí na dotaz, mají vlastní blok výsledků. */
    val bookmarks: List<MessageBookmark> = emptyList(),
    /** Názvy chatů záložek i popsaných fotek. */
    val chatTitles: Map<Long, String> = emptyMap(),
    /** Fotky popsané modelem v zařízení; hledání je najde podle popisu. */
    val describedPhotos: List<StoredPhotoDescription> = emptyList(),
)

/** Co udělá ťuknutí na výsledek hledání (`ChatSearchScreen`). */
internal data class SearchResultClicks(
    val onPick: (chatId: Long, slot: AccountSlot?) -> Unit,
    val onMessageClick: (chatId: Long, messageId: Long, topicId: Int?, slot: AccountSlot?) -> Unit,
    /** Hledaný hashtag z historie: hledá se znovu, jako by ho uživatel napsal. */
    val onPickTag: (String) -> Unit,
    val onPickSponsored: (SponsoredSearchChat) -> Unit,
)

private val TOP_CHAT_WIDTH = 76.dp

/** Odsazení seznamů v hledání: spodní systémové lišty a klávesnice. */
@Composable
internal fun resultsPadding(): PaddingValues {
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
internal fun snippetAround(text: String, query: String): String = snippetOf(text, query).text

/** Ukázka nalezené zprávy a entity, které do ní padnou, s offsety posunutými na ni. */
internal data class Snippet(val text: String, val entities: List<MessageEntity>)

/**
 * [snippetAround] i s [entities] (vlastní emoji, spoilery). Ukázka začíná kus před nalezeným
 * slovem, takže se offsety posunou o uříznutý začátek a výpustku. Entita, která končí před
 * ukázkou, vypadne; spoiler zasahující přes začátek se zkrátí (emoji v něm má zůstat skryté),
 * useknuté vlastní emoji vypadne — kreslit půlku obrázku nejde.
 */
internal fun snippetOf(text: String, query: String, entities: List<MessageEntity> = emptyList()): Snippet {
    val first = highlightRanges(text, query).firstOrNull()?.first
    if (first == null || first <= SNIPPET_CONTEXT) return Snippet(text, entities)
    val space = text.lastIndexOf(' ', first - SNIPPET_CONTEXT)
    val start = if (space in 0 until first) space + 1 else first - SNIPPET_CONTEXT
    val shift = ELLIPSIS.length - start
    val shifted = entities.mapNotNull { entity ->
        val end = entity.offset + entity.length
        when {
            end <= start -> null
            entity.offset >= start -> entity.copy(offset = entity.offset + shift)
            entity.type == MessageEntityType.Spoiler -> entity.copy(offset = ELLIPSIS.length, length = end - start)
            else -> null
        }
    }
    return Snippet(ELLIPSIS + text.substring(start), shifted)
}

private const val ELLIPSIS = "…"

/** Kolik znaků před nalezeným slovem zůstane ve výsledku vidět. */
private const val SNIPPET_CONTEXT = 24

/** Výměna nápovědy pilulky: stará zmizí, nová se objeví až potom (stejná čísla jako fade through obrazovek). */
private const val HINT_FADE_OUT_MS = 90
private const val HINT_FADE_IN_MS = 210

/** Věta, ne jméno: aspoň tři slova. Podle toho se nabídne „Hledat s AI". */
internal fun naturalSentence(text: String): Boolean =
    text.trim().split(Regex("\\s+")).count { it.isNotEmpty() } >= NATURAL_SEARCH_MIN_WORDS

private const val NATURAL_SEARCH_MIN_WORDS = 3

/** Ikona „Hledat s AI"; během čtení modelem se místo ní točí indikátor. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AiSearchButton(busy: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !busy, shapes = IconButtonDefaults.shapes()) {
        if (busy) {
            LoadingIndicator(Modifier.size(24.dp))
        } else {
            Icon(
                painterResource(Res.drawable.auto_awesome_24dp),
                contentDescription = stringResource(Res.string.chats_search_ai),
            )
        }
    }
}
