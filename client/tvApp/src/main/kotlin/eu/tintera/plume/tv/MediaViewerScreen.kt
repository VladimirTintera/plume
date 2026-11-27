package eu.tintera.plume.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chevron_left_24dp
import eu.tintera.plume.resources.chevron_right_24dp
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.play_arrow_24dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Položka prohlížeče: fotka nebo video jedné zprávy a její popisek. */
@Immutable
data class ViewerItem(val messageId: Long, val media: MessageMedia, val caption: String)

/**
 * Co jde s médiem ve zprávě udělat: pustit video ([playVideo]), nebo otevřít prohlížeč fotek
 * a videí na položce ([openViewer]). Jedna skupina místo dvou callbacků v každé funkci po cestě
 * od navigace ke zprávě (CLAUDE.md: „Parametry funkce se nepřidávají donekonečna").
 */
@Immutable
class MediaActions(
    val playVideo: (MessageMedia.Video, String) -> Unit,
    val openViewer: (List<ViewerItem>, Int) -> Unit,
)

/** Fotky a videa ze zpráv (od nejstarší) v pořadí, v jakém je prohlížeč prochází. */
fun viewerItems(messages: List<ChatMessage>): List<ViewerItem> = messages.mapNotNull { message ->
    val media = message.media
    if (media is MessageMedia.Photo || media is MessageMedia.Video) ViewerItem(message.id, media!!, message.text) else null
}

/**
 * Co se stane po OK na zprávě (nebo albu) [album]: samotné video se rovnou přehraje, fotka nebo
 * album otevře prohlížeč na první položce a šipkami jde dál po všech médiích z [allMessages]
 * (celé načtené konverzace, jako na telefonu). Zpráva bez média nic nedělá (`null`).
 */
fun mediaOpenAction(album: List<ChatMessage>, allMessages: List<ChatMessage>, actions: MediaActions): (() -> Unit)? {
    val first = album.first()
    val media = first.media
    return when {
        album.size == 1 && media is MessageMedia.Video -> { { actions.playVideo(media, first.text) } }
        album.any { it.media is MessageMedia.Photo || it.media is MessageMedia.Video } -> {
            {
                val items = viewerItems(allMessages)
                actions.openViewer(items, items.indexOfFirst { it.messageId == first.id }.coerceAtLeast(0))
            }
        }
        else -> null
    }
}

/** Jak dlouho zůstane popisek a počítadlo vidět, když nikdo nesahá na ovladač. */
private const val OVERLAY_TIMEOUT_MS = 4_000L

/**
 * Fotky a videa přes celou obrazovku. Šipky vlevo a vpravo jdou na předchozí a další položku,
 * OK na videu ho pustí ([MediaActions.playVideo], přehrávač leží nad prohlížečem), Zpět zavře.
 * Fotka se ukáže hned ve variantě z bubliny (už je stažená) a vymění se za plnou, jakmile
 * dorazí. Popisek a „3 / 12" dole zmizí po čtyřech vteřinách a vrátí se s každým stiskem.
 *
 * [playerOpen]: po zavření přehrávače si prohlížeč vezme fokus zpátky.
 */
@Composable
fun MediaViewerScreen(
    items: List<ViewerItem>,
    startIndex: Int,
    playerOpen: Boolean,
    onPlayVideo: (MessageMedia.Video, String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    val item = items[index]
    var lastInteraction by remember { mutableLongStateOf(0L) }
    var overlayVisible by remember { mutableStateOf(true) }
    LaunchedEffect(lastInteraction, index) {
        overlayVisible = true
        delay(OVERLAY_TIMEOUT_MS)
        overlayVisible = false
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(playerOpen) { if (!playerOpen) runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInteraction = System.nanoTime()
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (index > 0) index--
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (index < items.lastIndex) index++
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        (item.media as? MessageMedia.Video)?.let { onPlayVideo(it, item.caption) }
                        true
                    }
                    // Nahoru a dolů jen ukážou popisek; fokus jinam nemá kam jít.
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                    else -> false
                }
            },
    ) {
        Crossfade(targetState = item, animationSpec = tween(250), label = "viewer-item") { shown ->
            ViewerContent(shown)
        }
        if (index > 0) EdgeHint(Res.drawable.chevron_left_24dp, Modifier.align(Alignment.CenterStart))
        if (index < items.lastIndex) EdgeHint(Res.drawable.chevron_right_24dp, Modifier.align(Alignment.CenterEnd))
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(horizontal = SAFE_HORIZONTAL, vertical = SAFE_VERTICAL)
                    .padding(top = 48.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    item.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (items.size > 1) {
                    Spacer(Modifier.width(24.dp))
                    Text(
                        "${index + 1} / ${items.size}",
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerContent(item: ViewerItem) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val media = item.media) {
            is MessageMedia.Photo -> {
                // Plná fotka, a dokud nedorazí, ta z bubliny (bývá už stažená).
                val full = rememberImage(media.fullFileId, FULL_PX)
                val preview = rememberImage(media.fileId, PREVIEW_PX)
                (full ?: preview)?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } ?: LoadingScreen("")
            }
            is MessageMedia.Video -> {
                rememberImage(media.thumbnailFileId, PREVIEW_PX)?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(Res.drawable.play_arrow_24dp),
                            contentDescription = stringResource(Res.string.media_play),
                            tint = Color.White,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                    Text(
                        media.durationSeconds.asDuration(),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            else -> {}
        }
    }
}

/** Šipka u kraje: že je vlevo nebo vpravo další položka. */
@Composable
private fun EdgeHint(icon: org.jetbrains.compose.resources.DrawableResource, modifier: Modifier) {
    Box(
        modifier.padding(horizontal = 24.dp).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

/** Plná fotka v rozlišení 1080p (kratší strana), náhled z bubliny v polovičním. */
private const val FULL_PX = 1080
private const val PREVIEW_PX = 540
