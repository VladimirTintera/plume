package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.TranscribeOffer
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.speech_to_text_24dp
import eu.tintera.plume.resources.transcript_failed
import eu.tintera.plume.resources.transcript_hide
import eu.tintera.plume.resources.transcript_pending
import eu.tintera.plume.resources.transcript_premium_required
import eu.tintera.plume.resources.transcript_show
import eu.tintera.plume.resources.transcript_too_long
import eu.tintera.plume.resources.transcript_trial_too_long
import eu.tintera.plume.resources.transcript_used_up
import eu.tintera.plume.resources.transcript_used_up_until
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Přepis hlasovky nebo kolečka u jedné zprávy: jestli je pod záznamem vidět, a co udělá ťuknutí
 * na tlačítko.
 *
 * Výsledek nese zpráva ([transcript]), viditelnost jen obrazovka. Přepis, který už ve zprávě je
 * (přepsal ho kdokoli dřív), je vidět hned — ťuknutím se schová.
 */
internal class TranscriptState(
    val transcript: SpeechTranscript?,
    val offer: TranscribeOffer,
    private val shownState: MutableState<Boolean>,
    private val onTranscribe: () -> Unit,
) {
    val shown: Boolean get() = shownState.value

    /** Tlačítko má smysl, jen když je co ukázat, nebo když jde přepis aspoň vysvětlit. */
    val hasButton: Boolean get() = transcript != null || offer != TranscribeOffer.Hidden

    val pending: Boolean get() = transcript is SpeechTranscript.Pending

    fun toggle() {
        if (shownState.value) {
            shownState.value = false
            return
        }
        shownState.value = true
        // Přepis se pouští jen tam, kde ještě není; nepovedený jde zkusit znovu, moc dlouhý ne.
        val retry = transcript == null || (transcript is SpeechTranscript.Failed && transcript.canRetry)
        if (retry && offer == TranscribeOffer.Available) onTranscribe()
    }
}

@Composable
internal fun rememberTranscriptState(
    key: Long,
    transcript: SpeechTranscript?,
    offer: TranscribeOffer,
    onTranscribe: () -> Unit,
): TranscriptState {
    val shown = rememberSaveable(key) { mutableStateOf(transcript != null) }
    return TranscriptState(transcript, offer, shown, onTranscribe)
}

/**
 * Malé tonální tlačítko vedle záznamu: přepsat, během přepisu kolečko, s ukázaným přepisem šipka
 * nahoru (schová ho). [container] / [content] dodává bublina — v odchozí `primary` bublině by
 * výchozí tonální barva splynula.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranscribeButton(
    state: TranscriptState,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = state::toggle,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = container, contentColor = content),
        modifier = modifier.size(TRANSCRIBE_BUTTON_SIZE),
        shapes = IconButtonDefaults.shapes(),
    ) {
        when {
            state.shown && state.pending -> CircularWavyProgressIndicator(color = content, modifier = Modifier.size(20.dp))
            state.shown -> Icon(
                painterResource(Res.drawable.keyboard_arrow_up_24dp),
                contentDescription = stringResource(Res.string.transcript_hide),
            )
            else -> Icon(
                painterResource(Res.drawable.speech_to_text_24dp),
                contentDescription = stringResource(Res.string.transcript_show),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Text přepisu, nebo věta, proč přepis není. Stavové věty jsou ztlumené, aby se nečetly jako
 * slova toho, kdo mluvil.
 */
@Composable
internal fun TranscriptText(state: TranscriptState, color: Color, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.shown,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        TranscriptBody(state, color)
    }
}

/** Obsah [TranscriptText] bez animace — kolečko ho kreslí v kartě, která se animuje celá. */
@Composable
internal fun TranscriptBody(state: TranscriptState, color: Color, modifier: Modifier = Modifier) {
    val muted = color.copy(alpha = 0.7f)
    val (text, textColor) = when (val transcript = state.transcript) {
        is SpeechTranscript.Done -> transcript.text to color
        is SpeechTranscript.Pending ->
            transcript.partial.ifBlank { stringResource(Res.string.transcript_pending) } to
                if (transcript.partial.isBlank()) muted else color
        is SpeechTranscript.Failed -> stringResource(
            when (transcript.reason) {
                SpeechTranscript.Failed.Reason.TooLong -> Res.string.transcript_too_long
                SpeechTranscript.Failed.Reason.PremiumRequired -> Res.string.transcript_premium_required
                SpeechTranscript.Failed.Reason.Other -> Res.string.transcript_failed
            },
        ) to muted
        null -> when (val offer = state.offer) {
            is TranscribeOffer.TooLong ->
                stringResource(Res.string.transcript_trial_too_long, formatDuration(offer.maxSeconds))
            is TranscribeOffer.UsedUp -> if (offer.resetsAt > 0) {
                stringResource(Res.string.transcript_used_up_until, offer.resetsAt.asUpcomingTime())
            } else {
                stringResource(Res.string.transcript_used_up)
            }
            // Přepis odešel a čeká se na první úpravu zprávy.
            TranscribeOffer.Available, TranscribeOffer.Hidden -> stringResource(Res.string.transcript_pending)
        } to muted
    }
    Text(text = text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

private val TRANSCRIBE_BUTTON_SIZE = 32.dp

/**
 * [top] a pod ním [below] zalomené na šířku [top]: přepis nemá bublinu rozšířit, má se zalomit
 * pod záznam. Vlastní `Layout`, ne `IntrinsicSize` — kolečko je `BoxWithConstraints`
 * a intrinsické míry neumí.
 */
@Composable
internal fun BelowWithSameWidth(
    top: @Composable () -> Unit,
    below: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 6.dp,
) {
    androidx.compose.ui.layout.Layout(contents = listOf(top, below), modifier = modifier) { (tops, belows), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val topPlaceables = tops.map { it.measure(loose) }
        val width = topPlaceables.maxOfOrNull { it.width } ?: 0
        val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
        val belowPlaceables = belows.map {
            it.measure(androidx.compose.ui.unit.Constraints(maxWidth = width))
        }
        val belowHeight = belowPlaceables.maxOfOrNull { it.height } ?: 0
        val gap = if (belowHeight > 0) spacing.roundToPx() else 0
        layout(width, topHeight + gap + belowHeight) {
            topPlaceables.forEach { it.place(0, 0) }
            belowPlaceables.forEach { it.place(0, topHeight + gap) }
        }
    }
}
