package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.PostStats
import eu.tintera.plume.core.ui.compactCountText
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.mode_comment_24dp
import eu.tintera.plume.resources.post_comments
import eu.tintera.plume.resources.post_forwards
import eu.tintera.plume.resources.post_views
import eu.tintera.plume.resources.visibility_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Počty pod příspěvkem kanálu: zhlédnutí, přeposlání, komentáře.
 *
 * **Pod zprávou, ne v ní**, jako odznak doručení — a ze stejného důvodu barva patří PLOŠE
 * konverzace (`onSurfaceVariant`), ne bublině. Stojí na straně protější k čipům reakcí, které
 * visí přes dolní nároží na straně odesílatele; pod nimi by se oba řádky slily.
 *
 * Čísla jsou zkrácená („12 tis."), protože kanály mívají statisíce odběratelů a přesné číslo
 * pod každým příspěvkem je šum. Čtečka obrazovky dostane přesná čísla celými větami.
 * Přeposlání a komentáře s nulou se nekreslí; zhlédnutí nula nemají (počítá se i autor).
 */
@Composable
internal fun PostStatsRow(stats: PostStats, modifier: Modifier = Modifier) {
    val description = buildList {
        if (stats.views > 0) add(stringResource(Res.string.post_views, stats.views.grouped()))
        if (stats.forwards > 0) add(stringResource(Res.string.post_forwards, stats.forwards.grouped()))
        stats.comments?.takeIf { it > 0 }?.let { add(stringResource(Res.string.post_comments, it.grouped())) }
    }.joinToString(", ")
    if (description.isEmpty()) return

    Row(
        modifier = modifier
            .padding(top = POST_STATS_GAP, start = POST_STATS_INSET, end = POST_STATS_INSET)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(POST_STATS_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stats.views > 0) Stat(Res.drawable.visibility_24dp, stats.views)
        if (stats.forwards > 0) Stat(Res.drawable.forward_24dp, stats.forwards)
        stats.comments?.takeIf { it > 0 }?.let { Stat(Res.drawable.mode_comment_24dp, it) }
    }
}

@Composable
private fun Stat(icon: DrawableResource, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(POST_STATS_ICON_SIZE),
        )
        Text(
            text = compactCountText(count),
            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Jako ikona odznaku doručení — vedle zprávy nemá křičet. */
private val POST_STATS_ICON_SIZE = 14.dp

/** Mezera pod zprávou; stejná jako u odznaku doručení. */
private val POST_STATS_GAP = 2.dp

/** Odsazení od hrany zprávy, aby řádek neseděl přesně na jejím okraji. */
private val POST_STATS_INSET = 4.dp

private val POST_STATS_SPACING = 10.dp
