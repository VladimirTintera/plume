package eu.tintera.plume.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.InstantViewPage
import eu.tintera.plume.core.telegram.InstantViewRepository
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.cloud_off_24dp
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.instant_view_failed
import eu.tintera.plume.resources.instant_view_mark
import eu.tintera.plume.resources.tv_instant_view_partial
import eu.tintera.plume.resources.tv_instant_view_read
import java.net.URI
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Náhled odkazu pod textem zprávy: web, nadpis a popis v tónovaném bloku. U stránky s Instant
 * View i výzva „OK: číst v aplikaci" — OK na zprávě otevře čtečku ([InstantViewScreen]).
 * Obrázek náhledu se nekreslí: bublina by s ním na televizi byla vyšší než obrazovka a pro
 * rozhodnutí, jestli článek otevřít, stačí nadpis.
 */
@Composable
fun LinkPreviewCard(preview: LinkPreview, content: Color, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(content.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        val site = preview.siteName?.takeIf { it.isNotBlank() } ?: hostOf(preview.url)
        if (site != null) Text(site, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        preview.title?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        preview.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (preview.instantView) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.tv_instant_view_read), style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

/**
 * Čtečka článku (Instant View) přes celou obrazovku: nadpis, text s formátováním a fotky
 * v místě, kde v článku stojí. Šipky nahoru a dolů rolují, Zpět zavře. Sloupec má šířku na
 * čtení, ne celou televizi.
 *
 * Média se jen ukazují (fotka, náhled videa) — ovladačem by se mezi nimi v textu nedalo vybírat.
 * Článek, který server poslal jen zčásti, končí větou, že zbytek je na webu.
 */
@Composable
fun InstantViewScreen(url: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val repository = koinInject<InstantViewRepository>()
    // `null` = načítá se; Result s `null` = stránka Instant View nemá.
    val page by produceState<Result<InstantViewPage?>?>(null, url) { value = repository.page(url) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val loaded = page
        when {
            loaded == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
            loaded.getOrNull() == null -> MessageScreen(stringResource(Res.string.instant_view_failed), Res.drawable.cloud_off_24dp, error = true)
            else -> Article(loaded.getOrThrow()!!)
        }
    }
}

@Composable
private fun Article(page: InstantViewPage) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { ARTICLE_SCROLL_STEP.toPx() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = ARTICLE_WIDTH)
                .fillMaxSize()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            scope.launch { scroll.animateScrollBy(stepPx) }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            scope.launch { scroll.animateScrollBy(-stepPx) }
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
                .verticalScroll(scroll)
                .padding(horizontal = SAFE_HORIZONTAL, vertical = SAFE_VERTICAL + 16.dp),
        ) {
            Text(
                listOfNotNull(hostOf(page.url), stringResource(Res.string.instant_view_mark)).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
            page.title?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(20.dp))
            ArticleBody(page)
            if (page.isPartial) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(Res.string.tv_instant_view_partial), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * Text článku rozdělený v místech médií (`Article.media`: úsek textu, který médium v článku
 * zastupuje, se nahradí obrázkem). Entity se do každého kusu ořežou a posunou.
 */
@Composable
private fun ArticleBody(page: InstantViewPage) {
    val body = page.body
    val content = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val media = body.article?.media.orEmpty().sortedBy { it.start }
    var cursor = 0
    for (item in media) {
        if (item.start < cursor) continue
        ArticleText(body.text, body.entities, cursor, item.start, content, accent)
        ArticleMedia(item.media, item.caption)
        cursor = item.end.coerceAtMost(body.text.length)
    }
    ArticleText(body.text, body.entities, cursor, body.text.length, content, accent)
}

@Composable
private fun ArticleText(text: String, entities: List<MessageEntity>, start: Int, end: Int, content: Color, accent: Color) {
    val piece = text.substring(start, end).trim('\n')
    if (piece.isBlank()) return
    // Offset ořezaných řádků na začátku se musí odečíst i z entit.
    val lead = text.substring(start, end).indexOf(piece)
    val shift = start + lead
    val sliced = entities.mapNotNull { entity ->
        val from = maxOf(entity.offset, shift)
        val to = minOf(entity.offset + entity.length, shift + piece.length)
        if (to > from) entity.copy(offset = from - shift, length = to - from) else null
    }
    Text(rememberRichText(piece, sliced, content, accent, spoilersRevealed = true), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ArticleMedia(media: MessageMedia, caption: String) {
    val fileId = when (media) {
        is MessageMedia.Photo -> media.fileId
        is MessageMedia.Video -> media.thumbnailFileId
        else -> null
    } ?: return
    val px = with(LocalDensity.current) { ARTICLE_MEDIA_HEIGHT.roundToPx() }
    rememberImage(fileId, px)?.let {
        Image(
            it,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = ARTICLE_MEDIA_HEIGHT).clip(RoundedCornerShape(12.dp)),
        )
    }
    if (caption.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
}

private fun hostOf(url: String): String? = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()

/** Šířka sloupce na čtení (jako `ContentWidth.Reading` na telefonu). */
private val ARTICLE_WIDTH = 900.dp
private val ARTICLE_MEDIA_HEIGHT = 420.dp
private val ARTICLE_SCROLL_STEP = 240.dp
