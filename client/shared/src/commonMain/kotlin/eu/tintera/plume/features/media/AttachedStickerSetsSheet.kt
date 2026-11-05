package eu.tintera.plume.features.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.features.conversation.StickerPreview
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_attached_stickers
import eu.tintera.plume.resources.stickers_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sady samolepek nalepených na fotku nebo video, když jich je víc. Ťuknutí otevře sadu
 * (`StickerSetSheet`); jediná sada se otevírá rovnou a tenhle seznam se neukáže.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachedStickerSetsSheet(
    sets: List<StickerSetSummary>,
    onOpen: (setId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.padding(bottom = 16.dp)) {
            SheetTitle(
                stringResource(Res.string.media_attached_stickers),
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
            )
            val container = MaterialTheme.colorScheme.groupRow
            LazyColumn {
                itemsIndexed(sets, key = { _, set -> set.id }) { index, set ->
                    ListItem(
                        headlineContent = { Text(set.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = set.size.takeIf { it > 0 }?.let { count ->
                            { Text(pluralStringResource(Res.plurals.stickers_count, count, count)) }
                        },
                        leadingContent = set.cover?.let { cover ->
                            { StickerPreview(sticker = cover, playing = false, modifier = Modifier.size(40.dp)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = container),
                        modifier = Modifier.groupedItem(index, sets.size, container).clickable { onOpen(set.id) },
                    )
                }
            }
        }
    }
}
