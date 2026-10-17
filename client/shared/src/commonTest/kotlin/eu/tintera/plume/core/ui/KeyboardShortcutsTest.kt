package eu.tintera.plume.core.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KeyboardShortcutsTest {

    /** Systémový přehled nesmí slibovat klávesu, kterou aplikace nechytá — nebo jinou zkratku. */
    @Test
    fun prehledSediSeZkratkami() {
        SHORTCUT_BINDINGS.forEach { binding ->
            assertEquals(
                binding.shortcut,
                keyboardShortcut(binding.key, binding.ctrl, binding.alt, binding.shift, meta = false),
                "Přehled ukazuje $binding",
            )
        }
    }

    /** Každá zkratka je v přehledu aspoň jednou. */
    @Test
    fun prehledMaVsechnyZkratky() {
        assertEquals(KeyboardShortcut.entries.toSet(), SHORTCUT_BINDINGS.map { it.shortcut }.toSet())
    }

    private fun shortcut(key: Key, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false, meta: Boolean = false) =
        keyboardShortcut(key, ctrl, alt, shift, meta)

    @Test
    fun zkratkyHledani() {
        assertEquals(KeyboardShortcut.SearchChats, shortcut(Key.K, ctrl = true))
        assertEquals(KeyboardShortcut.SearchInChat, shortcut(Key.F, ctrl = true))
    }

    @Test
    fun prepinaniChatu() {
        assertEquals(KeyboardShortcut.NextChat, shortcut(Key.DirectionDown, alt = true))
        assertEquals(KeyboardShortcut.PreviousChat, shortcut(Key.DirectionUp, alt = true))
        assertEquals(KeyboardShortcut.NextChat, shortcut(Key.Tab, ctrl = true))
        assertEquals(KeyboardShortcut.PreviousChat, shortcut(Key.Tab, ctrl = true, shift = true))
    }

    /** Psaní nesmí spustit nic: holé klávesy, Shift+šipka (výběr textu) ani Ctrl+Shift+K. */
    @Test
    fun obycejneKlavesyNejsouZkratky() {
        assertNull(shortcut(Key.K))
        assertNull(shortcut(Key.DirectionDown))
        assertNull(shortcut(Key.DirectionDown, shift = true))
        assertNull(shortcut(Key.K, ctrl = true, shift = true))
        assertNull(shortcut(Key.DirectionDown, ctrl = true, alt = true))
    }

    /** Meta (⌘, Windows) si bere systém. */
    @Test
    fun metaPatriSystemu() {
        assertNull(shortcut(Key.K, ctrl = true, meta = true))
        assertNull(shortcut(Key.Tab, meta = true))
    }

    private fun macShortcut(key: Key, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false, meta: Boolean = false) =
        keyboardShortcut(key, ctrl, alt, shift, meta, commandIsMeta = true)

    /** macOS: hledání je ⌘K a ⌘F, Ctrl+K a Ctrl+F nic (Ctrl+K maže v poli do konce řádku). */
    @Test
    fun macHledaniPresCmd() {
        assertEquals(KeyboardShortcut.SearchChats, macShortcut(Key.K, meta = true))
        assertEquals(KeyboardShortcut.SearchInChat, macShortcut(Key.F, meta = true))
        assertNull(macShortcut(Key.K, ctrl = true))
        assertNull(macShortcut(Key.F, ctrl = true))
        assertNull(macShortcut(Key.K, ctrl = true, meta = true))
    }

    /** macOS: přepínání chatů zůstává Ctrl+Tab a ⌥+šipka; s ⌘ nic. */
    @Test
    fun macPrepinaniChatu() {
        assertEquals(KeyboardShortcut.NextChat, macShortcut(Key.Tab, ctrl = true))
        assertEquals(KeyboardShortcut.PreviousChat, macShortcut(Key.Tab, ctrl = true, shift = true))
        assertEquals(KeyboardShortcut.NextChat, macShortcut(Key.DirectionDown, alt = true))
        assertNull(macShortcut(Key.DirectionDown, alt = true, meta = true))
        assertNull(macShortcut(Key.Tab, meta = true))
    }

    @Test
    fun sousedniChat() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(3L, neighbourChat(ids, current = 2L, next = true))
        assertEquals(1L, neighbourChat(ids, current = 2L, next = false))
        // Na krajích se nezacyklí.
        assertNull(neighbourChat(ids, current = 3L, next = true))
        assertNull(neighbourChat(ids, current = 1L, next = false))
    }

    /** Bez otevřeného chatu (nebo s chatem mimo seznam) se začíná od kraje. */
    @Test
    fun bezOtevrenehoChatuOdKraje() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(1L, neighbourChat(ids, current = null, next = true))
        assertEquals(3L, neighbourChat(ids, current = 99L, next = false))
        assertNull(neighbourChat(emptyList(), current = null, next = true))
    }

    @Test
    fun bezPosluchaceZkratkaPropadne() {
        assertFalse(KeyboardShortcuts().trigger(KeyboardShortcut.SearchChats))
    }

    @Test
    fun nabidkaOknaSlibujeKlavesyKtereFunguji() {
        // Nabídka na macOS ukazuje ⌘K a ⌘F a sama je chytá: musí to být klávesy, které pravidlo
        // na Macu opravdu rozpozná — a Ctrl+Tab tam zůstat Ctrl+Tab.
        listOf(true, false).forEach { mac ->
            SHORTCUT_BINDINGS.forEach { binding ->
                val keys = binding.menuKeys(commandIsMeta = mac)
                assertEquals(
                    binding.shortcut,
                    keyboardShortcut(keys.key, ctrl = keys.ctrl, alt = keys.alt, shift = keys.shift, meta = keys.meta, commandIsMeta = mac),
                    "$binding na ${if (mac) "macOS" else "Windows"}",
                )
            }
        }
        assertEquals(MenuKeys(Key.K, meta = true), SHORTCUT_BINDINGS.first().menuKeys(commandIsMeta = true))
    }
}
