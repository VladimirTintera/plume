package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.drawBehind
import eu.tintera.plume.core.ui.animateMorph
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.MorphShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.telegram.EmojiRepository
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StickerCategory
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerQuery
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSearchResult
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.telegram.StickerSetSample
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.StickerPlayback
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.emojiClusterCount
import eu.tintera.plume.core.ui.tinted
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.stickers_preview
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.star_24dp
import eu.tintera.plume.resources.conversation_send_when_online
import eu.tintera.plume.resources.conversation_send_schedule
import eu.tintera.plume.resources.conversation_send_silent
import eu.tintera.plume.resources.conversation_send_options
import eu.tintera.plume.resources.online_prediction_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.schedule_send_24dp
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.resources.stickers_add
import eu.tintera.plume.resources.stickers_added
import eu.tintera.plume.resources.stickers_clear_search
import eu.tintera.plume.resources.stickers_favorites_empty
import eu.tintera.plume.resources.stickers_none_found
import eu.tintera.plume.resources.stickers_recent_empty
import eu.tintera.plume.resources.stickers_search
import eu.tintera.plume.resources.stickers_section_installed
import eu.tintera.plume.resources.stickers_section_public
import eu.tintera.plume.resources.stickers_section_sets
import eu.tintera.plume.resources.stickers_sets_show_all
import eu.tintera.plume.resources.stickers_set_empty
import eu.tintera.plume.resources.stickers_tab_emoji
import eu.tintera.plume.resources.stickers_tab_favorites
import eu.tintera.plume.resources.stickers_tab_gifs
import eu.tintera.plume.resources.stickers_tab_recent
import eu.tintera.plume.resources.stickers_tab_stickers
import eu.tintera.plume.resources.stickers_tab_trending
import eu.tintera.plume.resources.stickers_trending_failed
import eu.tintera.plume.resources.trending_up_24dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Záložka výběru samolepek. */
sealed interface StickerTab {
    data object Recent : StickerTab
    data object Favorites : StickerTab
    /** Populární sady k přidání. Nemá mřížku samolepek, ale seznam sad. */
    data object Trending : StickerTab
    data class Set(val summary: StickerSetSummary) : StickerTab
}

data class StickerPickerState(
    val tabs: List<StickerTab> = FIXED_TABS,
    /** `null`, dokud se nenačetly. */
    val recent: List<StickerItem>? = null,
    val favorites: List<StickerItem>? = null,
    /** Obsah už otevřených sad podle id. Chybějící sada se teprve načítá. */
    val sets: Map<Long, List<StickerItem>> = emptyMap(),
    /** Seznam záložek je úplný — dorazily i nainstalované sady. */
    val loaded: Boolean = false,
) {
    /** Samolepky záložky; `null`, dokud se její obsah načítá. */
    fun stickersOf(tab: StickerTab): List<StickerItem>? = when (tab) {
        StickerTab.Recent -> recent
        StickerTab.Favorites -> favorites
        // Trendy mají vlastní stránku se seznamem sad, mřížku samolepek nemají.
        StickerTab.Trending -> null
        is StickerTab.Set -> sets[tab.summary.id]
    }
}

/**
 * Záložky, které jsou vždycky, před nainstalovanými sadami. Trendy hned za oblíbenými: na konci
 * za desítkami sad by je nikdo nenašel.
 */
private val FIXED_TABS = listOf(StickerTab.Recent, StickerTab.Favorites, StickerTab.Trending)

/**
 * Stav výběru samolepek.
 *
 * Vlastní ViewModel, ne kus `ConversationViewModel`: výběr je pro všechny chaty stejný a konverzace
 * z něj potřebuje jen výsledek — ťuknutou samolepku.
 *
 * Drží obsah **všech** záložek, ne jen vybrané: při swipu mezi záložkami je vidět i sousední stránka.
 * Obsah sady se ale načítá **až když se její stránka poprvé ukáže** ([load]) a drží se po dobu
 * života ViewModelu. Sad bývá desítky a načíst je všechny kvůli otevření výběru by bylo zbytečné.
 */
class StickerPickerViewModel(
    private val repository: StickerRepository,
    private val emojiRepository: EmojiRepository,
    private val settings: AppSettings,
) : ViewModel() {

    /** Nedávno vybraná emoji, jen ta z katalogu (uložené mohlo zůstat z jiné verze aplikace). */
    val recentEmojis: StateFlow<List<String>> = settings.recentEmojis
        .map { list -> list.mapNotNull(::catalogEmoji) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settings.recentEmojis.value.mapNotNull(::catalogEmoji),
        )

    fun recordEmoji(emoji: String) = settings.recordRecentEmoji(emoji)

    /** Zvolené odstíny pleti: základ → varianta. */
    val skinTones: StateFlow<Map<String, String>> = settings.emojiSkinTones

    /** Zapamatuje odstín pro emoji; výchozí podoba (základ) zvolený odstín zruší. */
    fun setSkinTone(base: String, variant: String) = settings.setEmojiSkinTone(base, variant.takeIf { it != base })

    private val setContents = MutableStateFlow<Map<Long, List<StickerItem>>>(emptyMap())

    /** Sady, jejichž načítání už běží — aby dvě stránky nenačítaly totéž dvakrát. */
    private val loading = mutableSetOf<Long>()

    private val searchRequest = MutableStateFlow<StickerSearchRequest>(StickerSearchRequest.Typed("", emptyList()))

    /**
     * Výsledek hledání.
     *
     * Hledá se až po krátké pauze v psaní, ne na každý znak — veřejné hledání jde na server.
     * `transformLatest` rozepsané hledání zahodí, jakmile se text změní, takže pomalá odpověď na
     * „ko" nepřepíše výsledky pro „kočka". Kategorie se hledá hned: ťuknutí je hotový dotaz.
     */
    val search: StateFlow<StickerSearchState> = searchRequest
        .debounce { if (it is StickerSearchRequest.Typed && it.text.isNotBlank()) SEARCH_DEBOUNCE_MS else 0L }
        .distinctUntilChanged()
        .transformLatest { request ->
            val query = request.toQuery()
            if (query == null) {
                emit(StickerSearchState.Idle)
                return@transformLatest
            }
            emit(StickerSearchState.Searching)
            emit(StickerSearchState.Results(repository.search(query)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StickerSearchState.Idle)

    /**
     * Kategorie pod polem hledání. Jednou za život ViewModelu: TDLib je drží v lokální databázi
     * a mění se jen s novou sadou kategorií od Telegramu, ne během jednoho otevření výběru.
     */
    val categories: StateFlow<List<StickerCategory>> = flow { emit(repository.categories()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Hledání podle kategorie. Text v poli tím zmizí — obojí naráz se hledat nedá. */
    fun searchCategory(category: StickerCategory) {
        searchRequest.value = StickerSearchRequest.Category(category.emojis)
    }

    private val emojiRequest = MutableStateFlow(SearchRequest("", emptyList()))

    /**
     * Hledání emoji podle slov. Stejný debounce a `transformLatest` jako u samolepek. Výsledky se
     * zúží na emoji z katalogu — TDLib zná i novější, které by systémové písmo neumělo nakreslit.
     */
    val emojiSearch: StateFlow<EmojiSearchState> = emojiRequest
        .debounce { if (it.text.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .transformLatest { request ->
            val text = request.text.trim()
            if (text.isEmpty()) {
                emit(EmojiSearchState.Idle)
                return@transformLatest
            }
            emit(EmojiSearchState.Searching)
            val found = emojiRepository.searchEmojis(text, request.languageCodes).mapNotNull(::catalogEmoji)
            emit(EmojiSearchState.Results(found.distinct()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EmojiSearchState.Idle)

    fun searchEmoji(text: String, languageCodes: List<String>) {
        emojiRequest.value = SearchRequest(text, languageCodes)
    }

    /** Text hledání. Jazyk zařízení určuje, jak se slova převedou na emoji. */
    fun search(text: String, languageCodes: List<String>) {
        searchRequest.value = StickerSearchRequest.Typed(text, languageCodes)
    }

    val state: StateFlow<StickerPickerState> = combine(
        repository.installedSets(),
        repository.recentStickers(),
        repository.favoriteStickers(),
        setContents,
    ) { sets, recent, favorites, contents ->
        StickerPickerState(
            tabs = FIXED_TABS + sets.map { StickerTab.Set(it) },
            recent = recent,
            favorites = favorites,
            sets = contents,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StickerPickerState())

    /**
     * Přidá samolepku do oblíbených, nebo ji odebere. Stav se lokálně nepřepisuje — potvrzení
     * dorazí updatem a přepočítá záložku Oblíbené i hvězdičku.
     */
    fun toggleFavorite(sticker: StickerItem) {
        val favorite = state.value.favorites.orEmpty().any { it.sendFileId == sticker.sendFileId }
        viewModelScope.launch { repository.setFavorite(sticker.sendFileId, !favorite) }
    }

    /**
     * Trendy. Vlastní tok s `WhileSubscribed`, mimo [state]: jdou na server, takže se načtou,
     * až když si je někdo opravdu otevře — ne při každém otevření výběru.
     */
    val trending: StateFlow<List<StickerSetSample>?> = repository.trendingSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun installSet(setId: Long) {
        viewModelScope.launch { repository.setInstalled(setId, installed = true) }
    }

    /** Trendy byly vidět — Telegram je přestane hlásit jako nové. */
    fun markTrendingViewed(sets: List<StickerSetSample>) {
        viewModelScope.launch { repository.markTrendingViewed(sets.map { it.id }) }
    }

    /** Záložka, na které byl uživatel naposledy; čte se jednou, při otevření výběru. */
    val lastTabKey: String? get() = settings.lastStickerTab.value

    /** Uživatel dosedl na záložku — příště se výběr otevře na ní. */
    fun rememberTab(key: String) {
        if (key != settings.lastStickerTab.value) settings.setLastStickerTab(key)
    }

    /** Stránka záložky se ukázala. Nedávné a oblíbené jsou živé toky, načítá se jen sada. */
    fun load(tab: StickerTab) {
        if (tab !is StickerTab.Set) return

        val setId = tab.summary.id
        if (setId in setContents.value || !loading.add(setId)) return

        viewModelScope.launch {
            // Neúspěch se ukáže jako prázdná sada — výběr kvůli jedné sadě nezavíráme chybou.
            val stickers = repository.stickerSet(setId).getOrDefault(emptyList())
            setContents.update { it + (setId to stickers) }
            loading.remove(setId)
        }
    }

    private data class SearchRequest(val text: String, val languageCodes: List<String>)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Pauza v psaní, po které se hledá. */
        const val SEARCH_DEBOUNCE_MS = 300L

    }
}

/** Co se v samolepkách hledá: napsaný text, nebo ťuknutá kategorie. */
internal sealed interface StickerSearchRequest {
    data class Typed(val text: String, val languageCodes: List<String>) : StickerSearchRequest
    data class Category(val emojis: String) : StickerSearchRequest
}

/**
 * Dotaz pro repository, nebo `null`, když se nehledá. Napsaná emoji se hledají jako emoji,
 * cokoli jiného jako slovo (a tím i jako název sady).
 */
internal fun StickerSearchRequest.toQuery(): StickerQuery? = when (this) {
    is StickerSearchRequest.Category -> StickerQuery.Emoji(emojis).takeIf { emojis.isNotBlank() }
    is StickerSearchRequest.Typed -> {
        val trimmed = text.trim()
        when {
            trimmed.isEmpty() -> null
            trimmed.emojiClusterCount(MAX_SEARCH_EMOJI) > 0 -> StickerQuery.Emoji(trimmed)
            else -> StickerQuery.Text(trimmed, languageCodes)
        }
    }
}

/** Kolik emoji v hledání se ještě bere jako hledání podle emoji. */
private const val MAX_SEARCH_EMOJI = 10

sealed interface EmojiSearchState {
    data object Idle : EmojiSearchState
    data object Searching : EmojiSearchState
    data class Results(val emojis: List<String>) : EmojiSearchState
}

sealed interface StickerSearchState {
    /** Nehledá se — výběr ukazuje záložky. */
    data object Idle : StickerSearchState
    data object Searching : StickerSearchState
    data class Results(val result: StickerSearchResult) : StickerSearchState
}

/**
 * Výběr samolepek v bottom sheetu.
 *
 * Sheet, ne panel místo klávesnice: výměna klávesnice za panel bez poskočení vstupního pole je
 * v Compose nejkřehčí část celé věci, a sheet ji nepotřebuje. Klávesnice se před otevřením zavře.
 *
 * Záložky jsou stránky [HorizontalPager]u: mezi sadami se dá **swipovat do stran**. Řádek záložek
 * a pager sdílejí jeden stav — swipe přepne záložku, ťuknutí na záložku posune pager.
 *
 * Stránky mají **pevnou výšku**, aby se sheet při přepínání nenatahoval a nesmršťoval podle počtu
 * samolepek. Samolepky v nich **nehrají** (viz [StickerPreview]).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun StickerPickerSheet(
    mode: ExpressionMode,
    onModeChange: (ExpressionMode) -> Unit,
    /**
     * `false` při úpravě zprávy: výběr ukáže jen emoji a přepínač schová. Uložená volba ([mode])
     * se tím nemění — po úpravě se výběr otevře zase tam, kde byl naposledy.
     */
    stickersAvailable: Boolean,
    /** Rozepsaný text, do kterého emoji vkládá. */
    draft: TextFieldValue,
    onDraftEdit: (TextFieldValue) -> Unit,
    onPick: (StickerItem) -> Unit,
    /** Chat, do kterého se hledá — GIF bot to chce vědět. */
    chatId: Long,
    onPickGif: (GifItem) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Části, mezi kterými jde přepínat. Formulář ankety chce jen samolepku, nebo jen GIF —
     * s jedinou částí se přepínač nekreslí.
     */
    modes: List<ExpressionMode> = ExpressionMode.entries,
    /** Volby odeslání u podržené samolepky a GIFu; `null` = jde jen poslat ťuknutím. */
    sendMenu: SendMenuOffer? = null,
    onPickWith: (StickerItem, SendMenuChoice) -> Unit = { _, _ -> },
    onPickGifWith: (GifItem, SendMenuChoice) -> Unit = { _, _ -> },
    /** Hlavní tlačítko náhledu podržené samolepky. Formulář ankety samolepku nevysílá, vybírá. */
    pickLabel: StringResource = Res.string.conversation_send,
) {
    val viewModel: StickerPickerViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var searchText by rememberSaveable { mutableStateOf("") }
    /** Ťuknutá kategorie (podle názvu); s napsaným textem se vylučuje. */
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Jazyk zařízení a angličtina jako záloha: klíčová slova samolepek bývají anglicky.
    val languageCodes = remember { listOf(Locale.current.language, "en").distinct() }
    /*
     * Rovnou celý, bez napůl rozbaleného stavu. Obsah je vyšší než polovina obrazovky, takže
     * napůl rozbalený sheet schoval spodek pod okraj — tlačítko skončilo pod navigační lištou.
     */
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    /*
     * Samolepka otevřená podržením ve velkém náhledu (s hvězdičkou oblíbených a volbami odeslání).
     * Její buňka je pod náhledem podbarvená, ať je vidět, odkud přišla. Rotaci nepřežije — je to
     * chvilkové prohlédnutí, ne stav výběru.
     */
    var previewSticker by remember { mutableStateOf<StickerItem?>(null) }

    /** Sada otevřená z trendů. Sheet nad sheetem: výběr pod ní zůstává, zpátky se vrací zavřením. */
    var openSetId by rememberSaveable { mutableStateOf<Long?>(null) }

    val pick: (StickerItem) -> Unit = { sticker ->
        // Nejdřív sheet schovat, pak poslat: zmizení bez animace by vypadalo jako pád,
        // a zpráva se tak objeví až nad zavřeným sheetem.
        scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(sticker) }
    }
    val pickWith: (StickerItem, SendMenuChoice) -> Unit = { sticker, choice ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { onPickWith(sticker, choice) }
    }
    val favoriteIds = state.favorites.orEmpty().map { it.sendFileId }.toSet()
    val toggleFavorite: (StickerItem) -> Unit = { sticker ->
        haptics.toggle(on = sticker.sendFileId !in favoriteIds)
        viewModel.toggleFavorite(sticker)
    }
    // Podržení haptiku NEPOTŘEBUJE: `combinedClickable` ji u dlouhého stisku dělá samo
    // a ruční volání navíc ji zdvojí (změřeno: dvě vibrace 9 ms po sobě).
    val cells = StickerCells(
        activeStickerId = previewSticker?.id,
        favoriteFileIds = favoriteIds,
        onPick = pick,
        onPreview = { sticker -> previewSticker = sticker },
        onToggleFavorite = toggleFavorite,
        sendMenu = sendMenu,
        onPickWith = pickWith,
    )

    TrendingSetSheetHost(setId = openSetId, onClose = { openSetId = null }, pick = pick)
    StickerPreviewHost(
        sticker = previewSticker,
        cells = cells,
        pickLabel = stringResource(pickLabel),
        onClose = { previewSticker = null },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { expressionSheetInsets() },
    ) {
        /*
         * Emoji a samolepky jsou dvě části JEDNOHO sheetu, ne dva sheety: obojí se otevírá týmž
         * tlačítkem a patří k témuž psaní. Přepínač jsou segmentová tlačítka — dvě rovnocenné
         * volby, ne záložky obsahu (ty má každá část vlastní).
         */
        if (stickersAvailable && modes.size > 1) SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            modes.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { onModeChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                ) {
                    Text(
                        when (entry) {
                            ExpressionMode.Emoji -> stringResource(Res.string.stickers_tab_emoji)
                            ExpressionMode.Stickers -> stringResource(Res.string.stickers_tab_stickers)
                            ExpressionMode.Gifs -> stringResource(Res.string.stickers_tab_gifs)
                        },
                    )
                }
            }
        }

        if (mode == ExpressionMode.Emoji || !stickersAvailable) {
            EmojiPanel(
                viewModel = viewModel,
                draft = draft,
                onDraftEdit = onDraftEdit,
                languageCodes = languageCodes,
            )
            return@ModalBottomSheet
        }

        if (mode == ExpressionMode.Gifs) {
            GifPanel(
                chatId = chatId,
                // Jako u samolepky: nejdřív sheet schovat, pak poslat.
                onPick = { gif -> scope.launch { sheetState.hide() }.invokeOnCompletion { onPickGif(gif) } },
                sendMenu = sendMenu,
                onPickWith = { gif, choice ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onPickGifWith(gif, choice) }
                },
            )
            return@ModalBottomSheet
        }

        StickerSearchField(
            text = searchText,
            placeholder = stringResource(Res.string.stickers_search),
            onTextChange = {
                searchText = it
                // Psaní kategorii zruší: hledá se buď text, nebo kategorie.
                categoryName = null
                viewModel.search(it, languageCodes)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        StickerCategoryRow(
            categories = categories,
            selectedName = categoryName,
            onSelect = { category ->
                // Kategorie je hledání bez psaní: klávesnice by jen překryla výsledky.
                keyboard?.hide()
                focusManager.clearFocus()
                searchText = ""
                if (category.name == categoryName) {
                    categoryName = null
                    viewModel.search("", languageCodes)
                } else {
                    categoryName = category.name
                    viewModel.searchCategory(category)
                }
            },
        )

        /*
         * Hledání nahradí záložky i stránky, ale ve STEJNĚ vysoké ploše — jinak by sheet při
         * prvním písmenu poskočil. Výsledky se ukazují podle textu v poli, ne podle stavu
         * hledání: po smazání textu se záložky vrátí hned, bez čekání na debounce.
         */
        if (searchText.isNotBlank() || categoryName != null) {
            val installedSetIds = state.tabs.mapNotNullTo(HashSet()) { (it as? StickerTab.Set)?.summary?.id }
            Box(Modifier.fillMaxWidth().height(STICKER_TAB_ROW_HEIGHT + STICKER_PICKER_HEIGHT + sheetBottomInset())) {
                StickerSearchResults(
                    search = search,
                    // Výsledky jsou jediná mřížka na obrazovce, hrají celé.
                    cells = cells.copy(playing = true),
                    installedSetIds = installedSetIds,
                    onOpenSet = { openSetId = it },
                    onInstallSet = viewModel::installSet,
                )
            }
            return@ModalBottomSheet
        }

        StickerTabsPager(
            state = state,
            viewModel = viewModel,
            cells = cells,
            onOpenSet = { openSetId = it },
        )
    }
}

/**
 * Řádek záložek a pod ním jejich stránky.
 *
 * Otevře se na záložce, na které byl uživatel naposledy ([AppSettings.lastStickerTab]) — kdo
 * posílá z jedné sady, nemusí k ní pokaždé dojíždět. `PagerState` proto vzniká až se seznamem
 * sad: s prvním stavem (jen Nedávné, Oblíbené, Trendy) by se výběr otevřel na Nedávných a pak
 * přeskočil na sadu. Čekání je dotaz do lokální databáze TDLib, mezitím drží místo indikátor
 * ve stejně vysoké ploše, takže sheet neposkočí.
 *
 * Pamatuje se USAZENÁ stránka, ne každá, přes kterou prst přejel. Sada, která mezitím zmizela,
 * se nenajde a výběr začne Nedávnými.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StickerTabsPager(
    state: StickerPickerState,
    viewModel: StickerPickerViewModel,
    cells: StickerCells,
    onOpenSet: (Long) -> Unit,
) {
    if (!state.loaded) {
        Box(
            Modifier.fillMaxWidth().height(STICKER_TAB_ROW_HEIGHT + STICKER_PICKER_HEIGHT + sheetBottomInset()),
            contentAlignment = Alignment.Center,
        ) { LoadingIndicator() }
        return
    }

    val scope = rememberCoroutineScope()
    val tabs by rememberUpdatedState(state.tabs)
    // Počet stránek se mění: sada přibude nebo zmizí i za otevřeného výběru. Proto lambda, ne číslo.
    val pagerState = rememberPagerState(
        initialPage = remember { state.tabs.indexOfStored(viewModel.lastTabKey).coerceAtLeast(0) },
        pageCount = { tabs.size },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { tabs.getOrNull(pagerState.settledPage)?.storageKey() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect(viewModel::rememberTab)
    }

        PrimaryScrollableTabRow(
            // `currentPage` se přepne v půlce swipu, takže indikátor jde s prstem, ne až po dojetí.
            selectedTabIndex = pagerState.currentPage.coerceIn(0, state.tabs.lastIndex),
            edgePadding = 8.dp,
            containerColor = Color.Transparent,
            modifier = Modifier.height(STICKER_TAB_ROW_HEIGHT),
        ) {
            state.tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == pagerState.currentPage,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    icon = { StickerTabIcon(tab) },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            // Klíč podle záložky, ne indexu: až dorazí sady, stránky nedávných a oblíbených zůstanou
            // tytéž a nerozsype se jim stav (pozice scrollu).
            key = { index -> state.tabs.getOrNull(index)?.storageKey() ?: index },
            modifier = Modifier.fillMaxWidth().height(STICKER_PICKER_HEIGHT + sheetBottomInset()),
        ) { page ->
            val tab = state.tabs.getOrNull(page) ?: return@HorizontalPager
            LaunchedEffect(tab) { viewModel.load(tab) }

            if (tab == StickerTab.Trending) {
                TrendingPage(
                    viewModel = viewModel,
                    onOpenSet = onOpenSet,
                )
                return@HorizontalPager
            }

            StickerPage(
                tab = tab,
                stickers = state.stickersOf(tab),
                cells = cells.copy(
                    /*
                     * Hraje jen USAZENÁ stránka. Během swipu jsou vidět dvě; přijíždějící sada by
                     * se uprostřed tahu rozbalovala a parsovala desítky animací a tah by zadrhával.
                     * Rozběhne se po dosednutí, z náhledu, který odpovídá prvnímu snímku.
                     */
                    playing = page == pagerState.settledPage,
                ),
            )
        }
}

/**
 * Samolepka poslaná z detailu sady otevřeného nad výběrem. Oba sheety se zavřou: detail sám,
 * výběr přes [pick] — ten ho schová animací a pak samolepku pošle.
 */
@Composable
private fun TrendingSetSheetHost(
    setId: Long?,
    onClose: () -> Unit,
    pick: (StickerItem) -> Unit,
) {
    setId ?: return
    StickerSetSheet(
        setId = setId,
        onPick = { sticker ->
            onClose()
            pick(sticker)
        },
        onDismiss = onClose,
    )
}

/**
 * Stabilní klíč záložky: klíč stránky pageru i hodnota pro [AppSettings.lastStickerTab]. Sada
 * podle id, ne podle pořadí — pořadí sad si uživatel mění a přidaná sada by ostatní posunula.
 */
internal fun StickerTab.storageKey(): String = when (this) {
    StickerTab.Recent -> "recent"
    StickerTab.Favorites -> "favorites"
    StickerTab.Trending -> "trending"
    is StickerTab.Set -> "set:${summary.id}"
}

/** Index záložky uložené pod [key], nebo -1 (nic uloženo, sada mezitím odebraná). */
internal fun List<StickerTab>.indexOfStored(key: String?): Int =
    if (key == null) -1 else indexOfFirst { it.storageKey() == key }

/** Obsah jedné záložky: načítání, prázdný stav, nebo mřížka. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StickerPage(
    tab: StickerTab,
    stickers: List<StickerItem>?,
    cells: StickerCells,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (stickers == null) {
            LoadingIndicator()
            return@Box
        }

        /*
         * Mřížka zůstává, i když je prázdná. Kdyby ji prázdný stav nahradil, zmizela by v okamžiku,
         * kdy z ní odejde poslední samolepka — a ta by nestihla odejít animací (typicky poslední
         * oblíbená odebraná hvězdičkou). Prázdný text se proto nad ní jen plynule objeví.
         */
        LazyVerticalGrid(
            columns = GridCells.Adaptive(STICKER_CELL),
            contentPadding = sheetListPadding(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(stickers, key = { it.id }) { sticker ->
                StickerCell(sticker, cells, animateStickerItem())
            }
        }

        AnimatedVisibility(
            visible = stickers.isEmpty(),
            enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Text(
                text = when (tab) {
                    StickerTab.Recent -> stringResource(Res.string.stickers_recent_empty)
                    StickerTab.Favorites -> stringResource(Res.string.stickers_favorites_empty)
                    StickerTab.Trending -> ""
                    is StickerTab.Set -> stringResource(Res.string.stickers_set_empty)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/**
 * Trendy: seznam populárních sad, u každé pár samolepek na ukázku a tlačítko Přidat.
 *
 * Ťuknutí na sadu otevře její detail (celá sada, posílání, přidání) — řádek ukazuje jen ochutnávku.
 * Trendy se načítají až tady, při prvním zobrazení stránky, a pak se označí za zhlédnuté.
 */
@Composable
private fun TrendingPage(
    viewModel: StickerPickerViewModel,
    onOpenSet: (Long) -> Unit,
) {
    val trending by viewModel.trending.collectAsStateWithLifecycle()

    LaunchedEffect(trending) {
        trending?.takeIf { it.isNotEmpty() }?.let(viewModel::markTrendingViewed)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val sets = trending
        when {
            sets == null -> LoadingIndicator()

            sets.isEmpty() -> Text(
                text = stringResource(Res.string.stickers_trending_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )

            else -> LazyColumn(
                contentPadding = sheetListPadding(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sets, key = { it.id }) { set ->
                    StickerSetRow(
                        set = set,
                        onOpen = { onOpenSet(set.id) },
                        onInstall = { viewModel.installSet(set.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerSetRow(
    set: StickerSetSample,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = set.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Přidaná sada ukazuje stav, ne mrtvé tlačítko. Odebírá se v detailu sady.
            if (set.isInstalled) {
                Text(
                    text = stringResource(Res.string.stickers_added),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FilledTonalButton(onClick = onInstall) { Text(stringResource(Res.string.stickers_add)) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            set.covers.take(TRENDING_COVERS).forEach { cover ->
                StickerPreview(cover, playing = false, modifier = Modifier.size(TRENDING_COVER_SIZE))
            }
        }
    }
}

/**
 * Co buňky mřížky (a náhled podržené samolepky) potřebují od sheetu. Balík místo pěti parametrů,
 * které by se jinak táhly přes stránku záložky i výsledky hledání.
 */
internal data class StickerCells(
    /** Samolepka otevřená v náhledu; její buňka je podbarvená. */
    val activeStickerId: Long?,
    val favoriteFileIds: Set<Int>,
    val onPick: (StickerItem) -> Unit,
    /** Podržení: otevře náhled ([StickerPreviewHost]). */
    val onPreview: (StickerItem) -> Unit,
    val onToggleFavorite: (StickerItem) -> Unit,
    /** Volby odeslání v náhledu podržené samolepky; `null` = tlačítko není (sada, anketa). */
    val sendMenu: SendMenuOffer? = null,
    val onPickWith: (StickerItem, SendMenuChoice) -> Unit = { _, _ -> },
    /**
     * Hrají všechny samolepky mřížky, ne jen podržená. Jen tam, kde je mřížka na obrazovce
     * jediná (jedna sada, oblíbené, výsledky hledání) — vidět je jich nejvýš pár desítek
     * a `LazyVerticalGrid` skládá jen viditelné buňky.
     */
    val playing: Boolean = false,
)

/**
 * Jedna samolepka v mřížce: ťuknutí pošle, podržení otevře velký náhled ([StickerPreviewDialog]).
 *
 * **Hvězdička oblíbených a volby odeslání** (bez zvuku, naplánovat, až bude online) jsou v náhledu,
 * ne v buňce: hvězdička u každé buňky by zaplevelila mřížku a ťuknutí vedle ní by samolepku
 * omylem poslalo. U textu jsou volby na podržení odesílacího tlačítka, jenže podržení samolepky
 * patří náhledu — druhé gesto nad ní by se s ním pralo.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun StickerCell(sticker: StickerItem, cells: StickerCells, modifier: Modifier = Modifier) {
    val active = sticker.id == cells.activeStickerId
    val highlight = animateMorph(active, "sticker-highlight")
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier
            .size(STICKER_CELL)
            /*
             * Podbarvení nese potvrzení podržení i u statické samolepky, která se rozhýbat nemá
             * jak. Rozvine se z malého kruhu do dvanáctistranné „sušenky" (`ShapeMorphs.Highlight`)
             * a zase se do něj stáhne — hravé „tuhle si prohlížíš", u samolepek hravost sedí.
             * Kreslí se až při kreslení, takže průběh nic nerekomponuje.
             */
            .drawBehind {
                val progress = highlight.coerceIn(0f, 1f)
                if (progress <= 0f) return@drawBehind
                val grow = HIGHLIGHT_START_SCALE + (1f - HIGHLIGHT_START_SCALE) * progress
                scale(grow) {
                    drawOutline(
                        MorphShape(ShapeMorphs.Highlight, progress, rotation = HIGHLIGHT_ROTATION)
                            .createOutline(size, layoutDirection, this),
                        highlightColor.copy(alpha = progress),
                    )
                }
            }
    ) {
        /*
         * Klikání (a s ním vlnka stisku) je ve vlastní vrstvě ořezané do téže sušenky, do jaké se
         * rozvine podbarvení. Dřív byl ořez zaoblený čtverec na celé buňce a pod sušenkou pak
         * vykukovala čtvercová vlnka. Samolepka sama ořezaná není — přišla by o rohy.
         */
        Box(
            Modifier
                .matchParentSize()
                .clip(STICKER_TOUCH_SHAPE)
                .combinedClickable(
                    onClick = { cells.onPick(sticker) },
                    // Podržení samolepku neposílá — jen otevře náhled. Po puštění
                    // `combinedClickable` klik nevyvolá, takže nic neodejde omylem.
                    onLongClickLabel = stringResource(Res.string.stickers_preview),
                    onLongClick = { cells.onPreview(sticker) },
                ),
        )

        StickerPreview(sticker.preview, playing = active || cells.playing, modifier = Modifier.fillMaxSize().padding(6.dp))
    }
}

/** Nabízí aspoň jednu volbu odeslání (bez efektu, ten u samolepky a GIFu není). */
internal fun SendMenuOffer.hasSendOptions(): Boolean = silent || schedule || whenOnline

/**
 * Kulaté tlačítko s nabídkou voleb odeslání (bez zvuku, naplánovat, až bude online) u podrženého
 * GIFu. Nic, když [offer] žádnou z nich nedovolí. Samolepka má volby v náhledu.
 */
@Composable
internal fun SendOptionsButton(
    offer: SendMenuOffer,
    onChoice: (SendMenuChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!offer.hasSendOptions()) return
    Box(modifier) {
        var menuOpen by remember { mutableStateOf(false) }
        Surface(
            onClick = { menuOpen = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(SEND_OPTIONS_BUTTON_SIZE),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(Res.drawable.schedule_send_24dp),
                    contentDescription = stringResource(Res.string.conversation_send_options),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        SendOptionsMenu(
            offer = offer,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onChoice = onChoice,
        )
    }
}

/** Nabídka voleb odeslání pod tlačítkem, které ji otevřelo. Volba nabídku zavře. */
@Composable
internal fun SendOptionsMenu(
    offer: SendMenuOffer,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onChoice: (SendMenuChoice) -> Unit,
) {
    ExpressiveMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        groups = listOf(
            listOfNotNull(
                MenuAction(stringResource(Res.string.conversation_send_silent), Res.drawable.notifications_off_24dp) {
                    onDismiss()
                    onChoice(SendMenuChoice.Silent)
                }.takeIf { offer.silent },
                MenuAction(stringResource(Res.string.conversation_send_schedule), Res.drawable.schedule_send_24dp) {
                    onDismiss()
                    onChoice(SendMenuChoice.Schedule)
                }.takeIf { offer.schedule },
                MenuAction(stringResource(Res.string.conversation_send_when_online), Res.drawable.online_prediction_24dp) {
                    onDismiss()
                    onChoice(SendMenuChoice.WhenOnline)
                }.takeIf { offer.whenOnline },
            ),
        ),
    )
}

/** Tvar vlnky stisku: sušenka, do které se podbarvení rozvine (plný průběh i s pootočením). */
private val STICKER_TOUCH_SHAPE = MorphShape(ShapeMorphs.Highlight, 1f, rotation = HIGHLIGHT_ROTATION)

/** Z jak malého kruhu se podbarvení podržené samolepky rozvine. */
private const val HIGHLIGHT_START_SCALE = 0.5f

/** Pootočení podbarvení při plném tvaru. */
private const val HIGHLIGHT_ROTATION = 45f

/**
 * Příchod, odchod a přeskupení samolepky v mřížce.
 *
 * Odebraná samolepka (typicky z oblíbených) plynule zmizí a ostatní se na její místo dosunou —
 * bez toho mřížka při každé změně seznamu skočila. Průhlednost jde přes effects spec (bez
 * přeběhu, ten by u alfy blikl), posun přes spatial (s jemným pružením jako zbytek aplikace).
 */
@Composable
internal fun LazyGridItemScope.animateStickerItem(): Modifier = Modifier.animateItem(
    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    fadeOutSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
)

/**
 * Pole hledání nad záložkami.
 *
 * Kontejner nese [Surface], pole samo je průhledné — stejně jako vstupní pole konverzace, ať
 * nevznikají dvě zaoblené plochy v sobě.
 */
@Composable
internal fun StickerSearchField(
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            leadingIcon = { Icon(painterResource(Res.drawable.search_24dp), contentDescription = null) },
            trailingIcon = if (text.isNotEmpty()) {
                {
                    IconButton(onClick = { onTextChange("") }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.stickers_clear_search))
                    }
                }
            } else {
                null
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            // Hledá se průběžně; tlačítko na klávesnici jen uklidí klávesnici z výsledků.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Výsledky hledání, každá skupina pod svým nadpisem: sady podle názvu, samolepky z vlastních sad
 * a samolepky z veřejných sad.
 *
 * Sady stojí NAHOŘE, ale jen dvě (zbytek po ťuknutí na „Všechny sady"). Samolepek z vlastních
 * sad bývá ke slovu i přes stovku (dva dotazy po nejvýš sto) — „duck" na testovacím účtu
 * zaplnil mnoho obrazovek — a sada pod nimi by byla k nenalezení. Dvě sady zaberou zhruba
 * polovinu plochy, takže první samolepky jsou vidět hned pod nimi. Nainstalovanost sady se čte
 * z [installedSetIds] (živý seznam záložek), ne z výsledku hledání — ten je jednorázový a sada
 * přidaná tlačítkem by jinak dál nabízela Přidat.
 */
@Composable
private fun StickerSearchResults(
    search: StickerSearchState,
    cells: StickerCells,
    installedSetIds: Set<Long>,
    onOpenSet: (Long) -> Unit,
    onInstallSet: (Long) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (search) {
            // Idle při neprázdném textu = debounce ještě neproběhl; vypadá to stejně jako hledání.
            StickerSearchState.Idle, StickerSearchState.Searching -> LoadingIndicator()

            is StickerSearchState.Results -> {
                val result = search.result
                // Nadpisy sekcí se čtou tady: `searchSection` je rozšíření `LazyGridScope`,
                // ne composable funkce.
                val installedTitle = stringResource(Res.string.stickers_section_installed)
                val publicTitle = stringResource(Res.string.stickers_section_public)
                val setsTitle = stringResource(Res.string.stickers_section_sets)
                // Rozbalení platí pro jeden výsledek; nové hledání začíná zase sbalené.
                var setsExpanded by remember(result) { mutableStateOf(false) }
                if (result.installed.isEmpty() && result.public.isEmpty() && result.sets.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.stickers_none_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    return@Box
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(STICKER_CELL),
                    contentPadding = sheetListPadding(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    setsSection(
                        title = setsTitle,
                        sets = if (setsExpanded) result.sets else result.sets.take(SETS_COLLAPSED),
                        installedSetIds = installedSetIds,
                        onOpenSet = onOpenSet,
                        onInstallSet = onInstallSet,
                    )
                    if (!setsExpanded && result.sets.size > SETS_COLLAPSED) {
                        item(key = "sets-more", span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(onClick = { setsExpanded = true }) {
                                Text(stringResource(Res.string.stickers_sets_show_all))
                            }
                        }
                    }
                    searchSection(installedTitle, result.installed, cells)
                    searchSection(publicTitle, result.public, cells)
                }
            }
        }
    }
}

private fun LazyGridScope.searchSection(
    title: String,
    stickers: List<StickerItem>,
    cells: StickerCells,
) {
    if (stickers.isEmpty()) return

    sectionHeader(title)
    // Klíč nese sekci: tatáž samolepka se ve dvou sekcích neobjeví (veřejné se filtrují), ale
    // klíče nadpisu a samolepky musí být různého tvaru i tak.
    items(stickers, key = { "sticker-${it.id}" }) { sticker ->
        StickerCell(sticker, cells, animateStickerItem())
    }
}

/** Sady podle názvu: řádky jako v trendech, přes celou šířku mřížky. */
private fun LazyGridScope.setsSection(
    title: String,
    sets: List<StickerSetSample>,
    installedSetIds: Set<Long>,
    onOpenSet: (Long) -> Unit,
    onInstallSet: (Long) -> Unit,
) {
    if (sets.isEmpty()) return

    sectionHeader(title)
    items(sets, key = { "set-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { set ->
        StickerSetRow(
            set = set.copy(isInstalled = set.id in installedSetIds),
            onOpen = { onOpenSet(set.id) },
            onInstall = { onInstallSet(set.id) },
            // Mřížka má vlastní odsazení po stranách; řádek ho nepotřebuje znovu celé.
            horizontalPadding = 4.dp,
        )
    }
}

private fun LazyGridScope.sectionHeader(title: String) {
    item(key = "header-$title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

/**
 * Kategorie pod polem hledání (láska, pozdrav, smích…): ťuknutí hledá bez psaní, druhé ťuknutí
 * hledání zruší.
 *
 * Čip nese JEN ikonu, jako v Telegramu. Název od Telegramu je jen pro čtečku obrazovky: server
 * ho lokalizuje podle jazykového balíčku Telegramu (`language_pack_id`), který aplikace
 * nenastavuje — a čeština mezi oficiálními není —, takže v české aplikaci přišlo „Love",
 * „Approval" (ověřeno na emulátoru). Ikony (srdce, palec, úsměv) se čtou bez jazyka a vejde se
 * jich na řádek víc.
 *
 * Řádek se objeví až s načtenými kategoriemi; bez nich (bez sítě při prvním otevření) by byl
 * jen prázdný pruh.
 */
@Composable
private fun StickerCategoryRow(
    categories: List<StickerCategory>,
    selectedName: String?,
    onSelect: (StickerCategory) -> Unit,
) {
    AnimatedVisibility(
        visible = categories.isNotEmpty(),
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(CATEGORY_ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(categories, key = { it.name }) { category ->
                FilterChip(
                    selected = category.name == selectedName,
                    onClick = { onSelect(category) },
                    label = { StickerCategoryIcon(category, selected = category.name == selectedName) },
                    modifier = Modifier.semantics { contentDescription = category.name },
                )
            }
        }
    }
}

/**
 * Ikona kategorie: jednobarevné vlastní emoji Telegramu.
 *
 * Stojí na POSLEDNÍM snímku a po výběru se jednou přehraje. Animace ikon kategorií se kreslí
 * z prázdna, takže první snímek je prázdný, a statický náhled od Telegramu taky (viz
 * `TdlStickerRepository.categories`). Smyčka u deseti ikon vedle sebe by byla neklid.
 *
 * Barví se barvou obsahu čipu, takže se s výběrem přebarví spolu s plochou.
 */
@Composable
private fun StickerCategoryIcon(category: StickerCategory, selected: Boolean) {
    val tint = LocalContentColor.current
    val modifier = Modifier
        .size(CATEGORY_ICON_SIZE)
        .then(if (category.iconTinted) Modifier.tinted(tint) else Modifier)
    val animation = category.icon.animationFileId

    if (animation == null) {
        StickerPreview(category.icon, playing = false, modifier = modifier)
        return
    }
    AnimatedSticker(
        fileId = animation,
        // Popis nese popisek čipu.
        contentDescription = null,
        playback = if (selected) StickerPlayback.Once else StickerPlayback.LastFrame,
        modifier = modifier,
        placeholder = {},
    )
}

/** Kolik sad je ve výsledcích vidět, než je uživatel rozbalí. */
private const val SETS_COLLAPSED = 2

/** Ikona v čipu kategorie. O kus větší než ikona vedle popisku (18 dp) — popisek tu chybí. */
private val CATEGORY_ICON_SIZE = 20.dp

/** Řádek kategorií: čip (32 dp) a kousek vzduchu nad záložkami. */
private val CATEGORY_ROW_HEIGHT = 44.dp

@Composable
private fun StickerTabIcon(tab: StickerTab) {
    when (tab) {
        StickerTab.Recent -> Icon(painterResource(Res.drawable.schedule_24dp), contentDescription = stringResource(Res.string.stickers_tab_recent))
        StickerTab.Favorites -> Icon(painterResource(Res.drawable.star_24dp), contentDescription = stringResource(Res.string.stickers_tab_favorites))
        StickerTab.Trending -> Icon(painterResource(Res.drawable.trending_up_24dp), contentDescription = stringResource(Res.string.stickers_tab_trending))
        is StickerTab.Set -> {
            val cover = tab.summary.cover
            if (cover != null) {
                StickerPreview(cover, playing = false, modifier = Modifier.size(28.dp))
            } else {
                // Sada bez obálky: aspoň první písmeno názvu, ať se záložky dají rozeznat.
                Text(tab.summary.title.take(1), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * Samolepka ve výběru.
 *
 * Bez [playing] **nehraje** a kreslí se nejlevnější dostupnou podobou: rastrový náhled, pak statická
 * `.webp`, a teprve když není ani jedno, první snímek `.tgs` (ten znamená rozbalit a rozparsovat
 * animaci). Tak se kreslí všude, kde je vedle sebe víc sad naráz — obálky v záložkách, ukázky
 * v trendech.
 *
 * [playing] ji rozhýbe — mřížku jedné sady, nebo podrženou samolepku. Do načtení animace drží místo
 * tentýž statický náhled, takže se samolepka rozběhne na místě, bez probliknutí. Statická `.webp`
 * nemá co přehrát a zůstane stát.
 */
@Composable
internal fun StickerPreview(
    sticker: MessageMedia.Sticker,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val still = sticker.thumbnailFileId ?: sticker.fileId
    val description = sticker.emoji.ifBlank { stringResource(Res.string.media_sticker) }
    val animation = sticker.animationFileId

    val stillImage: @Composable () -> Unit = {
        if (still != null) {
            RemoteImage(
                fileId = still,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                background = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmojiFallback(sticker.emoji) }
        }
    }

    when {
        animation != null && playing -> AnimatedSticker(
            fileId = animation,
            contentDescription = description,
            isPlaying = true,
            modifier = modifier,
            placeholder = stillImage,
        )

        // Bez náhledu je první snímek animace pořád lepší než emoji.
        animation != null && still == null -> AnimatedSticker(
            fileId = animation,
            contentDescription = description,
            isPlaying = false,
            modifier = modifier,
            placeholder = stillImage,
        )

        else -> Box(modifier) { stillImage() }
    }
}

@Composable
private fun EmojiFallback(emoji: String) {
    Text(emoji, style = MaterialTheme.typography.headlineMedium)
}

/**
 * Tlačítko voleb odeslání v rohu podrženého GIFu. Malé, aby nezakrývalo GIF; ťuká se na něj
 * vědomě, ne omylem, protože se objeví až po podržení.
 */
private val SEND_OPTIONS_BUTTON_SIZE = 28.dp

/**
 * Insety sheetu výběru: nahoře stavová lišta, dole JEN klávesnice (hledání).
 *
 * Výchozí `modalWindowInsets` dává obsahu jako odsazení i navigační lištu, takže mřížka končila
 * ostrou hranou nad lištou a pod ní zůstal prázdný pruh sheetu. Lištu si proto berou seznamy
 * v `contentPadding` ([sheetListPadding]) a jedou pod ni — tentýž vzor jako seznam chatů,
 * konverzace a výsledky hledání. Klávesnici nechat seznamům nejde: mřížka má pevnou výšku
 * a skoro celá by zmizela pod ní.
 */
@Composable
internal fun expressionSheetInsets(): WindowInsets =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Top).union(WindowInsets.ime)

/**
 * Kus navigační lišty, který sheet obsahu nechává (viz [expressionSheetInsets]). S otevřenou
 * klávesnicí nula — lištu překrývá klávesnice a její místo drží sheet. O tolik jsou plochy
 * s pevnou výškou vyšší, aby viditelná část zůstala stejná jako dřív.
 */
@Composable
internal fun sheetBottomInset(): Dp =
    WindowInsets.navigationBars.exclude(WindowInsets.ime).asPaddingValues().calculateBottomPadding()

/** Odsazení seznamu v sheetu výběru: poslední řádek se doroluje nad navigační lištu. */
@Composable
internal fun sheetListPadding(horizontal: Dp, vertical: Dp): PaddingValues =
    PaddingValues(start = horizontal, top = vertical, end = horizontal, bottom = vertical + sheetBottomInset())

/** Řádek záložek. Při hledání ho výsledky zastoupí, takže jeho výška je pevná. */
internal val STICKER_TAB_ROW_HEIGHT = 48.dp

/** Výška mřížky. Kolem tří řádků — dost na výběr, konverzace nad sheetem zůstává vidět. */
internal val STICKER_PICKER_HEIGHT = 320.dp

/** Kolik samolepek je v řádku trendu vidět. Pět se vejde na šířku telefonu. */
private const val TRENDING_COVERS = 5

private val TRENDING_COVER_SIZE = 56.dp

/** Buňka mřížky. Na telefonu pět sloupců, jako v Telegramu. */
internal val STICKER_CELL = 76.dp
