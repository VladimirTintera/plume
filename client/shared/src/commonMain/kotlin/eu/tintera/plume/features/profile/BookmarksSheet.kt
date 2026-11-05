package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.telegram.DateTimeFormat
import eu.tintera.plume.core.telegram.DateTimePrecision
import eu.tintera.plume.core.ui.dateTimeText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bookmark_filled_24dp
import eu.tintera.plume.resources.menu_bookmark_remove
import eu.tintera.plume.resources.profile_bookmarks
import eu.tintera.plume.resources.summary_me
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Záložky zpráv v chatu (profil → Záložky), nebo ve všech chatech (menu účtu → Záložky, s názvem
 * chatu u každé — [chatTitles]). Ťuknutí otevře zprávu; plná záložka vpravo ji odebere. Řádek
 * ukazuje otisk zprávy z doby uložení (`MessageBookmark`), takže se nic nenačítá a záložka
 * smazané zprávy pořád říká, co to bylo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksSheet(
    bookmarks: List<MessageBookmark>,
    onOpen: (MessageBookmark) -> Unit,
    onRemove: (MessageBookmark) -> Unit,
    onDismiss: () -> Unit,
    /** Názvy chatů podle id — jen v přehledu přes všechny chaty; `null` = záložky jednoho chatu. */
    chatTitles: Map<Long, String>? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        SheetTitle(stringResource(Res.string.profile_bookmarks), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        // Blok zaoblených řádků, jako výsledky hledání: dřív holé řádky na ploše sheetu.
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(bookmarks, key = { _, it -> "${it.chatId}/${it.messageId}" }) { index, bookmark ->
                Row(
                    Modifier
                        .animateItem()
                        .groupedItem(index, bookmarks.size, MaterialTheme.colorScheme.groupRow)
                        .fillMaxWidth()
                        .clickable { onOpen(bookmark) }
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Přes všechny chaty: odkud záložka je, nad odesílatelem. U příspěvku
                        // kanálu je odesílatel kanál sám — dvakrát týž název by byl šum.
                        chatTitles?.let { titles -> titles[bookmark.chatId] }
                            ?.takeIf { it != bookmark.sender }
                            ?.let { title ->
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                bookmark.sender ?: stringResource(Res.string.summary_me),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                dateTimeText(
                                    bookmark.timestamp.toLong(),
                                    DateTimeFormat.Absolute(time = DateTimePrecision.Short, date = DateTimePrecision.Short, dayOfWeek = false),
                                ),
                                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Text(
                            bookmark.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = BOOKMARK_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onRemove(bookmark) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.bookmark_filled_24dp), contentDescription = stringResource(Res.string.menu_bookmark_remove))
                    }
                }
            }
        }
    }
}

private const val BOOKMARK_MAX_LINES = 3
