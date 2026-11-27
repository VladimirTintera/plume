package eu.tintera.plume.tv

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_play_video_note
import eu.tintera.plume.resources.play_arrow_24dp
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("TvVideoNote")

/**
 * Které kolečko (video zpráva) právě hraje — v celé aplikaci nejvýš jedno, jako hlasovka.
 * Přehrávač sám žije v bublině ([VideoNoteBubble]): kolečko hraje na místě, kde je, a když
 * bublina odjede ze seznamu nebo se odejde z chatu, přehrávání skončí s ní.
 */
@Stable
class VideoNotePlayback {
    /** Chat a zpráva hrajícího kolečka; `null` = nehraje nic. */
    var playing by mutableStateOf<Pair<Long, Long>?>(null)

    /** OK na kolečku: pustí ho, na hrajícím zastaví. */
    fun toggle(chatId: Long, messageId: Long) {
        playing = if (playing == chatId to messageId) null else chatId to messageId
    }
}

val LocalVideoNotes = staticCompositionLocalOf<VideoNotePlayback?> { null }

/**
 * Kolečko v konverzaci: kulatý náhled bez bubliny, délka a čas dole. Hrající ukazuje video
 * přímo v kruhu (se zvukem) a kolem něj prstenec průběhu. Fokus nese obrys kruhu.
 *
 * Obraz jde do `TextureView`, ne `SurfaceView` (to dělá `PlayerView`): `SurfaceView` je
 * samostatná vrstva okna a oříznutí do kruhu by nerespektoval.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoNoteBubble(message: ChatMessage, note: MessageMedia.VideoNote, sender: String?, focused: Boolean) {
    val colors = MaterialTheme.colorScheme
    val playback = LocalVideoNotes.current
    val playingIn = playback?.playing?.takeIf { it.second == message.id }
    var progress by remember { mutableFloatStateOf(0f) }
    var positionSeconds by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(false) }
    Column(horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start) {
        if (sender != null) {
            Text(sender, style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
        }
        val ring = colors.primary
        Box(
            Modifier
                .size(NOTE_SIZE)
                // Prstenec průběhu kolem kruhu; fokus má vlastní prstenec vně (focusRing).
                .drawWithContent {
                    drawContent()
                    if (playingIn != null) {
                        val stroke = 6.dp.toPx()
                        drawArc(
                            color = ring,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke),
                        )
                    }
                }
                .focusRing(focused, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val px = with(LocalDensity.current) { NOTE_SIZE.roundToPx() }
            val thumbnail = rememberImage(note.thumbnailFileId, px)
            Box(Modifier.fillMaxSize().background(colors.surfaceVariant))
            if (playingIn != null) {
                VideoNoteSurface(
                    chatId = playingIn.first,
                    message = message,
                    note = note,
                    onLoading = { loading = it },
                    onRendering = { rendering = it },
                    onProgress = { fraction, seconds ->
                        progress = fraction
                        positionSeconds = seconds
                    },
                    onEnded = { if (playback.playing == playingIn) playback.playing = null },
                )
            }
            // Náhled zůstává nad obrazem, dokud nepřijde první snímek — jinak by kruh probliknul
            // prázdnou plochou.
            if (thumbnail != null && !(playingIn != null && rendering)) {
                Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            when {
                playingIn != null && loading -> LoadingIndicator(color = Color.White, modifier = Modifier.size(64.dp))
                playingIn == null -> Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.play_arrow_24dp),
                        contentDescription = stringResource(Res.string.conversation_play_video_note),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            // Délka (hrající: uběhlý čas) dole uprostřed, čas odeslání pod kruhem.
            val seconds = if (playingIn != null) positionSeconds ?: 0 else note.durationSeconds
            Text(
                seconds.asDuration(),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(timeLabel(message), style = MaterialTheme.typography.labelSmall.tabular(), color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        message.reactions.takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(6.dp))
            ReactionChips(it, colors.onSurface)
        }
    }
}

/**
 * Přehrávač hrajícího kolečka. Soubor se stáhne celý (kolečko má jednotky MB a nejvýš minutu),
 * pak hraje. Fokus zvuku řeší ExoPlayer: hlasovka nebo hudba se pozastaví, když kolečko začne,
 * a naopak. Příchozí nezhlédnuté kolečko se po spuštění nahlásí jako zhlédnuté.
 */
@Composable
private fun VideoNoteSurface(
    chatId: Long,
    message: ChatMessage,
    note: MessageMedia.VideoNote,
    onLoading: (Boolean) -> Unit,
    onRendering: (Boolean) -> Unit,
    onProgress: (Float, Int) -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val files = koinInject<FileRepository>()
    val conversations = koinInject<ConversationRepository>()
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = onRendering(true)

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                logger.w(error) { "Kolečko ${message.id} na televizi nejde přehrát." }
                onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            onRendering(false)
            onLoading(false)
            onProgress(0f, 0)
        }
    }
    LaunchedEffect(player) {
        onLoading(true)
        val ready = files.file(note.fileId).first { it is FileState.Ready || it is FileState.Failed }
        onLoading(false)
        val path = (ready as? FileState.Ready)?.path ?: run {
            logger.w { "Kolečko ${message.id} se na televizi nestáhlo." }
            onEnded()
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.prepare()
        player.play()
        if (!message.isOutgoing && !note.isViewed) {
            conversations.openContent(chatId, message.id)
                .onFailure { logger.w(it) { "Zhlédnutí kolečka ${message.id} se nenahlásilo." } }
        }
        while (true) {
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (note.durationSeconds * 1000L)
            val position = player.currentPosition.coerceAtLeast(0)
            onProgress(if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f, (position / 1000).toInt())
            delay(PROGRESS_TICK_MS)
        }
    }
    AndroidView(
        factory = { TextureView(it) },
        update = { player.setVideoTextureView(it) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Průměr kolečka na televizi — jako fotka v bublině, ať se dá dívat z gauče. */
private val NOTE_SIZE = 300.dp

private const val PROGRESS_TICK_MS = 100L
