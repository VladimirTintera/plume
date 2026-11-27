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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.animateFloatAsState
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

/** Položka prohlížeče: fotka nebo video jedné zprávy chatu [chatId] a její popisek. */
@Immutable
data class ViewerItem(val chatId: Long, val messageId: Long, val media: MessageMedia, val caption: String)

/**
 * Video k přehrání a zpráva, ze které je: podle ní se pamatuje, kde uživatel přestal sledovat
 * (seznam Rozkoukaná videa otevírá video zase z té zprávy). [queue] jsou videa, která po něm
 * přehrávač pustí sám („kanál jako pořad"): v konverzaci novější zprávy, v kanálu starší
 * příspěvky — tím směrem, kterým se obrazovka čte.
 */
@Immutable
data class VideoRequest(
    val chatId: Long,
    val messageId: Long,
    val video: MessageMedia.Video,
    val caption: String,
    val queue: List<VideoRequest> = emptyList(),
) {
    /** Další video z fronty, se zbytkem fronty za sebou; `null` na konci. */
    fun next(): VideoRequest? = queue.firstOrNull()?.copy(queue = queue.drop(1))
}

/**
 * Videa ze zpráv [messages] v daném pořadí jako fronta pro přehrávač (nejvýš [MAX_VIDEO_QUEUE]).
 * Jen videa, která se dají přehrát (ne mizející ani tajná).
 */
fun videoQueue(chatId: Long, messages: List<ChatMessage>): List<VideoRequest> =
    messages.asSequence()
        .mapNotNull { message ->
            val video = message.media as? MessageMedia.Video ?: return@mapNotNull null
            if (video.selfDestructType != null || video.isSecret) return@mapNotNull null
            VideoRequest(chatId, message.id, video, message.text)
        }
        .take(MAX_VIDEO_QUEUE)
        .toList()

/** Kolik videí dopředu fronta nese (televize se dívá v kuse, ale ne celou historii). */
private const val MAX_VIDEO_QUEUE = 50

/**
 * Co jde s médiem ve zprávě udělat: pustit video ([playVideo]), nebo otevřít prohlížeč fotek
 * a videí na položce ([openViewer]). Jedna skupina místo dvou callbacků v každé funkci po cestě
 * od navigace ke zprávě (CLAUDE.md: „Parametry funkce se nepřidávají donekonečna").
 */
@Immutable
class MediaActions(
    val playVideo: (VideoRequest) -> Unit,
    val openViewer: (List<ViewerItem>, Int) -> Unit,
    val playAudio: (List<AudioItem>, Int) -> Unit,
    /** Kolečko (chat, zpráva): pustí ho na místě, hrající zastaví ([VideoNotePlayback]). */
    val toggleVideoNote: (Long, Long) -> Unit = { _, _ -> },
)

/**
 * Která položka alba je v bublině vybraná: šipky vlevo a vpravo na zaostřeném albu ji mění, OK
 * otevře prohlížeč rovnou na ní ([mediaOpenAction]). Dřív se otevíral vždy na první a k desáté
 * fotce se muselo dojít šipkami v prohlížeči. Drží ho položka seznamu, ať ho vidí obojí.
 */
@Stable
class AlbumPick {
    var index by mutableIntStateOf(0)
}

/** Fotky a videa ze zpráv (od nejstarší) v pořadí, v jakém je prohlížeč prochází. */
fun viewerItems(chatId: Long, messages: List<ChatMessage>): List<ViewerItem> = messages.mapNotNull { message ->
    val media = message.media
    if (media is MessageMedia.Photo || media is MessageMedia.Video) ViewerItem(chatId, message.id, media!!, message.text) else null
}

/**
 * Co se stane po OK na zprávě (nebo albu) [album] v chatu [chatId]: samotné video se rovnou
 * přehraje, fotka nebo album otevře prohlížeč na první položce a šipkami jde dál po všech médiích
 * z [allMessages] (celé načtené konverzace, jako na telefonu). Hlasovka nebo skladba se pustí
 * v přehrávači zvuku a po ní hrají další stejného druhu; OK na právě hrající ji pozastaví.
 * Zpráva bez média nic nedělá (`null`).
 *
 * Po videu přehrávač pustí další videa z [videosAfter] — bez něj novější videa z [allMessages]
 * (konverzace se čte od nejstarší dolů). Kanál dává frontu sám: jeho feed jde od nejnovějšího.
 */
fun mediaOpenAction(
    chatId: Long,
    album: List<ChatMessage>,
    allMessages: List<ChatMessage>,
    actions: MediaActions,
    videosAfter: List<VideoRequest>? = null,
    /** Položka alba vybraná šipkami v bublině ([AlbumPick]); prohlížeč se otevře na ní. */
    pick: AlbumPick? = null,
): (() -> Unit)? {
    val first = album.first()
    val media = first.media
    return when {
        media is MessageMedia.VoiceNote || media is MessageMedia.Audio -> {
            {
                val items = audioItems(chatId, allMessages, voice = media is MessageMedia.VoiceNote)
                actions.playAudio(items, items.indexOfFirst { it.messageId == first.id }.coerceAtLeast(0))
            }
        }
        media is MessageMedia.VideoNote -> {
            { actions.toggleVideoNote(chatId, first.id) }
        }
        album.size == 1 && media is MessageMedia.Video -> {
            {
                val queue = videosAfter ?: videoQueue(chatId, allMessages.filter { it.id > first.id })
                actions.playVideo(VideoRequest(chatId, first.id, media, first.text, queue))
            }
        }
        album.any { it.media is MessageMedia.Photo || it.media is MessageMedia.Video } -> {
            {
                val items = viewerItems(chatId, allMessages)
                val target = album.getOrNull(pick?.index ?: 0)?.id ?: first.id
                actions.openViewer(items, items.indexOfFirst { it.messageId == target }.coerceAtLeast(0))
            }
        }
        else -> null
    }
}

/** Jak dlouho zůstane popisek a počítadlo vidět, když nikdo nesahá na ovladač. */
private const val OVERLAY_TIMEOUT_MS = 4_000L

/**
 * Fotky a videa přes celou obrazovku. Šipky vlevo a vpravo jdou na předchozí a další položku,
 * OK na videu ho pustí ([MediaActions.playVideo], přehrávač leží nad prohlížečem), OK na fotce
 * ji přiblíží (2×, 3×, zpátky) a šipky ji pak posouvají, Zpět napřed zruší přiblížení, pak zavře.
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
    onPlayVideo: (VideoRequest) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    val item = items[index]
    // Přiblížení fotky: OK přiblíží (2×, 3×, pak zpátky), šipky v přiblížení posouvají místo
    // přechodu na jinou fotku, Zpět napřed přiblížení zruší. Posun je podíl největšího možného
    // posunu na každou stranu (-1..1), takže nezávisí na velikosti obrazovky.
    var zoom by remember(index) { mutableFloatStateOf(1f) }
    var panX by remember(index) { mutableFloatStateOf(0f) }
    var panY by remember(index) { mutableFloatStateOf(0f) }
    val zoomed = zoom > 1f
    BackHandler(enabled = zoomed) {
        zoom = 1f
        panX = 0f
        panY = 0f
    }
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
                val key = event.nativeKeyEvent.keyCode
                if (zoomed) {
                    // Krok posunu jako podíl největšího posunu: při větším přiblížení víc kroků.
                    val step = PAN_STEP / (zoom - 1f).coerceAtLeast(1f)
                    when (key) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> panX = (panX - step).coerceIn(-1f, 1f)
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> panX = (panX + step).coerceIn(-1f, 1f)
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> panY = (panY - step).coerceIn(-1f, 1f)
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> panY = (panY + step).coerceIn(-1f, 1f)
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                            zoom = ZOOM_LEVELS.firstOrNull { it > zoom } ?: 1f
                            if (zoom == 1f) {
                                panX = 0f
                                panY = 0f
                            }
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }
                if ((key == AndroidKeyEvent.KEYCODE_DPAD_CENTER || key == AndroidKeyEvent.KEYCODE_ENTER) && item.media is MessageMedia.Photo) {
                    zoom = ZOOM_LEVELS.first()
                    return@onPreviewKeyEvent true
                }
                when (key) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (index > 0) index--
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (index < items.lastIndex) index++
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        (item.media as? MessageMedia.Video)?.let { onPlayVideo(VideoRequest(item.chatId, item.messageId, it, item.caption)) }
                        true
                    }
                    // Nahoru a dolů jen ukážou popisek; fokus jinam nemá kam jít.
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                    else -> false
                }
            },
    ) {
        val scale by animateFloatAsState(zoom, tween(ZOOM_ANIMATION_MS), label = "viewer-zoom")
        val offsetX by animateFloatAsState(panX, tween(ZOOM_ANIMATION_MS), label = "viewer-pan-x")
        val offsetY by animateFloatAsState(panY, tween(ZOOM_ANIMATION_MS), label = "viewer-pan-y")
        Crossfade(targetState = item, animationSpec = tween(250), label = "viewer-item") { shown ->
            ViewerContent(shown, PhotoZoom(scale, offsetX, offsetY), sharp = zoomed)
        }
        if (!zoomed) {
            if (index > 0) EdgeHint(Res.drawable.chevron_left_24dp, Modifier.align(Alignment.CenterStart))
            if (index < items.lastIndex) EdgeHint(Res.drawable.chevron_right_24dp, Modifier.align(Alignment.CenterEnd))
        } else {
            // Kolikrát je fotka přiblížená; Zpět přiblížení zruší.
            Text(
                "${zoom.toInt()}×",
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = SAFE_VERTICAL, end = SAFE_HORIZONTAL)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
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

/** Přiblížení fotky a posun jako podíl největšího možného posunu (-1..1) na každou osu. */
private class PhotoZoom(val scale: Float, val panX: Float, val panY: Float)

/**
 * Přiblížení a posun obrázku [image] zobrazeného s `ContentScale.Fit`. Posun se omezí na to,
 * o kolik přiblížený obrázek SKUTEČNĚ přesahuje plochu — fotka užší než obrazovka (svislá,
 * 4 : 3) by jinak při posunu do strany vyjela a odkryla černý pruh (ověřeno na emulátoru).
 * Obrázek jede opačně, než se „dívá": šipka vpravo ukáže pravou část.
 */
private fun Modifier.zoomed(zoom: PhotoZoom, image: ImageBitmap): Modifier = graphicsLayer {
    val fit = minOf(size.width / image.width, size.height / image.height)
    val maxX = maxOf(0f, (image.width * fit * zoom.scale - size.width) / 2f)
    val maxY = maxOf(0f, (image.height * fit * zoom.scale - size.height) / 2f)
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = -zoom.panX * maxX
    translationY = -zoom.panY * maxY
}

/**
 * Obsah položky. [zoom] nese přiblížení a posun fotky; [sharp]: fotka je přiblížená a dekóduje
 * se ve dvojnásobném rozlišení (v 1080 px by při 3× byla rozmazaná).
 */
@Composable
private fun ViewerContent(item: ViewerItem, zoom: PhotoZoom = PhotoZoom(1f, 0f, 0f), sharp: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val media = item.media) {
            is MessageMedia.Photo -> {
                // Plná fotka, a dokud nedorazí, ta z bubliny (bývá už stažená). Ostrá varianta
                // se dekóduje až při přiblížení; než se dekóduje, stojí tam ta v 1080 px.
                val full = rememberImage(media.fullFileId, FULL_PX)
                val zoomedFull = if (sharp) rememberImage(media.fullFileId, ZOOMED_PX) else null
                val preview = rememberImage(media.fileId, PREVIEW_PX)
                (zoomedFull ?: full ?: preview)?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().zoomed(zoom, it))
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

/** Přiblížená fotka: dvojnásobné rozlišení, ať je při 3× ostrá. */
private const val ZOOMED_PX = 2160

/** Stupně přiblížení po stisku OK; po posledním zpátky na celou fotku. */
private val ZOOM_LEVELS = listOf(2f, 3f)

/** Krok posunu šipkou jako podíl největšího posunu na jednu stranu (při 2×). */
private const val PAN_STEP = 0.5f
private const val ZOOM_ANIMATION_MS = 220
private const val PREVIEW_PX = 540
