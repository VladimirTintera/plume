package eu.tintera.plume.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.StatValue
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.compactCountText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.statistics_growth
import eu.tintera.plume.resources.statistics_reactions_count
import eu.tintera.plume.resources.statistics_shares_count
import eu.tintera.plume.resources.statistics_views_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.PluralStringResource
import kotlin.math.abs
import kotlin.math.roundToInt
import eu.tintera.plume.core.ui.GroupTitle

/** Jeden údaj přehledu: velké číslo, co to je, a změna proti předchozímu období. */
internal data class StatTile(val label: String, val value: String, val growth: String? = null)

/** Údaje přehledu ve dvou sloupcích, v jednom bloku. */
@Composable
internal fun StatTiles(tiles: List<StatTile>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GROUP_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { tile -> TileCell(tile, Modifier.weight(1f)) }
                // Lichý počet: poslední údaj zůstane v levém sloupci, ne přes celou šířku.
                if (row.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun TileCell(tile: StatTile, modifier: Modifier) {
    Column(modifier) {
        Text(tile.value, style = MaterialTheme.typography.titleLargeEmphasized.tabularNumbers(), maxLines = 1)
        Text(
            tile.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        tile.growth?.let { growth ->
            Text(
                growth,
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Údaj s hodnotou a změnou ze [StatValue]. */
@Composable
internal fun statTile(label: String, value: StatValue): StatTile =
    StatTile(label = label, value = compactCountText(value.value.roundToInt()), growth = growthText(value))

/**
 * Změna proti předchozímu období: „+120 (+5,3 %)". Znaménko nese směr, barva ne — zelená
 * a červená by byly barvy stavu a barvoslepý by je nerozeznal. Bez srovnání (nový kanál,
 * obojí nula) se nepíše nic.
 */
@Composable
internal fun growthText(value: StatValue): String? {
    val delta = value.value - value.previous
    // Beze změny (nebo bez srovnání u nového kanálu) by „+0 (+0 %)" byl jen šum.
    if (delta.roundToInt() == 0 && value.growthPercent == 0.0) return null
    val sign = if (delta < 0) MINUS else PLUS
    val percentSign = if (value.growthPercent < 0) MINUS else PLUS
    return stringResource(
        Res.string.statistics_growth,
        sign + compactCountText(abs(delta).roundToInt()),
        percentSign + percentText(abs(value.growthPercent)),
    )
}

/** Nadpis oddílu nad blokem řádků. */
@Composable
internal fun StatSectionTitle(text: String, modifier: Modifier = Modifier) {
    // Seznam statistik nemá okraj, blok si ho dává sám — nadpis lícuje s obsahem řádků.
    GroupTitle(text, modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
}

/** Počty u příspěvku: „1,2 tis. zhlédnutí · 12 sdílení · 30 reakcí"; nuly se vynechají. */
@Composable
internal fun interactionLine(views: Int, forwards: Int, reactions: Int): String =
    listOfNotNull(
        countText(Res.plurals.statistics_views_count, views),
        countText(Res.plurals.statistics_shares_count, forwards).takeIf { forwards > 0 },
        countText(Res.plurals.statistics_reactions_count, reactions).takeIf { reactions > 0 },
    ).joinToString(SEPARATOR)

@Composable
private fun countText(plural: PluralStringResource, count: Int): String =
    pluralStringResource(plural, count, compactCountText(count))

internal const val SEPARATOR = " · "
private const val PLUS = "+"

/** Typografické minus, ne spojovník — u čísla se spojovník čte jako pomlčka. */
private const val MINUS = "−"
