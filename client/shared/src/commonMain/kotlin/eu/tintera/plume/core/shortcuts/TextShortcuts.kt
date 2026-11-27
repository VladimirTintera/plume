package eu.tintera.plume.core.shortcuts

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Vlastní textová zkratka: „/adresa" se ve vstupním poli nabídne a ťuknutím nahradí uloženým
 * [text]. Telegram má rychlé odpovědi jen v placeném Business; tyhle jsou v zařízení (mezi
 * zařízeními je nese synchronizace, `CloudSync`) a pro všechny účty společné — jsou to
 * uživatelovy texty, ne data účtu.
 *
 * [id] je pevné: synchronizace slévá po zkratkách podle něj, takže přejmenování na jednom
 * zařízení a úprava textu na druhém je táž zkratka, ne smazání a nová.
 */
@Serializable
data class TextShortcut(val id: String, val name: String, val text: String, val order: Int = 0)

/** Rozepsaná zkratka u kurzoru: `ahoj /adr|` → lomítko na [start], dotaz „adr". */
data class ShortcutQuery(val start: Int, val query: String)

/**
 * [ShortcutQuery] u kurzoru, nebo `null`. Stejná pravidla jako hashtag (`hashtagQueryAt`):
 * `/` na začátku textu nebo po mezeře (cesta „a/b" zkratka není), mezi ním a kurzorem jen
 * písmena, číslice a podtržítko. Samotné `/` je dotaz taky.
 */
fun shortcutQueryAt(text: String, cursor: Int): ShortcutQuery? {
    if (cursor < 1 || cursor > text.length) return null
    var slash = cursor - 1
    while (slash >= 0 && text[slash].isShortcutChar()) slash--
    if (slash < 0 || text[slash] != '/') return null
    if (slash > 0 && !text[slash - 1].isWhitespace()) return null
    val query = text.substring(slash + 1, cursor)
    if (query.length > MAX_SHORTCUT_NAME) return null
    return ShortcutQuery(slash, query)
}

/**
 * Zkratky, které začínají dotazem (bez ohledu na velikost písmen), v pořadí seznamu; přesná
 * shoda napřed. Prázdný dotaz vrátí všechny.
 */
fun matchingShortcuts(shortcuts: List<TextShortcut>, query: String): List<TextShortcut> {
    val needle = query.lowercase()
    return shortcuts.filter { it.name.lowercase().startsWith(needle) }
        .sortedWith(compareBy<TextShortcut> { it.name.lowercase() != needle }.thenBy { it.order }.thenBy { it.name })
}

/**
 * Text pole po výběru [shortcut] a kam patří kurzor: úsek od `/` po kurzor nahradí text
 * zkratky. Mezera se za něj nepřidává — zkratka bývá celá věta nebo adresa, ne slovo.
 */
fun shortcutInsertion(text: String, cursor: Int, query: ShortcutQuery, shortcut: TextShortcut): Pair<String, Int> {
    val result = text.substring(0, query.start) + shortcut.text + text.substring(cursor)
    return result to query.start + shortcut.text.length
}

/** Název, jak se píše za lomítko: malá písmena, číslice a podtržítko, nejvýš [MAX_SHORTCUT_NAME]. */
fun shortcutName(raw: String): String =
    raw.trim().removePrefix("/").lowercase().filter { it.isShortcutChar() }.take(MAX_SHORTCUT_NAME)

/** Předpona zkratky ve vstupním poli. */
const val SHORTCUT_PREFIX = "/"

/** `/název` — jak se zkratka píše a ukazuje (jako `hashtagText`). */
fun shortcutText(name: String): String = SHORTCUT_PREFIX + name

/** Nové id zkratky (šestnáctkové číslo, jako u štítků). */
fun newShortcutId(): String = Random.nextLong().toULong().toString(16)

private fun Char.isShortcutChar(): Boolean = isLetterOrDigit() || this == '_'

const val MAX_SHORTCUT_NAME = 32

/** Text zkratky může být delší zpráva, ne celý článek. */
const val MAX_SHORTCUT_TEXT = 4_000
