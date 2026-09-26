package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.post_author
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import eu.tintera.plume.resources.post_comment_first
import eu.tintera.plume.resources.post_comments_count
import eu.tintera.plume.resources.post_forwards
import eu.tintera.plume.resources.post_views
import eu.tintera.plume.resources.visibility_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Počty pod příspěvkem kanálu: zhlédnutí a přeposlání. Komentáře mají vlastní čip
 * ([CommentsChip]) — jdou otevřít, takže patří na cíl ťuknutí, ne do drobného řádku čísel.
 *
 * **Pod zprávou, ne v ní**, jako odznak doručení — a ze stejného důvodu barva patří PLOŠE
 * konverzace (`onSurfaceVariant`), ne bublině. Stojí na straně protější k čipům reakcí, které
 * visí přes dolní nároží na straně odesílatele; pod nimi by se oba řádky slily.
 *
 * Čísla jsou zkrácená („12 tis."), protože kanály mívají statisíce odběratelů a přesné číslo
 * pod každým příspěvkem je šum. Čtečka obrazovky dostane přesná čísla celými větami.
 * Přeposlání s nulou se nekreslí; zhlédnutí nula nemají (počítá se i autor).
 */
/**
 * Co stojí pod příspěvkem kanálu: čip komentářů (když má příspěvek diskusi) a vedle něj počty.
 * **Jeden řádek, ne dva nad sebou**: pod čipy reakcí by každý další řádek příspěvek jen
 * natahoval a mezi příspěvky vznikla díra.
 */
@Composable
internal fun PostFooter(
    stats: PostStats,
    isOutgoing: Boolean,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
    /** Podpis autora příspěvku (kanál s podpisy); stojí v řádku před počty. */
    signature: String? = null,
) {
    val comments = stats.comments
    if (comments == null) {
        PostStatsRow(stats, modifier, signature)
        return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Čip u zprávy, počty za ním — u odchozí zprávy zrcadlově.
        if (isOutgoing) PostStatsRow(stats, signature = signature)
        CommentsChip(count = comments, onClick = onOpenComments)
        if (!isOutgoing) PostStatsRow(stats, signature = signature)
    }
}

@Composable
internal fun PostStatsRow(stats: PostStats, modifier: Modifier = Modifier, signature: String? = null) {
    val description = buildList {
        if (signature != null) add(stringResource(Res.string.post_author, signature))
        if (stats.views > 0) add(stringResource(Res.string.post_views, stats.views.grouped()))
        if (stats.forwards > 0) add(stringResource(Res.string.post_forwards, stats.forwards.grouped()))
    }.joinToString(", ")
    if (description.isEmpty()) return

    Row(
        modifier = modifier
            .padding(top = POST_STATS_GAP, start = POST_STATS_INSET, end = POST_STATS_INSET)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(POST_STATS_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (signature != null) Signature(signature)
        if (stats.views > 0) Stat(Res.drawable.visibility_24dp, stats.views)
        if (stats.forwards > 0) Stat(Res.drawable.forward_24dp, stats.forwards)
    }
}

/**
 * Komentáře pod příspěvkem: ťuknutí otevře vlákno v diskusní skupině. S nulou vybízí
 * („Komentovat"), jinak nese počet — jako tlačítko pod příspěvkem u oficiálních klientů.
 *
 * Čip (`AssistChip`), ne text: je to jediný prvek pod příspěvkem, který něco dělá, a musí to
 * jít poznat. Stojí na straně bubliny, pod čipy reakcí.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = if (count > 0) {
                    pluralStringResource(Res.plurals.post_comments_count, count, compactCountText(count))
                } else {
                    stringResource(Res.string.post_comment_first)
                },
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(Res.drawable.mode_comment_24dp),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        modifier = modifier.padding(top = POST_STATS_GAP, start = POST_STATS_INSET, end = POST_STATS_INSET),
    )
}

/**
 * Podpis autora: ikona člověka a jméno, stejně drobně jako počty. Jméno se zkracuje — pod krátkým
 * příspěvkem je řádek jen tak široký jako sloupec zprávy.
 */
@Composable
private fun Signature(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            painter = painterResource(Res.drawable.person_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(POST_STATS_ICON_SIZE),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = SIGNATURE_MAX_WIDTH),
        )
    }
}

/** Nejdelší podpis v řádku; delší se zkrátí, aby počty vedle něj zůstaly vidět. */
private val SIGNATURE_MAX_WIDTH = 160.dp

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
