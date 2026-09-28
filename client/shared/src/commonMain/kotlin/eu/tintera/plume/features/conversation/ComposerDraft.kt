package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.TextFormat
import eu.tintera.plume.core.telegram.afterEdit
import eu.tintera.plume.core.telegram.cleared
import eu.tintera.plume.core.telegram.covers
import eu.tintera.plume.core.telegram.linkAt
import eu.tintera.plume.core.telegram.linked
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

    /** Co pole odešle, uloží jako koncept, nebo pošle jako úpravu. */
    val composed: ComposedText get() = ComposedText(value.text, formats)

    fun update(new: TextFieldValue) {
        if (new.text != value.text) {
            formats = formats.afterEdit(value.text, new.text, cursor = new.selection.end.takeIf { new.selection.collapsed })
        }
        value = new
    }

    /** Text zvenku (koncept, upravovaná zpráva) i s jeho formátováním; kurzor na konec. */
    fun fill(text: String, formats: List<FormatSpan>) {
        value = TextFieldValue(text, selection = TextRange(text.length))
        this.formats = formats.within(text.length)
    }

    /** Je výběr celý v [format]? Podle toho je tlačítko v liště formátování zapnuté. */
    fun isApplied(format: TextFormat): Boolean =
        !value.selection.collapsed && formats.covers(format, value.selection.min, value.selection.max)

    fun toggle(format: TextFormat) {
        if (value.selection.collapsed) return
        formats = formats.toggled(format, value.selection.min, value.selection.max, value.text)
    }

    /** Má výběr aspoň kus nějakého formátování? Podle toho se nabízí „Bez formátování". */
    val selectionFormatted: Boolean
        get() {
            val selection = value.selection
            return !selection.collapsed && formats.any { it.start < selection.max && it.end > selection.min }
        }

    /** Adresa odkazu na celém výběru, nebo `null` — podle toho dialog odkazu nabídne úpravu. */
    val selectionLink: String?
        get() = value.selection.let { formats.linkAt(it.min, it.max) }

    /** Odkaz na [url] přes úsek `[start, end)`; `null` ho sundá. */
    fun link(start: Int, end: Int, url: String?) {
        formats = formats.linked(start, end, url)
    }

    fun clearSelection() {
        if (value.selection.collapsed) return
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
                        listOf(it.format.name, it.start, it.end, it.url.orEmpty()).joinToString(FIELD)
                    },
                )
            },
            restore = { saved ->
                ComposerDraft(
                    value = TextFieldValue(saved[0] as String, TextRange(saved[1] as Int, saved[2] as Int)),
                    formats = (saved[3] as String).split(RECORD).filter { it.isNotEmpty() }.mapNotNull { entry ->
                        val fields = entry.split(FIELD).takeIf { it.size == 4 } ?: return@mapNotNull null
                        val format = TextFormat.entries.firstOrNull { it.name == fields[0] } ?: return@mapNotNull null
                        FormatSpan(
                            format = format,
                            start = fields[1].toIntOrNull() ?: return@mapNotNull null,
                            end = fields[2].toIntOrNull() ?: return@mapNotNull null,
                            url = fields[3].ifEmpty { null },
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
 * Nakreslí formátování v poli. Text se nemění ([OffsetMapping.Identity]), jen se obarví.
 *
 * **Spoiler se v poli neskrývá** — pisatel musí vidět, co píše. Pozná se podle podkladu, stejně
 * jako odkrytý spoiler ve zprávě. Kód má podklad a strojové písmo, citace tónovaný podklad
 * (`drawQuoteBlocks` s pásem přes celou šířku je jen ve zprávě; v poli by se musel počítat
 * z layoutu, který si pole drží samo).
 */
internal class FormattingTransformation(
    private val formats: List<FormatSpan>,
    private val contentColor: Color,
    private val accentColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (formats.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = AnnotatedString.Builder(text).apply {
            formats.within(text.length).forEach { span -> addStyle(span.format.fieldStyle(), span.start, span.end) }
        }.toAnnotatedString()
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private fun TextFormat.fieldStyle(): SpanStyle = when (this) {
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
    }

    // `VisualTransformation` pole porovnává, aby nepřepočítávalo text při každé kompozici.
    override fun equals(other: Any?): Boolean =
        other is FormattingTransformation && other.formats == formats &&
            other.contentColor == contentColor && other.accentColor == accentColor

    override fun hashCode(): Int = (formats.hashCode() * 31 + contentColor.hashCode()) * 31 + accentColor.hashCode()
}

/** Podklad spoileru v poli — výraznější než kód, aby se ty dva nepletly i bez strojového písma. */
private const val SPOILER_FIELD_ALPHA = 0.22f

private const val CODE_FIELD_ALPHA = 0.1f

private const val QUOTE_FIELD_ALPHA = 0.16f
