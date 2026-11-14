package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.accounts.LocalAccount
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.rememberFileState
import eu.tintera.plume.core.video.VideoPlayer
import eu.tintera.plume.core.video.rememberVideoPlayer
import kotlinx.coroutines.delay

/**
 * Video kolečko, které hraje se zvukem.
 *
 * **Přehrávač patří obrazovce, ne položce.** Dokud ho držela položka, kolečko s odscrollováním
 * zmlklo — položka mimo obrazovku vypadne z kompozice i s přehrávačem — a dock neměl co ukázat.
 * Teď hraje dál a obraz kreslí ta plocha, která je zrovna vidět: bublina, nebo kolečko v docku
 * ([docked]). **Vždycky jen jedna**: na Androidu jde video do jediného `TextureView`, a plocha,
 * která by o něj přišla, by zůstala viset na posledním snímku.
 *
 * Průběh je [VoicePlayback] (pozice, délka, hraje), aby ho dock kreslil stejně jako hlasovku.
 */
@Stable
internal class SoundingVideoNote(val key: VoiceKey, val player: VideoPlayer) {
    var playback by mutableStateOf(VoicePlayback(key))
        internal set

    /** Obraz kreslí dock, ne bublina. */
    var docked by mutableStateOf(false)

    /** Pozastavené kolečko pustí dál, hrající pozastaví. */
    fun toggle() {
        if (playback.playing) player.pause() else player.play()
        playback = playback.copy(playing = !playback.playing)
    }
}

/**
 * Přehrávač kolečka [messageId], které má hrát se zvukem; `null`, když nehraje žádné.
 *
 * **Odchod z obrazovky zvuk vrátí** ([onEnded]): přehrávač s kompozicí obrazovky skončí, a kdyby
 * ViewModel dál věřil, že kolečko hraje, rozeznělo by se po návratu samo. Totéž po dohrání.
 */
@Composable
internal fun rememberSoundingVideoNote(
    chatId: Long,
    messageId: Long?,
    note: MessageMedia.VideoNote?,
    startMs: () -> Long,
    onEnded: (messageId: Long) -> Unit,
): SoundingVideoNote? {
    if (messageId == null || note == null) return null
    return key(messageId) { soundingVideoNote(VoiceKey(LocalAccount.current.slot, chatId, messageId), note, startMs, onEnded) }
}

@Composable
private fun soundingVideoNote(
    key: VoiceKey,
    note: MessageMedia.VideoNote,
    startMs: () -> Long,
    onEnded: (messageId: Long) -> Unit,
): SoundingVideoNote {
    val player = rememberVideoPlayer()
    val holder = remember(player) { SoundingVideoNote(key, player) }
    val ended by rememberUpdatedState(onEnded)
    val start by rememberUpdatedState(startMs)
    DisposableEffect(holder) { onDispose { ended(key.messageId) } }

    val path = (rememberFileState(note.fileId) as? FileState.Ready)?.path
    LaunchedEffect(holder, path) {
        if (path == null) return@LaunchedEffect
        player.load(path, loop = false, muted = false)
        val from = start()
        if (from > 0) player.seekTo(from)
        player.play()
        while (true) {
            delay(PROGRESS_TICK_MS)
            holder.playback = VoicePlayback(
                key = key,
                loading = false,
                playing = player.isPlaying,
                positionMs = player.positionMs,
                durationMs = player.durationMs.coerceAtLeast(0),
            )
            if (player.isEnded) {
                ended(key.messageId)
                break
            }
        }
    }
    return holder
}

private const val PROGRESS_TICK_MS = 50L
