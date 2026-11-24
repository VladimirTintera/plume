package eu.tintera.plume.features.chats

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace
import eu.tintera.plume.core.telegram.opensAsTopics
import eu.tintera.plume.core.adaptive.WindowControlsSpace
import eu.tintera.plume.core.ui.TextFieldSlot
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.features.report.ReportHost
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_search
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.search_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinInternalApi

/** Kam vede hledání: zavření a otevření výsledku (shell, `ChatSearchRoute`). */
@Immutable
internal data class ChatSearchNavigation(
    val onClose: () -> Unit,
    /**
     * Chat z výsledků. [slot] = účet chatu při hledání přes víc účtů (`ChatSummary.slot`), jinak
     * `null` (účet hledání); `forum` = skupina s tématy, otevře se seznam témat.
     */
    val onChatClick: (chatId: Long, slot: AccountSlot?, forum: Boolean) -> Unit,
    val onMessageClick: (chatId: Long, messageId: Long, topicId: Int?, slot: AccountSlot?) -> Unit,
)

/**
 * Hledání v seznamu chatů jako vlastní obrazovka v panelu seznamu (`docs/navrh-hledani.md`):
 * nahoře pole na místě pilulky ze seznamu chatů, pod ním filtry a výsledky na celou výšku
 * panelu ([SearchResults] — tytéž, jaké dřív ukazoval popup). Na široké ploše zůstane konverzace
 * vpravo a výsledek se otevře vedle; hledání se tím nezavře, takže jde projít další výsledek.
 *
 * - **Stav je ViewModelu položky** ([ChatSearchViewModel]): se zavřením hledání zmizí dotaz
 *   i filtr, jako dřív se sbalením pilulky.
 * - **Režim Všechny účty** hledá ve všech účtech (ViewModel z kontejneru každého účtu,
 *   výsledky slévá `mergedSearchState`); návrhy pod prázdným dotazem jsou z účtu hledání. Účet
 *   hledání jde první: veřejný chat nalezený víc účty se otevře v něm.
 * - **Zavření**: šipka v poli a Esc v poli odeberou jen tuhle položku zásobníku (konverzace
 *   vpravo zůstane). Systémové zpět jde po zásobníku; klávesnici schová systém napřed sám.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, KoinInternalApi::class)
@Composable
internal fun ChatSearchScreen(
    slot: AccountSlot,
    /** Dotaz zvenku (hashtag z konverzace); prázdný = návrhy. */
    initialQuery: String,
    navigation: ChatSearchNavigation,
    modifier: Modifier = Modifier,
) {
    // Klíč s účtem: v režimu Všechny účty bere obrazovka ViewModely i ostatních účtů ze stejného
    // úložiště, a ten účtu hledání musí být tentýž (návrhy, sponzorované chaty).
    val viewModel: ChatSearchViewModel = koinViewModel(key = searchViewModelKey(slot))
    val switcher = rememberAccountSwitcher()
    val sources: List<Pair<AccountSlot, ChatSearchViewModel>> = if (switcher.allAccounts) {
        switcher.accounts.map { entry ->
            val accountSlot = entry.account.slot
            key(accountSlot) {
                accountSlot to if (accountSlot == slot) {
                    viewModel
                } else {
                    koinViewModel(
                        key = searchViewModelKey(accountSlot),
                        scope = entry.account.koin.scopeRegistry.rootScope,
                    )
                }
            }
        }
    } else {
        emptyList()
    }

    val screenSearch by viewModel.search.state.collectAsStateWithLifecycle()
    val searchSources = sources.map { (accountSlot, source) ->
        key(accountSlot) { accountSlot to source.search.state.collectAsStateWithLifecycle().value }
    }.sortedByDescending { it.first == slot }
    val savedMessagesBySlot = sources.mapNotNull { (accountSlot, source) ->
        key(accountSlot) { source.savedMessagesChatId.collectAsStateWithLifecycle().value }?.let { accountSlot to it }
    }.toMap()
    val search = if (sources.isEmpty()) screenSearch else mergedSearchState(screenSearch, searchSources, savedMessagesBySlot)
    val savedMessagesChatId by viewModel.savedMessagesChatId.collectAsStateWithLifecycle()
    val naturalSearchAvailable by viewModel.naturalSearchAvailable.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val bookmarkTitles by viewModel.foundChatTitles.collectAsStateWithLifecycle()
    val describedPhotos by viewModel.describedPhotos.collectAsStateWithLifecycle()

    // Přes víc účtů hledají hledání všech účtů; návrhy jsou z účtu hledání.
    val searchers = sources.map { it.second }.ifEmpty { listOf(viewModel) }
    val controls = remember(searchers) {
        ChatSearchControls(
            onQueryChange = { text -> searchers.forEach { it.search.setQuery(text) } },
            onExpandedChange = viewModel.search::setSuggestionsWanted,
            onFilterChange = { filter -> searchers.forEach { it.search.setFilter(filter) } },
            onLoadMoreMessages = { searchers.forEach { it.search.loadMoreMessages() } },
        )
    }
    // Návrhy (nejčastější, historie, doporučené kanály) se načítají jen při otevřeném hledání.
    DisposableEffect(viewModel) {
        viewModel.search.setSuggestionsWanted(true)
        onDispose { viewModel.search.setSuggestionsWanted(false) }
    }

    val textFieldState = rememberTextFieldState(initialQuery)
    val onQueryChangeLatest by rememberUpdatedState(controls.onQueryChange)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { onQueryChangeLatest(it) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    // Nahlášení sponzorovaného chatu z výsledků.
    ReportHost(viewModel.sponsoredChatReportFlow, snackbarHostState)

    val focusRequester = remember { FocusRequester() }
    /*
     * Kdo otevřel hledání, chce psát (na telefonu vyjede klávesnice) — ale jen při otevření.
     * Na telefonu konverzace otevřená z výsledků hledání překryje a po návratu se obrazovka skládá
     * znovu; bez příznaku vyjela klávesnice znovu přes výsledky, ke kterým se uživatel vracel.
     * `rememberSaveable`, protože obyčejný `remember` návrat nepřežije.
     */
    var focusedOnOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnOpen) {
            focusedOnOpen = true
            focusRequester.requestFocus()
        }
    }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Model zrovna čte dotaz; ikona se zatím točí a druhé ťuknutí nic nedělá.
    var aiBusy by remember { mutableStateOf(false) }
    val aiSearch = viewModel::parseSearch.takeIf { naturalSearchAvailable }
    val colors = SearchBarDefaults.appBarWithSearchColors().searchBarColors

    /*
     * Pole roste z pilulky (`SearchMorph`): začne v její poloze a šířce, roztáhne se na celou
     * šířku a výsledky se prolnou. Zavření šipkou nebo Esc přehraje totéž pozpátku a seznam
     * chatů se pak objeví. Bez pilulky (otevřeno jinak, než ze seznamu vidět) rovnou celé.
     */
    val morph = LocalSearchMorph.current
    val start = remember { morph?.pill }
    val progress = remember { Animatable(if (start != null) 0f else 1f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fastSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(Unit) { progress.animateTo(1f, spatial) }
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (start != null) progress.animateTo(0f, fastSpatial)
                morph?.markReturning()
                navigation.onClose()
            }
        }
    }
    // Kde pole stojí doma (souřadnice kořene); posun z pilulky se počítá proti tomu.
    var home by remember { mutableStateOf<Rect?>(null) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        /*
         * Pole na místě pilulky ze seznamu chatů: stejný tvar, barva i výška, 4 dp od horního
         * kraje lišty a 16 dp od boků, na hraně bloků s výsledky. Na desktopu leží v řádku
         * s tlačítky okna a nechává jim místo.
         */
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            WindowControlsSpace()
            Box(
                Modifier
                    .weight(1f)
                    .onGloballyPositioned { coordinates -> home = Rect(coordinates.positionInRoot(), coordinates.size.toSize()) },
            ) {
            Surface(
                shape = SearchBarDefaults.inputFieldShape,
                color = colors.containerColor,
                modifier = Modifier.growFrom(start, { home }, { progress.value }),
            ) {
                SearchBarDefaults.InputField(
                    state = textFieldState,
                    onSearch = {},
                    expanded = true,
                    onExpandedChange = {},
                    colors = colors.inputFieldColors,
                    placeholder = { Text(stringResource(Res.string.chats_search)) },
                    leadingIcon = {
                        TextFieldSlot {
                            // Lupa z pilulky se během růstu prolne do šipky zpět (a při zavření zpátky).
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(Res.drawable.search_24dp),
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer { alpha = 1f - progress.value.coerceIn(0f, 1f) },
                                )
                                IconButton(
                                    onClick = close,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
                                ) {
                                    Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                                }
                            }
                        }
                    },
                    trailingIcon = {
                        if (textFieldState.text.isNotEmpty()) {
                            TextFieldSlot {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // „Hledat s AI" jen u věty, ne u jména chatu: jedno dvě slova
                                    // se hledají tak, jak jsou, a ikona by u nich jen překážela.
                                    if (aiSearch != null && (aiBusy || naturalSentence(textFieldState.text.toString()))) {
                                        AiSearchButton(
                                            busy = aiBusy,
                                            onClick = {
                                                val query = textFieldState.text.toString()
                                                aiBusy = true
                                                scope.launch {
                                                    val found = try {
                                                        aiSearch(query)
                                                    } finally {
                                                        aiBusy = false
                                                    }
                                                    // Dotaz se mezitím změnil — výsledek patří k jinému.
                                                    if (textFieldState.text.toString() != query) return@launch
                                                    if (found == null) {
                                                        haptics.reject()
                                                    } else {
                                                        // Napřed filtr, pak text: hledání se pustí jednou,
                                                        // s obojím (`ChatSearch` sbírá nejnovější).
                                                        controls.onFilterChange(found.filter)
                                                        textFieldState.setTextAndPlaceCursorAtEnd(found.text)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    IconButton(onClick = { textFieldState.clearText() }, shapes = IconButtonDefaults.shapes()) {
                                        Icon(
                                            painterResource(Res.drawable.close_24dp),
                                            contentDescription = stringResource(Res.string.chats_search_clear),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        // Esc v poli zavře hledání, ne konverzaci vedle (zpět navigace jde shora).
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                                close()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            }
            WindowControlsEndSpace()
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Výsledky se objeví, až pole doroste (fade through), a mírně přijedou zdola.
                .graphicsLayer {
                    val p = progress.value.coerceAtMost(1f)
                    alpha = ((p - CONTENT_FADE_START) / (1f - CONTENT_FADE_START)).coerceIn(0f, 1f)
                    translationY = (1f - p) * CONTENT_SHIFT.toPx()
                },
        ) {
            SearchResults(
                query = textFieldState.text.toString(),
                search = search,
                savedMessagesChatId = savedMessagesChatId,
                clicks = SearchResultClicks(
                    onPick = { chatId, chatSlot ->
                        // Výsledky jsou položky seznamu, takže vědí, jestli se chat otevírá tématy.
                        val forum = with(search) { results + top + recent + global + sponsored.map { it.chat } }
                            .any { it.id == chatId && it.slot == chatSlot && it.opensAsTopics }
                        // Historie hledání patří účtu chatu.
                        val owner = sources.firstOrNull { it.first == chatSlot }?.second ?: viewModel
                        owner.search.recordFound(chatId)
                        navigation.onChatClick(chatId, chatSlot, forum)
                    },
                    onMessageClick = navigation.onMessageClick,
                    // Hledaný hashtag z historie: hledá se znovu, jako by ho uživatel napsal.
                    onPickTag = { tag -> textFieldState.setTextAndPlaceCursorAtEnd(tag) },
                    onPickSponsored = { ad ->
                        // Otevření se hlásí dřív, než se chat otevře (specifikace reklam).
                        viewModel.sponsoredChatOpened(ad.uniqueId)
                        navigation.onChatClick(ad.chat.id, null, ad.chat.opensAsTopics)
                    },
                ),
                controls = controls,
                suggestionActions = remember(viewModel) {
                    SuggestionActions(
                        onClear = viewModel.search::clearRecent,
                        onRemoveRecent = viewModel.search::removeRecent,
                        onRemoveTop = viewModel.search::removeTop,
                        onRemoveTag = viewModel.search::removeTag,
                        onClearTags = viewModel.search::clearTags,
                    )
                },
                sponsoredActions = remember(viewModel) {
                    SponsoredActions(
                        onSeen = viewModel::sponsoredChatSeen,
                        onOpen = viewModel::sponsoredChatOpened,
                        onReport = viewModel::reportSponsoredChat,
                    )
                },
                localSources = LocalSearchSources(
                    bookmarks = bookmarks,
                    chatTitles = bookmarkTitles,
                    describedPhotos = describedPhotos,
                ),
            )
            SnackbarHost(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter).padding(bottom = resultsPadding().calculateBottomPadding()),
            )
        }
    }
}

/**
 * Pole, které vyrůstá z [start] (pilulka) do své polohy [home] podle [progress] (0 = pilulka,
 * 1 = doma; pružina smí přestřelit). Pole je rozložené rovnou na plnou šířku a odkrývá se
 * ořezem ve tvaru pilulky, která roste — s měněnou šířkou rozložení se nápověda v úzkém poli
 * zalamovala na dva řádky. Poloha jde posunem ve vrstvě. Bez pilulky, nebo dokud se pole
 * nerozložilo, nedělá nic.
 */
private fun Modifier.growFrom(start: Rect?, home: () -> Rect?, progress: () -> Float): Modifier =
    if (start == null) {
        this
    } else {
        graphicsLayer {
            val end = home() ?: return@graphicsLayer
            val p = progress()
            if (p == 1f) return@graphicsLayer
            translationX = (start.left - end.left) * (1f - p)
            translationY = (start.top - end.top) * (1f - p)
            val width = (start.width + (size.width - start.width) * p).coerceIn(0f, size.width)
            clip = true
            shape = GenericShape { bounds, _ ->
                addRoundRect(RoundRect(0f, 0f, width, bounds.height, CornerRadius(bounds.height / 2)))
            }
        }
    }

/** Od jakého průběhu růstu pole se začnou objevovat výsledky. */
private const val CONTENT_FADE_START = 0.35f

/** O kolik výsledky přijedou zdola. */
private val CONTENT_SHIFT = 24.dp

/** Klíč ViewModelu hledání účtu [slot] v úložišti položky hledání. */
private fun searchViewModelKey(slot: AccountSlot): String = "chat-search-${slot.id}"
