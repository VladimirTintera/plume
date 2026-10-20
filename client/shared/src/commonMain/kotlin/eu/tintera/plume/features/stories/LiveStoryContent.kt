package eu.tintera.plume.features.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.calls.CallVideo
import eu.tintera.plume.core.calls.CallVideoSource
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.media.ON_SCRIM
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.story_live_badge
import eu.tintera.plume.resources.story_live_ended
import eu.tintera.plume.resources.story_live_failed
import eu.tintera.plume.resources.story_live_viewers
import eu.tintera.plume.resources.story_live_waiting
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Živý příběh v prohlížeči: obraz vysílání přes celou kartu, dokud běží. Průběh je celý
 * (vysílání nemá délku) a dál se nejde samo — až po konci vysílání nebo nezdaru, s větou,
 * která stojí [ENDED_DURATION_MS] jako fotka.
 *
 * Obraz se kreslí jen u příběhu, na kterém prohlížeč stojí ([active]): sledování běží jen
 * jedno a soused v pageru by jinak čekal na snímky, které nepřijdou.
 */
@Composable
internal fun BoxScope.LiveStoryContent(
    live: LiveStoryState?,
    active: Boolean,
    paused: Boolean,
    onProgress: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val currentPaused by rememberUpdatedState(paused)
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) { onProgress(1f) }
    val phase = live?.phase ?: LivePhase.Connecting
    when (phase) {
        LivePhase.Connecting, LivePhase.Live -> {
            if (active && live != null) CallVideo(CallVideoSource.Live, mirror = false, modifier = Modifier.fillMaxSize())
            if (live?.hasVideo != true) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LoadingIndicator(color = ON_SCRIM)
                    if (phase == LivePhase.Live) {
                        Text(stringResource(Res.string.story_live_waiting), color = ON_SCRIM, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        LivePhase.Ended, LivePhase.Failed -> {
            Text(
                stringResource(if (phase == LivePhase.Ended) Res.string.story_live_ended else Res.string.story_live_failed),
                color = ON_SCRIM,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            if (active) {
                LaunchedEffect(phase) {
                    var elapsed = 0L
                    var last = withFrameMillis { it }
                    while (elapsed < ENDED_DURATION_MS) {
                        val now = withFrameMillis { it }
                        if (!currentPaused) elapsed += now - last
                        last = now
                    }
                    finish()
                }
            }
        }
    }
}

/** Štítek „ŽIVĚ" a kolik lidí se dívá, místo času zveřejnění v hlavičce. */
@Composable
internal fun LiveBadge(viewers: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.story_live_badge),
            color = MaterialTheme.colorScheme.onError,
            style = MaterialTheme.typography.labelSmallEmphasized,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.error, ShapeDefaults.ExtraSmall)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
        if (viewers > 0) {
            Text(
                pluralStringResource(Res.plurals.story_live_viewers, viewers, viewers),
                color = ON_SCRIM,
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            )
        }
    }
}

/** Jak dlouho stojí věta o konci vysílání, než prohlížeč přejde dál. */
private const val ENDED_DURATION_MS = 6_000L
