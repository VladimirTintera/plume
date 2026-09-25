package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ConnectionState
import kotlinx.coroutines.delay

/**
 * Stav spojení, jak se má ukázat: `null`, dokud je všechno v pořádku.
 *
 * **Výpadek se ukáže až po [CONNECTION_SHOW_DELAY_MS], návrat hned.** Po každém návratu
 * z pozadí TDLib na zlomek vteřiny hlásí `Updating` a prstenec by při každém otevření aplikace
 * problikl kolem fotky. Změna mezi dvěma stavy výpadku (čekání na síť → připojování) je už
 * vidět, takže se nečeká.
 */
@Composable
internal fun rememberShownConnection(state: ConnectionState): ConnectionState? {
    val shown by produceState<ConnectionState?>(initialValue = null, state) {
        if (state == ConnectionState.Ready) {
            value = null
            return@produceState
        }
        if (value == null) delay(CONNECTION_SHOW_DELAY_MS)
        value = state
    }
    return shown
}

/**
 * Prstenec kolem fotky účtu, když aplikace nemá spojení. Text stavu nese nápověda v pilulce
 * hledání; prstenec je to, čeho si uživatel všimne koutkem oka.
 *
 * - **Připojování a aktualizace: obíhající vlnka** v `primary` — něco se děje, stačí počkat.
 * - **Čekání na síť: STOJÍCÍ vlnka** v `outline`. Na nic se nečeká, dokud se síť nevrátí, takže
 *   by rotace lhala; tlumená barva říká „tady nic neběží", vlnka „a spojení není v pořádku".
 *
 * Kreslí se mimo `IconButton`, protože ten obsah ořezává na svůj 40dp kruh.
 *
 * Při odjezdu kreslí poslední známý stav — v okamžiku skrytí je hodnota už `null`
 * a `AnimatedVisibility` by dobíhala s prázdným obsahem.
 */
@Composable
internal fun ConnectionRing(connection: ConnectionState?, modifier: Modifier = Modifier) {
    val last = remember { LastValue<ConnectionState>() }
    connection?.let { last.value = it }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val stroke = with(LocalDensity.current) { Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round) }

    AnimatedVisibility(
        visible = connection != null,
        // Prstenec naroste z fotky, ne odněkud z pilulky.
        enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.7f),
        exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.7f),
        // Stav je v nápovědě pilulky a v popisu tlačítka; indikátor by čtečce hlásil jen „průběh".
        modifier = modifier.clearAndSetSemantics { },
    ) {
        val state = last.value ?: return@AnimatedVisibility
        Crossfade(targetState = state == ConnectionState.WaitingForNetwork, animationSpec = effects) { waiting ->
            if (waiting) {
                CircularWavyProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(RING_SIZE),
                    color = MaterialTheme.colorScheme.outline,
                    trackColor = Color.Transparent,
                    stroke = stroke,
                    trackStroke = stroke,
                    // Výchozí amplituda je u plného průběhu nulová (kruh); tady má vlnka zůstat.
                    amplitude = { 1f },
                    waveSpeed = 0.dp,
                )
            } else {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(RING_SIZE),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    stroke = stroke,
                    trackStroke = stroke,
                )
            }
        }
    }
}

/** Obyčejné pole, ne stav — čte se jen při skládání obsahu, který se kvůli němu nepřekresluje. */
private class LastValue<T> {
    var value: T? = null
}

private const val CONNECTION_SHOW_DELAY_MS = 600L

/** Průměr prstence. Vejde se do 48dp slotu tlačítka i do 56dp pilulky. */
internal val RING_SIZE = 42.dp

private val RING_STROKE = 3.dp
