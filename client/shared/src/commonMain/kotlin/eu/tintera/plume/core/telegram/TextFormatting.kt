package eu.tintera.plume.core.telegram

/**
 * Ruční formátování, které jde nastavit ve vstupním poli.
 *
 * Odkazy, zmínky, hashtagy a příkazy sem nepatří: ty se při odeslání dohledají v textu samy
 * (`getTextEntities`), takže je pole nese jen jako text. Tohle je to, co z textu poznat nejde —
 * co musí pisatel říct.
 */
enum class TextFormat {
    Bold,
    Italic,
    Underline,
    Strikethrough,
    Spoiler,

    /** Kód v řádku. */
    Code,

    /** Blok kódu (`pre`) — ostatní klienti ho kreslí na vlastních řádcích s tlačítkem kopírování. */
    CodeBlock,

    Quote,

    /** Citace, kterou ostatní klienti sbalují na pár řádků. */
    ExpandableQuote,

    /** Odkaz s vlastním popiskem: text je popisek, adresu nese [FormatSpan.url]. */
    Link,
    ;

    /**
     * Druhy, které jsou jednou věcí ve dvou podobách. Nabídka přepíná RODINU: „Kód" u bloku
     * kódu ho zruší, stejně jako u kódu v řádku, a citace se nesmí vnořit ani překrývat se
     * sbalitelnou citací (TDLib: „blockquote entities can't be nested").
     */
    val family: TextFormat
        get() = when (this) {
            CodeBlock -> Code
            ExpandableQuote -> Quote
            else -> this
        }
}

/**
 * Formátovaný úsek `[start, end)` rozepsaného textu, v UTF-16 jednotkách jako `String`.
 * [url] je adresa odkazu ([TextFormat.Link]); u ostatních formátů `null`.
 */
data class FormatSpan(val format: TextFormat, val start: Int, val end: Int, val url: String? = null)

/**
 * Rozepsaný text i s ručním formátováním — to, co vstupní pole odešle, uloží jako koncept
 * a do čeho vrátí upravovanou zprávu.
 */
data class ComposedText(val text: String, val formats: List<FormatSpan> = emptyList()) {

    /**
     * Text oříznutý o bílé znaky na krajích, jak se posílá — i s rozsahy posunutými o to, co
     * zmizelo zepředu. Bez posunu by tučné slovo za mezerou na začátku odešlo o znak vedle.
     */
    fun trimmed(): ComposedText {
        val leading = text.length - text.trimStart().length
        val trimmed = text.trim()
        if (leading == 0 && trimmed.length == text.length) return this
        return ComposedText(
            text = trimmed,
            formats = formats.mapNotNull { span ->
                val start = (span.start - leading).coerceIn(0, trimmed.length)
                val end = (span.end - leading).coerceIn(0, trimmed.length)
                span.copy(start = start, end = end).takeIf { end > start }
            },
        )
    }
}

/**
 * Posune rozsahy za změnou textu z [old] na [new].
 *
 * Pole (`TextFieldValue`) nese jen text a formátování by při psaní ztratilo — `EditingBuffer`
 * z Compose vrací po každé úpravě `AnnotatedString(toString())`, tedy bez stylů. Rozsahy se proto
 * vedou zvlášť a změna se odvodí z rozdílu: společný začátek a konec, mezi nimi jeden nahrazený
 * úsek. Tak se chová psaní i mazání, vložení ze schránky i oprava slova klávesnicí.
 *
 * **Rozdíl nemusí být jednoznačný**: napsat „a" za „a" vypadá stejně jako napsat ho před ně.
 * Rozhodne [cursor] — kurzor po úpravě stojí za vloženým textem, takže nahrazený úsek končí
 * u něj. Bez kurzoru se bere nejdelší společný začátek.
 *
 * Text vložený přesně na konec rozsahu do něj patří (píše se dál tučně), na začátek ne. Text
 * napsaný přes celý rozsah si ho nechá — přepsané tučné slovo zůstane tučné. **Odkaz se psaním
 * na konci neprodlužuje**: slovo napsané za odkaz je další slovo věty, ne kus popisku.
 */
fun List<FormatSpan>.afterEdit(old: String, new: String, cursor: Int? = null): List<FormatSpan> {
    if (isEmpty() || old == new) return this
    val common = minOf(old.length, new.length)
    var prefix = 0
    while (prefix < common && old[prefix] == new[prefix]) prefix++
    if (cursor != null) prefix = minOf(prefix, (cursor - (new.length - old.length).coerceAtLeast(0)).coerceAtLeast(0))
    var suffix = 0
    while (suffix < common - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++

    val from = prefix
    val to = old.length - suffix
    val inserted = new.length - suffix - prefix
    val insertion = from == to
    val delta = inserted - (to - from)

    return mapNotNull { span ->
        val start = when {
            span.start < from -> span.start
            span.start == from -> if (insertion) span.start + inserted else from
            span.start < to -> from
            else -> span.start + delta
        }
        val end = when {
            span.end < from -> span.end
            span.end == from -> if (insertion && span.format != TextFormat.Link) span.end + inserted else span.end
            span.end <= to -> from + inserted
            else -> span.end + delta
        }
        span.copy(start = start, end = end).takeIf { end > start }
    }
}

/** Rozsahy oříznuté na text délky [length]; co z něj celé vypadlo, zmizí. */
fun List<FormatSpan>.within(length: Int): List<FormatSpan> = mapNotNull { span ->
    val start = span.start.coerceIn(0, length)
    val end = span.end.coerceIn(start, length)
    span.copy(start = start, end = end).takeIf { end > start }
}

/** Je celý úsek `[start, end)` formátovaný tou rodinou jako [format]? */
fun List<FormatSpan>.covers(format: TextFormat, start: Int, end: Int): Boolean {
    if (start >= end) return false
    var reached = start
    filter { it.format.family == format.family }
        .sortedBy { it.start }
        .forEach { span ->
            if (span.start > reached) return false
            if (span.end > reached) reached = span.end
            if (reached >= end) return true
        }
    return false
}

/**
 * Zapne, nebo vypne [format] na výběru `[start, end)` v [text]: je-li celý výběr už formátovaný,
 * formát se z něj sundá, jinak se na celý výběr natáhne. Tak to dělají textové editory.
 *
 * **Citace jde po celých řádcích**: ostatní klienti ji kreslí jako blok přes celé řádky, takže
 * citace půlky řádku by vypadala jinak, než ji uživatel viděl v poli. Výběr se proto roztáhne na
 * řádky, kterých se dotýká.
 *
 * **Kód přes víc řádků je blok kódu**, v jednom řádku kód v řádku — tak to rozlišují i oficiální
 * klienti. Vybrat to zvlášť by znamenalo dvě položky nabídky pro jednu věc.
 */
fun List<FormatSpan>.toggled(format: TextFormat, start: Int, end: Int, text: String): List<FormatSpan> {
    val (from, to) = if (format.family == TextFormat.Quote) text.lineBounds(start, end) else start to end
    if (from >= to) return this
    if (covers(format, from, to)) return removed(format.family, from, to)
    val added = when (format.family) {
        TextFormat.Code -> if ('\n' in text.substring(from, to)) TextFormat.CodeBlock else TextFormat.Code
        else -> format
    }
    return (removed(format.family, from, to) + FormatSpan(added, from, to)).merged()
}

/**
 * Nastaví na `[start, end)` odkaz na [url]; `null` odkaz z úseku sundá. Odkaz, který výběr
 * protíná, se rozdělí — kus mimo výběr si adresu nechá.
 */
fun List<FormatSpan>.linked(start: Int, end: Int, url: String?): List<FormatSpan> {
    if (start >= end) return this
    val without = removed(TextFormat.Link, start, end)
    return if (url == null) without else (without + FormatSpan(TextFormat.Link, start, end, url)).merged()
}

/** Adresa odkazu, který pokrývá celý úsek `[start, end)`, nebo `null`. */
fun List<FormatSpan>.linkAt(start: Int, end: Int): String? =
    firstOrNull { it.format == TextFormat.Link && it.start <= start && it.end >= end && start < end }?.url

/**
 * Adresa pro odkaz s popiskem z toho, co uživatel napsal, nebo `null`, když to adresa není.
 *
 * Bez schématu se doplní `https://` (napíše se `example.com`). Jinak se berou `http(s)`,
 * `tg://` (odkazy do Telegramu) a `mailto:`; `javascript:` a podobné ne — odkaz se po ťuknutí
 * otevírá u příjemce. Web musí mít doménu s tečkou, holé slovo adresa není.
 */
fun linkTargetOf(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val lower = trimmed.lowercase()
    if (lower.startsWith("mailto:")) return trimmed.takeIf { '@' in it }
    if (lower.startsWith("tg://")) return trimmed.takeIf { it.length > 5 }
    val url = if ("://" in trimmed) trimmed else "https://$trimmed"
    val scheme = url.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
    return url
}

/** Sundá z `[start, end)` všechno formátování („Bez formátování"). */
fun List<FormatSpan>.cleared(start: Int, end: Int): List<FormatSpan> {
    if (start >= end) return this
    return TextFormat.entries.map { it.family }.distinct().fold(this) { spans, family ->
        spans.removed(family, start, end)
    }
}

/** Vyřízne úsek `[start, end)` z rozsahů dané rodiny; co přečnívá, zůstane po obou stranách. */
private fun List<FormatSpan>.removed(family: TextFormat, start: Int, end: Int): List<FormatSpan> =
    flatMap { span ->
        if (span.format.family != family || span.end <= start || span.start >= end) {
            listOf(span)
        } else {
            listOfNotNull(
                span.copy(end = start).takeIf { it.end > it.start },
                span.copy(start = end).takeIf { it.end > it.start },
            )
        }
    }

/**
 * Spojí překrývající se a navazující rozsahy téhož druhu do jednoho. Odkazy jen se stejnou
 * adresou — dva sousední odkazy jinam jsou dva odkazy.
 */
private fun List<FormatSpan>.merged(): List<FormatSpan> =
    groupBy { it.format to it.url }.flatMap { (_, spans) ->
        spans.sortedBy { it.start }.fold(mutableListOf<FormatSpan>()) { acc, span ->
            val last = acc.lastOrNull()
            if (last != null && span.start <= last.end) {
                acc[acc.lastIndex] = last.copy(end = maxOf(last.end, span.end))
            } else {
                acc += span
            }
            acc
        }
    }.sortedWith(compareBy({ it.start }, { it.format.ordinal }))

/** Začátek řádku, na kterém leží [start], a konec řádku, na kterém končí výběr do [end]. */
private fun String.lineBounds(start: Int, end: Int): Pair<Int, Int> {
    val from = lastIndexOf('\n', start - 1) + 1
    val last = maxOf(start, end - 1).coerceAtMost(length)
    val to = indexOf('\n', last).let { if (it < 0) length else it }
    return from to maxOf(to, from)
}

/**
 * Ruční formátování zprávy tak, jak ho nese vstupní pole, nebo `null`, když ho pole nést NEUMÍ
 * a úprava by ho tiše smazala.
 *
 * Co se při odeslání dohledá znovu (odkaz psaný v textu, zmínka `@jméno`, hashtag, příkaz bota,
 * čas v médiu), pole nést nemusí. Odkaz s vlastním popiskem nese pole samo ([TextFormat.Link]).
 * Zmínka jménem, vlastní emoji, datum a blok kódu s jazykem ale z textu poznat nejdou.
 */
fun composedFormatsOf(text: String, entities: List<MessageEntity>): List<FormatSpan>? =
    formatsOf(text, entities, strict = true)

/**
 * Ruční formátování konceptu. Na rozdíl od [composedFormatsOf] se nevzdává: koncept z jiného
 * klienta s vlastním emoji vrátí do pole aspoň text a tučné, nic se tím nesmaže na serveru.
 */
fun draftFormatsOf(text: String, entities: List<MessageEntity>): List<FormatSpan> =
    formatsOf(text, entities, strict = false).orEmpty()

private fun formatsOf(text: String, entities: List<MessageEntity>, strict: Boolean): List<FormatSpan>? {
    val spans = mutableListOf<FormatSpan>()
    for (entity in entities) {
        val start = entity.offset.coerceIn(0, text.length)
        val end = (entity.offset + entity.length).coerceIn(start, text.length)
        val format = when (val type = entity.type) {
            MessageEntityType.Bold -> TextFormat.Bold
            MessageEntityType.Italic -> TextFormat.Italic
            MessageEntityType.Underline -> TextFormat.Underline
            MessageEntityType.Strikethrough -> TextFormat.Strikethrough
            MessageEntityType.Spoiler -> TextFormat.Spoiler
            is MessageEntityType.Code -> when {
                type.language.isNotEmpty() && strict -> return null
                type.block -> TextFormat.CodeBlock
                else -> TextFormat.Code
            }
            is MessageEntityType.Quote -> if (type.expandable) TextFormat.ExpandableQuote else TextFormat.Quote
            // Odkaz psaný přímo v textu (`example.com` → `https://example.com`) se dohledá znovu;
            // odkaz s popiskem („klikni sem") nese pole samo.
            is MessageEntityType.Link ->
                if (type.url.endsWith(text.substring(start, end), ignoreCase = true)) {
                    null
                } else {
                    if (end > start) spans += FormatSpan(TextFormat.Link, start, end, type.url)
                    null
                }
            is MessageEntityType.Mention,
            is MessageEntityType.Hashtag,
            is MessageEntityType.BotCommand,
            is MessageEntityType.MediaTimestamp,
            -> null
            is MessageEntityType.MentionUser,
            is MessageEntityType.CustomEmoji,
            is MessageEntityType.DateTime,
            -> if (strict) return null else null
        }
        if (format != null && end > start) spans += FormatSpan(format, start, end)
    }
    return spans.merged()
}
