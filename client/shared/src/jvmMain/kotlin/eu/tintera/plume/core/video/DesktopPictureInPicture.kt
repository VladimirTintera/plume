package eu.tintera.plume.core.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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

    class PipVideo(val player: VideoPlayer, val aspectRatio: Float)

    fun show(player: VideoPlayer, aspectRatio: Float) {
        if (current?.player === player) return
        close()
        player.heldByPip = true
        current = PipVideo(player, aspectRatio)
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
): PictureInPicture {
    // Prohlížeč pustil jiné video (rememberPictureInPicture se skládá jen pro to, které hraje).
    LaunchedEffect(player) { if (DesktopPip.current?.player.let { it != null && it !== player }) DesktopPip.close() }
    return remember(player, aspectRatio) { DesktopPictureInPicture(player, aspectRatio) }
}

private class DesktopPictureInPicture(private val player: VideoPlayer, private val aspectRatio: Float) : PictureInPicture {
    override val supported = true

    override val active: Boolean get() = DesktopPip.current?.player === player

    override val leavesViewer = true

    override fun enter() = DesktopPip.show(player, aspectRatio)
}

/**
 * Okénko obrazu v obraze. Skládá se v `application {}` vedle hlavního okna (jako okénko
 * příchozího hovoru), protože musí přežít zavření prohlížeče, ze kterého video přišlo.
 *
 * - **Bez rámu, vždy navrchu, nebere fokus** (`focusable = false`): klávesy jdou dál do chatu.
 *   Táhne se za obraz (`WindowDraggableArea`), velikost je pevná podle poměru stran videa.
 * - **Ovládání se ukáže, když je nad okénkem myš**: přehrát / pozastavit a zavřít. Zavřením
 *   video skončí (okénko je to poslední, co ho drží).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesktopPictureInPictureWindow() {
    val pip = DesktopPip.current ?: return
    val (width, height) = pipSize(pip.aspectRatio)
    val state = rememberWindowState(position = pipPosition(width, height), width = width, height = height)
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
                AnimatedVisibility(
                    visible = hovered,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
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

/** Velikost okénka: delší strana [PIP_LONG_SIDE], kratší podle poměru stran videa. */
internal fun pipSize(aspectRatio: Float): Pair<Dp, Dp> {
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    return if (ratio >= 1f) PIP_LONG_SIDE to PIP_LONG_SIDE / ratio else PIP_LONG_SIDE * ratio to PIP_LONG_SIDE
}

private val PIP_LONG_SIDE = 400.dp
private const val PIP_MARGIN = 16f
private const val POLL_MS = 200L
private const val SCRIM_ALPHA = 0.55f
