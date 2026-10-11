package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.CustomEmojiItem
import eu.tintera.plume.core.telegram.CustomEmojiSet
import eu.tintera.plume.core.telegram.CustomEmojiSetSample
import eu.tintera.plume.core.ui.CustomEmojiImage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.custom_emoji_add_sets
import eu.tintera.plume.resources.custom_emoji_trending_failed
import eu.tintera.plume.resources.stickers_add
import eu.tintera.plume.resources.stickers_added
import eu.tintera.plume.resources.stickers_set_empty
import org.jetbrains.compose.resources.stringResource

/**
 * Ikona záložky sady vlastních emoji: její první emoji. Popis nese název sady — samotný obrázek
 * čtečce nic neřekne.
 */
@Composable
internal fun CustomEmojiTabIcon(set: CustomEmojiSet) {
    val cover = set.cover
    Box(
        Modifier.padding(vertical = 10.dp).size(CUSTOM_EMOJI_TAB_ICON).semantics { contentDescription = set.title },
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            CustomEmojiImage(id = cover.id, fallback = cover.emoji, modifier = Modifier.fillMaxSize())
        } else {
            Text(set.title.take(1), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Stránka jedné sady vlastních emoji: mřížka, ťuknutí vloží emoji do textu. Obsah se načítá až
 * při prvním zobrazení stránky, jako sady samolepek.
 */
@Composable
internal fun CustomEmojiPage(
    set: CustomEmojiSet,
    viewModel: StickerPickerViewModel,
    onPick: (CustomEmojiItem) -> Unit,
) {
    LaunchedEffect(set.id) { viewModel.loadCustomEmoji(set.id) }
    val contents by viewModel.customEmoji.collectAsStateWithLifecycle()
    val items = contents[set.id]

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            items == null -> LoadingIndicator()
            items.isEmpty() -> PageMessage(stringResource(Res.string.stickers_set_empty))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(CUSTOM_EMOJI_CELL),
                contentPadding = sheetListPadding(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    Box(
                        Modifier
                            .size(CUSTOM_EMOJI_CELL)
                            .clip(CircleShape)
                            .clickable { onPick(item) }
                            .semantics { contentDescription = item.emoji },
                        contentAlignment = Alignment.Center,
                    ) {
                        CustomEmojiImage(id = item.id, fallback = item.emoji, modifier = Modifier.size(CUSTOM_EMOJI_SIZE))
                    }
                }
            }
        }
    }
}

/**
 * Populární sady vlastních emoji k přidání (záložka „+"). Bez nich by uživatel, který žádnou
 * sadu nemá — na Androidu se sady emoji jinak přidávají jen z odkazu `t.me/addemoji` —, vlastní
 * emoji ve výběru vůbec neviděl.
 */
@Composable
internal fun CustomEmojiTrendingPage(viewModel: StickerPickerViewModel) {
    val trending by viewModel.trendingCustomEmoji.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val sets = trending
        when {
            sets == null -> LoadingIndicator()
            sets.isEmpty() -> PageMessage(stringResource(Res.string.custom_emoji_trending_failed))
            else -> LazyColumn(
                contentPadding = sheetListPadding(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Text(
                        stringResource(Res.string.custom_emoji_add_sets),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(sets, key = { it.id }) { set ->
                    TrendingCustomEmojiRow(set, onInstall = { viewModel.installSet(set.id) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun TrendingCustomEmojiRow(set: CustomEmojiSetSample, onInstall: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                set.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Přidaná sada ukazuje stav a přibude jako záložka; odebírá se v Nastavení → Sady.
            if (set.isInstalled) {
                Text(
                    stringResource(Res.string.stickers_added),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FilledTonalButton(onClick = onInstall) { Text(stringResource(Res.string.stickers_add)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            set.covers.take(TRENDING_CUSTOM_EMOJI_COVERS).forEach { item ->
                CustomEmojiImage(id = item.id, fallback = item.emoji, modifier = Modifier.size(CUSTOM_EMOJI_SIZE))
            }
        }
    }
}

@Composable
private fun PageMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp),
    )
}

/** Buňka jako u obyčejných emoji, ať se mřížky při přepnutí záložky neliší. */
private val CUSTOM_EMOJI_CELL = 44.dp

/** Obrázek o kus menší než buňka — vlastní emoji má kresbu až do krajů. */
private val CUSTOM_EMOJI_SIZE = 32.dp

private val CUSTOM_EMOJI_TAB_ICON = 24.dp

private const val TRENDING_CUSTOM_EMOJI_COVERS = 8
