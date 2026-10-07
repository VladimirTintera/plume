package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.InlineQueryState
import eu.tintera.plume.core.telegram.InlineResult
import eu.tintera.plume.core.telegram.InlineRowKind
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.smart_toy_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Výsledky inline bota (`@gif kočka`) nad vstupním polem.
 *
 * **Nad polem, ne ve sheetu** — ze stejného důvodu jako napovídání příkazů ([BotCommandsPanel]):
 * výsledky se mění s každým písmenem dotazu a klávesnice musí zůstat otevřená. Proto i stejný
 * tvar, strop výšky a plovoucí vrstva ([FloatingSuggestion]), pod kterou konverzace neposkakuje.
 *
 * Obrázkové výsledky (GIF, fotka, obal videa, samolepka) jdou do mřížky, ostatní (článek, soubor,
 * kontakt, místo…) jsou řádky přes celou šířku. Jeden bot může vrátit obojí, takže se to
 * rozhoduje po výsledcích, ne po botovi.
 *
 * Ťuknutí výsledek rovnou pošle — jako oficiální klienti; výsledek je hotová zpráva připravená
 * botem, do pole se nic nevkládá.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InlineResultsPanel(
    inline: InlineQueryState?,
    visible: Boolean,
    onPick: (InlineResult) -> Unit,
    onButton: () -> Unit,
    onLoadMore: () -> Unit,
    /** Mléčné sklo nad seznamem zpráv (viz [FloatingSuggestion]). */
    glass: Modifier = Modifier,
) {
    // Při odjezdu se kreslí poslední stav, ne prázdno (stejně jako pruh připnutých). Stav, ne
    // obyčejné pole — zápis do pole by obsah uvnitř `AnimatedVisibility` nepřekreslil.
    val last = remember { mutableStateOf(inline) }
    if (inline != null) last.value = inline

    val hasContent = inline != null && (inline.results.isNotEmpty() || inline.button != null || inline.loading)
    FloatingSuggestion(
        visible = visible && hasContent,
        shape = RoundedCornerShape(PANEL_CORNER),
        glass = glass,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val shown = last.value ?: return@FloatingSuggestion
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CELL),
                modifier = Modifier.heightIn(max = MAX_HEIGHT),
                contentPadding = PaddingValues(6.dp),
            ) {
                // Nápověda od bota („Search GIFs…"), dokud se nic nenapsalo — říká, co bot umí.
                if (shown.query.isBlank() && shown.bot.placeholder.isNotBlank()) {
                    item(key = "placeholder", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = shown.bot.placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FloatingSuggestionColors.muted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                shown.button?.let { button ->
                    item(key = "button", span = { GridItemSpan(maxLineSpan) }) {
                        FilledTonalButton(
                            onClick = onButton,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(button.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                itemsIndexed(
                    shown.results,
                    key = { _, result -> "r-${result.id}" },
                    span = { _, result -> if (result is InlineResult.Row) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                ) { index, result ->
                    InlineResultItem(result, onPick)
                    LoadMoreAtEnd(index, shown.results.size, onLoadMore)
                }
                if (shown.loading) {
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(color = FloatingSuggestionColors.accent)
                        }
                    }
                }
            }
    }
}

@Composable
private fun InlineResultItem(result: InlineResult, onPick: (InlineResult) -> Unit) {
    when (result) {
        // Tentýž GIF jako ve výběru GIFů: hraje, dokud je vidět, velký se sám nestahuje.
        is InlineResult.Gif -> GifCell(gif = GifItem(result.animation), onPick = { onPick(result) })

        is InlineResult.Picture -> Box(
            Modifier
                .padding(2.dp)
                .fillMaxWidth()
                .height(CELL_HEIGHT)
                .clip(RoundedCornerShape(CELL_CORNER))
                .clickable { onPick(result) },
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                fileId = result.fileId,
                contentDescription = stringResource(if (result.isVideo) Res.string.media_video else Res.string.media_photo),
                placeholder = result.minithumbnail,
                modifier = Modifier.fillMaxSize(),
            )
            if (result.isVideo) {
                // Obal videa by se od fotky nedal rozeznat; symbol přehrání na tmavém kolečku jako v bublině.
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White) {
                    Icon(
                        painterResource(Res.drawable.play_arrow_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp).size(20.dp),
                    )
                }
            }
        }

        is InlineResult.Sticker -> Box(
            Modifier
                .padding(2.dp)
                .fillMaxWidth()
                .height(CELL_HEIGHT)
                .clip(RoundedCornerShape(CELL_CORNER))
                .clickable { onPick(result) },
        ) {
            StickerPreview(result.sticker, playing = false, modifier = Modifier.fillMaxSize().padding(6.dp))
        }

        is InlineResult.Row -> ListItem(
            colors = FloatingSuggestionColors.listItem(),
            leadingContent = {
                Box(
                    Modifier.size(ROW_THUMBNAIL).clip(RoundedCornerShape(CELL_CORNER)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (result.thumbnailFileId != null) {
                        RemoteImage(fileId = result.thumbnailFileId, modifier = Modifier.fillMaxSize())
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(result.kind.icon), contentDescription = null)
                            }
                        }
                    }
                }
            },
            headlineContent = { Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = result.description.takeIf { it.isNotBlank() }?.let {
                { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            },
            modifier = Modifier.clip(RoundedCornerShape(CELL_CORNER)).clickable { onPick(result) },
        )
    }
}

private val InlineRowKind.icon: DrawableResource
    get() = when (this) {
        InlineRowKind.Article -> Res.drawable.description_24dp
        InlineRowKind.File -> Res.drawable.attach_file_24dp
        InlineRowKind.Audio -> Res.drawable.music_note_24dp
        InlineRowKind.Voice -> Res.drawable.mic_24dp
        InlineRowKind.Contact -> Res.drawable.person_24dp
        InlineRowKind.Location -> Res.drawable.location_on_24dp
        InlineRowKind.Game -> Res.drawable.smart_toy_24dp
    }

/**
 * Dotáhne další stránku, až se složí jedna z posledních položek — ne podle `layoutInfo`, to se
 * u mřížky minulo (viz „Sdílená média v profilu" v docs/poznatky/profil-chatu.md).
 */
@Composable
private fun LoadMoreAtEnd(index: Int, count: Int, onLoadMore: () -> Unit) {
    if (index < count - LOAD_MORE_THRESHOLD) return
    val current by rememberUpdatedState(onLoadMore)
    LaunchedEffect(count) { current() }
}

private const val LOAD_MORE_THRESHOLD = 4

/** Nejvýš tolik zabere panel; zbytek se roluje. Stejně jako napovídání příkazů. */
private val MAX_HEIGHT = 280.dp

/** Nejmenší šířka buňky mřížky — na telefonu tři sloupce, stejně jako výběr GIFů. */
private val CELL = 110.dp
private val CELL_HEIGHT = 96.dp
private val CELL_CORNER = 12.dp
private val ROW_THUMBNAIL = 40.dp
private val PANEL_CORNER = 20.dp
