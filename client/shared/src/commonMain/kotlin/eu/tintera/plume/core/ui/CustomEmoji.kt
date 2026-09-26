package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import eu.tintera.plume.core.telegram.CustomEmoji
import eu.tintera.plume.core.telegram.CustomEmojiRepository
import eu.tintera.plume.core.telegram.MessageEntity
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

/**
 * Vlastní emoji samo o sobě, ne v textu: reakce, ikona tlačítka bota. Obrázek ve čtverci [size],
 * kde by jinak stál glyf obyčejného emoji.
 *
 * Náhradní znak tady není (`ReactionTypeCustomEmoji` i `iconCustomEmojiId` nesou jen id), takže
 * než obrázek dorazí, je místo prázdné — pořád lepší než reakce, která u zprávy chybí.
 */
@Composable
fun CustomEmojiIcon(customEmojiId: Long, size: Dp, modifier: Modifier = Modifier) {
    CustomEmojiImage(id = customEmojiId, fallback = "", modifier = modifier.size(size))
}

/**
 * Emoji status za jménem (viz `statusEmojiId`).
 *
 * Jednobarevný status se barví akcentem, ne barvou textu — tak ho kreslí i oficiální klienti
 * a vedle jména pak čte jako odznak, ne jako další písmeno. Na tónované ploše (vybraný chat)
 * volající podá akcent ověřený proti ní (`accentOn`), jako u ikony ověření.
 */
@Composable
fun EmojiStatusBadge(
    customEmojiId: Long,
    modifier: Modifier = Modifier,
    size: Dp = EMOJI_STATUS_SIZE,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    CompositionLocalProvider(LocalContentColor provides accent) {
        CustomEmojiImage(id = customEmojiId, fallback = "", modifier = modifier.size(size))
    }
}

/** Status je o kus větší než ikona ověření (16 dp): kresba vlastního emoji má vlastní okraj. */
private val EMOJI_STATUS_SIZE = 20.dp

/**
 * Připojí [text] a nad jeho vlastní emoji (z [entities]) dá inline obsah — pro místa, kde se
 * kreslí obyčejný `Text`, ne [RichText]: náhled v seznamu chatů, citace. Vrací id použitých
 * emoji; `Text` k nim potřebuje [customEmojiInlineContent].
 *
 * Offsety [entities] platí pro [text], ne pro celý řetězec — posunou se o to, co už v něm je.
 * Emoji v zakrytém spoileru zůstane náhradním znakem, jako v [RichText].
 */
fun AnnotatedString.Builder.appendWithCustomEmoji(text: String, entities: List<MessageEntity>): Set<Long> {
    val start = length
    append(text)
    return customEmojiRanges(text, entities, spoilersRevealed = false)
        .onEach { (range, id) ->
            addStringAnnotation(INLINE_CONTENT_TAG, customEmojiKey(id), start + range.first, start + range.last + 1)
        }
        .mapTo(mutableSetOf()) { it.second }
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
fun customEmojiInlineContent(ids: Collection<Long>): Map<String, InlineTextContent> =
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
