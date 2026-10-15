package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

import kotlinx.datetime.LocalDate

/** Delší dotaz se modelu nedává — hledání přirozeným jazykem je věta, ne odstavec. */
const val MAX_SEARCH_QUERY_CHARS = 300

/**
 * Prompt pro hledání přirozeným jazykem: věta („fotky ze skupin z loňského léta") → filtry,
 * které hledání zpráv už umí. Model nic negeneruje, jen vyplní pole; chybu uživatel vidí
 * hned ve filtrech nad výsledky a opraví ji ťuknutím (roadmapa).
 *
 * - **Výstup je pět řádků `KLÍČ: hodnota`**, ne věta ani JSON — malý model drží řádky
 *   spolehlivěji a [searchFields] je přečte i s omáčkou kolem.
 * - **Dnešní datum s jménem dne jde do promptu výslovně**: „loni v létě" a „minulý týden"
 *   se počítají od něj a model kalendář nezná (stejně jako u [reminderPrompt]).
 * - **Slova k hledání v podobě, jak je uživatel napsal**: TDLib hledá podle začátku slova,
 *   a model by „dovolené" rád převedl na „dovolená" — to by zprávu „z dovolené" nenašlo.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt]; dotaz bývá česky.
 * - [structured] = odpověď přijde jako [SearchAnswer] (`generateAnswer`); prompt pak o formátu
 *   mlčí, tvar dá modelu schéma.
 */
fun searchPrompt(query: String, today: LocalDate, structured: Boolean = false): String = buildString {
    val day = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    appendLine("You turn a search request in a chat app into search filters. Today is $day $today.")
    appendLine()
    appendLine("Request:")
    appendLine("\"\"\"")
    appendLine(query.take(MAX_SEARCH_QUERY_CHARS))
    appendLine("\"\"\"")
    appendLine()
    appendLine(if (structured) "Fill in these five fields:" else "Answer with exactly these five lines:")
    appendLine("TEXT: only the topic words to look for inside the messages, copied as the user wrote them; empty if there is no topic")
    appendLine("CHATS: PRIVATE, GROUPS, CHANNELS or ANY")
    appendLine("CONTENT: PHOTOS, VIDEOS, FILES, LINKS, VOICE, MUSIC or ANY")
    appendLine("FROM: the first day as YYYY-MM-DD, or NONE")
    appendLine("TO: the last day as YYYY-MM-DD, or NONE")
    appendLine()
    // Bez slovníku Gemma 4 E2B dala do TEXT celou větu a kanály nepoznala (ověřeno na
    // emulátoru). „Poslední měsíc" a slova filtru v TEXT plete i s ním — to dořeší pravidla
    // v aplikaci (`refineNaturalSearch`).
    appendLine("Words that set a filter never go into TEXT. The user may write Czech with or without diacritics:")
    appendLine("- fotky, fotka, obrázky, photos -> CONTENT: PHOTOS; videa, video -> VIDEOS; soubory, dokumenty, PDF -> FILES")
    appendLine("- odkazy, linky, links -> LINKS; hlasovky, hlasové zprávy -> VOICE; hudba, písničky -> MUSIC")
    appendLine("- kanál, kanálu, kanály, channels -> CHATS: CHANNELS; skupina, skupin, groups -> GROUPS; soukromé, private -> PRIVATE")
    appendLine("- poslední týden = the 7 days before today, poslední měsíc = the 30 days before today, loni = the whole last year")
    appendLine()
    appendLine("Example when today is 2026-10-11, request \"fotky z dovolené ve skupinách loni v létě\":")
    appendLine("TEXT: dovolené")
    appendLine("CHATS: GROUPS")
    appendLine("CONTENT: PHOTOS")
    appendLine("FROM: 2025-06-01")
    appendLine("TO: 2025-08-31")
    appendLine()
    appendLine("Example when today is 2026-10-11, request \"odkazy z kanalu za posledni tyden\":")
    appendLine("TEXT:")
    appendLine("CHATS: CHANNELS")
    appendLine("CONTENT: LINKS")
    appendLine("FROM: 2026-10-04")
    append("TO: 2026-10-11")
}

/**
 * Pole z výstupu modelu (`TEXT`, `CHATS`, `CONTENT`, `FROM`, `TO`) jako mapa s klíči velkými
 * písmeny. Tolerantní k obalu: odrážky, hvězdičky, mezery kolem dvojtečky, klíč malými písmeny.
 * Neznámé řádky se přeskočí; co v mapě chybí, to model nevyplnil.
 */
fun searchFields(output: String): Map<String, String> = buildMap {
    output.lineSequence().forEach { line ->
        val match = FIELD.find(line) ?: return@forEach
        val key = match.groupValues[1].uppercase()
        if (key !in this) put(key, match.groupValues[2].trim().trim('"', '*', '`').trim())
    }
}

private val FIELD = Regex("^[\\s*\\-•`]*(TEXT|CHATS|CONTENT|FROM|TO)[*`]*\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)

/**
 * Pole ze strukturované odpovědi ve tvaru, jaký vrací [searchFields] z textu — dál jde
 * všechno stejnou cestou. Prázdná a chybějící pole vynechá; `TEXT` smí být prázdný (nic
 * k hledání), proto se u něj nechává.
 */
fun searchFieldsOf(answer: SearchAnswer): Map<String, String> = buildMap {
    answer.text?.let { put("TEXT", it.trim()) }
    answer.chats?.trim()?.takeIf { it.isNotEmpty() }?.let { put("CHATS", it) }
    answer.content?.trim()?.takeIf { it.isNotEmpty() }?.let { put("CONTENT", it) }
    answer.from?.trim()?.takeIf { it.isNotEmpty() }?.let { put("FROM", it) }
    answer.to?.trim()?.takeIf { it.isNotEmpty() }?.let { put("TO", it) }
}
