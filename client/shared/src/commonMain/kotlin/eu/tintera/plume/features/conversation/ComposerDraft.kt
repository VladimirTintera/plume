package eu.tintera.plume.features.conversation

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.PendingFormats
import eu.tintera.plume.core.telegram.clearedPending
import eu.tintera.plume.core.telegram.codeBlockAt
import eu.tintera.plume.core.telegram.quoteAt
import eu.tintera.plume.core.telegram.toggledExpandable
import eu.tintera.plume.core.telegram.withLanguage
import eu.tintera.plume.core.telegram.toggledPending
import eu.tintera.plume.core.telegram.typed
import eu.tintera.plume.core.telegram.typingAt
import eu.tintera.plume.core.telegram.TextFormat
import eu.tintera.plume.core.telegram.afterEdit
import eu.tintera.plume.core.telegram.cleared
import eu.tintera.plume.core.telegram.covers
import eu.tintera.plume.core.telegram.linkAt
import eu.tintera.plume.core.telegram.linked
import eu.tintera.plume.core.telegram.insertCustomEmoji
import eu.tintera.plume.core.telegram.toggled
import eu.tintera.plume.core.telegram.within
import kotlin.reflect.KProperty

/**
 * Rozepsaný text vstupního pole i s ručním formátováním.
 *
 * `TextFieldValue` formátování nenese: `EditingBuffer` z Compose vrací po každé úpravě
 * `AnnotatedString(toString())`, tedy bez stylů. Rozsahy se proto vedou tady a **každá změna
 * textu jde přes [update]**, který je posune ([afterEdit]). Obrazovka s polem pracuje jako
 * s `TextFieldValue` (`var draft by composer`), takže se o formátování nestará žádné z míst, která
 * do pole vkládají — emoji z výběru, zmínka, návrh odpovědi, vyprázdnění po odeslání.
 */
@Stable
internal class ComposerDraft(value: TextFieldValue = TextFieldValue(), formats: List<FormatSpan> = emptyList()) {

    var value by mutableStateOf(value)
        private set

    var formats by mutableStateOf(formats)
        private set

    /**
     * Formát přepnutý u kurzoru bez výběru ([PendingFormats]) — platí pro text napsaný právě
     * tam. Neukládá se: po otočení nebo návratu do chatu stojí kurzor jinde.
     */
    var pending by mutableStateOf<PendingFormats?>(null)
        private set

    /** Co pole odešle, uloží jako koncept, nebo pošle jako úpravu. */
    val composed: ComposedText get() = ComposedText(value.text, formats)

    fun update(new: TextFieldValue) {
        val old = value
        if (new.text != old.text) {
            var next = formats.afterEdit(old.text, new.text, cursor = new.selection.end.takeIf { new.selection.collapsed })
            insertedAtPending(old, new)?.let { (start, end) -> next = next.typed(start, end, pending!!, new.text) }
            formats = next
            // Jednou napsaný text formát nese a psaním dál pokračuje sám (`afterEdit`).
            pending = null
        } else if (new.selection != old.selection) {
            // Kurzor jinde: čekající formát patřil místu, kde se přepnul.
            pending = null
        }
        value = new
    }

    /**
     * Úsek `[start, end)`, který se napsal přesně na kurzor s čekajícím formátem, nebo `null`.
     * Jen čisté vložení: oprava slova klávesnicí nebo vložení jinam čekající formát nepoužije.
     */
    private fun insertedAtPending(old: TextFieldValue, new: TextFieldValue): Pair<Int, Int>? {
        val at = pending?.cursor ?: return null
        if (!old.selection.collapsed || old.selection.start != at || !new.selection.collapsed) return null
        val added = new.text.length - old.text.length
        if (added <= 0 || new.selection.end != at + added) return null
        if (!new.text.startsWith(old.text.take(at)) || !new.text.endsWith(old.text.drop(at))) return null
        return at to at + added
    }

    /**
     * Vloží vlastní emoji ([emoji] je obyčejné emoji, za které stojí) místo výběru, kurzor za něj.
     * Jako každá jiná změna textu posune ostatní rozsahy ([insertCustomEmoji]).
     */
    fun insertCustomEmoji(emoji: String, customEmojiId: Long) {
        val selection = value.selection
        val result = insertCustomEmoji(value.text, formats, selection.min, selection.max, emoji, customEmojiId)
        formats = result.formats
        value = TextFieldValue(result.text, TextRange(selection.min + emoji.length))
        pending = null
    }

    /** Text zvenku (koncept, upravovaná zpráva) i s jeho formátováním; kurzor na konec. */
    fun fill(text: String, formats: List<FormatSpan>) {
        value = TextFieldValue(text, selection = TextRange(text.length))
        this.formats = formats.within(text.length)
        pending = null
    }

    /**
     * Je [format] zapnutý? S výběrem: je v něm celý výběr. Bez výběru: bude v něm text napsaný
     * na kurzoru ([typingAt]). Podle toho je tlačítko v liště formátování zapnuté.
     */
    fun isApplied(format: TextFormat): Boolean {
        val selection = value.selection
        return if (selection.collapsed) {
            format.family in formats.typingAt(selection.start, pending)
        } else {
            formats.covers(format, selection.min, selection.max)
        }
    }

    /**
     * Přepne [format] na výběru, nebo bez výběru pro další psaní ([PendingFormats]).
     *
     * **Citace bez výběru jde na celý řádek s kurzorem**, stejně jako na výběru — je to blok přes
     * řádky, ne styl písma. Jen na prázdném řádku čeká na psaní jako ostatní formáty.
     */
    fun toggle(format: TextFormat) {
        val selection = value.selection
        if (!selection.collapsed) {
            formats = formats.toggled(format, selection.min, selection.max, value.text)
            return
        }
        val cursor = selection.start
        val onEmptyLine = (cursor == 0 || value.text[cursor - 1] == '\n') &&
            (cursor == value.text.length || value.text[cursor] == '\n')
        if (format.family == TextFormat.Quote && !onEmptyLine) {
            formats = formats.toggled(format, cursor, cursor, value.text)
            pending = null
        } else {
            pending = formats.toggledPending(format, cursor, pending)
        }
    }

    /**
     * Je co sundat? S výběrem: má výběr aspoň kus formátování. Bez výběru: psalo by se v nějakém
     * formátu. Podle toho se nabízí „Bez formátování".
     */
    val selectionFormatted: Boolean
        get() {
            val selection = value.selection
            return if (selection.collapsed) {
                formats.typingAt(selection.start, pending).isNotEmpty()
            } else {
                formats.any { it.start < selection.max && it.end > selection.min }
            }
        }

    /** Blok kódu u výběru nebo kurzoru — lišta mu nabídne jazyk. */
    val codeBlock: FormatSpan?
        get() = value.selection.let { formats.codeBlockAt(it.min, it.max) }

    /** Citace u výběru nebo kurzoru — lišta ji nabídne přepnout na sbalitelnou. */
    val quote: FormatSpan?
        get() = value.selection.let { formats.quoteAt(it.min, it.max) }

    /** Jazyk bloku kódu, který leží na `[start, end)`; `null` ho sundá. */
    fun setCodeLanguage(start: Int, end: Int, language: String?) {
        val block = formats.codeBlockAt(start, end) ?: return
        formats = formats.withLanguage(block, language)
    }

    fun toggleExpandableQuote() {
        val quote = quote ?: return
        formats = formats.toggledExpandable(quote)
    }

    /** Adresa odkazu na celém výběru, nebo `null` — podle toho dialog odkazu nabídne úpravu. */
    val selectionLink: String?
        get() = value.selection.let { formats.linkAt(it.min, it.max) }

    /** Odkaz na [url] přes úsek `[start, end)`; `null` ho sundá. */
    fun link(start: Int, end: Int, url: String?) {
        formats = formats.linked(start, end, url)
    }

    fun clearSelection() {
        if (value.selection.collapsed) {
            pending = formats.clearedPending(value.selection.start)
            return
        }
        formats = formats.cleared(value.selection.min, value.selection.max)
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): TextFieldValue = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: TextFieldValue) = update(newValue)

    companion object {
        private const val RECORD = "\u001E"
        private const val FIELD = "\u001F"

        val Saver: Saver<ComposerDraft, Any> = listSaver(
            save = { draft ->
                listOf(
                    draft.value.text,
                    draft.value.selection.start,
                    draft.value.selection.end,
                    // Oddělovače mimo tisknutelné znaky: adresa odkazu obsahuje „:" i „;".
                    draft.formats.joinToString(RECORD) {
                        listOf(
                            it.format.name,
                            it.start,
                            it.end,
                            it.url.orEmpty(),
                            it.language.orEmpty(),
                            it.customEmojiId?.toString().orEmpty(),
                        ).joinToString(FIELD)
                    },
                )
            },
            restore = { saved ->
                ComposerDraft(
                    value = TextFieldValue(saved[0] as String, TextRange(saved[1] as Int, saved[2] as Int)),
                    formats = (saved[3] as String).split(RECORD).filter { it.isNotEmpty() }.mapNotNull { entry ->
                        // Čtyři pole ukládala verze před jazykem bloku kódu, pět před vlastními emoji.
                        val fields = entry.split(FIELD).takeIf { it.size in 4..6 } ?: return@mapNotNull null
                        val format = TextFormat.entries.firstOrNull { it.name == fields[0] } ?: return@mapNotNull null
                        FormatSpan(
                            format = format,
                            start = fields[1].toIntOrNull() ?: return@mapNotNull null,
                            end = fields[2].toIntOrNull() ?: return@mapNotNull null,
                            url = fields[3].ifEmpty { null },
                            language = fields.getOrNull(4)?.ifEmpty { null },
                            customEmojiId = fields.getOrNull(5)?.toLongOrNull(),
                        )
                    },
                )
            },
        )
    }
}

@Composable
internal fun rememberComposerDraft(): ComposerDraft = rememberSaveable(saver = ComposerDraft.Saver) { ComposerDraft() }

/**
 * Nakreslí formátování v poli. Text se nemění, jen se obarví: styly přidá [OutputTransformation]
 * přes `addStyle`, takže kurzor a výběr jdou po textu tak, jak leží v konceptu.
 *
 * **Spoiler se v poli neskrývá** — pisatel musí vidět, co píše. Pozná se podle podkladu, stejně
 * jako odkrytý spoiler ve zprávě. Kód má podklad a strojové písmo, citace tónovaný podklad
 * (`drawQuoteBlocks` s pásem přes celou šířku je jen ve zprávě; v poli by se musel počítat
 * z layoutu, který si pole drží samo).
 *
 * `data class` proto, aby pole poznalo stejné formátování z minulé kompozice a nepřepočítávalo
 * výstup (rovnost hodnot, ne instancí).
 */
internal data class FormattingTransformation(
    private val formats: List<FormatSpan>,
    private val contentColor: Color,
    private val accentColor: Color,
    /**
     * Zástupné emoji pod vlastním emoji se zprůhlední — přes něj se kreslí obrázek
     * (`CustomEmojiOverlay`). Jen když pole neroluje: posun rolování pole nevydá a obrázek by
     * ujel; pak zůstane vidět obyčejné emoji.
     */
    private val hideCustomEmoji: Boolean = false,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        formats.within(length).forEach { span ->
            span.format.fieldStyle()?.let { addStyle(it, span.start, span.end) }
        }
    }

    private fun TextFormat.fieldStyle(): SpanStyle? = when (this) {
        TextFormat.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        TextFormat.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        TextFormat.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
        TextFormat.Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        TextFormat.Spoiler -> SpanStyle(background = contentColor.copy(alpha = SPOILER_FIELD_ALPHA))
        TextFormat.Code, TextFormat.CodeBlock ->
            SpanStyle(fontFamily = FontFamily.Monospace, background = contentColor.copy(alpha = CODE_FIELD_ALPHA))
        TextFormat.Quote, TextFormat.ExpandableQuote -> SpanStyle(background = accentColor.copy(alpha = QUOTE_FIELD_ALPHA))
        // Jako odkaz ve zprávě: akcent a podtržení (podtržení nese odlišení i tam, kde barva splývá).
        TextFormat.Link -> SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline)
        // Průhledná barva skryje i barevné emoji: písmo emoji kreslí přes barvu s její alfou.
        TextFormat.CustomEmoji -> if (hideCustomEmoji) SpanStyle(color = Color.Transparent) else null
    }
}

/** Podklad spoileru v poli — výraznější než kód, aby se ty dva nepletly i bez strojového písma. */
private const val SPOILER_FIELD_ALPHA = 0.22f

private const val CODE_FIELD_ALPHA = 0.1f

private const val QUOTE_FIELD_ALPHA = 0.16f
