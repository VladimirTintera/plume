package eu.tintera.plume.features.videochat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.calls.CallVideo
import eu.tintera.plume.core.calls.CallVideoSource
import eu.tintera.plume.core.telegram.VideoChatParticipant
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.MediumButtonIcon
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.front_hand_24dp
import eu.tintera.plume.resources.logout_24dp
import eu.tintera.plume.resources.mic_off_24dp
import eu.tintera.plume.resources.screen_share_24dp
import eu.tintera.plume.resources.video_chat_active
import eu.tintera.plume.resources.video_chat_connecting
import eu.tintera.plume.resources.video_chat_ended
import eu.tintera.plume.resources.video_chat_ended_title
import eu.tintera.plume.resources.video_chat_failed
import eu.tintera.plume.resources.video_chat_hand_raised
import eu.tintera.plume.resources.video_chat_join_elsewhere
import eu.tintera.plume.resources.video_chat_leave
import eu.tintera.plume.resources.video_chat_listening
import eu.tintera.plume.resources.video_chat_muted
import eu.tintera.plume.resources.video_chat_nobody
import eu.tintera.plume.resources.video_chat_participants
import eu.tintera.plume.resources.video_chat_sends_video
import eu.tintera.plume.resources.video_chat_you
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.resources.videocam_off_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Videochat skupiny na poslech: obraz toho, kdo ho posílá (sdílená obrazovka přednostně),
 * pod ním mřížka účastníků a dole Opustit. Mluvit zatím nejde — posluchač je ztlumený a věta
 * pod obrazem to říká, ať se nikdo nediví, proč ho ostatní neslyší.
 *
 * Odchod z obrazovky (zpět i Opustit) z hovoru odejde.
 */
@Composable
fun VideoChatScreen(viewModel: VideoChatViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chatTitle by viewModel.chatTitle.collectAsStateWithLifecycle()
    val current = state
    val title = current?.title?.takeIf { it.isNotBlank() } ?: chatTitle.ifBlank { stringResource(Res.string.video_chat_active) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (current != null && current.participantCount > 0) {
                            Text(
                                pluralStringResource(Res.plurals.video_chat_participants, current.participantCount, current.participantCount.grouped()),
                                style = MaterialTheme.typography.bodyMedium,
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
            )
        },
        bottomBar = {
            if (current != null && current.phase != VideoChatPhase.Ended && current.phase != VideoChatPhase.Failed) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                    MediumButton(
                        onClick = {
                            viewModel.leave()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.widthIn(max = ContentWidth.Form).fillMaxWidth(),
                    ) {
                        MediumButtonIcon(Res.drawable.logout_24dp)
                        Text(stringResource(Res.string.video_chat_leave))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                !viewModel.supported -> IntroEmptyState(
                    icon = Res.drawable.videocam_off_24dp,
                    title = title,
                    text = stringResource(Res.string.video_chat_join_elsewhere),
                )

                current == null -> Unit

                current.phase == VideoChatPhase.Ended || current.phase == VideoChatPhase.Failed -> IntroEmptyState(
                    icon = Res.drawable.videocam_off_24dp,
                    title = stringResource(Res.string.video_chat_ended_title).takeIf { current.phase == VideoChatPhase.Ended } ?: title,
                    text = stringResource(if (current.phase == VideoChatPhase.Ended) Res.string.video_chat_ended else Res.string.video_chat_failed),
                )

                else -> VideoChatContent(current, Modifier.widthIn(max = ContentWidth.Conversation))
            }
        }
    }
}

@Composable
private fun VideoChatContent(state: VideoChatState, modifier: Modifier = Modifier) {
    val others = state.participants.filterNot { it.isCurrentUser }
    val showVideo = state.mainVideoOf != null || state.isRtmp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_WIDTH),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (showVideo) {
            item(key = "video", span = { GridItemSpan(maxLineSpan) }) {
                MainVideo(state)
            }
        }
        item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(if (state.phase == VideoChatPhase.Connecting) Res.string.video_chat_connecting else Res.string.video_chat_listening),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        items(state.participants, key = { it.id }) { participant ->
            ParticipantTile(participant, isShown = participant.id == state.mainVideoOf)
        }
        // Posluchač sám v seznamu: ať je vidět, že spojení běží a jen nikdo jiný nepřišel.
        if (others.isEmpty() && state.phase == VideoChatPhase.Live && !state.isRtmp) {
            item(key = "nobody", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(Res.string.video_chat_nobody),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Obraz účastníka (nebo RTMP vysílání) na šířku obsahu; dokud nepřijde, čeká se. */
@Composable
private fun MainVideo(state: VideoChatState) {
    val name = state.participants.firstOrNull { it.id == state.mainVideoOf }?.name
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(VIDEO_ASPECT)
            .clip(ShapeDefaults.Large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        CallVideo(CallVideoSource.Live, mirror = false, fit = true, modifier = Modifier.fillMaxSize())
        if (!state.hasVideo) LoadingIndicator()
        if (name != null) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = ShapeDefaults.Small,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

/** Účastník: fotka, jméno a stav ikonami — ztlumený, hlásí se, posílá obraz. */
@Composable
private fun ParticipantTile(participant: VideoChatParticipant, isShown: Boolean) {
    val name = if (participant.isCurrentUser) stringResource(Res.string.video_chat_you) else participant.name
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Avatar(chatId = participant.id, title = participant.name, avatarFileId = participant.avatarFileId, size = AVATAR_SIZE)
        Text(
            name,
            style = if (isShown) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (participant.isHandRaised) StatusIcon(Res.drawable.front_hand_24dp, stringResource(Res.string.video_chat_hand_raised), MaterialTheme.colorScheme.tertiary)
            if (participant.screen != null) {
                StatusIcon(Res.drawable.screen_share_24dp, stringResource(Res.string.video_chat_sends_video), MaterialTheme.colorScheme.primary)
            } else if (participant.camera != null) {
                StatusIcon(Res.drawable.videocam_24dp, stringResource(Res.string.video_chat_sends_video), MaterialTheme.colorScheme.primary)
            }
            if (participant.isMuted) StatusIcon(Res.drawable.mic_off_24dp, stringResource(Res.string.video_chat_muted), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusIcon(icon: DrawableResource, description: String, tint: Color) {
    Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
}

private val TILE_WIDTH = 96.dp
private val AVATAR_SIZE = 64.dp
private const val VIDEO_ASPECT = 16f / 9f
