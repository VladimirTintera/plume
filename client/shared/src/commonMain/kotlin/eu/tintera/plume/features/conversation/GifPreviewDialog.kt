package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.rememberFileState
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.rememberVideoPlayer
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bookmark_24dp
import eu.tintera.plume.resources.bookmark_filled_24dp
import eu.tintera.plume.resources.content_gif
import eu.tintera.plume.resources.gifs_remove_saved
import eu.tintera.plume.resources.gifs_save
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Náhled podrženého GIFu přes celou obrazovku: GIF velký, v plném poměru stran, a pod ním akce.
 *
 * Tentýž vzor jako náhled samolepky ([StickerPreviewDialog]): buňka mřížky je malá a ořízne GIF
 * do obdélníku, takže kdo ho podrží, chce vidět celý. Akce (poslat, uložit, volby odeslání) jsou
 * tady, ne jako drobná tlačítka v rozích buňky — na ta se špatně trefovalo a zakrývala GIF.
 *
 * GIF se tu **stahuje vždycky**, i velký, který mřížka sama nestahuje (`autoPlays`): podržením
 * si ho uživatel výslovně otevřel. Do stažení drží místo náhled.
 *
 * Ťuknutí mimo tlačítka a zpět náhled zavřou. Sheet pod ním zůstává.
 */
@Composable
internal fun GifPreviewDialog(
    gif: GifItem,
    saved: Boolean,
    /** Popisek hlavního tlačítka: „Poslat", ve formuláři ankety „Vybrat". */
    pickLabel: String,
    /** Volby odeslání (bez zvuku, naplánovat…); `null` = tlačítko není. */
    sendMenu: SendMenuOffer?,
    onPick: () -> Unit,
    onPickWith: (SendMenuChoice) -> Unit,
    /** `null` = záložka není (výsledek inline bota: posílá se výsledek, ne GIF). */
    onToggleSaved: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // Odchod se nejdřív dohraje, teprve pak dialog zmizí — zmizení bez animace vypadá jako pád.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { shown.targetState = false }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(shown) {
        snapshotFlow { !shown.targetState && !shown.currentState && shown.isIdle }.first { it }
        dismiss()
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = close),
        ) {
            AnimatedVisibility(
                visibleState = shown,
                enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = GIF_PREVIEW_START_SCALE) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = GIF_PREVIEW_START_SCALE) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    LargeGif(gif)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Poslat zavírá i sheet, náhled se proto neanimuje — odjede s ním.
                        Button(onClick = onPick, shapes = ButtonDefaults.shapes()) { Text(pickLabel) }
                        // Uložení náhled nezavírá, jako hvězdička u samolepky: stav potvrdí update.
                        if (onToggleSaved != null) FilledTonalIconToggleButton(
                            checked = saved,
                            onCheckedChange = { onToggleSaved() },
                            shapes = IconButtonDefaults.toggleableShapes(),
                        ) {
                            Icon(
                                painterResource(if (saved) Res.drawable.bookmark_filled_24dp else Res.drawable.bookmark_24dp),
                                contentDescription = stringResource(if (saved) Res.string.gifs_remove_saved else Res.string.gifs_save),
                            )
                        }
                        sendMenu?.let { offer -> SendOptionsIconButton(offer, onPickWith) }
                    }
                }
            }
        }
    }
}

/**
 * GIF v plném poměru stran. Extrémně úzký nebo vysoký se do poměru [MIN_RATIO]–[MAX_RATIO]
 * ořízne (`coverAspect`), jinak by náhled přerostl obrazovku a tlačítka pod ním by zmizela.
 */
@Composable
private fun LargeGif(gif: GifItem) {
    val animation = gif.animation
    val ratio = aspectRatio(animation.width, animation.height).coerceIn(MIN_RATIO, MAX_RATIO)
    val path = (rememberFileState(animation.fileId) as? FileState.Ready)?.path

    Box(
        Modifier
            .widthIn(max = GIF_PREVIEW_WIDTH)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(GIF_PREVIEW_CORNER)),
    ) {
        animation.thumbnailFileId?.let { thumbnail ->
            RemoteImage(
                fileId = thumbnail,
                contentDescription = stringResource(Res.string.content_gif),
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (path != null) {
            val player = rememberVideoPlayer()
            LaunchedEffect(player, path) {
                player.load(path, loop = true, muted = true)
                player.play()
            }
            VideoSurface(player, Modifier.coverAspect(animation.width, animation.height))
        }
    }
}

/** Na telefonu skoro celá šířka; na tabletu nepřeroste rozumnou velikost. */
private val GIF_PREVIEW_WIDTH = 360.dp
private val GIF_PREVIEW_CORNER = 16.dp
private const val GIF_PREVIEW_START_SCALE = 0.6f

/** Nejvyšší GIF, který se ještě ukáže celý (výška 4/3 šířky). */
private const val MIN_RATIO = 0.75f

/** Nejširší GIF, který se ještě ukáže celý. */
private const val MAX_RATIO = 2.5f
