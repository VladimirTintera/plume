package eu.tintera.plume.features.calls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.rememberRecordPermission
import eu.tintera.plume.core.calls.CallRequests
import org.jetbrains.compose.resources.DrawableResource
import eu.tintera.plume.resources.call_output_bluetooth
import eu.tintera.plume.resources.call_output_headset
import eu.tintera.plume.resources.call_output_phone
import eu.tintera.plume.resources.call_audio_output
import eu.tintera.plume.resources.bluetooth_24dp
import eu.tintera.plume.resources.headphones_24dp
import eu.tintera.plume.resources.smartphone_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.calls.CallAudioOutput
import androidx.compose.foundation.layout.Box
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.call_answer
import eu.tintera.plume.resources.call_bar_calling
import eu.tintera.plume.resources.call_bar_connecting
import eu.tintera.plume.resources.call_bar_ended
import eu.tintera.plume.resources.call_bar_failed
import eu.tintera.plume.resources.call_emojis_hint
import eu.tintera.plume.resources.call_hang_up
import eu.tintera.plume.resources.call_incoming
import eu.tintera.plume.resources.call_minimize
import eu.tintera.plume.resources.call_mute
import eu.tintera.plume.resources.call_speaker
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.notification_call_decline
import eu.tintera.plume.resources.volume_up_24dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

private val logger = Logger.withTag("CallOverlay")

/**
 * Probíhající hovor nad celou aplikací: nový hovor se ukáže přes celou obrazovku, zmenšit jde
 * do pruhu nahoře (šipka, nebo Zpět) a pruh ji zase otevře. Tak hovor přežije přechody mezi
 * obrazovkami a dá se během něj psát.
 *
 * Vyřizuje i požadavky z notifikace ([CallRequests]): „Přijmout" (po dotazu na mikrofon)
 * a ťuknutí / celoobrazovkovou výzvu, která obrazovku hovoru znovu rozbalí.
 */
@Composable
fun CallOverlay(modifier: Modifier = Modifier) {
    val calls = koinInject<CallRepository>()
    val requests = koinInject<CallRequests>()
    val call by calls.active.collectAsStateWithLifecycle()
    val answer by requests.answer.collectAsStateWithLifecycle()
    val show by requests.show.collectAsStateWithLifecycle()
    val permission = rememberRecordPermission()
    val scope = rememberCoroutineScope()

    LaunchedEffect(answer, call?.id, call?.phase) {
        val callId = answer ?: return@LaunchedEffect
        val ringing = call?.takeIf { it.id == callId && it.phase == CallPhase.Ringing } ?: return@LaunchedEffect
        requests.consumeAnswer(callId)
        permission.request { granted ->
            if (granted) {
                scope.launch {
                    calls.accept().onFailure { logger.w(it) { "Hovor ${ringing.id} se nepodařilo přijmout." } }
                }
            }
        }
    }

    val current = call ?: return
    // Nový hovor (i nové „ukaž hovor" z notifikace) se otevírá přes celou obrazovku.
    var expanded by rememberSaveable(current.id, show) { mutableStateOf(true) }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = expanded,
        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
        modifier = modifier,
        label = "call-overlay",
    ) { full ->
        if (full) {
            CallScreen(current, calls, onMinimize = { expanded = false })
        } else {
            CallBar(current, calls, onExpand = { expanded = true })
        }
    }
}

/** Jméno a fotka protějšku (soukromý chat má id uživatele). */
@Composable
private fun rememberPeer(userId: Long): Pair<String, Int?> {
    val chats = koinInject<ChatRepository>()
    var peer by remember(userId) { mutableStateOf("" to null as Int?) }
    LaunchedEffect(userId) {
        chats.summaries(listOf(userId)).firstOrNull()?.let { peer = it.title to it.avatarFileId }
    }
    return peer
}

/** Stav slovy, u běžícího hovoru čas od spojení. */
@Composable
private fun callStatus(call: ActiveCall): String {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(call.connectedAt) {
        while (call.connectedAt != null) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(500)
        }
    }
    return when (call.phase) {
        CallPhase.Ringing -> stringResource(Res.string.call_incoming)
        CallPhase.Calling -> stringResource(Res.string.call_bar_calling)
        CallPhase.Connecting -> stringResource(Res.string.call_bar_connecting)
        CallPhase.Active -> formatDuration(((now - (call.connectedAt ?: now)) / 1000).toInt())
        CallPhase.Ended -> stringResource(Res.string.call_bar_ended)
        CallPhase.Failed -> stringResource(Res.string.call_bar_failed)
    }
}

private val CallPhase.ended get() = this == CallPhase.Ended || this == CallPhase.Failed

@Composable
private fun CallScreen(call: ActiveCall, calls: CallRepository, onMinimize: () -> Unit) {
    val scope = rememberCoroutineScope()
    val permission = rememberRecordPermission()
    val (name, avatarFileId) = rememberPeer(call.userId)
    // Zpět hovor neukončí, jen ho zmenší — jako u telefonu.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onMinimize,
    )
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onMinimize, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.keyboard_arrow_down_24dp), contentDescription = stringResource(Res.string.call_minimize))
                }
            }
            Spacer(Modifier.weight(0.6f))
            Avatar(chatId = call.userId, title = name, avatarFileId = avatarFileId, size = 128.dp)
            Spacer(Modifier.height(24.dp))
            Text(name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(callStatus(call), style = MaterialTheme.typography.titleMedium.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (call.emojis.isNotEmpty() && !call.phase.ended) {
                Spacer(Modifier.height(24.dp))
                Text(call.emojis.joinToString("  "), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.call_emojis_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            when {
                call.phase == CallPhase.Ringing -> Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    LabeledButton(stringResource(Res.string.notification_call_decline)) {
                        HangUpButton(stringResource(Res.string.notification_call_decline)) { scope.launch { calls.hangUp() } }
                    }
                    LabeledButton(stringResource(Res.string.call_answer)) {
                        FilledIconButton(
                            onClick = {
                                permission.request { granted ->
                                    if (granted) scope.launch { calls.accept().onFailure { logger.w(it) { "Hovor se nepodařilo přijmout." } } }
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                        ) {
                            Icon(
                                painterResource(Res.drawable.call_24dp),
                                contentDescription = stringResource(Res.string.call_answer),
                                modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                            )
                        }
                    }
                }
                !call.phase.ended -> Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    AudioOutputButton(call, calls)
                    LabeledButton(stringResource(Res.string.call_mute)) {
                        FilledIconToggleButton(
                            checked = call.muted,
                            onCheckedChange = calls::setMuted,
                            shapes = IconButtonDefaults.toggleableShapes(),
                            modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                        ) {
                            Icon(painterResource(Res.drawable.mic_24dp), contentDescription = stringResource(Res.string.call_mute), modifier = Modifier.size(IconButtonDefaults.largeIconSize))
                        }
                    }
                    LabeledButton(stringResource(Res.string.call_hang_up)) {
                        HangUpButton(stringResource(Res.string.call_hang_up)) { scope.launch { calls.hangUp() } }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Kam jde zvuk. Jen sluchátko a reproduktor = přepínač „Reproduktor" (nejčastější případ,
 * jedno ťuknutí). S připojenou soupravou nebo sluchátky výběr z nabídky — přepínač by
 * nevěděl, kam se má vrátit; popisek pak říká, kam zvuk jde teď.
 */
@Composable
private fun AudioOutputButton(call: ActiveCall, calls: CallRepository) {
    val choice = call.outputs.any { it.kind == CallAudioOutput.Kind.Bluetooth || it.kind == CallAudioOutput.Kind.Headset }
    if (!choice) {
        LabeledButton(stringResource(Res.string.call_speaker)) {
            FilledIconToggleButton(
                checked = call.output.kind == CallAudioOutput.Kind.Speaker,
                onCheckedChange = { on -> calls.setOutput(if (on) CallAudioOutput.Speaker else CallAudioOutput.Earpiece) },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
            ) {
                Icon(painterResource(Res.drawable.volume_up_24dp), contentDescription = stringResource(Res.string.call_speaker), modifier = Modifier.size(IconButtonDefaults.largeIconSize))
            }
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    val currentLabel = outputLabel(call.output)
    LabeledButton(currentLabel) {
        Box {
            FilledTonalIconButton(
                onClick = { open = true },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
            ) {
                Icon(
                    painterResource(outputIcon(call.output)),
                    contentDescription = stringResource(Res.string.call_audio_output, currentLabel),
                    modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                )
            }
            ExpressiveMenu(
                expanded = open,
                onDismiss = { open = false },
                groups = listOf(
                    call.outputs.map { output ->
                        MenuAction(
                            label = outputLabel(output),
                            icon = outputIcon(output),
                            trailing = if (output == call.output) {
                                { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                open = false
                                calls.setOutput(output)
                            },
                        )
                    },
                ),
            )
        }
    }
}

@Composable
private fun outputLabel(output: CallAudioOutput): String = when (output.kind) {
    CallAudioOutput.Kind.Earpiece -> stringResource(Res.string.call_output_phone)
    CallAudioOutput.Kind.Speaker -> stringResource(Res.string.call_speaker)
    CallAudioOutput.Kind.Headset -> stringResource(Res.string.call_output_headset)
    CallAudioOutput.Kind.Bluetooth -> output.name ?: stringResource(Res.string.call_output_bluetooth)
}

private fun outputIcon(output: CallAudioOutput): DrawableResource = when (output.kind) {
    CallAudioOutput.Kind.Earpiece -> Res.drawable.smartphone_24dp
    CallAudioOutput.Kind.Speaker -> Res.drawable.volume_up_24dp
    CallAudioOutput.Kind.Headset -> Res.drawable.headphones_24dp
    CallAudioOutput.Kind.Bluetooth -> Res.drawable.bluetooth_24dp
}

@Composable
private fun LabeledButton(label: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        button()
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Zavěšení je sluchátko otočené dolů na chybové ploše, jako v systémovém telefonu. */
@Composable
private fun HangUpButton(description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
    ) {
        Icon(
            painterResource(Res.drawable.call_24dp),
            contentDescription = description,
            modifier = Modifier.size(IconButtonDefaults.largeIconSize).rotate(135f),
        )
    }
}

/** Zmenšený hovor: pruh nahoře, ťuknutí ho rozbalí. */
@Composable
private fun CallBar(call: ActiveCall, calls: CallRepository, onExpand: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (name, _) = rememberPeer(call.userId)
    Surface(
        onClick = onExpand,
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
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(callStatus(call), style = MaterialTheme.typography.bodyMedium.tabularNumbers(), maxLines = 1)
            }
            if (call.phase == CallPhase.Ringing) {
                Button(onClick = onExpand) { Text(stringResource(Res.string.call_answer)) }
            } else if (!call.phase.ended) {
                FilledIconToggleButton(
                    checked = call.muted,
                    onCheckedChange = calls::setMuted,
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(painterResource(Res.drawable.mic_24dp), contentDescription = stringResource(Res.string.call_mute))
                }
            }
            if (!call.phase.ended) {
                FilledTonalIconButton(
                    onClick = { scope.launch { calls.hangUp() } },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shapes = IconButtonDefaults.shapes(),
                ) {
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
