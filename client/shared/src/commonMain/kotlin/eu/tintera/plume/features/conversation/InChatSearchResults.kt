package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSearchHit
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.highlightRanges
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.chats.snippetOf
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_search_results
import eu.tintera.plume.resources.sender_you
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * **Výsledky hledání v konverzaci jako seznam** — vedle šipek „N z M", které se při stovkách
 * výsledků projít nedají. Řádek je jako u hledání napříč chaty: kdo, kdy, úryvek kolem
 * hledaného slova se zvýrazněním. Ukázaný výsledek je vybraný a seznam se otevře na něm.
 *
 * Seznam dotahuje další stránky sám, když dojede ke konci načtených ([onLoadMore]); ťuknutí
 * skočí na zprávu jako šipky ([onPick]) a sheet zavře — výsledek je pak vidět v konverzaci.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InChatSearchResultsSheet(
    search: InChatSearch,
    /** Kdo píše v soukromém chatu nebo kanálu, kde zpráva odesílatele nenese. */
    partnerName: String?,
    onPick: (index: Int) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = search.index.coerceAtLeast(0))
    val loadMore by rememberUpdatedState(onLoadMore)
    val canLoadMore = search.nextFromMessageId != null && !search.loading
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= search.results.size - LOAD_MORE_AHEAD) loadMore() }
    }
    val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    val you = stringResource(Res.string.sender_you)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        SheetTitle(
            pluralStringResource(Res.plurals.conversation_search_results, search.count, search.count),
            Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).navigationBarsPadding(),
        ) {
            itemsIndexed(search.results, key = { _, id -> id }) { index, messageId ->
                val hit = search.hits[messageId]
                val selected = index == search.index
                val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.groupRow
                val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                val supporting = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                ResultRow(
                    hit = hit,
                    sender = hit?.senderName ?: you.takeIf { hit?.isOutgoing == true } ?: partnerName,
                    query = search.resultsQuery,
                    highlight = highlight,
                    contentColor = content,
                    supportingColor = supporting,
                    modifier = Modifier
                        .groupedItem(index, search.results.size, container, selected = selected)
                        .clickable { onPick(index) },
                )
            }
            if (search.loading) {
                item(key = "loading") {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    hit: ChatSearchHit?,
    sender: String?,
    query: String,
    highlight: SpanStyle,
    contentColor: Color,
    supportingColor: Color,
    modifier: Modifier,
) {
    val text = hit?.text?.resolve().orEmpty()
    val snippet = remember(text, query, highlight) {
        // Zalomení by ve dvouřádkové ukázce plýtvalo místem.
        val plain = snippetOf(text.replace('\n', ' '), query).text
        buildAnnotatedString {
            append(plain)
            highlightRanges(plain, query).forEach { addStyle(highlight, it.first, it.last + 1) }
        }
    }
    ListItem(
        supportingContent = { Text(snippet, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            supportingContentColor = supportingColor,
        ),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                sender.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (hit != null) {
                Text(
                    hit.date.asRelativeTime(),
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = supportingColor,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** Kolik řádků před koncem načtených se dotáhne další stránka — ať seznam nedojede do prázdna. */
private const val LOAD_MORE_AHEAD = 5
