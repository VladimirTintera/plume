package eu.tintera.plume.features.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.rememberRecordPermission
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.call_answer
import eu.tintera.plume.resources.call_bar_calling
import eu.tintera.plume.resources.call_bar_connecting
import eu.tintera.plume.resources.call_bar_ended
import eu.tintera.plume.resources.call_bar_failed
import eu.tintera.plume.resources.call_hang_up
import eu.tintera.plume.resources.call_incoming
import eu.tintera.plume.resources.call_mute
import eu.tintera.plume.resources.mic_24dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

private val logger = Logger.withTag("CallBar")

/**
 * Probíhající hovor jako pruh nad celou aplikací (sonda — plná obrazovka hovoru přijde
 * později, viz `docs/roadmap.md`, „Hovory"). Zvoní / vytáčí / spojuje / běží s časem
 * a emoji k ověření; přijmout, ztlumit, zavěsit.
 */
@Composable
fun CallBar(modifier: Modifier = Modifier) {
    val calls = koinInject<CallRepository>()
    val call by calls.active.collectAsStateWithLifecycle()
    val shown = call
    AnimatedVisibility(visible = shown != null, modifier = modifier) {
        if (shown != null) CallBarContent(shown, calls)
    }
}

@Composable
private fun CallBarContent(call: ActiveCall, calls: CallRepository) {
    val chats = koinInject<ChatRepository>()
    val scope = rememberCoroutineScope()
    val permission = rememberRecordPermission()
    var name by remember(call.userId) { mutableStateOf<String?>(null) }
    LaunchedEffect(call.userId) {
        // Soukromý chat má id uživatele.
        name = chats.summaries(listOf(call.userId)).firstOrNull()?.title
    }
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(call.connectedAt) {
        while (call.connectedAt != null) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(500)
        }
    }

    val status = when (call.phase) {
        CallPhase.Ringing -> stringResource(Res.string.call_incoming)
        CallPhase.Calling -> stringResource(Res.string.call_bar_calling)
        CallPhase.Connecting -> stringResource(Res.string.call_bar_connecting)
        CallPhase.Active -> formatDuration(((now - (call.connectedAt ?: now)) / 1000).toInt())
        CallPhase.Ended -> stringResource(Res.string.call_bar_ended)
        CallPhase.Failed -> stringResource(Res.string.call_bar_failed)
    }
    val ended = call.phase == CallPhase.Ended || call.phase == CallPhase.Failed

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
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
                Text(
                    name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(status, call.emojis.joinToString("").ifEmpty { null }).joinToString("  "),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    maxLines = 1,
                )
            }
            if (call.phase == CallPhase.Ringing) {
                Button(onClick = {
                    permission.request { granted ->
                        if (!granted) return@request
                        scope.launch { calls.accept().onFailure { logger.w(it) { "Hovor se nepodařilo přijmout." } } }
                    }
                }) { Text(stringResource(Res.string.call_answer)) }
            } else if (!ended) {
                FilledIconToggleButton(
                    checked = call.muted,
                    onCheckedChange = calls::setMuted,
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(painterResource(Res.drawable.mic_24dp), contentDescription = stringResource(Res.string.call_mute))
                }
            }
            if (!ended) {
                FilledTonalIconButton(
                    onClick = { scope.launch { calls.hangUp() } },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    // Zavěšení je sluchátko otočené dolů, jako v systémovém telefonu.
                    Icon(
                        painterResource(Res.drawable.call_24dp),
                        contentDescription = stringResource(Res.string.call_hang_up),
                        modifier = Modifier.rotate(135f),
                    )
                }
            }
        }
    }
}

/**
 * Akce „Zavolat" pro profil člověka: zeptá se na mikrofon a vytočí. `null`, když hovory na
 * téhle platformě nejsou (bez mediální vrstvy), nebo profil není člověk.
 */
@Composable
fun rememberStartCall(userId: Long?): (() -> Unit)? {
    val calls = koinInject<CallRepository>()
    if (!calls.supported || userId == null) return null
    val scope = rememberCoroutineScope()
    val permission = rememberRecordPermission()
    return remember(userId, permission) {
        {
            permission.request { granted ->
                if (granted) scope.launch { calls.call(userId).onFailure { logger.w(it) { "Hovor se nepodařilo zahájit." } } }
            }
        }
    }
}
