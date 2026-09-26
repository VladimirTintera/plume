package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import eu.tintera.plume.core.telegram.CustomEmoji
import eu.tintera.plume.core.telegram.CustomEmojiRepository
import org.koin.compose.koinInject

/**
 * Vlastní emoji na místě náhradního znaku.
 *
 * Dokud obrázek nedorazí (a když ho server nevydá), kreslí se [fallback] — obyčejné emoji,
 * za které vlastní stojí. Místo v řádku drží placeholder textu, takže se výměnou nic nepřeskládá.
 */
@Composable
fun CustomEmojiImage(id: Long, fallback: String, modifier: Modifier = Modifier) {
    val repository = koinInject<CustomEmojiRepository>()
    val emoji by produceState<CustomEmoji?>(initialValue = null, repository, id) {
        value = repository.customEmoji(id)
    }
    val tint = LocalContentColor.current

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val placeholder: @Composable () -> Unit = {
            // Náhradní emoji vyplní čtverec placeholderu; písmo je o kus menší, glyf emoji
            // má nad sebou i pod sebou rezervu řádku.
            val size = with(LocalDensity.current) { (maxHeight * FALLBACK_SCALE).toSp() }
            BasicText(text = fallback, style = TextStyle(fontSize = size, color = tint))
        }

        val shown = emoji
        val image = Modifier.fillMaxSize().then(
            // Jednobarevné emoji se barví barvou textu — na odchozí bublině by jinak zmizelo.
            if (shown?.tinted == true) Modifier.tinted(tint) else Modifier,
        )
        when {
            shown?.animationFileId != null -> AnimatedSticker(
                fileId = shown.animationFileId,
                contentDescription = shown.emoji,
                modifier = image,
                placeholder = { shown.stillFileId?.let { StillEmoji(it, shown.emoji) } ?: placeholder() },
            )
            shown?.stillFileId != null -> Box(image) { StillEmoji(shown.stillFileId, shown.emoji) }
            else -> placeholder()
        }
    }
}

@Composable
private fun StillEmoji(fileId: Int, description: String) {
    RemoteImage(
        fileId = fileId,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        background = Color.Transparent,
        modifier = Modifier.fillMaxSize(),
    )
}

/** Obarví obsah jednou barvou a zachová jeho průhlednost (`SrcIn` nad vlastní vrstvou). */
private fun Modifier.tinted(color: Color): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(color = color, blendMode = BlendMode.SrcIn)
        }

/**
 * Inline obsah pro [RichText]: jedna položka na vlastní emoji v textu. Placeholder je čtverec
 * o velikosti [CUSTOM_EMOJI_SIZE] písma, svisle na střed řádku.
 */
internal fun customEmojiInlineContent(ids: Collection<Long>): Map<String, InlineTextContent> =
    ids.associate { id ->
        customEmojiKey(id) to InlineTextContent(
            Placeholder(
                width = CUSTOM_EMOJI_SIZE.em,
                height = CUSTOM_EMOJI_SIZE.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) { alternate -> CustomEmojiImage(id = id, fallback = alternate) }
    }

internal fun customEmojiKey(id: Long): String = "custom-emoji:$id"

/**
 * Značka, podle které `Text` pozná inline obsah. Veřejná konstanta v Compose není (je `internal`),
 * `appendInlineContent` ji ale přidává přesně takhle — a jinak než přes ni se inline obsah na
 * úsek UŽ PŘIPOJENÉHO textu dát nedá: offsety entit se nesmí posunout.
 */
internal const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

/** Vlastní emoji je o kus větší než písmo — tak velký je glyf obyčejného emoji i s rezervou. */
private const val CUSTOM_EMOJI_SIZE = 1.2f

private const val FALLBACK_SCALE = 0.8f
