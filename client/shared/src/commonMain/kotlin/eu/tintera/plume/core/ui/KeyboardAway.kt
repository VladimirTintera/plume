package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Schová klávesnici a akci pustí, až klávesnice sjede. Bez otevřené klávesnice hned.
 *
 * **Na co to je:** sheet (i systémový výběr) je samostatné okno, které si bere fokus. Animaci
 * insetů klávesnice dostává okno, které ji řídí — a jakmile fokus převezme sheet, hlavní okno
 * dostane rovnou konečné insety. Konverzace nad sheetem pak skokem spadne dolů, zatímco klávesnice
 * teprve sjíždí. S čekáním se napřed plynule sesune obsah a teprve potom vyjede sheet.
 *
 * Čeká se nejvýš [KEYBOARD_AWAY_TIMEOUT]: kdyby insety nikdy nedošly k nule (plovoucí klávesnice,
 * hardwarová klávesnice), akce se nesmí ztratit. Rozsah korutin patří volajícímu composable —
 * odejde-li z kompozice dřív, akce se nekoná, a to je správně: tlačítko, ze kterého šla, už není.
 */
@Composable
fun rememberAfterKeyboardHidden(): (action: () -> Unit) -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val scope = rememberCoroutineScope()
    return remember(keyboard, density, ime, scope) {
        { action ->
            if (ime.getBottom(density) == 0) {
                action()
            } else {
                keyboard?.hide()
                scope.launch {
                    withTimeoutOrNull(KEYBOARD_AWAY_TIMEOUT) {
                        snapshotFlow { ime.getBottom(density) }.first { it == 0 }
                    }
                    action()
                }
            }
        }
    }
}

/** Klávesnice sjede zhruba za čtvrt vteřiny; déle se na ni nečeká. */
private val KEYBOARD_AWAY_TIMEOUT = 600.milliseconds
