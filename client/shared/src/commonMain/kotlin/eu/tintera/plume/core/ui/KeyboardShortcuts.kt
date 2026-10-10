package eu.tintera.plume.core.ui

import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Klávesová zkratka z fyzické klávesnice (Fold, tablet, Chromebook). */
enum class KeyboardShortcut { SearchChats, SearchInChat, NextChat, PreviousChat }

/**
 * Zkratky z klávesnice jako události. Chytá je platforma DŘÍV, než klávesu dostane Compose
 * (Android `MainActivity.dispatchKeyEvent`): v Compose dorazí klávesa jen k zaostřenému prvku,
 * a když není zaostřené nic (seznam chatů po spuštění), nedorazí nikam. A vstupní pole by
 * Alt+šipku spolklo jako pohyb kurzoru.
 *
 * Odebírá je ten, kdo ví, co s nimi: shell hledání (zná zásobník a otevřenou konverzaci),
 * seznam chatů přepínání chatů (zná pořadí, jak ho uživatel vidí).
 */
class KeyboardShortcuts {
    private val events = MutableSharedFlow<KeyboardShortcut>(extraBufferCapacity = 8)

    val shortcuts: SharedFlow<KeyboardShortcut> = events.asSharedFlow()

    /**
     * Ohlásí zkratku; `false` = nikdo ji neposlouchá (přihlašovací obrazovka) a klávesa má jít
     * dál, jako by zkratka nebyla.
     */
    fun trigger(shortcut: KeyboardShortcut): Boolean =
        events.subscriptionCount.value > 0 && events.tryEmit(shortcut)
}

/**
 * Zkratka pro stisk klávesy [key] s modifikátory; `null` = obyčejná klávesa. Volí se zkratky,
 * které psaní textu nepotřebuje, protože se chytají i nad zaostřeným polem:
 * - Ctrl+K hledání chatů, Ctrl+F hledání v konverzaci (jako v prohlížeči),
 * - Alt+↓ / Alt+↑ a Ctrl+Tab / Ctrl+Shift+Tab další a předchozí chat (jako Telegram Desktop).
 *
 * S klávesou Meta (Windows, ⌘) nic: tu si bere systém (Meta+/ seznam zkratek, Meta+Tab).
 */
fun keyboardShortcut(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): KeyboardShortcut? {
    if (meta) return null
    val ctrlOnly = ctrl && !alt && !shift
    val altOnly = alt && !ctrl && !shift
    return when {
        ctrlOnly && key == Key.K -> KeyboardShortcut.SearchChats
        ctrlOnly && key == Key.F -> KeyboardShortcut.SearchInChat
        altOnly && key == Key.DirectionDown -> KeyboardShortcut.NextChat
        altOnly && key == Key.DirectionUp -> KeyboardShortcut.PreviousChat
        ctrl && !alt && key == Key.Tab -> if (shift) KeyboardShortcut.PreviousChat else KeyboardShortcut.NextChat
        else -> null
    }
}

/**
 * Sousední chat v pořadí [ids], jak je uživatel vidí; [current] je otevřený chat. Bez otevřeného
 * (nebo s chatem, který v seznamu není) začne další chat od prvního a předchozí od posledního.
 * Na krajích se nezacyklí — skok z posledního na první by vypadal jako chyba.
 */
fun neighbourChat(ids: List<Long>, current: Long?, next: Boolean): Long? {
    if (ids.isEmpty()) return null
    val index = ids.indexOf(current)
    if (index < 0) return if (next) ids.first() else ids.last()
    return ids.getOrNull(if (next) index + 1 else index - 1)
}

/**
 * Klávesa a modifikátory jedné zkratky, jak je ukazuje systémový přehled (Android Meta+/).
 * Zkratky z [keyboardShortcut] se tu vypisují znovu, protože přehled potřebuje klávesy, ne
 * pravidlo — že se oba seznamy nerozešly, hlídá `KeyboardShortcutsTest`.
 */
data class ShortcutBinding(
    val shortcut: KeyboardShortcut,
    val key: Key,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
)

/** Zkratky v pořadí, v jakém je přehled ukáže. */
val SHORTCUT_BINDINGS: List<ShortcutBinding> = listOf(
    ShortcutBinding(KeyboardShortcut.SearchChats, Key.K, ctrl = true),
    ShortcutBinding(KeyboardShortcut.SearchInChat, Key.F, ctrl = true),
    ShortcutBinding(KeyboardShortcut.NextChat, Key.DirectionDown, alt = true),
    ShortcutBinding(KeyboardShortcut.PreviousChat, Key.DirectionUp, alt = true),
    ShortcutBinding(KeyboardShortcut.NextChat, Key.Tab, ctrl = true),
    ShortcutBinding(KeyboardShortcut.PreviousChat, Key.Tab, ctrl = true, shift = true),
)
