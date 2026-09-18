package eu.tintera.plume.features.media

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Stránka prohlížeče: **karta se snímkem a pod ní popisek**, obojí svisle na středu.
 *
 * Vlastní `Layout`, protože karta se musí vejít do místa, které popisek nechá — nejdřív se změří
 * popisek, pak se snímek vepíše do zbytku ([fitInto]). S kartou přes celou výšku by dlouhý
 * popisek zajel přes fotku, a s popiskem jako samostatnou lištou by fotka poskočila při
 * listování mezi stránkou s popiskem a bez něj.
 *
 * [card] dostane přesnou velikost vepsaného snímku; tvar a obsah si kreslí sám.
 */
@Composable
internal fun MediaPageLayout(
    aspectRatio: Float,
    caption: String,
    modifier: Modifier = Modifier,
    card: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(
            card,
            {
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = CAPTION_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            },
        ),
        modifier = modifier,
    ) { (cardMeasurables, captionMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val caption = captionMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        val gap = if (caption != null) CAPTION_GAP.roundToPx() else 0
        val captionHeight = caption?.height ?: 0

        val size = fitInto(width, (height - captionHeight - gap).coerceAtLeast(0), aspectRatio)
        val card = cardMeasurables.first().measure(Constraints.fixed(size.width, size.height))

        val top = ((height - size.height - gap - captionHeight) / 2).coerceAtLeast(0)
        layout(width, height) {
            card.place((width - size.width) / 2, top)
            // Popisek začíná u levé hrany karty, ať u úzké fotky nevisí u okraje obrazovky.
            caption?.place(((width - size.width) / 2).coerceAtMost(width - caption.width), top + size.height + gap)
        }
    }
}

/**
 * Největší obdélník s poměrem stran [aspectRatio], který se vejde do [maxWidth] × [maxHeight].
 * Neznámý poměr (nula, záporný, NaN) se bere jako čtverec.
 */
internal fun fitInto(maxWidth: Int, maxHeight: Int, aspectRatio: Float): IntSize {
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    if (maxWidth <= 0 || maxHeight <= 0) return IntSize.Zero
    return if (maxWidth.toFloat() / maxHeight > ratio) {
        IntSize((maxHeight * ratio).roundToInt(), maxHeight)
    } else {
        IntSize(maxWidth, (maxWidth / ratio).roundToInt())
    }
}

/** Poměr stran z metadat; neznámé rozměry jako čtverec. */
internal fun aspectRatioOf(width: Int, height: Int): Float =
    if (width > 0 && height > 0) width.toFloat() / height else 1f

/** Víc řádků by fotku stlačilo; celý text je v konverzaci. */
private const val CAPTION_MAX_LINES = 4

private val CAPTION_GAP = 12.dp
