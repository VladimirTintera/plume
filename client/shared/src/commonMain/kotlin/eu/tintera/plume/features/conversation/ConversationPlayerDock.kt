package eu.tintera.plume.features.conversation

import androidx.compose.ui.input.key.key
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.ui.toggle
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.Dp
import eu.tintera.plume.features.conversation.items.SoundingVideoNote
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.resources.location_dock_you
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.content_video_note
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.features.conversation.items.MessageAction
import kotlin.math.abs
import eu.tintera.plume.resources.Res
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource

/** Pod tímhle podílem schované zprávy se poloha do docku nepočítá — řádek by se kreslil naprázdno. */
internal const val DOCK_EPSILON = 0.001f

/** Rozdíl průběhu, nad kterým se dock dojíždí animací místo vedení prstem (skok v seznamu). */
internal const val DOCK_JUMP = 0.35f

/**
 * Od jakého vysunutí docku kreslí obraz kolečka se zvukem dock, a ne bublina. Polovina: dokud je
 * víc kolečka vidět v seznamu, patří obraz jemu.
 */
private const val VIDEO_NOTE_HANDOFF = 0.5f

/** Mini přehrávač v docku: co se v něm kreslí a jak daleko je vysunutý ([rememberPlayerDock]). */
internal class PlayerDock(val player: DockedPlayer?, val shown: Boolean, val reveal: () -> Float)

/**
 * Hrající hlasovka, skladba nebo kolečko se zvukem, jejichž zpráva odjela z obrazovky. Měří se
 * stejně jako dock polohy; skladba z jiného chatu svou zprávu v seznamu nemá, a je proto v docku
 * celá. Kompozice čte jen KLÍČ hrajícího zvuku, průběh až řádek. Zvuk je v konverzaci nejvýš
 * jeden, takže kolečko a hlasovka se nepotkají.
 */
@Composable
internal fun rememberPlayerDock(
    viewModel: ConversationViewModel,
    state: ConversationState,
    voicePlayback: State<VoicePlayback?>,
    soundingVideoNote: State<SoundingVideoNote?>,
    listState: LazyListState,
    barsHeight: () -> Dp,
    spec: FiniteAnimationSpec<Float>,
): PlayerDock {
    val density = LocalDensity.current
    val source by rememberUpdatedState(state)
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val docked by remember(viewModel.chatId) {
        derivedStateOf(structuralEqualityPolicy()) {
            // Skladba z jiného účtu do konverzace nepatří (`docksIn`): otevřít ji jde jen v jejím
            // účtu — z docku nad seznamem chatů.
            soundingVideoNote.value?.let { it.key to false }
                ?: voicePlayback.value
                    ?.takeIf { it.docksIn(viewModel.slot, viewModel.chatId) }
                    ?.let { it.key to it.music }
        }
    }
    val target by remember(listState) {
        derivedStateOf {
            val key = docked?.first ?: return@derivedStateOf 0f
            if (!key.isIn(viewModel.slot, viewModel.chatId)) return@derivedStateOf 1f
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf 0f
            // 8 dp je horní odsazení seznamu nad pruhy, jako u docku polohy.
            val top = info.viewportEndOffset - with(density) { (8.dp + barsHeight()).toPx() }
            // Blok, ne zpráva: skladba z alba je položka s klíčem nejstarší zprávy alba.
            val block = source.blockKeyOf(key.messageId)
            val item = block?.let { b -> info.visibleItemsInfo.firstOrNull { it.key == b } }
            item?.let { dockProgress(it.offset, it.size, top, 0f) } ?: 1f
        }
    }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(reveal) {
        snapshotFlow { target }.collectLatest { to ->
            if (abs(to - reveal.value) > DOCK_JUMP) reveal.animateTo(to, spec) else reveal.snapTo(to)
        }
    }
    val shown by remember { derivedStateOf { target > DOCK_EPSILON || reveal.value > DOCK_EPSILON } }
    // Obraz kolečka si dock bere, až je víc v docku než v seznamu; kreslí ho vždycky jen jedna plocha.
    LaunchedEffect(soundingVideoNote.value) {
        val note = soundingVideoNote.value ?: return@LaunchedEffect
        snapshotFlow { shown && reveal.value >= VIDEO_NOTE_HANDOFF }.collect { note.docked = it }
    }
    val you = stringResource(Res.string.location_dock_you)
    val voiceLabel = stringResource(Res.string.conversation_voice_message)
    val videoNoteLabel = stringResource(Res.string.content_video_note)
    // Při zasouvání (zvuk dohrál, zpráva zpátky v pohledu) se kreslí poslední řádek, ne nic.
    val last = remember { arrayOf<DockedPlayer?>(null) }
    docked?.let { (key, music) ->
        val message = state.messages.firstOrNull { it.id == key.messageId }?.takeIf { key.isIn(viewModel.slot, viewModel.chatId) }
        val videoNote = (message?.media as? MessageMedia.VideoNote)?.takeIf { soundingVideoNote.value?.key == key }
        last[0] = if (videoNote != null) {
            DockedPlayer(
                key = key,
                music = false,
                title = message.let { if (it.isOutgoing) you else it.senderName ?: state.info?.title } ?: videoNoteLabel,
                label = videoNoteLabel,
                coverFileId = videoNote.thumbnailFileId,
                video = true,
            )
        } else if (music) {
            val audio = (message?.media as? MessageMedia.Audio)?.takeIf { key.part == VoiceKey.NO_PART }
            val track = nowPlaying?.takeIf { it.key == key }?.track
            DockedPlayer(
                key = key,
                music = true,
                title = (track?.title ?: audio?.title)?.ifBlank { null } ?: audio?.fileName.orEmpty(),
                label = track?.performer ?: audio?.performer?.ifBlank { null },
                coverFileId = track?.coverFileId ?: audio?.coverFileId,
                hasNext = nowPlaying?.takeIf { it.key == key }?.next != null,
            )
        } else {
            DockedPlayer(
                key = key,
                music = false,
                title = message?.let { if (it.isOutgoing) you else it.senderName ?: state.info?.title } ?: voiceLabel,
                label = voiceLabel.takeIf { message?.media is MessageMedia.VoiceNote },
            )
        }
    }
    return PlayerDock(player = last[0], shown = shown, reveal = { reveal.value })
}

/** Řádek mini přehrávače pod dockem polohy ([PlayerDockBar] nad stavem z [rememberPlayerDock]). */
@Composable
internal fun ConversationPlayerDock(
    dock: PlayerDock,
    chatHidden: Boolean,
    viewModel: ConversationViewModel,
    voicePlayback: State<VoicePlayback?>,
    soundingVideoNote: State<SoundingVideoNote?>,
    onOpenChat: (ChatTarget) -> Unit,
    glass: Modifier,
) {
    val player = dock.player ?: return
    if (!dock.shown || (chatHidden && player.key.isIn(viewModel.slot, viewModel.chatId))) return
    val note = { soundingVideoNote.value?.takeIf { player.video && it.key == player.key } }
    PlayerDockBar(
        player = player,
        playback = {
            if (player.video) note()?.playback else voicePlayback.value?.takeIf { it.key == player.key }
        },
        reveal = dock.reveal,
        onToggle = { if (player.video) note()?.toggle() else viewModel.togglePlaying() },
        onNext = viewModel::skipToNextTrack,
        onStop = {
            if (player.video) {
                viewModel.onAction(player.key.messageId, MessageAction.VideoNoteSoundEnded)
            } else {
                viewModel.stopPlaying()
            }
        },
        video = if (player.video) {
            { note()?.takeIf { it.docked }?.let { VideoSurface(it.player, Modifier.fillMaxSize()) } }
        } else {
            null
        },
        onShow = {
            if (player.key.isIn(viewModel.slot, viewModel.chatId)) {
                viewModel.jumpToMessage(player.key.messageId)
            } else {
                onOpenChat(ChatTarget(player.key.chatId, player.key.messageId))
            }
        },
        glass = glass,
    )
}
