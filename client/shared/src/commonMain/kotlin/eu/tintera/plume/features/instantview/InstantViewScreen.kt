package eu.tintera.plume.features.instantview

import eu.tintera.plume.core.adaptive.ContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ArticleMedia
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.features.conversation.items.ArticleSegment
import eu.tintera.plume.features.conversation.items.articleSegments
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.instant_view_failed
import eu.tintera.plume.resources.instant_view_full_page
import eu.tintera.plume.resources.instant_view_open_browser
import eu.tintera.plume.resources.instant_view_retry
import eu.tintera.plume.resources.instant_view_views
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Čtečka Instant View přes celou obrazovku. Stránka je tentýž strom bloků jako článek ve zprávě,
 * převedený na text s médii (`toRichBody`); tady se kreslí po úsecích ([articleSegments]) —
 * text jako [RichText] s klikatelnými odkazy, fotky přes celou šířku, video a GIF náhledem.
 *
 * **Vlastní sazba, ne bublina.** Bublina kreslí článek komponentami navázanými na zprávu
 * (rámeček, let do prohlížeče podle id zprávy), a stránka žádnou zprávou není.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InstantViewScreen(viewModel: InstantViewViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.close) { if (state.close) onBack() }
    val host = remember(viewModel.url) { hostOf(viewModel.url) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.page?.viewCount?.takeIf { it > 0 }?.let { views ->
                            Text(
                                pluralStringResource(Res.plurals.instant_view_views, views, views.grouped()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openInBrowser, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.open_in_new_24dp), contentDescription = stringResource(Res.string.instant_view_open_browser))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val page = state.page
        when {
            page != null -> {
                val segments = remember(page) { articleSegments(page.body.text, page.body.entities, page.body.article?.media.orEmpty()) }
                // Stránka psaná zprava doleva (arabsky, hebrejsky) se tak i sází.
                CompositionLocalProvider(LocalLayoutDirection provides if (page.isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        page.title?.let { title ->
                            item {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.widthIn(max = READING_WIDTH).fillMaxWidth(),
                                )
                            }
                        }
                        itemsIndexed(segments) { _, segment ->
                            Box(Modifier.widthIn(max = READING_WIDTH).fillMaxWidth()) {
                                when (segment) {
                                    is ArticleSegment.Text -> RichText(
                                        text = segment.text,
                                        entities = segment.entities,
                                        style = MaterialTheme.typography.bodyLarge,
                                        onLinkClick = viewModel::openLink,
                                    )
                                    is ArticleSegment.Media -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        segment.media.forEach { PageMedia(it) }
                                    }
                                }
                            }
                        }
                        if (page.isPartial) {
                            item {
                                FilledTonalButton(onClick = viewModel::openInBrowser) { Text(stringResource(Res.string.instant_view_full_page)) }
                            }
                        }
                    }
                }
            }
            state.failed -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.instant_view_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = viewModel::load) { Text(stringResource(Res.string.instant_view_retry)) }
                TextButton(onClick = viewModel::openInBrowser) { Text(stringResource(Res.string.instant_view_open_browser)) }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
    }
}

/**
 * Fotka přes celou šířku čtecího sloupce, video a GIF náhledem se značkou přehrát. Přehrát je
 * nejde — to dělá prohlížeč médií, který stojí na zprávě; tlačítko vpravo nahoře otevře stránku
 * v prohlížeči. Zvuk a soubor zůstávají slovem v textu.
 */
@Composable
private fun PageMedia(media: ArticleMedia) {
    val shape = RoundedCornerShape(12.dp)
    when (val m = media.media) {
        is MessageMedia.Photo -> RemoteImage(
            fileId = m.fileId,
            thumbnailFileId = m.thumbnailFileId,
            placeholder = m.minithumbnail,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspect(m.width, m.height).clip(shape),
        )
        is MessageMedia.Video -> VideoThumb(m.thumbnailFileId, m.minithumbnail, m.width, m.height, shape)
        is MessageMedia.Animation -> VideoThumb(m.thumbnailFileId, m.minithumbnail, m.width, m.height, shape)
        else -> Unit
    }
}

@Composable
private fun VideoThumb(thumbnail: Int?, mini: eu.tintera.plume.core.telegram.Minithumbnail?, width: Int, height: Int, shape: RoundedCornerShape) {
    Box(Modifier.fillMaxWidth().aspect(width, height).clip(shape), contentAlignment = Alignment.Center) {
        if (thumbnail != null) {
            RemoteImage(fileId = thumbnail, placeholder = mini, modifier = Modifier.fillMaxSize())
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)) {
            Icon(
                painterResource(Res.drawable.play_arrow_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(12.dp).size(32.dp),
            )
        }
    }
}

/**
 * Poměr stran média (oříznutý, aby extrémně úzký obrázek nezabral obrazovku); bez rozměrů pevná
 * výška, aby místo nepřeskakovalo, až obrázek dorazí. Uvnitř `LazyColumn` je výška omezená
 * jen šířkou, takže `aspectRatio` tu past z „Gotchas" (strop výšky) nemá.
 */
private fun Modifier.aspect(width: Int, height: Int): Modifier =
    if (width > 0 && height > 0) aspectRatio((width.toFloat() / height).coerceIn(MIN_RATIO, MAX_RATIO)) else height(200.dp)

private const val MIN_RATIO = 0.6f
private const val MAX_RATIO = 3f

/** Doména bez `www.` do hlavičky — stránka sama název nenese. */
internal fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")

/** Šířka čtecího sloupce; na tabletu by řádky přes celou šířku nešly číst. */
private val READING_WIDTH = ContentWidth.Reading
