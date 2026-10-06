package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.PageBlock
import dev.g000sha256.tdl.dto.PageBlockAnimation
import dev.g000sha256.tdl.dto.PageBlockAudio
import dev.g000sha256.tdl.dto.PageBlockAuthorDate
import dev.g000sha256.tdl.dto.PageBlockBlockQuote
import dev.g000sha256.tdl.dto.PageBlockCaption
import dev.g000sha256.tdl.dto.PageBlockCollage
import dev.g000sha256.tdl.dto.PageBlockDetails
import dev.g000sha256.tdl.dto.PageBlockDivider
import dev.g000sha256.tdl.dto.PageBlockDocument
import dev.g000sha256.tdl.dto.PageBlockExpandableBlockQuote
import dev.g000sha256.tdl.dto.PageBlockFooter
import dev.g000sha256.tdl.dto.PageBlockHeader
import dev.g000sha256.tdl.dto.PageBlockKicker
import dev.g000sha256.tdl.dto.PageBlockList
import dev.g000sha256.tdl.dto.PageBlockMathematicalExpression
import dev.g000sha256.tdl.dto.PageBlockParagraph
import dev.g000sha256.tdl.dto.PageBlockPhoto
import dev.g000sha256.tdl.dto.PageBlockPreformatted
import dev.g000sha256.tdl.dto.PageBlockPullQuote
import dev.g000sha256.tdl.dto.PageBlockSectionHeading
import dev.g000sha256.tdl.dto.PageBlockSlideshow
import dev.g000sha256.tdl.dto.PageBlockSubheader
import dev.g000sha256.tdl.dto.PageBlockSubtitle
import dev.g000sha256.tdl.dto.PageBlockTable
import dev.g000sha256.tdl.dto.PageBlockThinking
import dev.g000sha256.tdl.dto.PageBlockTitle
import dev.g000sha256.tdl.dto.PageBlockVideo
import dev.g000sha256.tdl.dto.PageBlockVoiceNote
import dev.g000sha256.tdl.dto.RichText
import dev.g000sha256.tdl.dto.RichTextAnchorLink
import dev.g000sha256.tdl.dto.RichTextBankCardNumber
import dev.g000sha256.tdl.dto.RichTextBold
import dev.g000sha256.tdl.dto.RichTextBotCommand
import dev.g000sha256.tdl.dto.RichTextCashtag
import dev.g000sha256.tdl.dto.RichTextCustomEmoji
import dev.g000sha256.tdl.dto.RichTextDateTime
import dev.g000sha256.tdl.dto.RichTextDiff
import dev.g000sha256.tdl.dto.RichTextEmailAddress
import dev.g000sha256.tdl.dto.RichTextFixed
import dev.g000sha256.tdl.dto.RichTextHashtag
import dev.g000sha256.tdl.dto.RichTextItalic
import dev.g000sha256.tdl.dto.RichTextMarked
import dev.g000sha256.tdl.dto.RichTextMathematicalExpression
import dev.g000sha256.tdl.dto.RichTextMention
import dev.g000sha256.tdl.dto.RichTextMentionName
import dev.g000sha256.tdl.dto.RichTextPhoneNumber
import dev.g000sha256.tdl.dto.RichTextPlain
import dev.g000sha256.tdl.dto.RichTextReference
import dev.g000sha256.tdl.dto.RichTextReferenceLink
import dev.g000sha256.tdl.dto.RichTextSpoiler
import dev.g000sha256.tdl.dto.RichTextStrikethrough
import dev.g000sha256.tdl.dto.RichTextSubscript
import dev.g000sha256.tdl.dto.RichTextSuperscript
import dev.g000sha256.tdl.dto.RichTexts
import dev.g000sha256.tdl.dto.RichTextUnderline
import dev.g000sha256.tdl.dto.RichTextUrl

/** Slova za přílohy článku. U fotky, videa a GIFu je bublina nahradí médiem, u zbytku zůstávají. */
internal data class RichMediaLabels(
    val photo: String,
    val video: String,
    val gif: String,
    val audio: String,
    val voice: String,
    val file: String,
)

/**
 * Článek (`MessageRichMessage`, TDLib 15) jako text s formátováním.
 *
 * Článek je strom bloků (nadpisy, odstavce, seznamy, citace, tabulky, média). Vlastní
 * vykreslení bloků by byla druhá sazba vedle bubliny; tady se strom rozloží do JEDNOHO textu
 * s entitami, které bublina už umí: nadpis tučně, citace jako pás, kód, odkazy, zmínky, spoiler.
 * Tím funguje i hledání se zvýrazněním, kopírování, výběr textu a citace z nabídky.
 *
 * Offsety jsou v UTF-16 jednotkách jako všude — počítají se z délky `StringBuilder`u.
 * **Média stojí v textu jako slovo** („[Fotka] popisek") a k tomu se zapíše, kde přesně
 * ([ArticleMedia]): bublina místo slova nakreslí fotku, video nebo GIF, a v kopírovaném
 * textu, náhledu, hledání a přepisu pro AI zůstane slovo. Kotvy vyrábí TENTÝŽ průchod jako
 * text — offsety ze dvou průchodů by se rozešly při první změně jednoho z nich.
 *
 * @param isFull článek je celý; jinak TDLib poslal jen začátek ([Article.isPartial]).
 * @param hideSensitive médium se rozostří jako citlivé (viz `hidesSensitive`).
 */
internal fun Array<PageBlock>.toRichBody(
    labels: RichMediaLabels,
    isFull: Boolean = true,
    hideSensitive: Boolean = false,
): MessageBody {
    val writer = RichWriter(labels, hideSensitive)
    forEach { writer.block(it, indent = "") }
    return writer.build(isPartial = !isFull)
}

private class RichWriter(private val labels: RichMediaLabels, private val hideSensitive: Boolean) {
    private val text = StringBuilder()
    private val entities = mutableListOf<MessageEntity>()
    private val media = mutableListOf<ArticleMedia>()

    /** Koláž, uvnitř které se právě píše; `null` mimo ni. */
    private var group: Int? = null
    private var groups = 0

    fun build(isPartial: Boolean): MessageBody {
        // Konec řádků za posledním blokem by v bublině nechal prázdný řádek.
        val trimmed = text.trimEnd().length
        return MessageBody(
            text = text.substring(0, trimmed),
            entities = entities.mapNotNull { entity ->
                val end = minOf(entity.offset + entity.length, trimmed)
                entity.copy(length = end - entity.offset).takeIf { it.length > 0 }
            },
            article = Article(
                media = media.filter { it.end <= trimmed },
                isPartial = isPartial,
            ).takeIf { it.media.isNotEmpty() || it.isPartial },
        )
    }

    /** Blok za odrážkou seznamu začne na řádku odrážky, ne na novém. */
    private var continueLine = false

    /** Nový řádek, jen když poslední není prázdný — bloky se tak nerozjedou dvojitými mezerami. */
    private fun newLine() {
        if (continueLine) {
            continueLine = false
            return
        }
        if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
    }

    /** Obalí, co [content] napíše, entitou [type]; prázdný úsek entitu nedostane. */
    private inline fun styled(type: MessageEntityType, content: () -> Unit) {
        val start = text.length
        content()
        if (text.length > start) entities += MessageEntity(start, text.length - start, type)
    }

    /** Řádek bloku: odsazení (u vnořených položek seznamu), pak obsah. */
    private inline fun line(indent: String, content: () -> Unit) {
        newLine()
        val start = text.length
        text.append(indent)
        val before = text.length
        content()
        // Prázdný řádek se nepíše — i s odsazením by zůstal viset.
        if (text.length == before) text.setLength(start)
    }

    fun block(block: PageBlock, indent: String) {
        when (block) {
            is PageBlockTitle -> line(indent) { styled(MessageEntityType.Bold) { rich(block.title) } }
            is PageBlockSubtitle -> line(indent) { styled(MessageEntityType.Italic) { rich(block.subtitle) } }
            is PageBlockHeader -> heading(block.header, indent)
            is PageBlockSubheader -> heading(block.subheader, indent)
            is PageBlockSectionHeading -> heading(block.text, indent)
            is PageBlockKicker -> line(indent) { styled(MessageEntityType.Bold) { rich(block.kicker) } }
            is PageBlockParagraph -> line(indent) { rich(block.text) }
            is PageBlockPreformatted -> line(indent) { styled(MessageEntityType.Code(block = true)) { rich(block.text) } }
            is PageBlockFooter -> line(indent) { styled(MessageEntityType.Italic) { rich(block.footer) } }
            is PageBlockThinking -> line(indent) { styled(MessageEntityType.Italic) { rich(block.text) } }
            is PageBlockAuthorDate -> line(indent) { styled(MessageEntityType.Italic) { rich(block.author) } }
            is PageBlockMathematicalExpression -> line(indent) { styled(MessageEntityType.Code()) { text.append(block.expression) } }
            is PageBlockDivider -> line(indent) { text.append(DIVIDER) }
            is PageBlockList -> block.items.forEach { item ->
                val bullet = when {
                    item.hasCheckbox -> if (item.isChecked) CHECKED else UNCHECKED
                    else -> item.label.ifBlank { BULLET }
                }
                // První blok položky stojí za odrážkou, další pod ní s odsazením.
                item.blocks.forEachIndexed { i, inner ->
                    if (i == 0) {
                        line(indent) {
                            text.append(bullet).append(' ')
                            inline(inner)
                        }
                    } else {
                        block(inner, "$indent$NESTED")
                    }
                }
                if (item.blocks.isEmpty()) line(indent) { text.append(bullet) }
            }
            is PageBlockBlockQuote -> {
                newLine()
                styled(MessageEntityType.Quote(expandable = false)) {
                    block.blocks.forEach { block(it, indent) }
                    credit(block.credit)
                }
            }
            is PageBlockPullQuote -> {
                newLine()
                styled(MessageEntityType.Quote(expandable = false)) {
                    rich(block.text)
                    credit(block.credit)
                }
            }
            is PageBlockExpandableBlockQuote -> {
                newLine()
                styled(MessageEntityType.Quote(expandable = true)) {
                    rich(block.text)
                    credit(block.credit)
                }
            }
            // Obsah sbalitelného bloku se ukáže celý — sbalit ho uprostřed textu bublina neumí.
            is PageBlockDetails -> {
                heading(block.header, indent)
                block.blocks.forEach { block(it, indent) }
            }
            is PageBlockTable -> {
                block.cells.forEach { row ->
                    line(indent) {
                        row.forEachIndexed { i, cell ->
                            if (i > 0) text.append(CELL_SEPARATOR)
                            if (cell.isHeader) {
                                styled(MessageEntityType.Bold) { rich(cell.text) }
                            } else {
                                rich(cell.text)
                            }
                        }
                    }
                }
                line(indent) { styled(MessageEntityType.Italic) { rich(block.caption) } }
            }
            is PageBlockPhoto -> media(
                labels.photo,
                block.caption,
                indent,
                block.photo?.toMedia(hasSpoiler = block.hasSpoiler || hideSensitive, isSensitive = hideSensitive),
            )
            is PageBlockVideo -> media(
                labels.video,
                block.caption,
                indent,
                block.video?.toMedia(hasSpoiler = block.hasSpoiler || hideSensitive, isSensitive = hideSensitive),
            )
            is PageBlockAnimation -> media(
                labels.gif,
                block.caption,
                indent,
                block.animation?.toMedia()?.copy(hasSpoiler = block.hasSpoiler || hideSensitive, isSensitive = hideSensitive),
            )
            // Zvuk a soubor se v článku nekreslí, ale nesmí z něj zmizet: slovo a popisek.
            is PageBlockAudio -> media(labels.audio, block.caption, indent, shown = null)
            is PageBlockVoiceNote -> media(labels.voice, block.caption, indent, shown = null)
            is PageBlockDocument -> media(
                labels.file,
                block.caption ?: PageBlockCaption(RichTextPlain(block.document.fileName), null),
                indent,
                shown = null,
            )
            // Prezentace (listování) se v bublině kreslí stejně jako koláž: mřížka.
            is PageBlockCollage -> collage(block.blocks, block.caption, indent)
            is PageBlockSlideshow -> collage(block.blocks, block.caption, indent)
            // Kotvy, tlačítka, mapy, vložené stránky a zvuk: v bublině nemají co ukázat.
            else -> Unit
        }
    }

    /** Blok za odrážkou: text bez vlastního nového řádku, jinak jako samostatný blok. */
    private fun inline(block: PageBlock) {
        when (block) {
            is PageBlockParagraph -> rich(block.text)
            else -> {
                continueLine = true
                block(block, indent = "")
                continueLine = false
            }
        }
    }

    private fun heading(richText: RichText?, indent: String) {
        if (richText == null) return
        // Nadpis odděluje oddíly, takže nad ním zůstane volný řádek (kromě úplného začátku
        // a nadpisu za odrážkou seznamu).
        if (text.isNotEmpty() && !continueLine) {
            newLine()
            text.append('\n')
        }
        line(indent) { styled(MessageEntityType.Bold) { rich(richText) } }
    }

    private fun credit(credit: RichText?) {
        if (credit == null) return
        val start = text.length
        text.append('\n').append(CREDIT_DASH)
        val before = text.length
        styled(MessageEntityType.Italic) { rich(credit) }
        if (text.length == before) text.setLength(start)
    }

    /**
     * Médium jako „[Fotka] popisek". U média, které bublina umí nakreslit ([shown]), se zapíše
     * kotva přes slovo i mezeru za ním — popisek zůstane textem pod médiem.
     */
    private fun media(label: String, caption: PageBlockCaption?, indent: String, shown: MessageMedia?) {
        line(indent) {
            val start = text.length
            styled(MessageEntityType.Italic) { text.append('[').append(label).append(']') }
            val before = text.length
            text.append(' ')
            val captionStart = text.length
            // Popisek média kurzívou: pod fotkou by jinak splynul s textem článku.
            styled(MessageEntityType.Italic) { rich(caption?.text) }
            if (text.length == before + 1) text.setLength(before)
            if (shown != null) {
                media += ArticleMedia(
                    start = start,
                    end = minOf(before + 1, text.length),
                    index = media.size,
                    media = shown,
                    caption = text.substring(minOf(captionStart, text.length)),
                    group = group,
                )
            }
        }
        credit(caption?.credit)
    }

    private fun collage(blocks: Array<PageBlock>, caption: PageBlockCaption?, indent: String) {
        val outer = group
        group = groups++
        blocks.forEach { block(it, indent) }
        group = outer
        caption(caption, indent)
    }

    private fun caption(caption: PageBlockCaption?, indent: String) {
        line(indent) { styled(MessageEntityType.Italic) { rich(caption?.text) } }
    }

    /** Formátovaný text. Co vykreslit neumíme (ikona, tlačítko), vypadne; text zůstane. */
    fun rich(richText: RichText?) {
        when (richText) {
            null -> Unit
            is RichTextPlain -> text.append(richText.text)
            is RichTexts -> richText.texts.forEach { rich(it) }
            is RichTextBold -> styled(MessageEntityType.Bold) { rich(richText.text) }
            is RichTextItalic -> styled(MessageEntityType.Italic) { rich(richText.text) }
            is RichTextUnderline -> styled(MessageEntityType.Underline) { rich(richText.text) }
            is RichTextStrikethrough -> styled(MessageEntityType.Strikethrough) { rich(richText.text) }
            is RichTextFixed -> styled(MessageEntityType.Code()) { rich(richText.text) }
            is RichTextSpoiler -> styled(MessageEntityType.Spoiler) { rich(richText.text) }
            is RichTextUrl -> styled(MessageEntityType.Link(richText.url.withSchemeIfMissing())) { rich(richText.text) }
            is RichTextEmailAddress -> styled(MessageEntityType.Link("mailto:${richText.emailAddress}")) { rich(richText.text) }
            is RichTextPhoneNumber -> styled(MessageEntityType.Link("tel:${richText.phoneNumber}")) { rich(richText.text) }
            is RichTextMention -> styled(MessageEntityType.Mention(richText.username.removePrefix("@"))) { rich(richText.text) }
            is RichTextMentionName -> styled(MessageEntityType.MentionUser(richText.userId)) { rich(richText.text) }
            is RichTextHashtag -> {
                val start = text.length
                rich(richText.text)
                val tag = text.substring(start)
                if (tag.isNotEmpty()) entities += MessageEntity(start, tag.length, MessageEntityType.Hashtag(tag))
            }
            is RichTextBotCommand -> {
                val start = text.length
                rich(richText.text)
                val command = text.substring(start)
                if (command.isNotEmpty()) entities += MessageEntity(start, command.length, MessageEntityType.BotCommand(command))
            }
            is RichTextCustomEmoji ->
                styled(MessageEntityType.CustomEmoji(richText.customEmojiId)) { text.append(richText.alternativeText) }
            is RichTextDateTime -> {
                val format = richText.formattingType?.toFormat()
                if (format == null) {
                    rich(richText.text)
                } else {
                    styled(MessageEntityType.DateTime(richText.unixTime.toLong(), format)) { rich(richText.text) }
                }
            }
            is RichTextMathematicalExpression -> styled(MessageEntityType.Code()) { text.append(richText.expression) }
            is RichTextMarked -> rich(richText.text)
            is RichTextSubscript -> rich(richText.text)
            is RichTextSuperscript -> rich(richText.text)
            is RichTextCashtag -> rich(richText.text)
            is RichTextBankCardNumber -> rich(richText.text)
            is RichTextAnchorLink -> rich(richText.text)
            is RichTextReference -> rich(richText.text)
            is RichTextReferenceLink -> rich(richText.text)
            is RichTextDiff -> rich(richText.text)
            else -> Unit
        }
    }
}

private fun String.withSchemeIfMissing(): String = if (contains(":")) this else "https://$this"

private const val BULLET = "•"
private const val CHECKED = "☑"
private const val UNCHECKED = "☐"
private const val NESTED = "    "
private const val DIVIDER = "———"
private const val CELL_SEPARATOR = " │ "
private const val CREDIT_DASH = "— "
