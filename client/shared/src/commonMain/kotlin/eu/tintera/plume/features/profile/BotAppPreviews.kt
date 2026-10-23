package eu.tintera.plume.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.ui.rememberFileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.FullScreenDialog
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.rememberVideoPlayer
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.profile_app_previews
import eu.tintera.plume.resources.profile_app_preview_position
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Ukázky mini aplikace v profilu bota (`getBotMediaPreviews`): řada náhledů na výšku pod
 * tlačítky, jako v obchodě s aplikacemi. Ťuknutí otevře [AppPreviewViewer].
 */
@Composable
internal fun BotAppPreviews(previews: List<MessageMedia>, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.profile_app_previews),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 0.dp)) {
            itemsIndexed(previews) { index, media ->
                Box(
                    Modifier
                        .height(PREVIEW_HEIGHT)
                        .aspectRatio(PREVIEW_ASPECT)
                        .clip(ShapeDefaults.Large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onOpen(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    PreviewStill(media, ContentScale.Crop)
                    if (media is MessageMedia.Video) {
                        Icon(
                            painterResource(Res.drawable.play_arrow_24dp),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.4f), ShapeDefaults.ExtraLarge).padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ukázky přes celou obrazovku, posun do stran. Video hraje ve smyčce, jen to na právě
 * ukázané stránce; ostatní stojí na náhledu (přehrávač je jeden na stránku a hraje jen vidět).
 */
@Composable
internal fun AppPreviewViewer(previews: List<MessageMedia>, initial: Int, onDismiss: () -> Unit) {
    // Přes celé okno i pod systémové lišty — jinak by nad a pod ukázkou prosvítal profil.
    FullScreenDialog(onDismiss = onDismiss) {
        val pager = rememberPagerState(initialPage = initial.coerceIn(0, (previews.size - 1).coerceAtLeast(0))) { previews.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val media = previews.getOrNull(page) ?: return@HorizontalPager
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (media is MessageMedia.Video && pager.currentPage == page) {
                        PreviewVideo(media)
                    } else {
                        PreviewStill(media, ContentScale.Fit)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp)) {
                IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes(), modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close), tint = Color.White)
                }
                if (previews.size > 1) {
                    Text(
                        stringResource(Res.string.profile_app_preview_position, pager.currentPage + 1, previews.size),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewStill(media: MessageMedia, scale: ContentScale) {
    when (media) {
        is MessageMedia.Photo -> RemoteImage(
            fileId = media.fullFileId,
            thumbnailFileId = media.fileId,
            placeholder = media.minithumbnail,
            contentScale = scale,
            background = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
        )
        is MessageMedia.Video -> RemoteImage(
            fileId = media.thumbnailFileId ?: media.fileId,
            placeholder = media.minithumbnail,
            contentScale = scale,
            background = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
        )
        else -> Unit
    }
}

/** Video ukázky ve smyčce; dokud se nestáhne, stojí náhled. */
@Composable
private fun PreviewVideo(media: MessageMedia.Video) {
    val path = (rememberFileState(media.fileId) as? FileState.Ready)?.path
    PreviewStill(media, ContentScale.Fit)
    if (path != null) {
        val player = rememberVideoPlayer()
        VideoSurface(player, Modifier.fillMaxSize())
        LaunchedEffect(player, path) {
            player.load(path, loop = true)
            player.play()
        }
    }
}

/** Ukázky jsou na výšku jako příběhy (9 : 16). */
private const val PREVIEW_ASPECT = 9f / 16f
private val PREVIEW_HEIGHT = 200.dp
