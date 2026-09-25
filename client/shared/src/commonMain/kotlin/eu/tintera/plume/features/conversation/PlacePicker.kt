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
import eu.tintera.plume.resources.poll_place_title
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
 * Výběr místa k odpovědi nebo otázce ankety: hledání podle názvu (inline bot míst), výsledky
 * jako blok zaoblených řádků — tentýž jazyk jako výběr chatu nebo země.
 *
 * **Poloha zařízení se nezjišťuje.** Hledá se podle názvu, město se napíše do dotazu; oprávnění
 * k poloze kvůli jedné příloze ankety by byla cena, která za to nestojí.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlacePickerSheet(
    search: PlaceSearch,
    onQueryChange: (String) -> Unit,
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    // Rovnou celý: výsledky i pole se do napůl rozbaleného sheetu nevejdou (viz výběr země).
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.poll_place_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SearchPill(
                initialQuery = search.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(Res.string.poll_place_search),
                clearDescription = stringResource(Res.string.common_close),
                autoFocus = true,
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
