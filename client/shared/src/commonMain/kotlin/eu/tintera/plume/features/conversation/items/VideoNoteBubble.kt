package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.rememberFileState
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.rememberVideoPlayer
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_video_note
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.volume_off_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Obsluha videa kolečka napojená na obrazovku; mimo konverzaci (nabídka) nedělá nic. */
internal class VideoNoteHandlers(
    /** Hraje TOHLE kolečko se zvukem? Čte se v kompozici kolečka. */
    val sounding: () -> Boolean,
    val onToggleSound: () -> Unit,
    /** Přehrávač kolečka se zvukem; drží ho obrazovka ([rememberSoundingVideoNote]). */
    val shared: () -> SoundingVideoNote? = { null },
) {
    companion object {
        val None = VideoNoteHandlers({ false }, {})
    }
}

/**
 * Video kolečko v konverzaci.
 *
 * **Hraje samo, ve smyčce a bez zvuku, jako GIF** — stejný přehrávač, stejný strop velikosti
 * ([MessageMedia.VideoNote.autoPlays]), stejně se nestahuje nad ním. **Zvuk až na ťuknutí**:
 * kolečko se pustí od začátku, se zvukem a bez smyčky, a po obvodu běží průběh. Další ťuknutí
 * nebo dohrání ho vrátí do tiché smyčky. Se zvukem hraje v konverzaci nejvýš jedno kolečko —
 * o tom, které, rozhoduje ViewModel ([VideoNoteHandlers.sounding]), protože ví i o hlasovkách.
 *
 * **Se zvukem hraje přehrávač obrazovky, ne položky** ([SoundingVideoNote]): odscrollované
 * kolečko hraje dál v docku a bublina si obraz bere zpátky, až je zase vidět. Pozastavené
 * (z docku) se ťuknutím pustí dál, hrající ťuknutí vrátí do tiché smyčky.
 */
@Composable
internal fun VideoNoteBubble(
    note: MessageMedia.VideoNote,
    handlers: VideoNoteHandlers,
    onRequestMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sounding = handlers.sounding()
    val shared = if (sounding) handlers.shared() else null
    var requested by remember(note.fileId) { mutableStateOf(note.autoPlays) }
    // Se zvukem stahuje a přehrává obrazovka; vlastní tichá smyčka jen bez něj.
    val fileState = rememberFileState(note.fileId.takeIf { requested && !sounding })
    val path = (fileState as? FileState.Ready)?.path
    val description = stringResource(Res.string.content_video_note)
    val elapsedSeconds by remember(shared) {
        derivedStateOf { ((shared?.playback?.positionMs ?: 0L) / 1000).toInt() }
    }
    val paused by remember(shared) {
        derivedStateOf { shared?.playback?.let { !it.loading && !it.playing } == true }
    }

    Box(
        modifier = modifier
            .size(VIDEO_NOTE_SIZE)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            // Dlouhý stisk se opakuje — potomek s vlastním klikáním ho k bublině nepustí.
            .combinedClickable(
                onClick = {
                    requested = true
                    if (paused) shared?.toggle() else handlers.onToggleSound()
                },
                onLongClick = onRequestMenu,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Náhled drží místo, dokud video nedorazí, a zůstává pod přehrávačem.
        note.thumbnailFileId?.let { thumbnail ->
            RemoteImage(
                fileId = thumbnail,
                placeholder = note.minithumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        when {
            // Obraz kreslí jen jedna plocha: dokud je kolečko v docku, bublina ukazuje náhled.
            shared != null -> if (!shared.docked) VideoSurface(shared.player, Modifier.fillMaxSize())
            !sounding && path != null -> {
                val player = rememberVideoPlayer()
                LaunchedEffect(player, path) {
                    player.load(path, loop = true, muted = true)
                    player.play()
                }
                VideoSurface(player, Modifier.fillMaxSize())
            }
        }

        if (sounding) {
            CircularProgressIndicator(
                progress = { shared?.playback?.progress ?: 0f },
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = RING_TRACK_ALPHA),
                strokeWidth = RING_WIDTH,
                gapSize = 0.dp,
            )
        }

        if (paused) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = BADGE_ALPHA),
                contentColor = Color.White,
            ) {
                Icon(
                    painterResource(Res.drawable.play_arrow_24dp),
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(32.dp),
                )
            }
        }

        // Délka (se zvukem uplynulý čas) a přeškrtnutý reproduktor, dokud hraje potichu —
        // jinak by nebylo poznat, že se zvuk pustí ťuknutím.
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = Color.Black.copy(alpha = BADGE_ALPHA),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = formatDuration(if (sounding) elapsedSeconds else note.durationSeconds),
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                )
                if (!sounding) {
                    Icon(
                        painterResource(Res.drawable.volume_off_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp).size(12.dp),
                    )
                }
            }
        }
    }
}

/** Velikost kolečka; stejně velké jako u oficiálních klientů. */
internal val VIDEO_NOTE_SIZE = 200.dp

private const val BADGE_ALPHA = 0.55f
private const val RING_TRACK_ALPHA = 0.25f
private val RING_WIDTH = 3.dp
