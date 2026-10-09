package eu.tintera.plume.core.ui

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key

/**
 * Zkratka z klávesy, která přichází do activity (`dispatchKeyEvent`); `true` = vyřízeno a dál
 * nepokračuje. Jen stisk — uvolnění zkratky jde dál a nic nespustí.
 */
fun KeyboardShortcuts.handle(event: KeyEvent): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false
    val shortcut = keyboardShortcut(
        key = Key(event.keyCode),
        ctrl = event.isCtrlPressed,
        alt = event.isAltPressed,
        shift = event.isShiftPressed,
        meta = event.isMetaPressed,
    ) ?: return false
    return trigger(shortcut)
}

/**
 * Esc bez modifikátorů = Zpět: zavře hledání, konverzaci, sheet. Systém to dělá sám (záložní
 * akce Esc v mapě kláves), jenže jen když klávesu nikdo nezpracuje — a zaostřené vstupní pole
 * ji spolkne, takže z konverzace s kurzorem v poli Esc nedělal nic. Proto dřív než Compose,
 * a jako systémové Zpět až při uvolnění: stisk se jen spolkne, aby nic nespustil dvakrát.
 */
fun handleEscape(event: KeyEvent, onBack: () -> Unit): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_ESCAPE || event.hasModifiers(KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON)) {
        return false
    }
    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
    return true
}
