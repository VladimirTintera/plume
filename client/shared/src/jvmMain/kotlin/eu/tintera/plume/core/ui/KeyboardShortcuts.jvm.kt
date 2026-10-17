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
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.input.key.KeyShortcut
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.shortcuts_group_chats

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
 *
 * **Spotřebovaná klávesa už nedojde do nabídky okna** ([DesktopMenuBar]), takže se akce
 * nespustí dvakrát (ověřeno sondou: ⌘K jen jednou). Nabídka dostane klávesu, jen když ji okno
 * nespotřebuje — tedy když zkratku nikdo neposlouchá (přihlašovací obrazovka); pak nic
 * nespustí ani ona.
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

/** Položky nabídky „Chaty" na macOS ([DesktopMenuBar]): u každé akce první klávesa. */
private val MAC_MENU_BINDINGS: List<ShortcutBinding> = SHORTCUT_BINDINGS.distinctBy { it.shortcut }

/**
 * Nabídka „Chaty" v liště macOS se zkratkami ze [SHORTCUT_BINDINGS] (u každé akce první,
 * tedy ⌘K, ⌘F, ⌥↓, ⌥↑). Na Macu se klávesové zkratky hledají v nabídkách — bez nich se
 * o ⌘K nikdo nedozvěděl. Položka spustí totéž co klávesa ([KeyboardShortcuts.trigger]).
 *
 * **Jen macOS.** Na Windows a Linuxu by `MenuBar` přidal pruh nabídek dovnitř okna nad obsah
 * aplikace, kde žádný jiný není. Nabídku aplikace (O aplikaci, Ukončit) přidává macOS sám.
 */
@Composable
fun FrameWindowScope.DesktopMenuBar() {
    if (desktopOs != DesktopOs.MacOs) return
    val shortcuts = koinInject<KeyboardShortcuts>()
    MenuBar {
        Menu(stringResource(Res.string.shortcuts_group_chats)) {
            MAC_MENU_BINDINGS.forEach { binding ->
                val keys = binding.menuKeys(commandIsMeta = true)
                Item(
                    text = stringResource(binding.shortcut.label),
                    shortcut = KeyShortcut(key = keys.key, ctrl = keys.ctrl, meta = keys.meta, alt = keys.alt, shift = keys.shift),
                    onClick = { shortcuts.trigger(binding.shortcut) },
                )
            }
        }
    }
}
