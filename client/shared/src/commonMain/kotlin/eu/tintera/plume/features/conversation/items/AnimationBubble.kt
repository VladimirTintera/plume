package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.LocalAutoDownload
import eu.tintera.plume.core.ui.rememberFileState
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.rememberVideoPlayer
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_gif
import eu.tintera.plume.resources.play_arrow_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * GIF v bublině.
 *
 * **V Telegramu je GIF MP4 bez zvuku**, ne skutečný GIF, takže se přehrává týmž přehrávačem
 * jako video — jen ve smyčce a ztlumeně.
 *
 * **Hraje sám, dokud je vidět.** GIF, který se nehýbe, není GIF; tak to dělá i oficiální klient.
 * Přehrávač žije s kompozicí bloku, takže odscrollovaný GIF dekodér vrátí. `LazyColumn` skládá
 * i kousek za viewportem, takže pár GIFů hraje mimo obrazovku — proti sledování skutečné
 * viditelnosti je to levnější a rozdíl je neznatelný.
 *
 * **Velký soubor se sám nestahuje** ([MessageMedia.Animation.autoPlays]): autoplay by bez ptaní
 * snědl data. Nad stropem zůstane náhled se symbolem přehrání a čeká na ťuknutí.
 *
 * Ťuknutí na hrající GIF ho pozastaví, další pustí — jinak by nešel zastavit vůbec.
 *
 * **Skrytý GIF (spoiler, citlivý obsah) se nestahuje ani nehraje, dokud ho uživatel
 * neodkryje** ([revealed]): pod rozostřením by se hýbal a prozradil, co skrývá. Dokud spoiler
 * u GIFu nebyl vůbec, hrál sám i ten, který odesílatel schoval.
 */
@Composable
internal fun AnimationBubble(
    animation: MessageMedia.Animation,
    shape: Shape,
    onRequestMenu: () -> Unit,
    modifier: Modifier = Modifier,
    revealed: Boolean = true,
    onReveal: () -> Unit = {},
) {
    // Nad stropem automatického stahování (podle sítě) se čeká na ťuknutí. GIF, který už na
    // disku je, hraje i tak — jen se kvůli němu nic nestahuje.
    var requested by remember(animation.fileId) { mutableStateOf(false) }
    val download = requested || animation.autoPlaysWithin(LocalAutoDownload.current.maxVideoBytes)
    var paused by remember(animation.fileId) { mutableStateOf(false) }
    val hidden = animation.hasSpoiler && !revealed
    val spoilerStyle = rememberSpoilerBlurStyle()

    val fileState = rememberFileState(animation.fileId.takeIf { !hidden }, download)
    val path = (fileState as? FileState.Ready)?.path

    Box(
        modifier = modifier
            .clip(shape)
            // Dlouhý stisk se opakuje — potomek s vlastním klikáním ho k bublině nepustí.
            .combinedClickable(
                onClick = {
                    when {
                        hidden -> onReveal()
                        path == null -> requested = true
                        else -> paused = !paused
                    }
                },
                onLongClick = onRequestMenu,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Náhled drží obsah, dokud GIF nedorazí, a pod přehrávačem zůstává, takže při
        // pozastavení není vidět prázdno. Server ho u animace nemusí dát vůbec.
        animation.thumbnailFileId?.let { thumbnail ->
            RemoteImage(
                fileId = thumbnail,
                placeholder = animation.minithumbnail,
                contentDescription = stringResource(Res.string.content_gif),
                modifier = Modifier
                    .fillMaxSize()
                    .spoilerBlur(hidden, revealed = false, spoilerStyle),
            )
        }

        if (path != null) {
            val player = rememberVideoPlayer()
            LaunchedEffect(player, path) { player.load(path, loop = true, muted = true) }
            LaunchedEffect(player, paused) { if (paused) player.pause() else player.play() }
            if (!paused) VideoSurface(player, Modifier.fillMaxSize())
        }

        // Symbol přehrání jen dokud GIF neběží: u hrajícího by překážel obrazu.
        if (!hidden && (path == null || paused)) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color.Black.copy(alpha = BADGE_ALPHA),
                contentColor = Color.White,
            ) {
                Icon(
                    painterResource(Res.drawable.play_arrow_24dp),
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(28.dp),
                )
            }
        }

        if (animation.hasSpoiler) SpoilerOverlay(visible = hidden, sensitive = animation.isSensitive)

        // Značka „GIF" v rohu, jako v ostatních klientech — jinak vypadá GIF jako němé video.
        Surface(
            shape = RoundedCornerShape(GIF_LABEL_CORNER),
            color = Color.Black.copy(alpha = BADGE_ALPHA),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.content_gif),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Podklad značek na snímku — na světlém GIFu by bílý text zanikl. */
private const val BADGE_ALPHA = 0.55f
private val GIF_LABEL_CORNER = 6.dp
