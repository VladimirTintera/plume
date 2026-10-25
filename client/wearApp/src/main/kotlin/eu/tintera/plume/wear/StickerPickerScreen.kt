package eu.tintera.plume.wear

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.stickers_tab_favorites
import eu.tintera.plume.resources.stickers_tab_recent
import eu.tintera.plume.resources.wear_confirm_not_sent
import eu.tintera.plume.resources.wear_stickers_none
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("WearStickers")

/** Kolik samolepek z každého seznamu — na hodinkách se v desítkách nikdo nehrabe. */
private const val MAX_PER_SECTION = 18

private const val PER_ROW = 3

private val STICKER = 52.dp

/**
 * Výběr samolepky: oblíbené a naposledy použité (z telefonu i z hodinek, `recentStickers` je
 * na účtu), po třech v řádku. Ťuknutí samolepku pošle a vrátí se do konverzace, která ji uvidí
 * z `newMessages`. Celé sady na hodinkách nejsou — procházet je na malém displeji by bylo
 * pracné a nedávné pokryjí to, co člověk posílá.
 *
 * Náhledy (`thumbnailFileId`, WebP) i u animovaných a videosamolepek, jako v konverzaci:
 * animace by na hodinkách stála baterii.
 */
@Composable
fun StickerPickerScreen(chatId: Long, onDone: () -> Unit) {
    val stickers = koinInject<StickerRepository>()
    val conversations = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    val sections by remember {
        combine(stickers.favoriteStickers(), stickers.recentStickers()) { favorite, recent ->
            val favorites = favorite.take(MAX_PER_SECTION)
            // Oblíbená, která je i mezi nedávnými, podruhé ne.
            val recentOnly = recent.filterNot { item -> favorites.any { it.id == item.id } }.take(MAX_PER_SECTION)
            favorites to recentOnly
        }
    }.collectAsState(null)
    var failed by remember { mutableStateOf(false) }
    val send: (StickerItem) -> Unit = { sticker ->
        scope.launch {
            conversations.sendSticker(chatId, sticker, replyToMessageId = null)
                .onSuccess { onDone() }
                .onFailure {
                    logger.w(it) { "Samolepka z hodinek do chatu $chatId neodešla." }
                    failed = true
                }
        }
    }

    val current = sections
    when {
        current == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        current.first.isEmpty() && current.second.isEmpty() -> MessageScreen(stringResource(Res.string.wear_stickers_none), Res.drawable.sticker_24dp)
        else -> {
            val state = rememberTransformingLazyColumnState()
            val spec = rememberTransformationSpec()
            ScreenScaffold(scrollState = state) { contentPadding ->
                TransformingLazyColumn(state = state, contentPadding = contentPadding) {
                    section(spec, Res.string.stickers_tab_favorites, current.first, send)
                    section(spec, Res.string.stickers_tab_recent, current.second, send)
                }
            }
        }
    }
    FailureConfirmation(visible = failed, text = stringResource(Res.string.wear_confirm_not_sent), onDismiss = { failed = false })
}

/**
 * Nadpis a řádky samolepek. Každý řádek je vlastní položka seznamu: u okraje displeje se
 * zmenšuje zvlášť (jako mřížka reakcí v nabídce nad zprávou).
 */
private fun TransformingLazyColumnScope.section(
    spec: TransformationSpec,
    title: StringResource,
    items: List<StickerItem>,
    onSend: (StickerItem) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        ListHeader(
            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
            transformation = SurfaceTransformation(spec),
        ) { Text(stringResource(title)) }
    }
    items.chunked(PER_ROW).forEach { row ->
        item {
            Row(
                Modifier.fillMaxWidth().transformedItem(this, spec),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { sticker -> StickerCell(sticker, onClick = { onSend(sticker) }) }
            }
        }
    }
}

@Composable
private fun StickerCell(sticker: StickerItem, onClick: () -> Unit) {
    val preview = sticker.preview
    val sizePx = with(LocalDensity.current) { STICKER.roundToPx() }
    val bitmap = (preview.thumbnailFileId ?: preview.fileId)?.let { rememberPhotoBitmap(it, sizePx) }
    Box(
        Modifier
            .size(STICKER)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = preview.emoji, contentScale = ContentScale.Fit, modifier = Modifier.size(STICKER))
        } else {
            Text(preview.emoji, style = MaterialTheme.typography.titleLarge)
        }
    }
}
