package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType

/**
 * Text zprávy s formátováním převedeným na Markdown — pro kopírování výběru do poznámek.
 *
 * Převádí se jen to, co Markdown umí a co nese význam: tučné `**`, kurzíva `_`, přeškrtnuté
 * `~~`, kód `` ` `` a blok kódu s jazykem, odkaz s popiskem `[text](url)`, spoiler `||`
 * (Telegram i Discord) a citace `> `. Podtržení Markdown nemá a zmínky, hashtagy, příkazy
 * a vlastní emoji jsou v textu čitelné samy — zůstávají, jak jsou. Odkaz psaný přímo v textu
 * (popisek == adresa) se nebalí, `[x](x)` by jen zdvojil adresu.
 *
 * **Znaky Markdownu v textu se neescapují.** Výsledek je pro čtení a vkládání do poznámek;
 * hvězdička nebo podtržítko v obyčejné větě by se escapováním změnily v `\*`, které poznámková
 * aplikace ukáže doslova. Za cenu, že výjimečně něco omylem zformátuje.
 *
 * Offsety entit jsou v UTF-16, stejně jako indexy `String` — emoji před formátováním je
 * neposune.
 */
internal fun markdownOf(text: String, entities: List<MessageEntity>): String {
    val marks = entities.mapNotNull { it.toMarks(text) }
    if (marks.isEmpty()) return text

    // Kdo dřív začne, otevírá dřív; při stejném začátku delší (vnější) napřed. Zavírá se
    // v opačném pořadí, takže se značky vnoří a nekříží.
    val opening = marks.sortedWith(compareBy<Marks>({ it.start }, { -it.end }))
    // Úseky se stejnými kraji (tučná kurzíva přes celé slovo) se zavírají přesně obráceně, než
    // se otevřely — bez pořadí otevření by z `**_x_**` bylo `**_x**_`.
    val rank = opening.withIndex().associate { (index, mark) -> mark to index }
    val closing = marks.sortedWith(compareBy<Marks>({ it.end }, { -it.start }, { -rank.getValue(it) }))
    val quotes = marks.filter { it.quote }

    return buildString {
        var open = 0
        var close = 0
        for (index in 0..text.length) {
            while (close < closing.size && closing[close].end == index) append(closing[close++].suffix)
            while (open < opening.size && opening[open].start == index) append(opening[open++].prefix)
            if (index == text.length) break
            val char = text[index]
            append(char)
            // Každý řádek citace začíná `> `, ne jen první.
            // Zalomení UVNITŘ citace; to před ní řeší značka na jejím začátku.
            if (char == '\n' && quotes.any { index >= it.start && index + 1 < it.end }) {
                append("> ")
            }
        }
    }
}

/** Úsek [start, end) a co se před něj a za něj vloží. */
private class Marks(val start: Int, val end: Int, val prefix: String, val suffix: String, val quote: Boolean = false)

private fun MessageEntity.toMarks(text: String): Marks? {
    val from = offset.coerceIn(0, text.length)
    val to = (offset + length).coerceIn(from, text.length)
    if (from == to) return null
    return when (val type = type) {
        MessageEntityType.Bold -> inline(text, from, to, "**")
        MessageEntityType.Italic -> inline(text, from, to, "_")
        MessageEntityType.Strikethrough -> inline(text, from, to, "~~")
        MessageEntityType.Spoiler -> inline(text, from, to, "||")
        // Blok kódu a citace musí stát na vlastním řádku, jinak je Markdown nepozná.
        is MessageEntityType.Code -> if (type.block) {
            Marks(from, to, lineBreakBefore(text, from) + "```${type.language}\n", "\n```" + lineBreakAfter(text, to))
        } else {
            inline(text, from, to, "`")
        }
        is MessageEntityType.Link ->
            if (text.substring(from, to) == type.url || type.url.endsWith("//" + text.substring(from, to))) {
                null
            } else {
                Marks(from, to, "[", "](${type.url})")
            }
        is MessageEntityType.Quote -> Marks(from, to, lineBreakBefore(text, from) + "> ", lineBreakAfter(text, to), quote = true)
        else -> null
    }
}

/**
 * Značka v řádku obejde mezery na krajích úseku: `** tučně**` Markdown za tučné nevezme. Úsek
 * jen z mezer se nevyznačí vůbec.
 */
private fun inline(text: String, from: Int, to: Int, mark: String): Marks? {
    var start = from
    var end = to
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return if (start == end) null else Marks(start, end, mark, mark)
}

private fun lineBreakBefore(text: String, index: Int) = if (index > 0 && text[index - 1] != '\n') "\n" else ""

private fun lineBreakAfter(text: String, index: Int) = if (index < text.length && text[index] != '\n') "\n" else ""
