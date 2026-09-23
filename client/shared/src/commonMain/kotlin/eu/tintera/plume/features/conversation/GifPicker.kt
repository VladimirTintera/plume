package eu.tintera.plume.features.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.GifPage
import eu.tintera.plume.core.telegram.GifRepository
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.rememberFileState
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.rememberVideoPlayer
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.content_gif
import eu.tintera.plume.resources.gifs_failed
import eu.tintera.plume.resources.gifs_none_found
import eu.tintera.plume.resources.gifs_remove_saved
import eu.tintera.plume.resources.gifs_search
import eu.tintera.plume.resources.gifs_section_saved
import eu.tintera.plume.resources.gifs_section_trending
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Výsledky hledání GIFů pro jeden dotaz. Prázdný [query] = populární.
 *
 * Stránky patří dotazu: stránka, která dorazí pro jiný dotaz, se zahodí ([withPage]). Pomalá
 * odpověď na „ko" by jinak přepsala výsledky pro „kočka".
 */
data class GifResults(
    val query: String = "",
    val gifs: List<GifItem> = emptyList(),
    /** Prázdný = další stránka není (nebo se ještě nenačetla ani první). */
    val nextOffset: String = "",
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    /** Dotaz dohledaný do konce a bez výsledku — tehdy se ukáže „nic nenalezeno". */
    val nothingFound: Boolean get() = !loading && !failed && gifs.isEmpty()

    val canLoadMore: Boolean get() = !loading && nextOffset.isNotEmpty()

    /**
     * Připojí stránku. Bot vrací tentýž GIF i na dvou stránkách; mřížka s dvěma stejnými klíči
     * spadne, takže se duplicity zahazují.
     */
    fun withPage(forQuery: String, page: GifPage): GifResults {
        if (forQuery != query) return this
        return copy(
            gifs = (gifs + page.gifs).distinctBy { it.id },
            nextOffset = page.nextOffset,
            loading = false,
            failed = false,
        )
    }

    /** Selhání první stránky se hlásí; další stránky jen přestanou přibývat. */
    fun withFailure(forQuery: String): GifResults {
        if (forQuery != query) return this
        return copy(loading = false, failed = gifs.isEmpty(), nextOffset = "")
    }
}

/**
 * Stav výběru GIFů. Klíčovaný chatem ([chatId] chce inline bot vědět), proto vlastní ViewModel,
 * ne kus [StickerPickerViewModel], který je pro všechny chaty stejný.
 */
class GifPickerViewModel(
    private val chatId: Long,
    private val repository: GifRepository,
) : ViewModel() {

    private val logger = Logger.withTag("Gifs")

    /** `null`, dokud se nenačetly. */
    val saved: StateFlow<List<GifItem>?> = repository.savedGifs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _results = MutableStateFlow(GifResults())
    val results: StateFlow<GifResults> = _results.asStateFlow()

    private var searchJob: Job? = null

    /** První [search] (populární při otevření) musí projít, i když je dotaz prázdný jako výchozí. */
    private var searched = false

    /**
     * Hledá až po pauze v psaní — dotaz jde na server. Předchozí hledání i dotahování stránky
     * se zruší. Prázdný dotaz (populární) se načte hned.
     */
    fun search(text: String) {
        val query = text.trim()
        if (searched && query == _results.value.query) return
        searched = true
        searchJob?.cancel()
        _results.value = GifResults(query = query)
        searchJob = viewModelScope.launch {
            if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
            load(query, offset = "")
        }
    }

    fun loadMore() {
        val current = _results.value
        if (!current.canLoadMore) return
        _results.value = current.copy(loading = true)
        searchJob = viewModelScope.launch { load(current.query, current.nextOffset) }
    }

    fun removeSaved(gif: GifItem) {
        viewModelScope.launch {
            repository.removeSaved(gif).onFailure { logger.w(it) { "GIF se nepodařilo odebrat z uložených." } }
        }
    }

    private suspend fun load(query: String, offset: String) {
        repository.search(chatId, query, offset)
            .onSuccess { page -> _results.update { it.withPage(query, page) } }
            .onFailure { failure ->
                logger.w(failure) { "Hledání GIFů selhalo." }
                _results.update { it.withFailure(query) }
            }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/**
 * GIFy ve výběru: pole hledání a pod ním mřížka. Bez dotazu nahoře uložené, pod nimi populární.
 *
 * **GIFy v mřížce hrají.** GIF, který se nehýbe, nejde vybrat podle toho, co dělá — a na tom se
 * vybírá. Hrají jen složené buňky (mřížka skládá viditelné a kousek okolo), stejně jako GIF
 * v konverzaci; odscrollovaný vrátí dekodér.
 *
 * Plocha má **stejnou výšku** jako záložky a stránky samolepek, takže přepnutí části sheet
 * nenatáhne.
 */
@Composable
internal fun GifPanel(
    chatId: Long,
    onPick: (GifItem) -> Unit,
) {
    val viewModel: GifPickerViewModel = koinViewModel(key = "gifs-$chatId") { parametersOf(chatId) }
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    var searchText by rememberSaveable { mutableStateOf("") }

    /** Podržený uložený GIF — ukáže se u něj křížek na odebrání. Jen u jednoho, jako hvězdička u samolepek. */
    var activeSavedId by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { viewModel.search(searchText) }

    StickerSearchField(
        text = searchText,
        placeholder = stringResource(Res.string.gifs_search),
        onTextChange = {
            searchText = it
            viewModel.search(it)
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    val searching = searchText.isNotBlank()
    val savedGifs = if (searching) emptyList() else saved.orEmpty()
    val savedTitle = stringResource(Res.string.gifs_section_saved)
    val trendingTitle = stringResource(Res.string.gifs_section_trending)

    Box(
        Modifier.fillMaxWidth().height(STICKER_TAB_ROW_HEIGHT + STICKER_PICKER_HEIGHT + sheetBottomInset()),
        contentAlignment = Alignment.Center,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(GIF_CELL),
            contentPadding = sheetListPadding(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (savedGifs.isNotEmpty()) {
                gifHeader(savedTitle)
                itemsIndexed(savedGifs, key = { _, gif -> "saved-${gif.id}" }) { _, gif ->
                    GifCell(
                        gif = gif,
                        onPick = onPick,
                        active = gif.id == activeSavedId,
                        // Podržení si haptiku dělá `combinedClickable` samo — viz `Haptics`.
                        onActivate = { activeSavedId = gif.id },
                        onRemove = {
                            activeSavedId = null
                            viewModel.removeSaved(gif)
                        },
                    )
                }
                if (results.gifs.isNotEmpty()) gifHeader(trendingTitle)
            }
            // Klíč nese sekci: uložený GIF bývá i mezi populárními.
            itemsIndexed(results.gifs, key = { _, gif -> "found-${gif.id}" }) { index, gif ->
                GifCell(gif = gif, onPick = onPick)
                LoadMoreAtEnd(index, results.gifs.size, viewModel::loadMore)
            }
            if (results.loading && results.gifs.isNotEmpty()) {
                item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
            }
        }

        when {
            // Uložené se ukazují hned; načítání populárních pod nimi drží indikátor v patičce.
            results.loading && results.gifs.isEmpty() && savedGifs.isEmpty() -> LoadingIndicator()
            results.failed && savedGifs.isEmpty() -> GifMessage(stringResource(Res.string.gifs_failed))
            results.nothingFound && savedGifs.isEmpty() -> GifMessage(stringResource(Res.string.gifs_none_found))
        }
    }
}

private fun LazyGridScope.gifHeader(title: String) {
    item(key = "header-$title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun GifMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp),
    )
}

/**
 * Jeden GIF v mřížce: ťuknutí ho pošle. U uloženého podržení nabídne odebrání ([onRemove]).
 *
 * Buňka má pevnou výšku a GIF se do ní ořízne — v řádku by se jinak buňky rozjely podle poměru
 * stran. Velký GIF se sám nestahuje (stejné pravidlo jako v konverzaci), zůstane náhled.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GifCell(
    gif: GifItem,
    onPick: (GifItem) -> Unit,
    active: Boolean = false,
    onActivate: (() -> Unit)? = null,
    onRemove: () -> Unit = {},
) {
    val animation = gif.animation
    val fileState = rememberFileState(animation.fileId.takeIf { animation.autoPlays })
    val path = (fileState as? FileState.Ready)?.path

    Box(
        Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .height(GIF_CELL_HEIGHT)
            .clip(RoundedCornerShape(GIF_CELL_CORNER))
            .combinedClickable(
                onClick = { onPick(gif) },
                onLongClick = onActivate,
            ),
    ) {
        // Náhled drží místo, dokud GIF nedorazí; server ho ale nemusí dát vůbec.
        animation.thumbnailFileId?.let { thumbnail ->
            RemoteImage(
                fileId = thumbnail,
                contentDescription = stringResource(Res.string.content_gif),
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (path != null) {
            val player = rememberVideoPlayer()
            LaunchedEffect(player, path) {
                player.load(path, loop = true, muted = true)
                player.play()
            }
            VideoSurface(player, Modifier.coverAspect(animation.width, animation.height))
        }

        if (active) {
            Surface(
                onClick = onRemove,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(REMOVE_BUTTON_SIZE),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(Res.drawable.close_24dp),
                        contentDescription = stringResource(Res.string.gifs_remove_saved),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Velikost, která buňku POKRYJE při zachovaném poměru stran; přečnívající kus ořízne buňka.
 * `VideoSurface` obraz vepisuje, takže by v buňce pevné výšky zůstaly pruhy a sousední GIFy by
 * měly každý jinou velikost. Obrázek náhledu se ořezává stejně (`Crop`).
 */
private fun Modifier.coverAspect(width: Int, height: Int): Modifier = layout { measurable, constraints ->
    val boxWidth = constraints.maxWidth
    val boxHeight = constraints.maxHeight
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else boxWidth.toFloat() / boxHeight
    val coverWidth: Int
    val coverHeight: Int
    if (boxWidth.toFloat() / boxHeight > ratio) {
        coverWidth = boxWidth
        coverHeight = (boxWidth / ratio).roundToInt()
    } else {
        coverHeight = boxHeight
        coverWidth = (boxHeight * ratio).roundToInt()
    }
    val placeable = measurable.measure(Constraints.fixed(coverWidth, coverHeight))
    layout(boxWidth, boxHeight) {
        placeable.place((boxWidth - coverWidth) / 2, (boxHeight - coverHeight) / 2)
    }
}

/**
 * Dotáhne další stránku, až se složí jedna z posledních buněk. Ne podle `layoutInfo` — to se
 * u mřížky minulo (viz „Sdílená média v profilu" v CLAUDE.md).
 */
@Composable
private fun LoadMoreAtEnd(index: Int, count: Int, onLoadMore: () -> Unit) {
    if (index < count - LOAD_MORE_THRESHOLD) return
    val current by rememberUpdatedState(onLoadMore)
    LaunchedEffect(count) { current() }
}

private const val LOAD_MORE_THRESHOLD = 6

/** Nejmenší šířka buňky. Na telefonu tři sloupce. */
private val GIF_CELL = 110.dp
private val GIF_CELL_HEIGHT = 96.dp
private val GIF_CELL_CORNER = 12.dp
private val REMOVE_BUTTON_SIZE = 28.dp
