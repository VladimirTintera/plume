package eu.tintera.plume.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Snackbar s **ubíhajícím odpočtem** u akce, kterou jde vrátit jen do určité chvíle (smazání chatu
 * s „Vrátit"). Vlevo kroužek, který se plynule vyprázdní, a v něm zbývající vteřiny — jako
 * u oficiálního klienta. Bez odpočtu by nebylo vidět, kolik času na „Vrátit" zbývá, a obyčejný
 * snackbar zmizí dřív, než se člověk rozhodne.
 *
 * - **Odpočet běží od chvíle, kdy je snackbar vidět**, ne od zavolání: snackbar ve frontě za jiným
 *   by jinak doběhl dřív, než se ukáže. Po [durationMs] se zavře sám ([CountdownSnackbarHost]).
 * - **Lhůta akce musí být o kus delší než [durationMs]**, aby „Vrátit" ťuknuté na poslední chvíli
 *   ještě stihlo (`PendingChatDeletes`).
 * - Kroužek je průběh (kolik času zbývá), proto `CircularProgressIndicator`, ne `LoadingIndicator`.
 */
class CountdownSnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
    val durationMs: Long,
) : SnackbarVisuals {
    override val withDismissAction: Boolean get() = false

    /** Zavírá ho odpočet, ne `SnackbarHostState` (ten zná jen pevné délky). */
    override val duration: SnackbarDuration get() = SnackbarDuration.Indefinite
}

/** Ukáže snackbar s odpočtem; výsledek jako `showSnackbar` (akce, nebo vypršení). */
suspend fun SnackbarHostState.showCountdownSnackbar(message: String, actionLabel: String, durationMs: Long): SnackbarResult =
    showSnackbar(CountdownSnackbarVisuals(message, actionLabel, durationMs))

/**
 * Obsah `SnackbarHost`, který snackbar s odpočtem ([CountdownSnackbarVisuals]) kreslí s kroužkem
 * a ostatní jako obyčejný M3 `Snackbar`. Patří do každého hostitele, který odpočet může dostat.
 */
@Composable
fun CountdownSnackbarHost(data: SnackbarData) {
    val visuals = data.visuals
    if (visuals !is CountdownSnackbarVisuals) {
        Snackbar(data)
        return
    }
    val remaining = remember(data) { Animatable(1f) }
    // Vteřiny jen při změně, ne každý snímek animace (kroužek čte průběh až při kreslení).
    val seconds by remember(data) {
        derivedStateOf { ceil(remaining.value * visuals.durationMs / 1000.0).toInt() }
    }
    LaunchedEffect(data) {
        remaining.animateTo(0f, tween(visuals.durationMs.toInt(), easing = LinearEasing))
        data.dismiss()
    }
    Snackbar(
        // Okraj jako u `Snackbar(data)`, který si ho přidává sám; bez něj snackbar s odpočtem
        // ležel přes celou šířku displeje, na rozdíl od všech ostatních.
        modifier = Modifier.padding(12.dp),
        action = {
            TextButton(
                onClick = data::performAction,
                colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                shapes = ButtonDefaults.shapes(),
            ) { Text(visuals.actionLabel) }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RING_SIZE)) {
                CircularProgressIndicator(
                    progress = { remaining.value },
                    color = SnackbarDefaults.contentColor,
                    trackColor = SnackbarDefaults.contentColor.copy(alpha = TRACK_ALPHA),
                    strokeWidth = RING_STROKE,
                    strokeCap = StrokeCap.Round,
                    // Bez mezery mezi průběhem a stopou (a bez tečky na jejím konci): u kroužku,
                    // který se jen vyprazdňuje, působila jako vada.
                    gapSize = 0.dp,
                    modifier = Modifier.size(RING_SIZE),
                )
                RollingNumber(
                    value = seconds,
                    // Řádek oříznutý na výšku písma a vystředěný, jinak by číslice seděla
                    // o kus níž kvůli proložení řádku.
                    style = MaterialTheme.typography.labelLargeEmphasized.tabularNumbers().copy(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                    color = SnackbarDefaults.contentColor,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(visuals.message)
        }
    }
}

private val RING_SIZE = 32.dp
private val RING_STROKE = 3.dp
private const val TRACK_ALPHA = 0.3f

/**
 * Jak dlouho jde smazání vrátit (chat, soubor ze Stahování). Delší než odpočet snackbaru
 * ([UNDO_COUNTDOWN_MS]) s „Vrátit".
 */
const val UNDO_WINDOW_MS = 8_000L

/**
 * Odpočet ve snackbaru s „Vrátit" (`CountdownSnackbarVisuals`): o půl vteřiny kratší než lhůta,
 * aby ťuknutí na „Vrátit" na poslední chvíli smazání ještě předběhlo.
 */
const val UNDO_COUNTDOWN_MS = UNDO_WINDOW_MS - 500L
