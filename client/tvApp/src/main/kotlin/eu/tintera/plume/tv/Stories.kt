package eu.tintera.plume.tv

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.stories.StoriesState
import eu.tintera.plume.core.stories.next
import eu.tintera.plume.core.stories.previous
import eu.tintera.plume.core.stories.showPoster
import eu.tintera.plume.core.stories.storyListOrder
import eu.tintera.plume.core.stories.viewerPosters
import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StoryListKind
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chevron_left_24dp
import eu.tintera.plume.resources.chevron_right_24dp
import eu.tintera.plume.resources.my_stories_title
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.stories_stack
import eu.tintera.plume.resources.story_live_unsupported
import eu.tintera.plume.resources.story_unavailable
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/** Autoři s příběhy v hlavním seznamu, v pořadí TDLib (nezhlédnuté a vlastní napřed posouvá server). */
@Composable
fun rememberStoryPosters(): List<ActiveStories> {
    val active by koinInject<StoryRepository>().active.collectAsState()
    return remember(active) { storyListOrder(active.values, StoryListKind.Main) }
}

/**
 * Řada příběhů nad seznamem chatů: autor = kulatý avatar s lemem z dílků (barevné nezhlédnuté,
 * šedé zhlédnuté), pod ním jméno. Na televizi řada místo hromádky v pilulce, jak ji má telefon:
 * avatar v řádku chatu nejde ovladačem zaostřit zvlášť a kontakt bez chatu by se jinak k příběhům
 * nedostal. Je to první položka seznamu, takže s pohybem dolů odjede a chatům místo nebere.
 */
@Immutable
class StoryRow(
    val posters: List<ActiveStories>,
    /** Autor, jehož příběhy se zrovna prohlížely — po zavření prohlížeče na něj jde fokus zpátky. */
    val lastOpened: Long?,
    /** Leží nad seznamem prohlížeč (nebo přehrávač): seznam si pak fokus nebere. */
    val overlayOpen: Boolean,
    val onOpen: (Long) -> Unit,
)

@Composable
fun StoryRowContent(row: StoryRow, modifier: Modifier = Modifier) {
    val savedMessagesId = savedMessagesChatId()
    val returnFocus = remember { FocusRequester() }
    LaunchedEffect(row.overlayOpen) {
        if (!row.overlayOpen && row.lastOpened != null) runCatching { returnFocus.requestFocus() }
    }
    Column(modifier) {
        Text(
            stringResource(Res.string.stories_stack),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(row.posters, key = { it.chatId }) { poster ->
                val own = poster.chatId == savedMessagesId
                val title = if (own) stringResource(Res.string.my_stories_title) else poster.title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(STORY_ITEM_WIDTH),
                ) {
                    val shape = poster.kind.avatarShape()
                    Surface(
                        onClick = { row.onOpen(poster.chatId) },
                        shape = ClickableSurfaceDefaults.shape(shape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                        modifier = if (poster.chatId == row.lastOpened) Modifier.focusRequester(returnFocus) else Modifier,
                    ) {
                        StoryAvatar(poster, title, shape)
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Avatar s lemem: dílek na příběh, od horního středu po směru hodinových ručiček, nejstarší první. */
@Composable
private fun StoryAvatar(poster: ActiveStories, title: String, shape: Shape) {
    val unread = MaterialTheme.colorScheme.primary
    val read = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Box(
        Modifier
            .padding(4.dp)
            .size(STORY_AVATAR + 12.dp)
            .drawWithCache {
                val stroke = 3.dp.toPx()
                val segments = storyRingSegments(poster, shape, stroke, inset = 0f)
                onDrawBehind { drawStoryRing(segments, unread, read, stroke) }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Vlastní příběh má fotku účtu (u Uložených zpráv by ChatAvatar dal záložku).
        ChatAvatar(fileId = poster.avatarFileId, title = title, size = STORY_AVATAR, savedMessages = false, shape = shape)
    }
}

/**
 * Dílky lemu příběhů jako cesty, druhá hodnota říká, jestli je příběh zhlédnutý. **Lem jde po
 * tvaru avataru** ([shape]: kruh, sušenka, čtverec), ne po kruhu — tvar nese druh chatu a lem ho
 * nesmí přebít (jako `storyRing` na telefonu, `core/ui/StoryRing.kt` v `:client:shared`).
 * Dílek na příběh, od horního středu po směru hodinových ručiček, nejstarší první. [inset] > 0
 * kreslí dovnitř okraje plochy, záporný ven z ní (lem kolem avataru v řádku chatu, který nesmí
 * měnit jeho velikost).
 */
internal fun CacheDrawScope.storyRingSegments(
    poster: ActiveStories,
    shape: Shape,
    stroke: Float,
    inset: Float,
): List<Pair<Path, Boolean>> {
    val edge = inset + stroke / 2
    val outline = shape.createOutline(Size(size.width - 2 * edge, size.height - 2 * edge), layoutDirection, this)
    val path = Path().apply {
        addOutline(outline)
        translate(Offset(edge, edge))
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    if (length <= 0f) return emptyList()
    val seen = poster.stories.ifEmpty { listOf(null) }.map { it == null || it.id <= poster.maxReadStoryId }

    // Začátek obrysu se u každého tvaru liší (RoundRect začíná vlevo nahoře, polygon jinde),
    // proto se horní střed hledá vzorkováním.
    val samples = 128
    var start = 0f
    var best = Float.MAX_VALUE
    for (i in 0 until samples) {
        val distance = length * i / samples
        val point = measure.getPosition(distance)
        val score = abs(point.x - size.width / 2) + abs(point.y - edge)
        if (score < best) {
            best = score
            start = distance
        }
    }
    // Po směru hodinových ručiček = nahoře doprava. Obrys může jít i obráceně.
    val ahead = measure.getPosition((start + length / samples) % length)
    val direction = if (ahead.x >= measure.getPosition(start).x) 1f else -1f

    val count = seen.size
    val step = length / count
    // Kulaté konce dílek prodlouží o polovinu tloušťky na každé straně; mezera to musí unést.
    val gap = if (count == 1) 0f else min(stroke + RING_GAP_FRACTION * step, step * RING_MAX_GAP_SHARE)
    return seen.mapIndexed { index, read ->
        val segment = Path()
        if (count == 1) {
            measure.getSegment(0f, length, segment, true)
            segment.close()
        } else {
            val from = start + direction * (index * step + gap / 2)
            val to = start + direction * ((index + 1) * step - gap / 2)
            addRingArc(measure, length, min(from, to), max(from, to), segment)
        }
        segment to read
    }
}

/** Nakreslí dílky z [storyRingSegments]: nezhlédnuté v [unread], zhlédnuté tenčí v [read]. */
internal fun DrawScope.drawStoryRing(segments: List<Pair<Path, Boolean>>, unread: Color, read: Color, stroke: Float) {
    segments.forEach { (segment, seen) ->
        drawPath(
            segment,
            color = if (seen) read else unread,
            style = Stroke(width = if (seen) stroke * 0.6f else stroke, cap = StrokeCap.Round),
        )
    }
}

/** Úsek obrysu mezi dvěma vzdálenostmi, i přes konec cesty (obrys je uzavřený). */
private fun addRingArc(measure: PathMeasure, length: Float, from: Float, to: Float, into: Path) {
    val a = ((from % length) + length) % length
    val b = a + (to - from)
    if (b <= length) {
        measure.getSegment(a, b, into, true)
    } else {
        measure.getSegment(a, length, into, true)
        measure.getSegment(0f, b - length, into, false)
    }
}

/** Mezera mezi dílky jako podíl jednoho dílku (navíc k tloušťce čáry). */
private const val RING_GAP_FRACTION = 0.08f

/** Mezera nesmí sníst víc než tuhle část dílku — u desítek příběhů by z lemu zbyly tečky. */
private const val RING_MAX_GAP_SHARE = 0.45f

/**
 * Lem příběhů KOLEM avataru chatu [chatId] (v řádku seznamu, v hlavičce konverzace), když má
 * autor aktivní příběh; jinak nic. Kreslí se mimo plochu avataru — velikost řádku se nezmění,
 * ať má lem, nebo ne. Jde po tvaru avataru ([shape]). Vlastní příběhy (Uložené zprávy) lem
 * nemají: řádek nese záložku, ne fotku účtu, a „Moje příběhy" jsou v řadě nad seznamem.
 *
 * Jen ukazatel: OK na řádku otevře chat jako dřív, příběhy se otevírají z řady nad seznamem
 * (avatar v řádku nejde ovladačem zaostřit zvlášť).
 */
@Composable
fun Modifier.storyRing(
    chatId: Long,
    shape: Shape,
    savedMessages: Boolean = false,
    /**
     * Avatar leží na inverzní ploše — zaostřený řádek seznamu (`inverseSurface`, v tmavém motivu
     * světlá). `primary` na ní skoro nebylo vidět (ověřeno na emulátoru), patří tam
     * `inversePrimary`. Řádek to musí říct sám: odvodit to z `LocalContentColor` uvnitř avataru
     * na emulátoru nefungovalo (barva se nepřepnula), proč, zjištěné není.
     */
    onInverse: Boolean = false,
): Modifier {
    if (savedMessages) return this
    val active by koinInject<StoryRepository>().active.collectAsState()
    val poster = active[chatId]?.takeIf { it.stories.isNotEmpty() } ?: return this
    val colors = MaterialTheme.colorScheme
    val unread = if (onInverse) colors.inversePrimary else colors.primary
    val read = (if (onInverse) colors.inverseOnSurface else colors.onSurfaceVariant).copy(alpha = 0.45f)
    return drawWithCache {
        val stroke = RING_STROKE.toPx()
        val segments = storyRingSegments(poster, shape, stroke, inset = -(RING_GAP.toPx() + stroke))
        onDrawBehind { drawStoryRing(segments, unread, read, stroke) }
    }
}

private val RING_STROKE = 2.5.dp
private val RING_GAP = 3.dp

/**
 * Prohlížeč příběhů ovladačem. Příběh stojí uprostřed na výšku (9 : 16), autoři i jejich příběhy
 * se při otevření zmrazí (`StoriesState` z `core`, tentýž jako na telefonu): šipky vlevo
 * a vpravo jdou na předchozí a další příběh (za posledním autora na dalšího autora), nahoru
 * a dolů rovnou na předchozího a dalšího autora, OK pozastaví, Zpět zavře.
 *
 * - **Zhlédnutí**: `openStory` při přechodu na příběh, `closeStory` při odchodu (i zavření, přes
 *   aplikační scope). Bez nich se zhlédnutí nepočítá a lem zůstane barevný.
 * - **Fotka stojí 6 s od chvíle, kdy je načtená**, video běží, jak dlouho hraje. Příběh, který
 *   už není, nebo živé vysílání (televize ho neumí) ukáže větu a po 6 s jde dál.
 */
@Composable
fun StoriesViewerScreen(startChatId: Long, onClose: () -> Unit) {
    val stories = koinInject<StoryRepository>()
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    // Rovnou na vybraného autora (seznam je celý hlavní seznam příběhů): přes prvního by se
    // jeho příběh otevřel a TDLib by ho započítal jako zhlédnutý.
    var state by remember {
        val posters = viewerPosters(stories.active.value, startChatId)
        mutableStateOf(StoriesState(posters = posters, poster = posters.indexOfFirst { it.chatId == startChatId }.coerceAtLeast(0)))
    }
    BackHandler(onBack = onClose)
    LaunchedEffect(state.closed, state.posters.isEmpty()) { if (state.closed || state.posters.isEmpty()) onClose() }

    val current = state.current
    // Zhlédnutí se počítá mezi openStory a closeStory.
    DisposableEffect(current) {
        val key = current
        if (key != null) appScope.launch { stories.open(key.posterChatId, key.storyId) }
        onDispose { if (key != null) appScope.launch { stories.close(key.posterChatId, key.storyId) } }
    }
    // Obsah aktuálního příběhu a dalšího (ať přechod nečeká na síť).
    LaunchedEffect(current) {
        val next = state.next().current
        listOfNotNull(current, next).filterNot { it in state.pages }.forEach { key ->
            val page = stories.story(key.posterChatId, key.storyId)
            state = state.copy(pages = state.pages + (key to page))
        }
    }

    var paused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        paused = false
                        state = state.previous()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_NEXT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        paused = false
                        state = state.next()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        paused = false
                        state = state.showPoster(state.poster - 1)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        paused = false
                        // Za posledním autorem se prohlížeč zavře, jako po dohrání.
                        state = if (state.poster + 1 < state.posters.size) state.showPoster(state.poster + 1) else state.copy(closed = true)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        paused = !paused
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                        paused = false
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        paused = true
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val cardHeight = maxHeight - SAFE_VERTICAL * 2
        val cardWidth = cardHeight * 9f / 16f
        val poster = state.posters.getOrNull(state.poster)
        if (poster != null && current != null) {
            Box(Modifier.width(cardWidth).height(cardHeight).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111))) {
                // Klíč i s `restart`: zpět u úplně prvního příběhu ho pustí znovu od začátku.
                androidx.compose.runtime.key(current, state.restart) {
                    StoryPage(
                        page = state.pages[current],
                        paused = paused,
                        onFinished = { state = state.next() },
                        progressOf = { progress -> StoryHeader(poster, state.storyIndex(state.poster), progress, state.pages[current]?.postedAt ?: 0) },
                    )
                }
                if (paused) {
                    Box(
                        Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(Res.drawable.pause_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
            }
            // Šipky u příběhu: že jde vlevo zpět a vpravo dál.
            Row(
                Modifier.width(cardWidth + EDGE_HINT_SPACE * 2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoryEdgeHint(Res.drawable.chevron_left_24dp)
                StoryEdgeHint(Res.drawable.chevron_right_24dp)
            }
        }
    }
}

/**
 * Obsah jednoho příběhu a jeho průběh. [progressOf] kreslí hlavičku s průběhem nahoře na kartě
 * (dostane podíl 0..1), [onFinished] se zavolá po dohrání.
 */
@Composable
private fun StoryPage(
    page: MessageMedia.Story?,
    paused: Boolean,
    onFinished: () -> Unit,
    progressOf: @Composable (Float) -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        when {
            page == null -> LoadingScreen("")
            !page.isAvailable || page.isLive || page.content == null -> {
                StoryNotice(stringResource(if (page.isLive) Res.string.story_live_unsupported else Res.string.story_unavailable))
                TimedProgress(ready = true, paused = paused, onProgress = { progress = it }, onFinished = onFinished)
            }
            page.content is MessageMedia.Photo -> {
                val photo = page.content as MessageMedia.Photo
                val targetPx = with(LocalDensity.current) { STORY_PHOTO_HEIGHT.roundToPx() }
                val image = rememberImage(photo.fullFileId, targetPx) ?: rememberImage(photo.fileId, targetPx)
                image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    ?: LoadingScreen("")
                TimedProgress(ready = image != null, paused = paused, onProgress = { progress = it }, onFinished = onFinished)
            }
            page.content is MessageMedia.Video -> StoryVideo(
                video = page.content as MessageMedia.Video,
                paused = paused,
                onProgress = { progress = it },
                onFinished = onFinished,
            )
            else -> {
                StoryNotice(stringResource(Res.string.story_unavailable))
                TimedProgress(ready = true, paused = paused, onProgress = { progress = it }, onFinished = onFinished)
            }
        }
        if (page != null && page.caption.isNotBlank()) {
            Text(
                page.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp),
            )
        }
        progressOf(progress)
    }
}

/** Fotka a věta: [STORY_PHOTO_MS] od chvíle, kdy je obsah na obrazovce ([ready]); pauza čas zastaví. */
@Composable
private fun TimedProgress(ready: Boolean, paused: Boolean, onProgress: (Float) -> Unit, onFinished: () -> Unit) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ready, paused) {
        if (!ready || paused) return@LaunchedEffect
        while (elapsed < STORY_PHOTO_MS) {
            delay(TICK_MS)
            elapsed += TICK_MS
            onProgress(elapsed / STORY_PHOTO_MS)
        }
        onFinished()
    }
}

/**
 * Video příběhu: stáhne se celé, pak hraje (příběhy mají do minuty a jednotky MB — streamování
 * jako u videa ve zprávě by tu jen přidalo složitost). Průběh z přehrávače, po dohrání dál.
 */
@OptIn(UnstableApi::class)
@Composable
private fun StoryVideo(video: MessageMedia.Video, paused: Boolean, onProgress: (Float) -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val files = koinInject<FileRepository>()
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(video.fileId) {
        val state = files.file(video.fileId).first { it is FileState.Ready || it is FileState.Failed }
        val path = (state as? FileState.Ready)?.path
        if (path == null) {
            onFinished()
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.prepare()
        ready = true
    }
    LaunchedEffect(ready, paused) { if (ready) player.playWhenReady = !paused }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onFinished()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        snapshotFlow { ready }.first { it }
        while (true) {
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (video.durationSeconds * 1000L).coerceAtLeast(1)
            onProgress((player.currentPosition.toFloat() / duration).coerceIn(0f, 1f))
            delay(TICK_MS)
        }
    }
    Box(Modifier.fillMaxSize()) {
        rememberImage(video.thumbnailFileId, with(LocalDensity.current) { STORY_PHOTO_HEIGHT.roundToPx() })?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (ready) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        this.player = player
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LoadingScreen("")
        }
    }
}

/** Hlavička na kartě: dílky průběhu (jeden na příběh autora), pod nimi avatar, jméno a čas. */
@Composable
private fun StoryHeader(poster: ActiveStories, index: Int, progress: Float, postedAt: Int) {
    val savedMessagesId = savedMessagesChatId()
    val own = poster.chatId == savedMessagesId
    val title = if (own) stringResource(Res.string.my_stories_title) else poster.title
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            poster.stories.indices.forEach { i ->
                val fill = when {
                    i < index -> 1f
                    i == index -> progress
                    else -> 0f
                }
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))) {
                    Box(Modifier.fillMaxWidth(fill).height(3.dp).background(Color.White))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(fileId = poster.avatarFileId, title = title, size = 32.dp, shape = poster.kind.avatarShape())
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (postedAt > 0) {
                Spacer(Modifier.width(8.dp))
                val time = if (postedAt.isSameDayAs(nowSeconds())) postedAt.asTimeOfDay() else postedAt.asDate()
                Text(time, style = MaterialTheme.typography.labelMedium.tabular(), color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun StoryNotice(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StoryEdgeHint(icon: org.jetbrains.compose.resources.DrawableResource) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

private val STORY_AVATAR = 56.dp
private val STORY_ITEM_WIDTH = 84.dp
private val EDGE_HINT_SPACE = 72.dp

/** Výška, na kterou se fotka příběhu dekóduje (karta je skoro na výšku obrazovky). */
private val STORY_PHOTO_HEIGHT: Dp = 540.dp

/** Jak dlouho stojí fotka (a věta u příběhu, který nejde ukázat), jako na telefonu. */
private const val STORY_PHOTO_MS = 6_000f
private const val TICK_MS = 50L
