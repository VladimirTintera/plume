package eu.tintera.plume.core.ui

import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.compose.ui.input.key.nativeKeyCode
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.shortcut_back
import eu.tintera.plume.resources.shortcut_new_line
import eu.tintera.plume.resources.shortcut_next_chat
import eu.tintera.plume.resources.shortcut_previous_chat
import eu.tintera.plume.resources.shortcut_search_chats
import eu.tintera.plume.resources.shortcut_search_in_chat
import eu.tintera.plume.resources.shortcut_send
import eu.tintera.plume.resources.shortcuts_group_chats
import eu.tintera.plume.resources.shortcuts_group_writing
import org.jetbrains.compose.resources.StringResource
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

/**
 * Zkratky pro systémový přehled (Meta+/, `Activity.onProvideKeyboardShortcuts`): chaty
 * ([SHORTCUT_BINDINGS] a Esc) a psaní zprávy (Enter odešle, Shift+Enter zalomí — viz
 * `MessageInput`). Texty se čtou předem ([load]), protože přehled se skládá synchronně na hlavním
 * vlákně a prostředky Compose se čtou `suspend`.
 */
class KeyboardShortcutOverview private constructor(private val texts: Map<StringResource, String>) {

    fun groups(): List<KeyboardShortcutGroup> {
        fun info(label: StringResource, keyCode: Int, modifiers: Int = 0) =
            KeyboardShortcutInfo(texts.getValue(label), keyCode, modifiers)
        val chats = SHORTCUT_BINDINGS.map { binding ->
            var modifiers = 0
            if (binding.ctrl) modifiers = modifiers or KeyEvent.META_CTRL_ON
            if (binding.alt) modifiers = modifiers or KeyEvent.META_ALT_ON
            if (binding.shift) modifiers = modifiers or KeyEvent.META_SHIFT_ON
            info(binding.shortcut.label, binding.key.nativeKeyCode, modifiers)
        } + info(Res.string.shortcut_back, KeyEvent.KEYCODE_ESCAPE)
        val writing = listOf(
            info(Res.string.shortcut_send, KeyEvent.KEYCODE_ENTER),
            info(Res.string.shortcut_new_line, KeyEvent.KEYCODE_ENTER, KeyEvent.META_SHIFT_ON),
        )
        return listOf(
            KeyboardShortcutGroup(texts.getValue(Res.string.shortcuts_group_chats), chats),
            KeyboardShortcutGroup(texts.getValue(Res.string.shortcuts_group_writing), writing),
        )
    }

    companion object {
        /** Texty v jazyce aplikace. Po změně jazyka se activity skládá znovu a čte je znovu. */
        suspend fun load(): KeyboardShortcutOverview {
            val labels = KeyboardShortcut.entries.map { it.label } + listOf(
                Res.string.shortcut_back,
                Res.string.shortcut_send,
                Res.string.shortcut_new_line,
                Res.string.shortcuts_group_chats,
                Res.string.shortcuts_group_writing,
            )
            return KeyboardShortcutOverview(labels.associateWith { readString(it) })
        }
    }
}

private val KeyboardShortcut.label: StringResource
    get() = when (this) {
        KeyboardShortcut.SearchChats -> Res.string.shortcut_search_chats
        KeyboardShortcut.SearchInChat -> Res.string.shortcut_search_in_chat
        KeyboardShortcut.NextChat -> Res.string.shortcut_next_chat
        KeyboardShortcut.PreviousChat -> Res.string.shortcut_previous_chat
    }
