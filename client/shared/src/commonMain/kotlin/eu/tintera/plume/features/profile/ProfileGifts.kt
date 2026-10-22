package eu.tintera.plume.features.profile

import androidx.compose.foundation.background
import eu.tintera.plume.resources.profile_gift_collection_chip
import eu.tintera.plume.resources.profile_gift_collection_empty
import eu.tintera.plume.resources.profile_story_albums_all
import eu.tintera.plume.core.ui.GroupEmptyRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.GiftKind
import eu.tintera.plume.core.telegram.ProfileGift
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.StickerPreview
import eu.tintera.plume.features.conversation.items.GiftCard
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.gift_number
import eu.tintera.plume.resources.gift_received_on
import eu.tintera.plume.resources.push_pin_filled_24dp
import eu.tintera.plume.resources.star_filled_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Dárky v profilu jako mřížka dlaždic: samolepka dárku, u sběratelského na pozadí v jeho
 * barvách a s pořadovým číslem, u běžného cena ve hvězdách. Ťuknutí otevře kartu dárku.
 * Stránky se dotahují, když se mřížka blíží konci.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileGiftsGrid(
    gifts: ProfileGifts,
    contentPadding: PaddingValues,
    onOpen: (ProfileGift) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    /** Výběr sbírky v čipech nad mřížkou; `null` = všechny dárky. */
    onSelectCollection: (collectionId: Int?) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GIFT_TILE_MIN),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (gifts.collections.isNotEmpty()) {
            item(key = "collections", span = { GridItemSpan(maxLineSpan) }) {
                CollectionChips(gifts, onSelectCollection)
            }
            if (gifts.items.isEmpty() && !gifts.loading && gifts.nextOffset == null) {
                item(key = "collection-empty", span = { GridItemSpan(maxLineSpan) }) {
                    GroupEmptyRow(stringResource(Res.string.profile_gift_collection_empty))
                }
            }
        }
        itemsIndexed(gifts.items, key = { _, gift -> gift.key }) { index, gift ->
            // Konec mřížky si řekne o další stránku (efektem, ne přímo v kompozici).
            if (gifts.nextOffset != null) LoadMoreAtEnd(index, gifts.items.size, onLoadMore)
            GiftTile(gift, onClick = { onOpen(gift) })
        }
        if (gifts.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }
}

/**
 * Sbírky dárků jako čipy — stejně jako alba příběhů v záložce Příběhy. Počet dárků je v čipu:
 * sbírka se vybírá podle jména, ale číslo řekne, jestli se ťuknutí vyplatí.
 */
@Composable
private fun CollectionChips(gifts: ProfileGifts, onSelect: (Int?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = gifts.collectionId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Res.string.profile_story_albums_all)) },
            )
        }
        items(gifts.collections, key = { it.id }) { collection ->
            FilterChip(
                selected = gifts.collectionId == collection.id,
                onClick = { onSelect(collection.id) },
                label = {
                    Text(
                        stringResource(Res.string.profile_gift_collection_chip, collection.name, collection.giftCount.grouped()),
                        maxLines = 1,
                        style = LocalTextStyle.current.tabularNumbers(),
                    )
                },
            )
        }
    }
}

@Composable
private fun GiftTile(gift: ProfileGift, onClick: () -> Unit) {
    val kind = gift.gift.kind
    val upgraded = kind as? GiftKind.Upgraded
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.aspectRatio(TILE_ASPECT).clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (upgraded != null) {
                            Modifier.background(
                                Brush.radialGradient(
                                    listOf(Color(OPAQUE or upgraded.centerColor), Color(OPAQUE or upgraded.edgeColor)),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(8.dp),
            ) {
                // V mřížce stojí samolepky na místě — desítky animací naráz by jen zatěžovaly.
                gift.gift.sticker?.let { StickerPreview(sticker = it, playing = false, modifier = Modifier.size(STICKER_SIZE)) }
                TileLabel(kind, onBackdrop = upgraded != null)
            }
            if (gift.isPinned) {
                Icon(
                    painterResource(Res.drawable.push_pin_filled_24dp),
                    contentDescription = stringResource(Res.string.chats_section_pinned),
                    tint = if (upgraded != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                )
            }
        }
    }
}

/** Pod samolepkou: číslo sběratelského dárku, nebo cena běžného ve hvězdách. */
@Composable
private fun TileLabel(kind: GiftKind, onBackdrop: Boolean) {
    // Na barevném pozadí sběratelského dárku bílá — barvy pozadí určuje Telegram, ne motiv.
    val color = if (onBackdrop) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    when (kind) {
        is GiftKind.Upgraded -> Text(
            text = stringResource(Res.string.gift_number, kind.number.toString()),
            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        is GiftKind.Regular -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(painterResource(Res.drawable.star_filled_24dp), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(kind.starCount.grouped(), style = MaterialTheme.typography.labelMedium.tabularNumbers(), color = color)
        }
        else -> Unit
    }
}

/** Dárek z profilu v sheetu: tatáž karta jako dárek ve zprávě a pod ní, kdy přišel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileGiftSheet(gift: ProfileGift, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            // Kopírovat kód umí jen dárkový kód Premium a ten v profilu není — akce nic nedělá.
            GiftCard(gift = gift.gift, onAction = {})
            Text(
                text = stringResource(Res.string.gift_received_on, gift.date.asDate()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Nejužší dlaždice — na telefon se vejdou tři vedle sebe. */
private val GIFT_TILE_MIN = 100.dp

private val STICKER_SIZE = 64.dp

/** Dlaždice o kousek vyšší než široká: pod samolepkou je řádek s cenou nebo číslem. */
private const val TILE_ASPECT = 0.85f

/** Barvy pozadí dárku chodí bez alfy (RGB). */
private const val OPAQUE = 0xFF000000.toInt()
