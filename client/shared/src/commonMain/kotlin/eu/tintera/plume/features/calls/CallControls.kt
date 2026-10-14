package eu.tintera.plume.features.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.IntSize
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_controls_toggle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Viditelnost ovládání hovoru a co ji řídí ([modifier] patří na plochu přes celou obrazovku). */
@Stable
internal class CallControls(val visible: Boolean, val modifier: Modifier)

/**
 * Ovládání přes obraz hovoru: s [autoHide] (videohovor přes celou plochu, spojený) po chvíli
 * bez dotyku zmizí, aby nezakrývalo obraz, a ťuknutí do obrazu ho vrátí. Jako přehrávač videa.
 *
 * - **Ťuknutí do obrazu přepíná** (ukázat ↔ schovat). Tlačítka si ťuknutí berou sama, plocha
 *   pod nimi dostane jen ťuknutí mimo ně.
 * - **Každý dotyk odpočet obnoví** — i na tlačítko, ať ovládání nezmizí pod prstem
 *   (`PointerEventPass.Initial`, nic se nespotřebuje).
 * - **Myš (desktop) ovládání ukáže pohybem**, jen dotyk by na počítači nikdo nehledal.
 *   Pohyb prstu ne: ten předchází každému ťuknutí a hned by se přepnul zpátky.
 * - **Doba bere ohled na přístupnost** (`calculateRecommendedTimeoutMillis`): kdo má v systému
 *   delší čas na reakci, dostane ho; „nikdy" ovládání neschová vůbec.
 * - Bez [autoHide] (zvonění, hlasový hovor, podržený hovor s tlačítkem Pokračovat) je
 *   ovládání vidět pořád.
 */
@Composable
internal fun rememberCallControls(autoHide: Boolean): CallControls {
    var shown by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    val accessibility = LocalAccessibilityManager.current
    val timeout = remember(accessibility) {
        accessibility?.calculateRecommendedTimeoutMillis(
            CONTROLS_TIMEOUT_MS,
            containsIcons = true,
            containsText = true,
            containsControls = true,
        ) ?: CONTROLS_TIMEOUT_MS
    }
    LaunchedEffect(autoHide, shown, touches) {
        if (!autoHide) {
            shown = true
            return@LaunchedEffect
        }
        if (shown && timeout != Long.MAX_VALUE) {
            delay(timeout)
            shown = false
        }
    }
    val toggleLabel = stringResource(Res.string.call_controls_toggle)
    val modifier = if (!autoHide) {
        Modifier
    } else {
        Modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press -> touches++
                            PointerEventType.Move -> if (event.changes.any { it.type == PointerType.Mouse }) {
                                if (shown) touches++ else shown = true
                            }
                        }
                    }
                }
            }
            .clickable(interactionSource = null, indication = null, onClickLabel = toggleLabel) { shown = !shown }
    }
    return CallControls(visible = shown || !autoHide, modifier = modifier)
}

/** Blok ovládání, který se schovává a ukazuje: prolnutí a sbalení, ať se zbytek posune plynule. */
@Composable
internal fun ColumnScope.CallControlsVisibility(visible: Boolean, content: @Composable () -> Unit) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(effects) + expandVertically(spatial),
        exit = fadeOut(effects) + shrinkVertically(spatial),
    ) {
        content()
    }
}

private const val CONTROLS_TIMEOUT_MS = 4_000L
