package eu.tintera.plume.features.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_download_progress
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Stav stahování videa, které už hraje.
 *
 * - **Sbalený je to kroužek v pravém horním rohu**, nerušivý — video už běží a velký průběh
 *   uprostřed by ho zakrýval.
 * - **Ťuknutí ho roztáhne do čáry přes horní okraj karty** a další ho zase svine. Nahoře, ne u
 *   spodní hrany: kroužek tam už je, takže se jen roztáhne do šířky, a dole by se čára
 *   přetahovala o místo s posuvníkem.
 * - **Jsou to běžné M3 Expressive indikátory** (`CircularWavyProgressIndicator` /
 *   `LinearWavyProgressIndicator`) a přechod je obyčejné `AnimatedContent` se změnou velikosti.
 *   Ručně kreslený oblouk, který se rozvine do čáry (první verze), byl vymyšlený prvek navíc;
 *   standardní komponenty vypadají jako všude jinde v aplikaci.
 * - **Ukazuje se celkový podíl staženého** (`FileState.Downloading.progress`), ne souvislý úsek:
 *   oba indikátory kreslí průběh od nuly.
 * - **Horní roh ustupuje stavové liště**, jen když pod ni karta opravdu sahá. Insety samy nevědí,
 *   kde prvek leží (viz docs/poznatky/navigace.md, „Navigace"), takže se porovnávají s polohou karty v okně.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoDownloadIndicator(
    progress: Float,
    visible: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val safe = WindowInsets.safeDrawing
    var topInset by remember { mutableFloatStateOf(0f) }
    val backdrop = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
    val track = Color.White.copy(alpha = 0.3f)
    val description = stringResource(Res.string.media_download_progress)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                topInset = (safe.getTop(density) - coordinates.boundsInWindow().top).coerceAtLeast(0f)
            },
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(effects),
            exit = fadeOut(effects),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, topInset.roundToInt()) }
                .padding(12.dp),
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(effects) togetherWith fadeOut(effects) using
                        SizeTransform(sizeAnimationSpec = { _, _ -> spatial })
                },
                // Plocha roste z rohu doleva — kroužek se roztáhne, nepřeskočí.
                contentAlignment = Alignment.TopEnd,
                label = "download-indicator",
                // Podklad nese kontejner, ne obsah: jeho velikost se animuje, takže pilulka roste
                // i se zaoblením. Na obsahu by ji `SizeTransform` ořízl do hranatého okraje.
                modifier = Modifier
                    .clip(CircleShape)
                    .background(backdrop)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .clickable(role = Role.Button, onClick = onToggle),
            ) { wide ->
                Box(
                    Modifier
                        .then(if (wide) Modifier.fillMaxWidth() else Modifier)
                        .padding(if (wide) 12.dp else 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (wide) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            color = Color.White,
                            trackColor = track,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        CircularWavyProgressIndicator(
                            progress = { progress },
                            color = Color.White,
                            trackColor = track,
                            modifier = Modifier.size(RING_SIZE),
                        )
                    }
                }
            }
        }
    }
}

private val RING_SIZE = 28.dp
