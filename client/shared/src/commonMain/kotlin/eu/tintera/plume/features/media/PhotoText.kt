package eu.tintera.plume.features.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ocr.RecognizedBlock
import eu.tintera.plume.core.ocr.RecognizedText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.media_text_copy
import eu.tintera.plume.resources.media_text_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Zvýraznění rozpoznaného textu přímo na fotce: fotka ztmavne a bloky textu zůstanou světlé
 * a orámované, jako v Google Lens. Leží UVNITŘ karty se snímkem, takže se s ní přibližuje
 * a posouvá; souřadnice bloků jsou podíly rozměrů obrázku a karta má jeho poměr stran.
 *
 * Výřezy do závoje kreslí `BlendMode.Clear`, a ten potřebuje vlastní vrstvu
 * (`CompositingStrategy.Offscreen`) — jinak by vymazal i fotku pod závojem.
 */
@Composable
internal fun PhotoTextHighlights(blocks: List<RecognizedBlock>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
        val pad = HIGHLIGHT_PADDING.toPx()
        val radius = CornerRadius(HIGHLIGHT_RADIUS.toPx())
        for (block in blocks) {
            val topLeft = Offset(block.left * size.width - pad, block.top * size.height - pad)
            val boxSize = Size((block.right - block.left) * size.width + 2 * pad, (block.bottom - block.top) * size.height + 2 * pad)
            drawRoundRect(Color.Black, topLeft, boxSize, radius, blendMode = BlendMode.Clear)
            drawRoundRect(Color.White, topLeft, boxSize, radius, style = Stroke(width = HIGHLIGHT_STROKE.toPx()))
        }
    }
}

/**
 * Rozpoznaný text pod fotkou, na místě pruhu reakcí: jde v něm vybrat úsek (systémová nabídka
 * nad výběrem nabídne Kopírovat) nebo jedním tlačítkem zkopírovat všechno.
 *
 * Panel, ne modální sheet: sheet by fotku překryl závojem a zvýraznění na ní by nebylo vidět —
 * a právě podle něj uživatel pozná, co se přečetlo.
 */
@Composable
internal fun PhotoTextPanel(text: RecognizedText, onCopy: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = PANEL_MAX_WIDTH)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.media_text_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                }
            }
            SelectionContainer {
                Text(
                    text.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .heightIn(max = TEXT_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onCopy) {
                    Icon(painterResource(Res.drawable.content_copy_24dp), contentDescription = null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(stringResource(Res.string.media_text_copy))
                }
            }
        }
    }
}

/** Ztmavení fotky kolem zvýrazněného textu. */
private const val SCRIM_ALPHA = 0.45f
private val HIGHLIGHT_PADDING = 3.dp
private val HIGHLIGHT_RADIUS = 6.dp
private val HIGHLIGHT_STROKE = 1.5.dp

/** Nad tuhle výšku se text v panelu posouvá; fotka nad ním musí zůstat vidět. */
private val TEXT_MAX_HEIGHT = 180.dp
private val PANEL_MAX_WIDTH = 560.dp
