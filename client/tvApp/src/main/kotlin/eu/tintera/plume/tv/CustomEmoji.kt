package eu.tintera.plume.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.CustomEmoji
import eu.tintera.plume.core.telegram.CustomEmojiRepository
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.compactCountText
import org.koin.compose.koinInject

/**
 * Značka vloženého obsahu v [AnnotatedString] — tatáž, jakou píše `appendInlineContent`.
 * Přidává se nad PŮVODNÍ úsek textu (náhradní emoji), takže se offsety ostatních entit nehnou:
 * `Text` s `inlineContent` úsek s touhle značkou nahradí obrázkem, bez obrázku zůstane náhrada.
 */
internal const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

private const val CUSTOM_EMOJI_PREFIX = "custom-emoji:"

/** Klíč vloženého obsahu pro vlastní emoji [id] (v [rememberRichText] i v čipech reakcí). */
internal fun customEmojiKey(id: Long) = "$CUSTOM_EMOJI_PREFIX$id"

/**
 * Obrázky vlastních emoji pro `Text(inlineContent = …)`: jeden záznam na každé emoji v [text]
 * (podle značek z [customEmojiKey]). Emoji je čtverec velký jako písmo (1,2 em, jako telefon),
 * svisle na středu řádku.
 *
 * **Jen statický obrázek** (`CustomEmoji.stillFileId`: `.webp` nebo náhled animace): animované
 * `.tgs` a `.webm` by na televizi chtěly přehrávač na každé emoji a na slabém procesoru by
 * kanál plný emoji trhal rolování. Dokud se obrázek nestáhne, nebo když ho server nedá, je na
 * místě náhradní emoji z textu.
 */
@Composable
fun rememberCustomEmojiContent(text: AnnotatedString): Map<String, InlineTextContent> {
    val keys = remember(text) {
        text.getStringAnnotations(INLINE_CONTENT_TAG, 0, text.length)
            .filter { it.item.startsWith(CUSTOM_EMOJI_PREFIX) }
            .associate { it.item to text.substring(it.start, it.end) }
    }
    if (keys.isEmpty()) return emptyMap()
    return keys.mapValues { (key, fallback) ->
        val id = key.removePrefix(CUSTOM_EMOJI_PREFIX).toLong()
        InlineTextContent(Placeholder(EMOJI_SIZE, EMOJI_SIZE, PlaceholderVerticalAlign.TextCenter)) {
            CustomEmojiImage(id, fallback)
        }
    }
}

/** Obrázek vlastního emoji [id]; [fallback] (náhradní emoji), dokud není nebo když není. */
@Composable
private fun CustomEmojiImage(id: Long, fallback: String) {
    val repository = koinInject<CustomEmojiRepository>()
    val emoji by produceState<CustomEmoji?>(null, id) { value = repository.customEmoji(id) }
    // Velikost obrázku: písmo bývá do 40 sp, dlaždice 100 px stačí s rezervou.
    val image = rememberImage(emoji?.stillFileId, EMOJI_DECODE_PX)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                image,
                contentDescription = emoji?.emoji ?: fallback,
                contentScale = ContentScale.Fit,
                // Jednobarevné emoji (`needsRepainting`) má barvu textu, jinak by na odchozí bublině
                // bylo tmavé na tmavém.
                colorFilter = if (emoji?.tinted == true) ColorFilter.tint(LocalContentColor.current) else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(fallback)
        }
    }
}

/**
 * Text reakce do čipu: emoji a počet. Vlastní emoji jako vložený obrázek ([customEmojiKey]),
 * náhradní znak hvězdička — reakce nese jen id, ne emoji, za které stojí.
 */
fun reactionLabel(emoji: String, countText: String?): AnnotatedString = buildAnnotatedString {
    val custom = customEmojiIdOf(emoji)
    if (custom != null) {
        pushStringAnnotation(INLINE_CONTENT_TAG, customEmojiKey(custom))
        append(CUSTOM_REACTION_FALLBACK)
        pop()
    } else {
        append(emoji)
    }
    if (countText != null) {
        append(' ')
        append(countText)
    }
}

/** Samotné emoji reakce (bez počtu), vlastní jako obrázek — řádek v dialogu „Kdo reagoval". */
@Composable
fun ReactionEmoji(emoji: String, style: androidx.compose.ui.text.TextStyle) {
    val label = remember(emoji) { reactionLabel(emoji, countText = null) }
    Text(label, style = style, inlineContent = rememberCustomEmojiContent(label), maxLines = 1)
}

/** Reakce (emoji nebo vlastní emoji) s počtem v barvě [color] a stylu podle volajícího. */
@Composable
fun ReactionText(emoji: String, count: Int, color: Color, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val countText = compactCountText(count)
    val label = remember(emoji, countText) { reactionLabel(emoji, countText) }
    Text(label, style = style, color = color, inlineContent = rememberCustomEmojiContent(label), modifier = modifier, maxLines = 1)
}

private val EMOJI_SIZE: TextUnit = 1.2.em
private const val EMOJI_DECODE_PX = 100
private const val CUSTOM_REACTION_FALLBACK = "⭐"
