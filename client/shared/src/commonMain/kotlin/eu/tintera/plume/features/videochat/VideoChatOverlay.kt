package eu.tintera.plume.features.videochat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.calls.rememberCallMicrophonePermission
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.mic_off_24dp
import eu.tintera.plume.resources.video_chat_bar_listening
import eu.tintera.plume.resources.video_chat_bar_speaking
import eu.tintera.plume.resources.video_chat_leave
import eu.tintera.plume.resources.video_chat_mute
import eu.tintera.plume.resources.video_chat_participants
import eu.tintera.plume.resources.video_chat_unmute
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Videochat, ve kterém posluchač zůstal po zavření jeho obrazovky: pruh nahoře nad aplikací,
 * jako zmenšený hovor ve dvou. Ťuknutí obrazovku videochatu otevře znovu ([onOpen]), vpravo
 * mikrofon (jen kdo smí mluvit) a Opustit.
 *
 * Není vidět, dokud je obrazovka videochatu otevřená, a ustoupí hovoru ve dvou — jeho pruh
 * leží na tomtéž místě.
 */
@Composable
fun VideoChatOverlay(onOpen: (chatId: Long, groupCallId: Int) -> Unit, modifier: Modifier = Modifier) {
    val session = koinInject<VideoChatSession>()
    val calls = koinInject<CallRepository>()
    val active by session.active.collectAsStateWithLifecycle()
    val screenShown by session.screenShown.collectAsStateWithLifecycle()
    val call by calls.active.collectAsStateWithLifecycle()
    val current = active?.takeIf { it.running && !screenShown && call == null }
    // Poslední videochat zůstává i během odjezdu, kdy už je `current` null.
    val last = remember { arrayOf<ActiveVideoChat?>(null) }
    if (current != null) last[0] = current
    AnimatedVisibility(
        visible = current != null,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        last[0]?.let { chat -> VideoChatBar(chat, session, onOpen = { onOpen(chat.chatId, chat.groupCallId) }) }
    }
}

@Composable
private fun VideoChatBar(chat: ActiveVideoChat, session: VideoChatSession, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val microphone = rememberCallMicrophonePermission()
    val state = chat.state
    val status = stringResource(if (state.micOn) Res.string.video_chat_bar_speaking else Res.string.video_chat_bar_listening)
    val count = state.participantCount.takeIf { it > 0 }
        ?.let { pluralStringResource(Res.plurals.video_chat_participants, it, it.grouped()) }
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(status, count).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.canTalk) {
                FilledIconToggleButton(
                    // Zaškrtnutý = ztlumený, jako v pruhu hovoru ve dvou.
                    checked = !state.micOn,
                    onCheckedChange = { muted ->
                        if (muted) {
                            scope.launch { session.setMuted(true) }
                        } else {
                            microphone.request { granted -> if (granted) scope.launch { session.setMuted(false) } }
                        }
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(
                        painterResource(if (state.micOn) Res.drawable.mic_24dp else Res.drawable.mic_off_24dp),
                        contentDescription = stringResource(if (state.micOn) Res.string.video_chat_mute else Res.string.video_chat_unmute),
                    )
                }
            }
            FilledTonalIconButton(
                onClick = session::leave,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painterResource(Res.drawable.call_24dp),
                    contentDescription = stringResource(Res.string.video_chat_leave),
                    modifier = Modifier.rotate(135f),
                )
            }
        }
    }
}
