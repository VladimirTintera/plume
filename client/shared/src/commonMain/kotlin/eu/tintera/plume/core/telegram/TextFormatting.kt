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

    /**
     * Vlastní emoji: text je obyčejné emoji, za které stojí, id nese [FormatSpan.customEmojiId].
     * Není to formát písma, ale OBSAH — je nedělitelné ([afterEdit]), psaním se neprodlužuje,
     * dvě stejná vedle sebe se nespojí a „Bez formátování" ho nesmaže ([cleared]).
     */
    CustomEmoji,
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
 * [url] je adresa odkazu ([TextFormat.Link]), [language] jazyk bloku kódu
 * ([TextFormat.CodeBlock], `pre` s jazykem), [customEmojiId] id vlastního emoji
 * ([TextFormat.CustomEmoji]); u ostatních formátů `null`.
 */
data class FormatSpan(
    val format: TextFormat,
    val start: Int,
    val end: Int,
    val url: String? = null,
    val language: String? = null,
    val customEmojiId: Long? = null,
)

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
 *
 * **Vlastní emoji je nedělitelné**: úprava, která zasáhne DOVNITŘ (smazání půlky emoji, vložení
 * mezi jeho znaky, přepsání), ho zruší — zbytek znaků už není emoji, za které id stojí. Psaní
 * před něj a za něj ho jen posune.
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
        if (span.format == TextFormat.CustomEmoji) return@mapNotNull span.atomicAfterEdit(from, to, inserted, delta)
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

/** Vlastní emoji po úpravě `[from, to)` → `inserted` znaků: posunuté, nebo `null`, když do něj zasáhla. */
private fun FormatSpan.atomicAfterEdit(from: Int, to: Int, inserted: Int, delta: Int): FormatSpan? = when {
    // Celá úprava před emoji (i vložení přesně na jeho začátek): posun.
    to <= start -> copy(start = start + delta, end = end + delta)
    // Celá úprava za emoji (i vložení přesně na jeho konec): beze změny.
    from >= end -> this
    else -> null
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

/**
 * Formát zapnutý nebo vypnutý u kurzoru bez výběru — „zapnout tučné a psát dál".
 *
 * Platí pro text napsaný PŘÍMO na [cursor] ([typed]). Dál už se o nic starat nemusí: text psaný
 * na konec rozsahu do něj patří ([afterEdit]), takže napsané tučné slovo pokračuje tučně samo
 * a text napsaný bez formátu za ním zůstane bez něj. Přesun kurzoru čekající změny zahodí —
 * patří místu, kde uživatel formát přepnul.
 *
 * [on] a [off] jsou RODINY ([TextFormat.family]), jako u přepínání na výběru.
 */
data class PendingFormats(
    val cursor: Int,
    val on: Set<TextFormat> = emptySet(),
    val off: Set<TextFormat> = emptySet(),
)

/**
 * Rodiny formátů, ve kterých by pokračoval text napsaný na [cursor] ([afterEdit]): rozsah začíná
 * před kurzorem a končí na něm nebo za ním. Odkaz se psaním neprodlužuje, takže mezi nimi není.
 */
fun List<FormatSpan>.continuingAt(cursor: Int): Set<TextFormat> =
    filter { it.format != TextFormat.Link && it.format != TextFormat.CustomEmoji && it.start < cursor && it.end >= cursor }
        .mapTo(mutableSetOf()) { it.format.family }

/** Rodiny, ve kterých se na [cursor] bude psát: co pokračuje, změněné čekajícími formáty. */
fun List<FormatSpan>.typingAt(cursor: Int, pending: PendingFormats?): Set<TextFormat> {
    val active = continuingAt(cursor).toMutableSet()
    if (pending != null && pending.cursor == cursor) {
        active -= pending.off
        active += pending.on
    }
    return active
}

/**
 * Přepne [format] pro psaní na [cursor]. Čekající změny z jiného místa se zahodí. Vypnutí
 * formátu, ve kterém by se psalo dál, se pamatuje jako [PendingFormats.off]; zapnutí nového
 * jako [PendingFormats.on]; druhé ťuknutí vrátí původní stav.
 */
fun List<FormatSpan>.toggledPending(format: TextFormat, cursor: Int, pending: PendingFormats?): PendingFormats {
    val family = format.family
    val base = pending?.takeIf { it.cursor == cursor } ?: PendingFormats(cursor)
    val continuing = family in continuingAt(cursor)
    val active = family in typingAt(cursor, base)
    return when {
        active && continuing -> base.copy(off = base.off + family)
        active -> base.copy(on = base.on - family)
        continuing -> base.copy(off = base.off - family)
        else -> base.copy(on = base.on + family)
    }
}

/** „Bez formátování" u kurzoru: psát dál bez čehokoli, co by jinak pokračovalo. */
fun List<FormatSpan>.clearedPending(cursor: Int): PendingFormats = PendingFormats(cursor, off = continuingAt(cursor))

/**
 * Použije čekající formáty na text napsaný na `[start, end)`. Kód přes víc řádků je blok kódu,
 * stejně jako u výběru ([toggled]).
 */
fun List<FormatSpan>.typed(start: Int, end: Int, pending: PendingFormats, text: String): List<FormatSpan> {
    if (start >= end) return this
    var result = this
    pending.off.forEach { family -> result = result.removed(family, start, end) }
    pending.on.forEach { family ->
        val format = if (family == TextFormat.Code && '\n' in text.substring(start, end)) TextFormat.CodeBlock else family
        result = (result.removed(family, start, end) + FormatSpan(format, start, end)).merged()
    }
    return result
}

/**
 * Blok kódu, ve kterém leží úsek `[start, end)` (kurzor, když `start == end`), nebo `null`.
 * Podle něj lišta nabídne jazyk bloku.
 */
fun List<FormatSpan>.codeBlockAt(start: Int, end: Int): FormatSpan? =
    firstOrNull { it.format == TextFormat.CodeBlock && it.start <= start && it.end >= end }

/** Nastaví bloku kódu [block] jazyk; `null` ho sundá. Ostatní rozsahy zůstanou. */
fun List<FormatSpan>.withLanguage(block: FormatSpan, language: String?): List<FormatSpan> =
    map { if (it == block) it.copy(language = language) else it }

/**
 * Jazyk bloku kódu z toho, co uživatel napsal, nebo `null` = bez jazyka. Jedno slovo bez mezer,
 * jak ho píšou ostatní klienti za ` ``` ` (`kotlin`, `c++`, `objective-c`); delší text by
 * zvýrazňování kódu u příjemce stejně nepoznalo.
 */
fun codeLanguageOf(text: String): String? =
    text.trim().takeIf { it.isNotEmpty() && it.length <= MAX_CODE_LANGUAGE && it.none(Char::isWhitespace) }

/** Delší jazyk nikdo nepíše; strop brání tomu, aby se do entity dostal celý odstavec. */
private const val MAX_CODE_LANGUAGE = 32

/**
 * Citace (i sbalitelná), ve které leží úsek `[start, end)` nebo kurzor, nebo `null`. Podle ní
 * lišta nabídne sbalitelnou citaci.
 */
fun List<FormatSpan>.quoteAt(start: Int, end: Int): FormatSpan? =
    firstOrNull { it.format.family == TextFormat.Quote && it.start <= start && it.end >= end }

/**
 * Přepne citaci [quote] mezi obyčejnou a sbalitelnou — ostatní klienti sbalitelnou ukazují
 * na pár řádků se „Zobrazit víc". Je to tatáž citace, mění se jen druh, rozsah zůstává.
 */
fun List<FormatSpan>.toggledExpandable(quote: FormatSpan): List<FormatSpan> {
    val format = if (quote.format == TextFormat.ExpandableQuote) TextFormat.Quote else TextFormat.ExpandableQuote
    return map { if (it == quote) it.copy(format = format) else it }
}

/** Sundá z `[start, end)` všechno formátování („Bez formátování"). Vlastní emoji je obsah, zůstane. */
fun List<FormatSpan>.cleared(start: Int, end: Int): List<FormatSpan> {
    if (start >= end) return this
    return TextFormat.entries.filter { it != TextFormat.CustomEmoji }.map { it.family }.distinct().fold(this) { spans, family ->
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
 * adresou — dva sousední odkazy jinam jsou dva odkazy. **Vlastní emoji se nespojují nikdy**:
 * dvě stejná za sebou jsou dvě emoji, ne jedno přes dva znaky.
 */
private fun List<FormatSpan>.merged(): List<FormatSpan> =
    groupBy { Triple(it.format, it.url, it.language) }.flatMap { (key, spans) ->
        if (key.first == TextFormat.CustomEmoji) return@flatMap spans
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
 * Vloží vlastní emoji [emoji] s id [customEmojiId] místo výběru `[start, end)` v [text]. Vrací
 * nový text a rozsahy; kurzor patří za vložené emoji (`start + emoji.length`).
 */
fun insertCustomEmoji(
    text: String,
    formats: List<FormatSpan>,
    start: Int,
    end: Int,
    emoji: String,
    customEmojiId: Long,
): ComposedText {
    val from = start.coerceIn(0, text.length)
    val to = end.coerceIn(from, text.length)
    val newText = text.substring(0, from) + emoji + text.substring(to)
    val shifted = formats.afterEdit(text, newText, cursor = from + emoji.length)
    return ComposedText(newText, (shifted + FormatSpan(TextFormat.CustomEmoji, from, from + emoji.length, customEmojiId = customEmojiId)).merged())
}

/**
 * Ruční formátování zprávy tak, jak ho nese vstupní pole, nebo `null`, když ho pole nést NEUMÍ
 * a úprava by ho tiše smazala.
 *
 * Co se při odeslání dohledá znovu (odkaz psaný v textu, zmínka `@jméno`, hashtag, příkaz bota,
 * čas v médiu), pole nést nemusí. Odkaz s vlastním popiskem nese pole samo ([TextFormat.Link]),
 * stejně jako jazyk bloku kódu ([FormatSpan.language]). Zmínka jménem, vlastní emoji a datum
 * ale z textu poznat nejdou.
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
                type.block && type.language.isNotEmpty() -> {
                    if (end > start) spans += FormatSpan(TextFormat.CodeBlock, start, end, language = type.language)
                    null
                }
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
            // Vlastní emoji nese pole samo, ale úprava zprávy ho dál nebere: bez Premium by ho
            // server při úpravě mohl tiše zahodit. Koncept ho vrátí do pole.
            is MessageEntityType.CustomEmoji -> {
                if (strict) return null
                if (end > start) spans += FormatSpan(TextFormat.CustomEmoji, start, end, customEmojiId = type.id)
                null
            }
            is MessageEntityType.MentionUser,
            is MessageEntityType.DateTime,
            -> if (strict) return null else null
        }
        if (format != null && end > start) spans += FormatSpan(format, start, end)
    }
    return spans.merged()
}
