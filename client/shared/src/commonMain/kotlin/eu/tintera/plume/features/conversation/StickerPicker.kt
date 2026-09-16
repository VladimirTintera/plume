package eu.tintera.plume.features.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.star_24dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import eu.tintera.plume.core.telegram.StickerQuery
import eu.tintera.plume.core.telegram.StickerSearchResult
import eu.tintera.plume.core.ui.emojiClusterCount
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.search_24dp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import androidx.compose.foundation.shape.CircleShape
import eu.tintera.plume.resources.star_filled_24dp
import org.koin.compose.viewmodel.koinViewModel

/** Záložka výběru samolepek. */
sealed interface StickerTab {
    data object Recent : StickerTab
    data object Favorites : StickerTab
    data class Set(val summary: StickerSetSummary) : StickerTab
}

data class StickerPickerState(
    val tabs: List<StickerTab> = listOf(StickerTab.Recent, StickerTab.Favorites),
    /** `null`, dokud se nenačetly. */
    val recent: List<StickerItem>? = null,
    val favorites: List<StickerItem>? = null,
    /** Obsah už otevřených sad podle id. Chybějící sada se teprve načítá. */
    val sets: Map<Long, List<StickerItem>> = emptyMap(),
) {
    /** Samolepky záložky; `null`, dokud se její obsah načítá. */
    fun stickersOf(tab: StickerTab): List<StickerItem>? = when (tab) {
        StickerTab.Recent -> recent
        StickerTab.Favorites -> favorites
        is StickerTab.Set -> sets[tab.summary.id]
    }
}

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
) : ViewModel() {

    private val setContents = MutableStateFlow<Map<Long, List<StickerItem>>>(emptyMap())

    /** Sady, jejichž načítání už běží — aby dvě stránky nenačítaly totéž dvakrát. */
    private val loading = mutableSetOf<Long>()

    private val searchRequest = MutableStateFlow(SearchRequest("", emptyList()))

    /**
     * Výsledek hledání.
     *
     * Hledá se až po krátké pauze v psaní, ne na každý znak — veřejné hledání jde na server.
     * `transformLatest` rozepsané hledání zahodí, jakmile se text změní, takže pomalá odpověď na
     * „ko" nepřepíše výsledky pro „kočka".
     */
    val search: StateFlow<StickerSearchState> = searchRequest
        .debounce { if (it.text.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .transformLatest { request ->
            val text = request.text.trim()
            if (text.isEmpty()) {
                emit(StickerSearchState.Idle)
                return@transformLatest
            }
            emit(StickerSearchState.Searching)
            val query = if (text.emojiClusterCount(MAX_SEARCH_EMOJI) > 0) {
                StickerQuery.Emoji(text)
            } else {
                StickerQuery.Text(text, request.languageCodes)
            }
            emit(StickerSearchState.Results(repository.search(query)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StickerSearchState.Idle)

    /** Text hledání. Jazyk zařízení určuje, jak se slova převedou na emoji. */
    fun search(text: String, languageCodes: List<String>) {
        searchRequest.value = SearchRequest(text, languageCodes)
    }

    val state: StateFlow<StickerPickerState> = combine(
        repository.installedSets(),
        repository.recentStickers(),
        repository.favoriteStickers(),
        setContents,
    ) { sets, recent, favorites, contents ->
        StickerPickerState(
            tabs = listOf(StickerTab.Recent, StickerTab.Favorites) + sets.map { StickerTab.Set(it) },
            recent = recent,
            favorites = favorites,
            sets = contents,
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

        /** Kolik emoji v hledání se ještě bere jako hledání podle emoji. */
        const val MAX_SEARCH_EMOJI = 10
    }
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
fun StickerPickerSheet(
    onPick: (StickerItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: StickerPickerViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    var searchText by rememberSaveable { mutableStateOf("") }
    // Jazyk zařízení a angličtina jako záloha: klíčová slova samolepek bývají anglicky.
    val languageCodes = remember { listOf(Locale.current.language, "en").distinct() }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    /*
     * Samolepka rozhýbaná podržením. Vždy nejvýš JEDNA — hraje, dokud se nepodrží jiná.
     *
     * Jedna kvůli výkonu (mřížka je právě proto statická) i kvůli čitelnosti: kdo si prohlíží
     * sadu, porovnává samolepky po jedné. Přežívá přepnutí záložky; po návratu hraje dál.
     */
    var activeStickerId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Počet stránek se mění: sady dorazí až po nedávných a oblíbených. Proto lambda, ne číslo.
    val pagerState = rememberPagerState(pageCount = { state.tabs.size })

    val pick: (StickerItem) -> Unit = { sticker ->
        // Nejdřív sheet schovat, pak poslat: zmizení bez animace by vypadalo jako pád,
        // a zpráva se tak objeví až nad zavřeným sheetem.
        scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(sticker) }
    }
    val favoriteIds = state.favorites.orEmpty().map { it.sendFileId }.toSet()
    val toggleFavorite: (StickerItem) -> Unit = { sticker ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        viewModel.toggleFavorite(sticker)
    }
    val activate: (StickerItem) -> Unit = { sticker ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        activeStickerId = sticker.id
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        StickerSearchField(
            text = searchText,
            onTextChange = {
                searchText = it
                viewModel.search(it, languageCodes)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        /*
         * Hledání nahradí záložky i stránky, ale ve STEJNĚ vysoké ploše — jinak by sheet při
         * prvním písmenu poskočil. Výsledky se ukazují podle textu v poli, ne podle stavu
         * hledání: po smazání textu se záložky vrátí hned, bez čekání na debounce.
         */
        if (searchText.isNotBlank()) {
            Box(Modifier.fillMaxWidth().height(STICKER_TAB_ROW_HEIGHT + STICKER_PICKER_HEIGHT)) {
                StickerSearchResults(
                    search = search,
                    cells = StickerCells(activeStickerId, favoriteIds, pick, activate, toggleFavorite),
                )
            }
            return@ModalBottomSheet
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
            key = { index -> state.tabs.getOrNull(index)?.pageKey() ?: index },
            modifier = Modifier.fillMaxWidth().height(STICKER_PICKER_HEIGHT),
        ) { page ->
            val tab = state.tabs.getOrNull(page) ?: return@HorizontalPager
            LaunchedEffect(tab) { viewModel.load(tab) }

            StickerPage(
                tab = tab,
                stickers = state.stickersOf(tab),
                cells = StickerCells(activeStickerId, favoriteIds, pick, activate, toggleFavorite),
            )
        }
    }
}

/** Stabilní klíč stránky záložky. */
private fun StickerTab.pageKey(): Any = when (this) {
    StickerTab.Recent -> "recent"
    StickerTab.Favorites -> "favorites"
    is StickerTab.Set -> summary.id
}

/** Obsah jedné záložky: načítání, prázdný stav, nebo mřížka. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StickerPage(
    tab: StickerTab,
    stickers: List<StickerItem>?,
    cells: StickerCells,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            stickers == null -> LoadingIndicator()

            stickers.isEmpty() -> Text(
                text = when (tab) {
                    StickerTab.Recent -> "Tady se objeví samolepky, které pošleš."
                    StickerTab.Favorites -> "Zatím tu nejsou žádné oblíbené samolepky."
                    is StickerTab.Set -> "Sada je prázdná, nebo se ji nepodařilo načíst."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(STICKER_CELL),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(stickers, key = { it.id }) { sticker -> StickerCell(sticker, cells) }
            }
        }
    }
}

/**
 * Co buňky mřížky potřebují od sheetu. Balík místo pěti parametrů, které by se jinak táhly přes
 * stránku záložky i výsledky hledání.
 */
private class StickerCells(
    val activeStickerId: Long?,
    val favoriteFileIds: Set<Int>,
    val onPick: (StickerItem) -> Unit,
    val onActivate: (StickerItem) -> Unit,
    val onToggleFavorite: (StickerItem) -> Unit,
)

/**
 * Jedna samolepka v mřížce: ťuknutí pošle, podržení rozhýbe.
 *
 * U podržené samolepky se v rohu objeví **hvězdička oblíbených**. Jen u ní: hvězdička u každé
 * buňky by zaplevelila mřížku a ťuknutí vedle ní by samolepku omylem poslalo. Podržení je
 * zároveň „prohlížím si tuhle", takže nabídnout k ní akci dává smysl.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StickerCell(sticker: StickerItem, cells: StickerCells) {
    val active = sticker.id == cells.activeStickerId

    Box(
        Modifier
            .size(STICKER_CELL)
            .clip(RoundedCornerShape(12.dp))
            // Podbarvení nese potvrzení podržení i u statické samolepky, která se rozhýbat nemá jak.
            .background(if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .combinedClickable(
                onClick = { cells.onPick(sticker) },
                // Podržení samolepku neposílá — jen ji rozhýbe. Po puštění `combinedClickable`
                // klik nevyvolá, takže nic neodejde omylem.
                onLongClick = { cells.onActivate(sticker) },
            ),
    ) {
        StickerPreview(sticker.preview, playing = active, modifier = Modifier.fillMaxSize().padding(6.dp))

        if (active) {
            val favorite = sticker.sendFileId in cells.favoriteFileIds
            Surface(
                onClick = { cells.onToggleFavorite(sticker) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(FAVORITE_BUTTON_SIZE),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (favorite) Res.drawable.star_filled_24dp else Res.drawable.star_24dp),
                        contentDescription = if (favorite) "Odebrat z oblíbených" else "Přidat do oblíbených",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Pole hledání nad záložkami.
 *
 * Kontejner nese [Surface], pole samo je průhledné — stejně jako vstupní pole konverzace, ať
 * nevznikají dvě zaoblené plochy v sobě.
 */
@Composable
private fun StickerSearchField(
    text: String,
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
            placeholder = { Text("Hledat samolepky") },
            singleLine = true,
            leadingIcon = { Icon(painterResource(Res.drawable.search_24dp), contentDescription = null) },
            trailingIcon = if (text.isNotEmpty()) {
                {
                    IconButton(onClick = { onTextChange("") }) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = "Smazat hledání")
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

/** Výsledky hledání: nejdřív z vlastních sad, pak z veřejných, každé pod svým nadpisem. */
@Composable
private fun StickerSearchResults(
    search: StickerSearchState,
    cells: StickerCells,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (search) {
            // Idle při neprázdném textu = debounce ještě neproběhl; vypadá to stejně jako hledání.
            StickerSearchState.Idle, StickerSearchState.Searching -> LoadingIndicator()

            is StickerSearchState.Results -> {
                val result = search.result
                if (result.installed.isEmpty() && result.public.isEmpty()) {
                    Text(
                        text = "Žádné samolepky jsme nenašli.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    return@Box
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(STICKER_CELL),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    searchSection("Z tvých sad", result.installed, cells)
                    searchSection("Další samolepky", result.public, cells)
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

    item(key = "header-$title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
    }
    // Klíč nese sekci: tatáž samolepka se ve dvou sekcích neobjeví (veřejné se filtrují), ale
    // klíče nadpisu a samolepky musí být různého tvaru i tak.
    items(stickers, key = { "sticker-${it.id}" }) { sticker -> StickerCell(sticker, cells) }
}

@Composable
private fun StickerTabIcon(tab: StickerTab) {
    when (tab) {
        StickerTab.Recent -> Icon(painterResource(Res.drawable.schedule_24dp), contentDescription = "Nedávné")
        StickerTab.Favorites -> Icon(painterResource(Res.drawable.star_24dp), contentDescription = "Oblíbené")
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
 * Běžně **nehraje** a kreslí se nejlevnější dostupnou podobou: rastrový náhled, pak statická `.webp`,
 * a teprve když není ani jedno, první snímek `.tgs` (ten znamená rozbalit a rozparsovat animaci).
 * Desítky animací v jedné mřížce by stály víc, než kolik přinesou.
 *
 * [playing] ji rozhýbe — jen podrženou. Do načtení animace drží místo tentýž statický náhled, takže
 * se samolepka rozběhne na místě, bez probliknutí. Statická `.webp` nemá co přehrát a zůstane stát.
 */
@Composable
private fun StickerPreview(
    sticker: MessageMedia.Sticker,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val still = sticker.thumbnailFileId ?: sticker.fileId
    val description = sticker.emoji.ifBlank { "Samolepka" }
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
 * Hvězdička oblíbených v rohu podržené samolepky. Malá, aby nezakrývala samolepku; ťuká se na ni
 * vědomě, ne omylem, protože se objeví až po podržení.
 */
private val FAVORITE_BUTTON_SIZE = 28.dp

/** Řádek záložek. Při hledání ho výsledky zastoupí, takže jeho výška je pevná. */
private val STICKER_TAB_ROW_HEIGHT = 48.dp

/** Výška mřížky. Kolem tří řádků — dost na výběr, konverzace nad sheetem zůstává vidět. */
private val STICKER_PICKER_HEIGHT = 320.dp

/** Buňka mřížky. Na telefonu pět sloupců, jako v Telegramu. */
private val STICKER_CELL = 76.dp
