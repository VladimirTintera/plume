package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopOs
import org.koin.compose.koinInject

/**
 * Obsluha klávesových zkratek pro `onPreviewKeyEvent` hlavního okna — desktopový protějšek
 * `MainActivity.dispatchKeyEvent` na Androidu. Dřív na desktopu nefungovala žádná zkratka:
 * [KeyboardShortcuts] spouštěla jen activity a okno žádnou obsluhu kláves nemělo.
 *
 * Proč okno a ne `onPreviewKeyEvent` nějakého prvku: v Compose dorazí klávesa jen
 * k zaostřenému prvku a jeho předkům. Po spuštění není zaostřené nic, takže by zkratka nedošla
 * nikam, a vstupní pole by Alt+šipku spolklo jako pohyb kurzoru. `onPreviewKeyEvent` okna jde
 * PŘED scénou (`ComposeSceneMediator.onKeyEvent`), ať je zaostřené cokoli.
 *
 * Esc jako Zpět se tu neřeší: na desktopu ho převádí Compose sám (`BackNavigationEventInput`),
 * když klávesu nespotřebuje nic jiného.
 */
@Composable
fun rememberDesktopShortcutHandler(): (KeyEvent) -> Boolean {
    val shortcuts = koinInject<KeyboardShortcuts>()
    return remember(shortcuts) { { event -> shortcuts.handle(event) } }
}

/**
 * Zkratka ze stisku klávesy; `true` = vyřízeno a dál nepokračuje. Jen stisk — uvolnění jde dál
 * a nic nespustí. Na macOS je příkazová klávesa ⌘ (viz [keyboardShortcut]).
 */
private fun KeyboardShortcuts.handle(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val shortcut = keyboardShortcut(
        key = event.key,
        ctrl = event.isCtrlPressed,
        alt = event.isAltPressed,
        shift = event.isShiftPressed,
        meta = event.isMetaPressed,
        commandIsMeta = desktopOs == DesktopOs.MacOs,
    ) ?: return false
    return trigger(shortcut)
}
