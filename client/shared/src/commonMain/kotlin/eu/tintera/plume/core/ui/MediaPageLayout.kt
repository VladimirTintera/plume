package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Stránka prohlížeče: **karta se snímkem a pod ní popisek**, obojí svisle na středu.
 *
 * Vlastní `Layout`, protože karta se musí vejít do místa, které popisek nechá — nejdřív se změří
 * popisek, pak se snímek vepíše do zbytku ([pageCardFrame]). S kartou přes celou výšku by dlouhý
 * popisek zajel přes fotku, a s popiskem jako samostatnou lištou by fotka poskočila při
 * listování mezi stránkou s popiskem a bez něj.
 *
 * Místo pro lišty ([reservedTop], [reservedBottom]) i pro popisek se s [immersion] plynule
 * vrací kartě — v režimu celé obrazovky se snímek vepisuje do celé stránky. Čte se až při
 * měření, takže přechod nerekomponuje.
 *
 * [card] dostane přesnou velikost vepsaného snímku; tvar a obsah si kreslí sám.
 */
@Composable
fun MediaPageLayout(
    aspectRatio: Float,
    caption: String,
    modifier: Modifier = Modifier,
    /** Místo nahoře a dole, které v galerii patří lištám. */
    reservedTop: Dp = 0.dp,
    reservedBottom: Dp = 0.dp,
    /** 0 = galerie, 1 = celá obrazovka. Čte se při měření a kreslení. */
    immersion: () -> Float = { 0f },
    /**
     * Průhlednost popisku, čtená až při kreslení. Sousední stránka vykukuje u okraje i s kusem
     * popisku; ten tam jen ruší, takže mizí, jak stránka odjíždí ze středu.
     */
    captionAlpha: () -> Float = { 1f },
    /**
     * Kam se karta ve stránce srovná: −1 k levému okraji, 0 na střed, 1 k pravému. Čte se až při
     * umístění, takže posun pageru nic nerekomponuje.
     */
    cardBias: () -> Float = { 0f },
    /**
     * Vrstva přes CELOU stránku nad kartou a popiskem — ovládání, které patří k okrajům
     * obrazovky, ne ke snímku (posuvník videa). Dostane přesnou velikost stránky.
     */
    chrome: @Composable () -> Unit = {},
    /**
     * Horní hrana pantu skládačky na stole v pixelech od horní hrany STRÁNKY, nebo `null`. Karta se
     * pak vepíše do půlky nad pantem (ta stojí a dá se na ni dívat), spodní půlka zůstane ovládání
     * v [chrome]. Čte se až při měření; stránku mimo celou obrazovku volající nechá na `null`.
     */
    tabletopSplit: () -> Int? = { null },
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
                        modifier = Modifier
                            .graphicsLayer { alpha = captionAlpha() * (1f - immersion()) }
                            .padding(horizontal = 4.dp),
                    )
                }
            },
            chrome,
        ),
        modifier = modifier,
    ) { (cardMeasurables, captionMeasurables, chromeMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val caption = captionMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        val gallery = 1f - immersion()

        val split = tabletopSplit()?.takeIf { it in 1 until height }
        val frame = if (split != null) {
            tabletopCardFrame(IntSize(width, height), split, aspectRatio)
        } else pageCardFrame(
            pageSize = IntSize(width, height),
            reservedTop = (reservedTop.roundToPx() * gallery).roundToInt(),
            reservedBottom = (reservedBottom.roundToPx() * gallery).roundToInt(),
            captionHeight = ((caption?.height ?: 0) * gallery).roundToInt(),
            captionGap = if (caption != null) (CAPTION_GAP.roundToPx() * gallery).roundToInt() else 0,
            aspectRatio = aspectRatio,
        )
        val card = cardMeasurables.first().measure(Constraints.fixed(frame.width, frame.height))
        val chrome = chromeMeasurables.map { it.measure(Constraints.fixed(width, height)) }

        layout(width, height) {
            val left = ((width - frame.width) / 2f * (1f + cardBias().coerceIn(-1f, 1f))).roundToInt()
            card.place(left, frame.top)
            // Popisek začíná u levé hrany karty, ať u úzké fotky nevisí u okraje obrazovky.
            caption?.place(
                IntOffset(left.coerceAtMost(width - caption.width), frame.bottom + (CAPTION_GAP.roundToPx() * gallery).roundToInt()),
            )
            chrome.forEach { it.place(0, 0) }
        }
    }
}

/**
 * Kde ve stránce leží karta: vepsaná do místa mezi lištami a nad popiskem, svisle na středu
 * toho místa. Vodorovně je vycentrovaná; posun do strany (`cardBias`) přidává až umístění.
 */
fun pageCardFrame(
    pageSize: IntSize,
    reservedTop: Int,
    reservedBottom: Int,
    captionHeight: Int,
    captionGap: Int,
    aspectRatio: Float,
): IntRect {
    val available = (pageSize.height - reservedTop - reservedBottom).coerceAtLeast(0)
    val size = fitInto(pageSize.width, (available - captionHeight - captionGap).coerceAtLeast(0), aspectRatio)
    val top = reservedTop + ((available - size.height - captionGap - captionHeight) / 2).coerceAtLeast(0)
    val left = (pageSize.width - size.width) / 2
    return IntRect(IntOffset(left, top), size)
}

/**
 * Kde leží karta na skládačce na stole: vepsaná do půlky nad pantem ([split] px od horní hrany
 * stránky), vodorovně i svisle na jejím středu. Popisek ani lišty se do půlky nevejdou, ovládání
 * patří pod pant. Pant mimo stránku nic nemění — vrací kartu přes celou stránku.
 */
fun tabletopCardFrame(pageSize: IntSize, split: Int, aspectRatio: Float): IntRect =
    pageCardFrame(
        pageSize = if (split in 1 until pageSize.height) IntSize(pageSize.width, split) else pageSize,
        reservedTop = 0,
        reservedBottom = 0,
        captionHeight = 0,
        captionGap = 0,
        aspectRatio = aspectRatio,
    )

/**
 * Největší obdélník s poměrem stran [aspectRatio], který se vejde do [maxWidth] × [maxHeight].
 * Neznámý poměr (nula, záporný, NaN) se bere jako čtverec.
 */
fun fitInto(maxWidth: Int, maxHeight: Int, aspectRatio: Float): IntSize {
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    if (maxWidth <= 0 || maxHeight <= 0) return IntSize.Zero
    return if (maxWidth.toFloat() / maxHeight > ratio) {
        IntSize((maxHeight * ratio).roundToInt(), maxHeight)
    } else {
        IntSize(maxWidth, (maxWidth / ratio).roundToInt())
    }
}

/** Poměr stran z metadat; neznámé rozměry jako čtverec. */
fun aspectRatioOf(width: Int, height: Int): Float =
    if (width > 0 && height > 0) width.toFloat() / height else 1f

/** Víc řádků by fotku stlačilo; celý text je v konverzaci. */
private const val CAPTION_MAX_LINES = 4

private val CAPTION_GAP = 12.dp
