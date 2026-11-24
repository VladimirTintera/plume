package eu.tintera.plume.core.ui

import eu.tintera.plume.resources.shortcut_previous_chat
import eu.tintera.plume.resources.shortcut_next_chat
import eu.tintera.plume.resources.shortcut_search_in_chat
import eu.tintera.plume.resources.shortcut_search_chats
import eu.tintera.plume.resources.shortcut_toggle_chat_list
import eu.tintera.plume.resources.shortcut_toggle_profile_pane
import eu.tintera.plume.resources.Res
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Klávesová zkratka z fyzické klávesnice (desktop, Fold, tablet, Chromebook). */
enum class KeyboardShortcut { SearchChats, SearchInChat, NextChat, PreviousChat, ToggleChatList, ToggleProfilePane }

/**
 * Zkratky z klávesnice jako události. Chytá je platforma DŘÍV, než klávesu dostane Compose
 * (Android `MainActivity.dispatchKeyEvent`, desktop `onPreviewKeyEvent` okna, viz
 * `rememberDesktopShortcutHandler`): v Compose dorazí klávesa jen k zaostřenému prvku,
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
 * - Alt+↓ / Alt+↑ a Ctrl+Tab / Ctrl+Shift+Tab další a předchozí chat (jako Telegram Desktop),
 * - Ctrl+Shift+S sbalí nebo rozbalí seznam chatů vedle konverzace. Ctrl+B (VS Code) by se pralo
 *   s tučným písmem ve vstupním poli; Ctrl+S samo patří uložení v prohlížeči médií.
 * - Ctrl+Shift+I otevře nebo zavře panel s profilem vedle konverzace. Ne Ctrl+Alt+I: na Windows
 *   je Ctrl+Alt totéž co AltGr a AltGr+I v některých rozloženích píše znak.
 *
 * S klávesou Meta (Windows, ⌘) nic: tu si bere systém (Meta+/ seznam zkratek, Meta+Tab).
 *
 * **Výjimka je macOS ([commandIsMeta])**: tam je příkazová klávesa ⌘, ne Ctrl, takže hledání je
 * ⌘K a ⌘F a Ctrl+K / Ctrl+F nic nedělají (Ctrl+K maže v textovém poli do konce řádku).
 * Přepínání chatů zůstává Ctrl+Tab a ⌥+šipka — ⌘Tab si bere systém. Seznam chatů je ⌃⌘S,
 * jako „Zobrazit / skrýt boční panel" ve Finderu, Mailu a Poznámkách, panel s profilem ⌥⌘I
 * jako inspektor ve Finderu.
 */
fun keyboardShortcut(
    key: Key,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean,
    meta: Boolean,
    commandIsMeta: Boolean = false,
): KeyboardShortcut? {
    val command = if (commandIsMeta) meta && !ctrl else ctrl && !meta
    val commandOnly = command && !alt && !shift
    val altOnly = alt && !ctrl && !shift && !meta
    val toggleList = if (commandIsMeta) ctrl && meta && !alt && !shift else ctrl && shift && !alt && !meta
    val toggleProfile = if (commandIsMeta) alt && meta && !ctrl && !shift else ctrl && shift && !alt && !meta
    return when {
        toggleList && key == Key.S -> KeyboardShortcut.ToggleChatList
        toggleProfile && key == Key.I -> KeyboardShortcut.ToggleProfilePane
        commandOnly && key == Key.K -> KeyboardShortcut.SearchChats
        commandOnly && key == Key.F -> KeyboardShortcut.SearchInChat
        altOnly && key == Key.DirectionDown -> KeyboardShortcut.NextChat
        altOnly && key == Key.DirectionUp -> KeyboardShortcut.PreviousChat
        ctrl && !alt && !meta && key == Key.Tab -> if (shift) KeyboardShortcut.PreviousChat else KeyboardShortcut.NextChat
        else -> null
    }
}

/**
 * Sousední chat v pořadí [ids], jak je uživatel vidí; [current] je otevřený chat. Bez otevřeného
 * (nebo s chatem, který v seznamu není) začne další chat od prvního a předchozí od posledního.
 * Na krajích se nezacyklí — skok z posledního na první by vypadal jako chyba. Klíč je id chatu,
 * ve společném seznamu víc účtů id s účtem (totéž id má chat ve dvou účtech).
 */
fun <K> neighbourChat(ids: List<K>, current: K?, next: Boolean): K? {
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
    ShortcutBinding(KeyboardShortcut.ToggleChatList, Key.S, ctrl = true, shift = true),
    ShortcutBinding(KeyboardShortcut.ToggleProfilePane, Key.I, ctrl = true, shift = true),
)

/** Popis zkratky v přehledu (Android Meta+/) a v nabídce okna (macOS). */
internal val KeyboardShortcut.label: StringResource
    get() = when (this) {
        KeyboardShortcut.SearchChats -> Res.string.shortcut_search_chats
        KeyboardShortcut.SearchInChat -> Res.string.shortcut_search_in_chat
        KeyboardShortcut.NextChat -> Res.string.shortcut_next_chat
        KeyboardShortcut.PreviousChat -> Res.string.shortcut_previous_chat
        KeyboardShortcut.ToggleChatList -> Res.string.shortcut_toggle_chat_list
        KeyboardShortcut.ToggleProfilePane -> Res.string.shortcut_toggle_profile_pane
    }

/** Klávesy zkratky i s ⌘ (Meta) — pro nabídku okna, která klávesu ukazuje a sama ji chytá. */
data class MenuKeys(
    val key: Key,
    val meta: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
)

/**
 * Klávesy [this] pro platformu, kde je příkazová klávesa ⌘ ([commandIsMeta], macOS): Ctrl
 * u hledání se tam mění na ⌘, Ctrl+Tab přepínání chatů zůstává (⌘Tab si bere systém) —
 * stejně jako v [keyboardShortcut]. Seznam chatů je tam ⌃⌘S místo Ctrl+Shift+S, panel s profilem
 * ⌥⌘I místo Ctrl+Shift+I. Že nabídka
 * slibuje to, co pravidlo opravdu dělá, hlídá test.
 */
fun ShortcutBinding.menuKeys(commandIsMeta: Boolean): MenuKeys {
    if (commandIsMeta && shortcut == KeyboardShortcut.ToggleChatList) return MenuKeys(key, meta = true, ctrl = true)
    if (commandIsMeta && shortcut == KeyboardShortcut.ToggleProfilePane) return MenuKeys(key, meta = true, alt = true)
    val command = ctrl && key != Key.Tab
    return MenuKeys(
        key = key,
        meta = commandIsMeta && command,
        ctrl = ctrl && !(commandIsMeta && command),
        alt = alt,
        shift = shift,
    )
}
