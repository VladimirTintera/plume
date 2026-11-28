package eu.tintera.plume.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType

/**
 * Text zprávy s formátováním z entit (tučné, kurzíva, podtržení, přeškrtnutí, kód, citace,
 * odkazy, zmínky, tagy) jako na telefonu. Dřív byl na televizi všechno holý text — i spoiler
 * byl rovnou čitelný.
 *
 * - **Odkazy a zmínky jen vypadají jako odkaz** ([accent]); otevírat je televize neumí (ovladač
 *   by musel vybírat mezi odkazy v textu). Kde je [accent] stejný jako text (odchozí bublina:
 *   `primary` na `primaryContainer` nemá kontrast), odliší je podtržení.
 * - **Spoiler je opravdu nečitelný**, dokud ho uživatel neodkryje ([spoilersRevealed]): průhledné
 *   písmo na tónovaném podkladu, ne jiná barva — ta by obsah vyzradila.
 * - Vlastní emoji jsou značka vloženého obsahu nad náhradním emoji; obrázek dosadí `Text`
 *   s [rememberCustomEmojiContent] (bez něj zůstane náhrada). Datum a čas textem od pisatele.
 *
 * Rozsahy se smí vnořovat; každý jen přidá svůj styl, vnoření vznikne samo. Offsety jsou
 * v UTF-16 jako indexy `String`, mimo text se ořežou.
 */
@Composable
fun rememberRichText(
    text: String,
    entities: List<MessageEntity>,
    content: Color,
    accent: Color,
    spoilersRevealed: Boolean,
): AnnotatedString = remember(text, entities, content, accent, spoilersRevealed) {
    if (entities.isEmpty()) return@remember AnnotatedString(text)
    buildAnnotatedString {
        append(text)
        for (entity in entities) {
            val start = entity.offset.coerceIn(0, text.length)
            val end = (entity.offset + entity.length).coerceIn(start, text.length)
            if (start == end) continue
            // Vlastní emoji: značka vloženého obsahu nad náhradním znakem (CustomEmoji.kt);
            // obrázek dosadí `Text` s `rememberCustomEmojiContent`. Ve spoileru ne — prozradil by ho.
            val type = entity.type
            if (type is MessageEntityType.CustomEmoji && (spoilersRevealed || !inSpoiler(entities, start, end))) {
                addStringAnnotation(INLINE_CONTENT_TAG, customEmojiKey(type.id), start, end)
            }
            val style = entity.type.spanStyle(content, accent, spoilersRevealed) ?: continue
            addStyle(style, start, end)
        }
    }
}

private fun MessageEntityType.spanStyle(content: Color, accent: Color, spoilersRevealed: Boolean): SpanStyle? {
    val underlineLinks = accent == content
    return when (this) {
        MessageEntityType.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        MessageEntityType.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        MessageEntityType.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
        MessageEntityType.Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        is MessageEntityType.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = content.copy(alpha = 0.12f))
        is MessageEntityType.Quote -> SpanStyle(background = content.copy(alpha = 0.08f), fontStyle = FontStyle.Italic)
        is MessageEntityType.Link -> SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
        is MessageEntityType.Mention, is MessageEntityType.MentionUser, is MessageEntityType.Hashtag,
        is MessageEntityType.BotCommand, is MessageEntityType.MediaTimestamp,
        -> SpanStyle(color = accent, textDecoration = if (underlineLinks) TextDecoration.Underline else null)
        MessageEntityType.Spoiler -> if (spoilersRevealed) null else SpanStyle(color = Color.Transparent, background = content.copy(alpha = 0.35f))
        is MessageEntityType.CustomEmoji, is MessageEntityType.DateTime -> null
    }
}

/** Leží úsek [start]–[end] uvnitř spoileru? */
private fun inSpoiler(entities: List<MessageEntity>, start: Int, end: Int): Boolean =
    entities.any { it.type == MessageEntityType.Spoiler && it.offset <= start && it.offset + it.length >= end }

/** Je v textu spoiler, který by se musel odkrýt. */
fun List<MessageEntity>.hasSpoiler(): Boolean = any { it.type == MessageEntityType.Spoiler }

/**
 * Text s nahrazeným obsahem spoilerů ([SPOILER_MASK] za každý znak kromě mezer) — pro místa, kde
 * se text kreslí bez stylů (karta příspěvku ve feedu, náhled chatu). Bez toho by tam spoiler
 * byl čitelný.
 */
fun maskSpoilers(text: String, entities: List<MessageEntity>): String {
    if (!entities.hasSpoiler()) return text
    val chars = text.toCharArray()
    for (entity in entities) {
        if (entity.type != MessageEntityType.Spoiler) continue
        val start = entity.offset.coerceIn(0, chars.size)
        val end = (entity.offset + entity.length).coerceIn(start, chars.size)
        for (i in start until end) if (!chars[i].isWhitespace()) chars[i] = SPOILER_MASK
    }
    return String(chars)
}

private const val SPOILER_MASK = '▒'
