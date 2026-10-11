package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.TextFormat
import eu.tintera.plume.core.ui.CustomEmojiImage
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Obrázky vlastních emoji ve vstupním poli, nakreslené PŘES text podle jeho rozložení.
 *
 * `BasicTextField` vložený obsah (`InlineTextContent`) neumí, takže zástupné emoji zůstává
 * v textu (zprůhledněné, `FormattingTransformation.hideCustomEmoji`) a obrázek se klade na jeho
 * místo. Pozice z [layout] patří textu bez rolování — proto se vrstva kreslí, jen když se text do
 * pole vejde celý ([customEmojiOverlayFits]); u rolujícího pole by obrázky ujely a zůstává vidět
 * obyčejné emoji.
 *
 * Rozložení textu přichází o snímek později než změna textu; rozsahy, které do něj ještě
 * nepatří, se přeskočí.
 */
@Composable
internal fun CustomEmojiOverlay(layout: TextLayoutResult?, formats: List<FormatSpan>) {
    layout ?: return
    val length = layout.layoutInput.text.length
    val density = LocalDensity.current
    formats.forEach { span ->
        val id = span.customEmojiId
        if (span.format != TextFormat.CustomEmoji || id == null || span.end > length || span.start >= span.end) return@forEach
        val line = layout.getLineForOffset(span.start)
        val box = layout.getBoundingBox(span.start)
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)
        // Čtverec o výšce řádku, ale ne širší než místo zástupného emoji (to je v písmu o něco
        // užší než řádek vysoký).
        val side = min(lineBottom - lineTop, box.width.coerceAtLeast(1f) * EMOJI_WIDEN)
        val x = box.left + (box.width - side) / 2
        val y = lineTop + (lineBottom - lineTop - side) / 2
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(with(density) { side.toDp() }),
        ) {
            CustomEmojiImage(id = id, fallback = "", modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Vejde se text do pole celý? Jen tehdy sedí pozice z rozložení na obrazovku — rolující pole
 * posun nevydá. [viewportHeight] je výška viditelné části textu (`innerTextField`).
 */
internal fun customEmojiOverlayFits(layout: TextLayoutResult?, viewportHeight: Int): Boolean =
    layout != null && viewportHeight > 0 && layout.size.height <= viewportHeight + 1

/** Obrázek smí být o kus širší než znak zástupného emoji — emoji v písmu má kolem sebe vzduch. */
private const val EMOJI_WIDEN = 1.15f
