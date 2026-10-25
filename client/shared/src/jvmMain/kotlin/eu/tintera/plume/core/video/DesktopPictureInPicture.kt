package eu.tintera.plume.core.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import java.awt.MouseInfo
import java.awt.Cursor
import eu.tintera.plume.resources.media_picture_in_picture_return
import eu.tintera.plume.resources.open_in_full_24dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import eu.tintera.plume.SettingsTheme
import eu.tintera.plume.core.platform.DesktopWindow
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_picture_in_picture
import eu.tintera.plume.resources.media_picture_in_picture_close
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Obraz v obraze na desktopu: video z prohlížeče hraje dál v okénku vždy navrchu v pravém
 * dolním rohu ([DesktopPictureInPictureWindow]), prohlížeč se zavře a dá se psát dál — jako
 * v Telegram Desktop.
 *
 * - **Přehrávač okénku předává prohlížeč**, nezakládá se nový: video jede dál bez škobrtnutí,
 *   i se zvukem a pozicí. Stránka prohlížeče po zavření z kompozice odejde, přehrávač ale
 *   uvolní až okénko ([VideoPlayer.heldByPip], [VideoPlayer.orphaned]).
 * - **Pozastavení od stránky se v okénku nepočítá**: zavírající se prohlížeč video pozastaví
 *   (stránka přestane být vybraná), okénko ale hraje dál. Pauzu dává jen okénko samo.
 * - **Okénko je jedno.** Pustí-li prohlížeč jiné video, to v okénku skončí — dva přehrávače
 *   by hrály přes sebe.
 */
internal object DesktopPip {
    /** Co hraje v okénku; jen hlavní vlákno. */
    var current: PipVideo? by mutableStateOf(null)
        private set

    /** [onReturn] otevře prohlížeč znovu na téže zprávě v čase (ms); `null` = návrat nejde. */
    class PipVideo(val player: VideoPlayer, val aspectRatio: Float, val onReturn: ((positionMs: Long) -> Unit)?)

    /**
     * Delší strana okénka; pamatuje se, dokud aplikace běží — další video se otevře tak velké,
     * jak si uživatel okénko naposledy roztáhl.
     */
    var longSide: Dp = PIP_LONG_SIDE

    fun show(player: VideoPlayer, aspectRatio: Float, onReturn: ((positionMs: Long) -> Unit)? = null) {
        if (current?.player === player) return
        close()
        player.heldByPip = true
        current = PipVideo(player, aspectRatio, onReturn)
    }

    /**
     * Zpět do prohlížeče: okénko se zavře a prohlížeč se otevře na téže zprávě od místa, kde
     * video bylo (jako čas v médiu z odkazu). Hlavní okno jde dopředu, mohlo být za jiným.
     */
    fun returnToViewer() {
        val pip = current ?: return
        val reopen = pip.onReturn ?: return
        val position = pip.player.positionMs
        close()
        reopen(position)
        DesktopWindow.activate()
    }

    /** Zavře okénko; přehrávač, jehož stránka už odešla, se tím uvolní. */
    fun close() {
        val pip = current ?: return
        current = null
        pip.player.heldByPip = false
        if (pip.player.orphaned) pip.player.release() else pip.player.pauseFromPip()
    }
}

@Composable
actual fun rememberPictureInPicture(
    player: VideoPlayer,
    aspectRatio: Float,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onReturn: ((positionMs: Long) -> Unit)?,
): PictureInPicture {
    // Prohlížeč pustil jiné video (rememberPictureInPicture se skládá jen pro to, které hraje).
    LaunchedEffect(player) { if (DesktopPip.current?.player.let { it != null && it !== player }) DesktopPip.close() }
    val currentReturn by rememberUpdatedState(onReturn)
    return remember(player, aspectRatio) { DesktopPictureInPicture(player, aspectRatio) { currentReturn } }
}

private class DesktopPictureInPicture(
    private val player: VideoPlayer,
    private val aspectRatio: Float,
    private val onReturn: () -> ((Long) -> Unit)?,
) : PictureInPicture {
    override val supported = true

    override val active: Boolean get() = DesktopPip.current?.player === player

    override val leavesViewer = true

    override fun enter() = DesktopPip.show(player, aspectRatio, onReturn())
}

/**
 * Okénko obrazu v obraze. Skládá se v `application {}` vedle hlavního okna (jako okénko
 * příchozího hovoru), protože musí přežít zavření prohlížeče, ze kterého video přišlo.
 *
 * - **Bez rámu, vždy navrchu, nebere fokus** (`focusable = false`): klávesy jdou dál do chatu.
 *   Táhne se za obraz (`WindowDraggableArea`), velikost se mění tažením za roh ([ResizeHandle])
 *   se zachovaným poměrem stran.
 * - **Ovládání se ukáže, když je nad okénkem myš**: přehrát / pozastavit, zpět do prohlížeče
 *   a zavřít. Zavřením video skončí (okénko je to poslední, co ho drží).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesktopPictureInPictureWindow() {
    val pip = DesktopPip.current ?: return
    // Klíč `pip`: další video dostane okénko znovu v rohu, ne tam, kam se odtáhlo předchozí.
    val state = key(pip) {
        val (width, height) = pipSize(pip.aspectRatio, DesktopPip.longSide)
        rememberWindowState(position = pipPosition(width, height), width = width, height = height)
    }
    Window(
        onCloseRequest = DesktopPip::close,
        state = state,
        title = stringResource(Res.string.media_picture_in_picture),
        undecorated = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        SettingsTheme {
            val player = pip.player
            var playing by remember(player) { mutableStateOf(player.isPlaying) }
            // Přehrávač stav nehlásí; čte se po kouskách jako na stránce prohlížeče.
            LaunchedEffect(player) {
                while (true) {
                    playing = player.isPlaying
                    delay(POLL_MS)
                }
            }
            val hover = remember { MutableInteractionSource() }
            val hovered by hover.collectIsHoveredAsState()
            Box(Modifier.fillMaxSize().background(Color.Black).hoverable(hover)) {
                WindowDraggableArea(Modifier.fillMaxSize()) {
                    VideoSurface(player, Modifier.fillMaxSize())
                }
                // Za rohy se okénko zvětšuje a zmenšuje (bez rámu by to jinak nešlo).
                for (corner in PipCorner.entries) {
                    ResizeHandle(corner, state, pip.aspectRatio, Modifier.align(corner.alignment))
                }
                AnimatedVisibility(
                    visible = hovered || !playing,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    FilledIconButton(
                        onClick = {
                            if (playing) player.pauseFromPip() else player.play()
                            playing = !playing
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA),
                            contentColor = Color.White,
                        ),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painterResource(if (playing) Res.drawable.pause_24dp else Res.drawable.play_arrow_24dp),
                            contentDescription = stringResource(if (playing) Res.string.media_pause else Res.string.media_play),
                        )
                    }
                }
                if (pip.onReturn != null) {
                    AnimatedVisibility(
                        visible = hovered,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        modifier = Modifier.align(Alignment.TopStart).padding(PIP_BUTTON_INSET),
                    ) {
                        IconButton(
                            onClick = DesktopPip::returnToViewer,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA),
                                contentColor = Color.White,
                            ),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painterResource(Res.drawable.open_in_full_24dp),
                                contentDescription = stringResource(Res.string.media_picture_in_picture_return),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = hovered,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    modifier = Modifier.align(Alignment.TopEnd).padding(PIP_BUTTON_INSET),
                ) {
                    IconButton(
                        onClick = DesktopPip::close,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA),
                            contentColor = Color.White,
                        ),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.media_picture_in_picture_close))
                    }
                }
            }
        }
    }
}

/**
 * Pravý dolní roh použitelné plochy obrazovky, na které je hlavní okno. `WindowPosition.Aligned`
 * bere celou obrazovku: okénko pak leželo pod Dockem a trčelo za pravý okraj. Souřadnice AWT
 * jsou logické body, tedy dp okna.
 */
private fun pipPosition(width: Dp, height: Dp): WindowPosition {
    val config = DesktopWindow.awtWindow?.graphicsConfiguration
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val screen = config.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    val right = screen.x + screen.width - insets.right
    val bottom = screen.y + screen.height - insets.bottom
    return WindowPosition((right - width.value - PIP_MARGIN).dp, (bottom - height.value - PIP_MARGIN).dp)
}

/** Velikost okénka: delší strana [longSide], kratší podle poměru stran videa. */
internal fun pipSize(aspectRatio: Float, longSide: Dp = PIP_LONG_SIDE): Pair<Dp, Dp> {
    val ratio = pipRatio(aspectRatio)
    return if (ratio >= 1f) longSide to longSide / ratio else longSide * ratio to longSide
}

private fun pipRatio(aspectRatio: Float): Float = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)

/** Roh okénka, za který se táhne; protilehlý roh při tom stojí. */
internal enum class PipCorner(val alignment: Alignment, val cursor: Int, val left: Boolean, val top: Boolean) {
    TopStart(Alignment.TopStart, Cursor.NW_RESIZE_CURSOR, left = true, top = true),
    TopEnd(Alignment.TopEnd, Cursor.NE_RESIZE_CURSOR, left = false, top = true),
    BottomStart(Alignment.BottomStart, Cursor.SW_RESIZE_CURSOR, left = true, top = false),
    BottomEnd(Alignment.BottomEnd, Cursor.SE_RESIZE_CURSOR, left = false, top = false),
}

/** Poloha a velikost okénka v bodech obrazovky (dp okna). */
internal data class PipBounds(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Nové rozměry okénka po tahu za [corner] o [dx], [dy] od [start]: poměr stran zůstává, řídí ta
 * osa, po které se táhlo víc, a protilehlý roh stojí. Delší strana mezi [minLong] a [maxLong].
 */
internal fun resizePip(corner: PipCorner, start: PipBounds, dx: Float, dy: Float, ratio: Float, minLong: Float, maxLong: Float): PipBounds {
    val r = pipRatio(ratio)
    val byX = start.width + if (corner.left) -dx else dx
    val byY = (start.height + if (corner.top) -dy else dy) * r
    val wanted = if (kotlin.math.abs(dx) >= kotlin.math.abs(dy * r)) byX else byY
    val minWidth = if (r >= 1f) minLong else minLong * r
    val maxWidth = if (r >= 1f) maxLong else maxLong * r
    val width = wanted.coerceIn(minWidth, maxWidth)
    val height = width / r
    val x = if (corner.left) start.x + start.width - width else start.x
    val y = if (corner.top) start.y + start.height - height else start.y
    return PipBounds(x, y, width, height)
}

/**
 * Úchyt v rohu okénka. Poloha myši se bere z obrazovky (`MouseInfo`), ne z události: okno se
 * při tahu samo hýbe a souřadnice v něm by s ním ujížděly.
 */
@Composable
private fun ResizeHandle(corner: PipCorner, state: WindowState, aspectRatio: Float, modifier: Modifier) {
    Box(
        modifier
            .size(RESIZE_HANDLE)
            .pointerHoverIcon(PointerIcon(Cursor(corner.cursor)))
            .pointerInput(corner, state, aspectRatio) {
                var start: PipBounds? = null
                var origin = MouseInfo.getPointerInfo()?.location
                detectDragGestures(
                    onDragStart = {
                        val position = state.position
                        origin = MouseInfo.getPointerInfo()?.location
                        start = PipBounds(position.x.value, position.y.value, state.size.width.value, state.size.height.value)
                    },
                    onDragEnd = { start = null },
                    onDragCancel = { start = null },
                ) { change, _ ->
                    change.consume()
                    val from = start ?: return@detectDragGestures
                    val pressed = origin ?: return@detectDragGestures
                    val now = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                    val bounds = resizePip(
                        corner, from, (now.x - pressed.x).toFloat(), (now.y - pressed.y).toFloat(),
                        aspectRatio, PIP_MIN_LONG_SIDE.value, PIP_MAX_LONG_SIDE.value,
                    )
                    state.position = WindowPosition(bounds.x.dp, bounds.y.dp)
                    state.size = DpSize(bounds.width.dp, bounds.height.dp)
                    DesktopPip.longSide = maxOf(bounds.width, bounds.height).dp
                }
            },
    )
}

private val PIP_LONG_SIDE = 400.dp
private val PIP_MIN_LONG_SIDE = 240.dp
private val PIP_MAX_LONG_SIDE = 960.dp
private val RESIZE_HANDLE = 16.dp
private val PIP_BUTTON_INSET = 8.dp
private const val PIP_MARGIN = 16f
private const val POLL_MS = 200L
private const val SCRIM_ALPHA = 0.55f
