package eu.tintera.plume.features.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.location.GeoPoint
import eu.tintera.plume.core.location.rememberDeviceLocation
import eu.tintera.plume.core.telegram.PlaceRepository
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import eu.tintera.plume.core.telegram.Place
import androidx.compose.ui.graphics.Shape
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.poll_place_empty
import eu.tintera.plume.resources.poll_place_failed
import eu.tintera.plume.resources.poll_place_locating
import eu.tintera.plume.resources.poll_place_mine
import eu.tintera.plume.resources.poll_place_mine_hint
import eu.tintera.plume.resources.poll_place_no_location
import eu.tintera.plume.resources.my_location_24dp
import eu.tintera.plume.resources.poll_place_search
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Hledání místa: poloha, kolem které se hledá, dotaz, ke kterému výsledky patří, a stav. */
data class PlaceSearch(
    val location: PlaceLocation = PlaceLocation.Locating,
    val query: String = "",
    val results: List<Place> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** Poloha zařízení pro hledání míst. */
sealed interface PlaceLocation {
    data object Locating : PlaceLocation
    data class Found(val point: GeoPoint) : PlaceLocation

    /** Nedovolená, nebo ji zařízení nezjistilo — místa pak najít nejde (viz `PlaceRepository`). */
    data object Unavailable : PlaceLocation
}

/**
 * Hledání míst kolem polohy zařízení — pro sheet [PlacePickerHost]. Sdílí ho formulář ankety
 * (místo u otázky a odpovědi) a konverzace (sponka → Poloha).
 *
 * Třída nad [CoroutineScope], ne ViewModel: patří do ViewModelu té obrazovky, která sheet ukazuje.
 */
class PlaceSearcher(
    private val scope: CoroutineScope,
    private val repository: PlaceRepository,
    private val chatId: Long,
) {
    private val _state = MutableStateFlow(PlaceSearch())
    val state: StateFlow<PlaceSearch> = _state.asStateFlow()
    private var job: Job? = null

    /**
     * Místa k dotazu, s pauzou po psaní a zrušením předchozího hledání — pomalá odpověď na
     * „Praha" nesmí přepsat výsledky pro „Praha Hrad".
     */
    fun search(query: String) {
        job?.cancel()
        val text = query.trim()
        val center = (_state.value.location as? PlaceLocation.Found)?.point
        _state.update { it.copy(query = text, loading = center != null, failed = false) }
        center ?: return
        job = scope.launch {
            // Prázdný dotaz (místa v okolí) hned, psaní s pauzou.
            if (text.isNotEmpty()) delay(PLACE_SEARCH_DELAY_MS)
            repository.search(chatId, text, center)
                .onSuccess { found -> _state.update { it.copy(results = found, loading = false) } }
                .onFailure { _state.update { it.copy(loading = false, failed = true) } }
        }
    }

    /** Výběr místa se otevírá: poloha se zjišťuje znovu a výsledky z minula se zahodí. */
    fun start() {
        job?.cancel()
        _state.value = PlaceSearch()
    }

    /** Poloha zařízení dorazila (nebo ne); hned se hledají místa v okolí. */
    fun onLocation(point: GeoPoint?) {
        _state.update { it.copy(location = point?.let { p -> PlaceLocation.Found(p) } ?: PlaceLocation.Unavailable) }
        search(_state.value.query)
    }
}

/**
 * Sheet výběru místa i se zjištěním polohy: při otevření se [searcher] vynuluje a zeptá se na
 * polohu zařízení (systém se na oprávnění zeptá až teď, ne při startu aplikace).
 */
@Composable
internal fun PlacePickerHost(
    searcher: PlaceSearcher,
    title: String,
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Pole hledání si vezme fokus hned. V anketě ano — místo se tam hledá podle názvu; v konverzaci
     * ne: hlavní akce je „Moje poloha" nahoře a klávesnice by schovala místa v okolí.
     */
    autoFocusSearch: Boolean = true,
) {
    val search by searcher.state.collectAsStateWithLifecycle()
    val location = rememberDeviceLocation()
    LaunchedEffect(Unit) {
        searcher.start()
        location.request(searcher::onLocation)
    }
    PlacePickerSheet(
        search = search,
        title = title,
        onQueryChange = searcher::search,
        onPick = onPick,
        onDismiss = onDismiss,
        autoFocusSearch = autoFocusSearch,
    )
}

/**
 * Výběr místa: nahoře vlastní poloha, pod ní místa v okolí a hledání podle názvu (inline bot
 * míst, kolem polohy zařízení), výsledky jako blok zaoblených řádků — tentýž jazyk jako výběr
 * chatu nebo země.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlacePickerSheet(
    search: PlaceSearch,
    title: String,
    onQueryChange: (String) -> Unit,
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
    autoFocusSearch: Boolean = true,
) {
    // Rovnou celý: výsledky i pole se do napůl rozbaleného sheetu nevejdou (viz výběr země).
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SearchPill(
                initialQuery = search.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(Res.string.poll_place_search),
                clearDescription = stringResource(Res.string.common_close),
                autoFocus = autoFocusSearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Box(Modifier.fillMaxWidth().height(LIST_HEIGHT), contentAlignment = Alignment.Center) {
                when (val location = search.location) {
                    PlaceLocation.Locating -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
                        Note(stringResource(Res.string.poll_place_locating))
                    }
                    PlaceLocation.Unavailable -> Note(stringResource(Res.string.poll_place_no_location))
                    is PlaceLocation.Found -> {
                        // Vlastní poloha je vždycky první — i když hledání nic nenajde.
                        val mine = Place(title = "", address = "", latitude = location.point.latitude, longitude = location.point.longitude)
                        val rows = listOf(mine) + search.results
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                            modifier = Modifier.fillMaxWidth().height(LIST_HEIGHT),
                        ) {
                            itemsIndexed(rows, key = { index, place -> if (index == 0) "mine" else place.id.ifEmpty { "$index" } }) { index, place ->
                                PlaceRow(
                                    title = if (index == 0) stringResource(Res.string.poll_place_mine) else place.title,
                                    subtitle = if (index == 0) stringResource(Res.string.poll_place_mine_hint) else place.address,
                                    mine = index == 0,
                                    shape = groupItemShape(index, rows.size),
                                ) { onPick(place) }
                            }
                            item(key = "status") {
                                when {
                                    search.loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        LoadingIndicator()
                                    }
                                    search.failed -> Note(stringResource(Res.string.poll_place_failed))
                                    search.results.isEmpty() && search.query.isNotEmpty() -> Note(stringResource(Res.string.poll_place_empty))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
    )
}

/** Řádek místa. Vlastní poloha má ikonu v primární roli, aby se od nalezených míst odlišila. */
@Composable
private fun PlaceRow(title: String, subtitle: String, mine: Boolean, shape: Shape, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = shape, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = subtitle.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (mine) colors.primaryContainer else colors.secondaryContainer),
                ) {
                    Icon(
                        painterResource(if (mine) Res.drawable.my_location_24dp else Res.drawable.location_on_24dp),
                        contentDescription = null,
                        tint = if (mine) colors.onPrimaryContainer else colors.onSecondaryContainer,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

private val LIST_HEIGHT = 420.dp

private const val PLACE_SEARCH_DELAY_MS = 400L
