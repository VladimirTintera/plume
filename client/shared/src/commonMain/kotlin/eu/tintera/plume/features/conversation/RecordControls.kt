package eu.tintera.plume.features.conversation

import androidx.compose.material3.IconButtonDefaults
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Stable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.camera.VIDEO_NOTE_MAX_DURATION_MS
import eu.tintera.plume.core.camera.VideoNoteCamera
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.cameraswitch_24dp
import eu.tintera.plume.resources.conversation_recording_lock
import eu.tintera.plume.resources.conversation_switch_camera
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.lock_24dp
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.clickable
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.LaunchedEffect
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.rememberVideoPlayer
import eu.tintera.plume.resources.conversation_play_video_note
import eu.tintera.plume.resources.media_pause
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Podržení tlačítka v prázdném poli nahrává, jako v Telegramu: po [HOLD_TO_RECORD_MS] začne
 * nahrávání, puštění ho odešle, tah doleva zahodí, tah nahoru zamkne (nahrává se dál bez
 * prstu a odesílá se ťuknutím). Kratší stisk je obyčejné ťuknutí — to tlačítko přepíná mezi
 * hlasovkou a kolečkem.
 *
 * - **Čte se v `Initial` a po začátku nahrávání se spotřebovává VŠECHNO**, takže tlačítko pod
 *   tím ťuknutí nevyhodnotí: puštění odešle nahrávku, ne přepnutí režimu. Stejný postup jako
 *   [longPressInstead].
 * - **Musí být v řetězu modifikátorů PRVNÍ.** Tlačítko během gesta změní druh (mikrofon →
 *   odeslání) a s ním i modifikátory za tímhle; uzel před nimi zůstane a gesto neztratí.
 * - Dokud se čeká na podržení, nic se nespotřebovává: tlačítko má svou vlnku i ťuknutí.
 */
internal fun Modifier.holdToRecord(
    enabled: Boolean,
    cancelDistance: Dp,
    lockDistance: Dp,
    onHoldStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    onLock: () -> Unit,
): Modifier = composed {
    val active by rememberUpdatedState(enabled)
    val start by rememberUpdatedState(onHoldStart)
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    val lock by rememberUpdatedState(onLock)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!active) return@awaitEachGesture
            // `null` = vypršel čas a prst je pořád dole.
            val released = withTimeoutOrNull(HOLD_TO_RECORD_MS) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial)
                        .changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
            }
            if (released != null) return@awaitEachGesture

            start()
            val cancelPx = cancelDistance.toPx()
            val lockPx = lockDistance.toPx()
            var outcome = RecordDrag.None
            var touchCancelled = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                /*
                 * Zrušený dotyk (otočení displeje, systémové gesto, stažená lišta) přijde jako
                 * puštění, které je UŽ spotřebované — tak ho Compose odlišuje. Puštění by
                 * nahrávku odeslalo, aniž by o tom uživatel rozhodl; zamčení ji nechá běžet
                 * a rozhodne on (odeslat, nebo koš).
                 */
                val cancelled = change != null && !change.pressed && change.isConsumed
                event.changes.forEach { it.consume() }
                if (cancelled) {
                    outcome = RecordDrag.Lock
                    touchCancelled = true
                    break
                }
                if (change == null || !change.pressed) break
                val delta = change.position - down.position
                drag(delta)
                outcome = recordDragOutcome(delta.x, delta.y, cancelPx, lockPx)
                if (outcome != RecordDrag.None) break
            }
            drag(Offset.Zero)
            when (outcome) {
                RecordDrag.None -> release()
                RecordDrag.Cancel -> cancel()
                RecordDrag.Lock -> lock()
            }
            // Zrušeno nebo zamčeno uprostřed tahu: zbytek gesta nesmí na tlačítku nic ťuknout.
            // Po zrušeném dotyku žádný zbytek není — čekání by spolklo až příští ťuknutí.
            if (outcome != RecordDrag.None && !touchCancelled) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}

/**
 * Stav gesta nahrávání (viz [holdToRecord]), sdílený vstupním polem a obrazovkou: prst drží
 * tlačítko, jeho posun od místa stisku a zamčení (nahrává se dál bez prstu).
 *
 * Drží ho obrazovka, ne pole, protože zámek nad tlačítkem se kreslí MIMO lištu
 * ([RecordLockHost]): pás skla pod polem kreslí lištu do vrstvy o její velikosti a všechno,
 * co z ní přečnívá, ořízne.
 */
@Stable
internal class RecordGestureState {
    var held by mutableStateOf(false)
    var locked by mutableStateOf(false)

    /** Posun prstu; čte se až ve vrstvách nápovědy a zámku, takže tah nic nepřeskládává. */
    val drag = mutableStateOf(Offset.Zero)

    /** Vodorovný střed tlačítka v okně — nad ním stojí zámek. */
    var buttonCenterX by mutableFloatStateOf(Float.NaN)
}

/**
 * Zámek nad tlačítkem odeslání, kreslený v obsahu obrazovky těsně nad vstupním polem
 * ([modifier] musí končit tam, kde pole začíná). Vodorovně se srovná na střed tlačítka.
 */
@Composable
internal fun RecordLockHost(visible: Boolean, gesture: RecordGestureState, modifier: Modifier = Modifier) {
    val left = remember { floatArrayOf(0f) }
    Box(modifier.onGloballyPositioned { left[0] = it.boundsInWindow().left }) {
        val density = LocalDensity.current
        RecordLockIndicator(
            visible = visible && !gesture.buttonCenterX.isNaN(),
            progress = {
                lockProgress(gesture.drag.value.y, with(density) { RECORD_LOCK_DISTANCE.toPx() })
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        val x = gesture.buttonCenterX - left[0] - placeable.width / 2f
                        placeable.place(x.roundToInt(), -LOCK_ABOVE_BAR.roundToPx())
                    }
                },
        )
    }
}

/**
 * Zámek nad tlačítkem, dokud se nahrává s prstem na tlačítku: ukazuje, že tahem nahoru jde
 * nahrávání zamknout. Jede s prstem ([lockProgress] 0..1).
 */
@Composable
private fun RecordLockIndicator(visible: Boolean, progress: () -> Float, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.6f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.6f),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = 2.dp,
            modifier = Modifier.graphicsLayer {
                val shown = progress().coerceIn(0f, 1f)
                translationY = -LOCK_TRAVEL.toPx() * shown
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.lock_24dp),
                    contentDescription = stringResource(Res.string.conversation_recording_lock),
                    modifier = Modifier.size(20.dp),
                )
                Icon(
                    painterResource(Res.drawable.keyboard_arrow_up_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Přehrávání náhledu kolečka. Drží ho obrazovka, protože ho čtou dvě místa: kruh nad konverzací
 * (přehrávač, průběh po obvodu) a pruh ve vstupní kartě (tlačítko a čas).
 *
 * Náhled hraje jako kolečko v bublině: potichu ve smyčce, se zvukem až na ťuknutí — od začátku,
 * s průběhem, a po dohrání zpátky potichu. Uživatel tak vidí, co protějšek dostane.
 */
@Stable
internal class VideoNotePreviewPlayback {
    var sounding by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var elapsedMs by mutableStateOf(0L)

    fun toggle() {
        sounding = !sounding
        progress = 0f
        elapsedMs = 0L
    }
}

/**
 * Náhled kamery v kruhu nad konverzací, dokud se nahrává kolečko: kolečko se kreslí tak,
 * jak ho uvidí protějšek. Po obvodu jede průběh do minutového stropu ([VIDEO_NOTE_MAX_DURATION_MS]).
 *
 * Po ■ ([preview]) se místo kamery přehraje nahrané kolečko a průběh po obvodu patří přehrávání
 * se zvukem; ťuknutí na kruh zvuk pustí a zase vypne. Dokud se nahrávka připravuje, točí se
 * v kruhu indikátor.
 *
 * - **Závoj bere dotyky**, jinak by se pod nahrávajícím kolečkem dal scrollovat seznam a ťukat
 *   na zprávy. Vstupní pole je nad ním (kreslí ho Scaffold po obsahu), takže odeslání i koš
 *   zůstávají ovladatelné.
 * - **Náhled je `TextureView` (Android) / vrstva s vlastním ořezem (iOS)** — `SurfaceView` ani
 *   UIKit view by ořez `clip` z Compose nerespektovaly.
 */
@Composable
internal fun VideoNoteRecordingOverlay(
    visible: Boolean,
    camera: VideoNoteCamera?,
    recording: () -> VoiceRecording?,
    modifier: Modifier = Modifier,
    preview: VideoNotePreview? = null,
    playback: VideoNotePreviewPlayback? = null,
) {
    AnimatedVisibility(
        visible = (visible && camera != null) || preview != null,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        // Při odjezdu (odeslání, koš) je `preview` už `null`; kreslí se poslední známý stav.
        val lastPreview = remember { mutableStateOf<VideoNotePreview?>(null) }
        if (preview != null) lastPreview.value = preview
        val shownPreview = preview ?: lastPreview.value
        val shown = camera
        if (shown == null && shownPreview == null) return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints {
                val diameter = minOf(maxWidth * CIRCLE_WIDTH_FRACTION, maxHeight * CIRCLE_HEIGHT_FRACTION, MAX_CIRCLE)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val ring = MaterialTheme.colorScheme.tertiary
                    val track = MaterialTheme.colorScheme.surfaceContainerHighest
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(diameter),
                    ) {
                        val inner = Modifier.size(diameter - RING_GAP * 2).clip(CircleShape)
                        if (shownPreview != null) {
                            VideoNotePreviewCircle(shownPreview, playback, inner)
                        } else {
                            shown?.Preview(inner)
                        }
                        Canvas(Modifier.size(diameter)) {
                            val stroke = RING_STROKE.toPx()
                            val inset = stroke / 2
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(
                                color = track,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = stroke),
                            )
                            val fraction = if (shownPreview != null) {
                                if (playback?.sounding == true) playback.progress else 0f
                            } else {
                                val elapsed = recording()?.elapsedMs ?: 0L
                                (elapsed.toFloat() / VIDEO_NOTE_MAX_DURATION_MS).coerceIn(0f, 1f)
                            }
                            drawArc(
                                color = ring,
                                startAngle = -90f,
                                sweepAngle = 360f * fraction,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                    if (shownPreview == null && shown != null && shown.canSwitch) {
                        FilledTonalIconButton(onClick = shown::switchCamera, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painterResource(Res.drawable.cameraswitch_24dp),
                                contentDescription = stringResource(Res.string.conversation_switch_camera),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nahrané kolečko v kruhu náhledu. Přehrávač žije s kompozicí, jako u kolečka v bublině;
 * přepnutí zvuku soubor načte znovu (se zvukem od začátku a bez smyčky).
 */
@Composable
private fun VideoNotePreviewCircle(
    preview: VideoNotePreview,
    playback: VideoNotePreviewPlayback?,
    modifier: Modifier,
) {
    val note = preview.note
    val sounding = playback?.sounding == true
    val label = stringResource(if (sounding) Res.string.media_pause else Res.string.conversation_play_video_note)
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = note != null && playback != null, onClickLabel = label) { playback?.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (note == null) {
            LoadingIndicator()
            return@Box
        }
        val player = rememberVideoPlayer()
        LaunchedEffect(player, note.path, sounding) {
            player.load(note.path, loop = !sounding, muted = !sounding)
            player.play()
            if (!sounding) return@LaunchedEffect
            while (true) {
                delay(PREVIEW_TICK_MS)
                val duration = player.durationMs
                if (duration > 0) playback?.progress = (player.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                playback?.elapsedMs = player.positionMs
                if (player.isEnded) {
                    playback?.toggle()
                    break
                }
            }
        }
        VideoSurface(player, Modifier.fillMaxSize())
    }
}

private const val PREVIEW_TICK_MS = 50L

/**
 * Jak dlouho se musí tlačítko držet, než se začne nahrávat. Kratší stisk je ťuknutí (přepnutí
 * hlasovka ↔ kolečko); delší prodleva by se u hlasovky projevila useknutým prvním slovem.
 */
private const val HOLD_TO_RECORD_MS = 250L

/** Tah doleva, který nahrávku zahodí. */
internal val RECORD_CANCEL_DISTANCE = 120.dp

/** Tah nahoru, který nahrávání zamkne. */
internal val RECORD_LOCK_DISTANCE = 80.dp

/** Mezera mezi zámkem a vstupním polem pod ním. */
private val LOCK_ABOVE_BAR = 12.dp

/** Kam až zámek s prstem vyjede. */
private val LOCK_TRAVEL = 24.dp

private const val SCRIM_ALPHA = 0.5f

private const val CIRCLE_WIDTH_FRACTION = 0.72f

private const val CIRCLE_HEIGHT_FRACTION = 0.6f

private val MAX_CIRCLE = 320.dp

private val RING_STROKE = 4.dp

/** Mezera mezi obrysem průběhu a náhledem. */
private val RING_GAP = 8.dp
