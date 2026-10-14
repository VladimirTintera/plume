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
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import eu.tintera.plume.core.calls.CallVideo
import eu.tintera.plume.core.calls.rememberCallPictureInPicture
import androidx.compose.runtime.mutableFloatStateOf
import eu.tintera.plume.core.calls.CallVideoSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.resources.call_camera
import eu.tintera.plume.resources.call_switch_camera
import eu.tintera.plume.resources.call_share_screen
import eu.tintera.plume.resources.cameraswitch_24dp
import eu.tintera.plume.resources.screen_share_24dp
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.resources.video_call_incoming
import eu.tintera.plume.core.calls.CallRequests
import eu.tintera.plume.core.calls.rememberCallCameraGranted
import eu.tintera.plume.core.calls.rememberCallCameraPermission
import eu.tintera.plume.core.calls.rememberCallMicrophonePermission
import eu.tintera.plume.core.calls.rememberScreenSharePermission
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
import eu.tintera.plume.resources.call_on_hold
import eu.tintera.plume.resources.call_resume
import eu.tintera.plume.resources.call_waiting_for_network
import eu.tintera.plume.resources.call_bar_ended
import eu.tintera.plume.resources.call_bar_dropped
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
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.call_failed_microphone
import eu.tintera.plume.resources.call_failed_camera
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
    val accept = rememberAcceptCall(calls)
    val scope = rememberCoroutineScope()

    // Zvonící videohovor: náhled vlastní kamery, jen když už je povolená — kvůli zvonění se
    // systém na kameru neptá, dotaz přijde až s „Přijmout". Zastaví ho repository (přijetí bez
    // kamery, odmítnutí, konec) a odchod obrazovky hovoru z kompozice.
    val cameraGranted = rememberCallCameraGranted()
    val ringingVideo = call?.takeIf { it.phase == CallPhase.Ringing && it.isVideo }?.id
    DisposableEffect(ringingVideo, cameraGranted) {
        if (ringingVideo != null && cameraGranted) calls.previewCamera(true)
        onDispose { if (ringingVideo != null) calls.previewCamera(false) }
    }

    LaunchedEffect(answer, call?.id, call?.phase) {
        val callId = answer ?: return@LaunchedEffect
        val ringing = call?.takeIf { it.id == callId && it.phase == CallPhase.Ringing } ?: return@LaunchedEffect
        requests.consumeAnswer(callId)
        accept(ringing)
    }

    val current = call ?: return
    // Poměr stran obrazu protistrany — tvar okénka obrazu v obraze. Do prvního snímku na výšku.
    var remoteAspect by remember(current.id) { mutableFloatStateOf(PORTRAIT_ASPECT) }
    val video = !current.phase.ended && (current.remoteVideo || current.camera)
    val pip = rememberCallPictureInPicture(
        enabled = video,
        aspectRatio = if (current.remoteVideo) remoteAspect else PORTRAIT_ASPECT,
        muted = current.muted,
        onToggleMute = { calls.setMuted(!current.muted) },
        onHangUp = { scope.launch { calls.hangUp() } },
        // Zavřené okénko: hovor běží dál na pozadí, ale obraz z kamery by šel ven, aniž by
        // o něm uživatel věděl. Kamera se vypne, po návratu do aplikace jde zapnout znovu.
        onDismissed = { if (current.camera) calls.setCamera(false) },
    )
    if (pip.active) {
        CallPictureInPictureContent(current, modifier)
        return
    }
    // V okénku je něco jiného (video z prohlížeče): pruh hovoru by ležel přes něj.
    if (pip.elsewhere) return
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
            CallScreen(current, calls, onMinimize = { expanded = false }, onRemoteAspect = { remoteAspect = it })
        } else {
            CallBar(current, calls, onExpand = { expanded = true })
        }
    }
}

/** Jméno a fotka protějšku (soukromý chat má id uživatele). */
@Composable
internal fun rememberPeer(userId: Long): Pair<String, Int?> {
    val chats = koinInject<ChatRepository>()
    var peer by remember(userId) { mutableStateOf("" to null as Int?) }
    LaunchedEffect(userId) {
        chats.summaries(listOf(userId)).firstOrNull()?.let { peer = it.title to it.avatarFileId }
    }
    return peer
}

/** Stav slovy, u běžícího hovoru čas od spojení. */
@Composable
internal fun callStatus(call: ActiveCall): String {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(call.connectedAt) {
        while (call.connectedAt != null) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(500)
        }
    }
    // Konkrétní důvod (soukromí, omezení počtu hovorů, oprávnění) říká víc než obecný stav.
    call.failure?.let { return it.resolve() }
    return when (call.phase) {
        CallPhase.Ringing -> stringResource(if (call.isVideo) Res.string.video_call_incoming else Res.string.call_incoming)
        CallPhase.Calling -> stringResource(Res.string.call_bar_calling)
        CallPhase.Connecting, CallPhase.Active -> when {
            // Systém dal přednost jinému hovoru (GSM) — zvuk neteče.
            call.held -> stringResource(Res.string.call_on_hold)
            // Výpadek sítě (přepnutí Wi-Fi a dat): čas by běžel, i když nic neteče.
            call.networkLost -> stringResource(Res.string.call_waiting_for_network)
            call.phase == CallPhase.Connecting -> stringResource(Res.string.call_bar_connecting)
            else -> formatDuration(((now - (call.connectedAt ?: now)) / 1000).toInt())
        }
        CallPhase.Ended -> stringResource(Res.string.call_bar_ended)
        // Spojený hovor, který spadl (síť se do 20 s nevrátila), se „nespojil" neříká.
        CallPhase.Failed -> stringResource(if (call.connectedAt != null) Res.string.call_bar_dropped else Res.string.call_bar_failed)
    }
}

private val CallPhase.ended get() = this == CallPhase.Ended || this == CallPhase.Failed

@Composable
private fun CallScreen(call: ActiveCall, calls: CallRepository, onMinimize: () -> Unit, onRemoteAspect: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    val accept = rememberAcceptCall(calls)
    val cameraPermission = rememberCallCameraPermission()
    val screenShare = rememberScreenSharePermission()?.takeIf { calls.supportsScreenSharing }
    val (name, avatarFileId) = rememberPeer(call.userId)
    // Zpět hovor neukončí, jen ho zmenší — jako u telefonu.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onMinimize,
    )
    val ongoing = !call.phase.ended
    // Přes celou plochu obraz protistrany, a když ho není, náš náhled; v rohu náš vedle jejího.
    val full = when {
        ongoing && call.remoteVideo -> CallVideoSource.Remote
        // Bez náhledu před spojením (desktop) by místo fotky zbyla prázdná plocha.
        ongoing && call.camera && (calls.previewsBeforeConnect || call.connectedAt != null) -> CallVideoSource.Local
        // Zvonící videohovor: náhled vlastní kamery za tlačítky Přijmout / Odmítnout.
        call.phase == CallPhase.Ringing && call.preview -> CallVideoSource.Local
        else -> null
    }
    val corner = CallVideoSource.Local.takeIf { full == CallVideoSource.Remote && call.camera }
    // Na obrazu by text i popisky splynuly; stojí proto na průsvitné ploše.
    val veil = if (full != null) MaterialTheme.colorScheme.surface.copy(alpha = VEIL_ALPHA) else Color.Transparent
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            full?.let { source ->
                CallVideo(
                    source,
                    mirror = source == CallVideoSource.Local && call.frontCamera,
                    modifier = Modifier.fillMaxSize(),
                    onAspectRatio = { if (source == CallVideoSource.Remote) onRemoteAspect(it) },
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FilledTonalIconButton(onClick = onMinimize, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.keyboard_arrow_down_24dp), contentDescription = stringResource(Res.string.call_minimize))
                    }
                    Spacer(Modifier.weight(1f))
                    // Sdílení obrazovky jen ve spojeném hovoru: posílá se místo kamery.
                    if (screenShare != null && call.phase == CallPhase.Active) {
                        FilledTonalIconToggleButton(
                            checked = call.screenSharing,
                            onCheckedChange = { on ->
                                if (!on) {
                                    calls.setScreenSharing(false)
                                } else {
                                    screenShare.request { granted -> if (granted) calls.setScreenSharing(true) }
                                }
                            },
                            shapes = IconButtonDefaults.toggleableShapes(),
                        ) {
                            Icon(painterResource(Res.drawable.screen_share_24dp), contentDescription = stringResource(Res.string.call_share_screen))
                        }
                    }
                    if (ongoing && call.camera && calls.canSwitchCamera) {
                        FilledTonalIconButton(onClick = calls::switchCamera, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.cameraswitch_24dp), contentDescription = stringResource(Res.string.call_switch_camera))
                        }
                    }
                }
                if (full == null) {
                    Spacer(Modifier.weight(0.6f))
                    Avatar(chatId = call.userId, title = name, avatarFileId = avatarFileId, size = 128.dp)
                    Spacer(Modifier.height(24.dp))
                    CallHeadline(call, name)
                    Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.height(16.dp))
                    Surface(color = veil, shape = MaterialTheme.shapes.extraLarge) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            CallHeadline(call, name, compact = true)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
                // Náš náhled vedle obrazu protistrany: vpravo dole nad ovládáním, kde nepřekáží
                // jménu ani tlačítkům nahoře.
                corner?.let { source ->
                    CallVideo(
                        source,
                        mirror = call.frontCamera,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 16.dp)
                            .size(width = 108.dp, height = 192.dp)
                            .clip(MaterialTheme.shapes.large),
                    )
                }
                if (call.held && ongoing) {
                    // Po GSM hovoru systém hovor sám nevrátí; bez tlačítka by zůstal viset podržený.
                    Button(onClick = { scope.launch { calls.resume() } }, modifier = Modifier.padding(bottom = 24.dp)) {
                        Text(stringResource(Res.string.call_resume))
                    }
                }
                Surface(color = veil, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    when {
                        call.phase == CallPhase.Ringing -> Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            LabeledButton(stringResource(Res.string.notification_call_decline)) {
                                HangUpButton(stringResource(Res.string.notification_call_decline)) { scope.launch { calls.hangUp() } }
                            }
                            LabeledButton(stringResource(Res.string.call_answer)) {
                                FilledIconButton(
                                    onClick = { accept(call) },
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                                ) {
                                    Icon(
                                        painterResource(if (call.isVideo) Res.drawable.videocam_24dp else Res.drawable.call_24dp),
                                        contentDescription = stringResource(Res.string.call_answer),
                                        modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                                    )
                                }
                            }
                        }
                        ongoing -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            // Jediný výstup (desktop: vybírá systém) není co přepínat.
                            if (call.outputs.size > 1) AudioOutputButton(call, calls, Modifier.weight(1f))
                            if (calls.supportsVideo) LabeledButton(stringResource(Res.string.call_camera), Modifier.weight(1f)) {
                                FilledIconToggleButton(
                                    checked = call.camera,
                                    onCheckedChange = { on ->
                                        if (!on) {
                                            calls.setCamera(false)
                                        } else {
                                            cameraPermission.request { granted -> if (granted) calls.setCamera(true) }
                                        }
                                    },
                                    shapes = IconButtonDefaults.toggleableShapes(),
                                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                                ) {
                                    Icon(painterResource(Res.drawable.videocam_24dp), contentDescription = stringResource(Res.string.call_camera), modifier = Modifier.size(IconButtonDefaults.mediumIconSize))
                                }
                            }
                            LabeledButton(stringResource(Res.string.call_mute), Modifier.weight(1f)) {
                                FilledIconToggleButton(
                                    checked = call.muted,
                                    onCheckedChange = calls::setMuted,
                                    shapes = IconButtonDefaults.toggleableShapes(),
                                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                                ) {
                                    Icon(painterResource(Res.drawable.mic_24dp), contentDescription = stringResource(Res.string.call_mute), modifier = Modifier.size(IconButtonDefaults.mediumIconSize))
                                }
                            }
                            LabeledButton(stringResource(Res.string.call_hang_up), Modifier.weight(1f)) {
                                HangUpButton(stringResource(Res.string.call_hang_up), large = false) { scope.launch { calls.hangUp() } }
                            }
                        }
                        // Konec: ovládání zmizí, plocha pod ním taky.
                        else -> Unit
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Jméno, stav a emoji k ověření; [compact] nad obrazem (menší písmo, bez vysvětlivky). */
@Composable
private fun CallHeadline(call: ActiveCall, name: String, compact: Boolean = false) {
    Text(
        name,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
    Text(
        callStatus(call),
        style = MaterialTheme.typography.titleMedium.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (call.emojis.isNotEmpty() && !call.phase.ended) {
        Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
        Text(call.emojis.joinToString("  "), style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
        if (!compact) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.call_emojis_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

/**
 * Přijetí hovoru: zeptá se na mikrofon, u videohovoru i na kameru. Kdo kameru nepovolí,
 * přijme bez ní — protistranu dál uvidí, jen ona jeho ne.
 */
@Composable
private fun rememberAcceptCall(calls: CallRepository): (ActiveCall) -> Unit {
    val scope = rememberCoroutineScope()
    val microphone = rememberCallMicrophonePermission()
    val camera = rememberCallCameraPermission()
    return remember(calls, microphone, camera) {
        { call ->
            val accept = { withCamera: Boolean ->
                scope.launch { calls.accept(camera = withCamera).onFailure { logger.w(it) { "Hovor ${call.id} se nepodařilo přijmout." } } }
                Unit
            }
            val denied = { calls.reportFailure(call.userId, call.isVideo, UiText(Res.string.call_failed_microphone)) }
            if (call.isVideo) {
                camera.request { granted -> if (granted) accept(true) else microphone.request { if (it) accept(false) else denied() } }
            } else {
                microphone.request { if (it) accept(false) else denied() }
            }
        }
    }
}

/**
 * Obsah okénka obrazu v obraze: jen obraz (protistrany, a když ho není, náš), bez ovládání —
 * na ťuknutí by okénko bylo malé a akce Ztlumit / Zavěsit dává systém pod okénkem.
 */
@Composable
private fun CallPictureInPictureContent(call: ActiveCall, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize()) {
        val source = if (call.remoteVideo) CallVideoSource.Remote else CallVideoSource.Local
        CallVideo(source, mirror = source == CallVideoSource.Local && call.frontCamera, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Kam jde zvuk. Jen sluchátko a reproduktor = přepínač „Reproduktor" (nejčastější případ,
 * jedno ťuknutí). S připojenou soupravou nebo sluchátky výběr z nabídky — přepínač by
 * nevěděl, kam se má vrátit; popisek pak říká, kam zvuk jde teď.
 */
@Composable
private fun AudioOutputButton(call: ActiveCall, calls: CallRepository, modifier: Modifier = Modifier) {
    // Přepínač stačí jen na dvojici sluchátko telefonu ↔ reproduktor (Android bez soupravy);
    // desktop má výstupů víc a pojmenovaných (reproduktory počítače, monitoru, sluchátka).
    val toggle = call.outputs.all { it.name == null && (it.kind == CallAudioOutput.Kind.Earpiece || it.kind == CallAudioOutput.Kind.Speaker) }
    if (toggle) {
        LabeledButton(stringResource(Res.string.call_speaker), modifier) {
            FilledIconToggleButton(
                checked = call.output.kind == CallAudioOutput.Kind.Speaker,
                onCheckedChange = { on -> calls.setOutput(if (on) CallAudioOutput.Speaker else CallAudioOutput.Earpiece) },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(painterResource(Res.drawable.volume_up_24dp), contentDescription = stringResource(Res.string.call_speaker), modifier = Modifier.size(IconButtonDefaults.mediumIconSize))
            }
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    val currentLabel = outputLabel(call.output)
    LabeledButton(currentLabel, modifier) {
        Box {
            FilledTonalIconButton(
                onClick = { open = true },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(
                    painterResource(outputIcon(call.output)),
                    contentDescription = stringResource(Res.string.call_audio_output, currentLabel),
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
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
private fun outputLabel(output: CallAudioOutput): String = output.name ?: when (output.kind) {
    CallAudioOutput.Kind.Earpiece -> stringResource(Res.string.call_output_phone)
    CallAudioOutput.Kind.Speaker -> stringResource(Res.string.call_speaker)
    CallAudioOutput.Kind.Headset -> stringResource(Res.string.call_output_headset)
    CallAudioOutput.Kind.Bluetooth -> stringResource(Res.string.call_output_bluetooth)
}

private fun outputIcon(output: CallAudioOutput): DrawableResource = when (output.kind) {
    CallAudioOutput.Kind.Earpiece -> Res.drawable.smartphone_24dp
    CallAudioOutput.Kind.Speaker -> Res.drawable.volume_up_24dp
    CallAudioOutput.Kind.Headset -> Res.drawable.headphones_24dp
    CallAudioOutput.Kind.Bluetooth -> Res.drawable.bluetooth_24dp
}

@Composable
internal fun LabeledButton(label: String, modifier: Modifier = Modifier, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        button()
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Zavěšení je sluchátko otočené dolů na chybové ploše, jako v systémovém telefonu. */
@Composable
internal fun HangUpButton(description: String, large: Boolean = true, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(if (large) IconButtonDefaults.largeContainerSize() else IconButtonDefaults.mediumContainerSize()),
    ) {
        Icon(
            painterResource(Res.drawable.call_24dp),
            contentDescription = description,
            modifier = Modifier.size(if (large) IconButtonDefaults.largeIconSize else IconButtonDefaults.mediumIconSize).rotate(135f),
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
                Text(callStatus(call), style = MaterialTheme.typography.bodyMedium.tabularNumbers(), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * Akce „Zavolat" / „Videohovor" pro profil člověka (argument `video`): zeptá se na mikrofon,
 * u videohovoru i na kameru, a vytočí. `null`, když hovory na téhle platformě nejsou (bez
 * mediální vrstvy), nebo profil není člověk.
 */
@Composable
fun rememberStartCall(userId: Long?): ((video: Boolean) -> Unit)? {
    val calls = koinInject<CallRepository>()
    if (!calls.supported || userId == null) return null
    val scope = rememberCoroutineScope()
    val microphone = rememberCallMicrophonePermission()
    val camera = rememberCallCameraPermission()
    return remember(userId, microphone, camera) {
        { requested ->
            // Bez obrazu (desktop) se videohovor vytočí jako hlasový — kamera tam ani není.
            val video = requested && calls.supportsVideo
            (if (video) camera else microphone).request { granted ->
                if (granted) {
                    // Selhání ukáže obrazovka hovoru (`ActiveCall.failure`), tady zbývá log.
                    scope.launch { calls.call(userId, video).onFailure { logger.w(it) { "Hovor se nepodařilo zahájit." } } }
                } else {
                    // Bez oprávnění (zvlášť trvale odepřeného, kdy se systém už nezeptá) by
                    // ťuknutí neudělalo vůbec nic.
                    calls.reportFailure(userId, video, UiText(if (video) Res.string.call_failed_camera else Res.string.call_failed_microphone))
                }
            }
        }
    }
}

/** Hovory umí i obraz — jinak se videohovor nenabízí (desktop). */
@Composable
fun rememberVideoCallsSupported(): Boolean = koinInject<CallRepository>().supportsVideo

/** Poměr stran obrazu z telefonu na výšku (šířka / výška). */
private const val PORTRAIT_ASPECT = 9f / 16f

/** Plocha pod textem a ovládáním nad obrazem: obraz prosvítá, text je čitelný. */
private const val VEIL_ALPHA = 0.8f
