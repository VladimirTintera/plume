package eu.tintera.plume.features.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ocr.RecognizedLine
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.media_text_clear_selection
import eu.tintera.plume.resources.media_text_copy
import eu.tintera.plume.resources.media_text_copy_selection
import eu.tintera.plume.resources.media_text_hint
import eu.tintera.plume.resources.media_text_title
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Zvýraznění rozpoznaného textu přímo na fotce: fotka ztmavne a řádky textu zůstanou světlé
 * a orámované, jako v Google Lens. Leží UVNITŘ karty se snímkem, takže se s ní přibližuje
 * a posouvá; souřadnice řádků jsou podíly rozměrů obrázku a karta má jeho poměr stran.
 *
 * Ťuknutí na řádek ho vybere (vyplní se barvou výběru textu, stejnou jako v panelu pod fotkou),
 * další ťuknutí výběr zruší. Ťuknutí vedle řádků jde dál na stránku (přepnutí celé obrazovky).
 *
 * - **Ťuknutí se pozná až při zvednutí prstu a teprve to se spotřebuje.** Dotyk sám zůstává
 *   volný, takže štípnutí, posun přiblížené fotky, zavírací tah i listování začaté na řádku
 *   fungují dál — gesta stránky si pohyb spotřebují a ťuknutí na řádek se tím zruší.
 *   Spotřebované zvednutí zruší i ťuknutí stránky, takže výběr řádku nepřepne celou obrazovku.
 *   Dvojité ťuknutí na řádek proto fotku nepřiblíží, jen řádek dvakrát přepne.
 * - **Řádek se trefuje s přesahem** ([HIT_SLOP]) a vyhrává nejbližší: drobné písmo na fotce
 *   je pár bodů vysoké a přesně trefit by nešlo.
 * - Výřezy do závoje kreslí `BlendMode.Clear`, a ten potřebuje vlastní vrstvu
 *   (`CompositingStrategy.Offscreen`) — jinak by vymazal i fotku pod závojem.
 * - **Čtečka obrazovky** dostane každý řádek jako zaškrtávací prvek s jeho textem (neviditelné
 *   boxy na místě řádků), na kreslení v `Canvas` by nedosáhla.
 */
@Composable
internal fun PhotoTextHighlights(
    lines: List<RecognizedLine>,
    selected: Set<Int>,
    onToggle: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val selectionFill = LocalTextSelectionColors.current.backgroundColor
    val currentLines by rememberUpdatedState(lines)
    val currentSelected by rememberUpdatedState(selected)
    val currentToggle by rememberUpdatedState(onToggle)
    val toggle = { index: Int ->
        haptics.toggle(index !in currentSelected)
        currentToggle(index)
    }
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) {
                    val reach = (HIGHLIGHT_PADDING + HIT_SLOP).toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hit = lineAt(currentLines, down.position, size.width.toFloat(), size.height.toFloat(), reach)
                            ?: return@awaitEachGesture
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (lineAt(currentLines, up.position, size.width.toFloat(), size.height.toFloat(), reach) != hit) {
                            return@awaitEachGesture
                        }
                        up.consume()
                        toggle(hit)
                    }
                },
        ) {
            drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
            val pad = HIGHLIGHT_PADDING.toPx()
            val radius = CornerRadius(HIGHLIGHT_RADIUS.toPx())
            lines.forEachIndexed { index, line ->
                val topLeft = Offset(line.left * size.width - pad, line.top * size.height - pad)
                val boxSize = Size((line.right - line.left) * size.width + 2 * pad, (line.bottom - line.top) * size.height + 2 * pad)
                drawRoundRect(Color.Black, topLeft, boxSize, radius, blendMode = BlendMode.Clear)
                val isSelected = index in selected
                if (isSelected) drawRoundRect(selectionFill, topLeft, boxSize, radius)
                val stroke = if (isSelected) SELECTED_STROKE else HIGHLIGHT_STROKE
                drawRoundRect(Color.White, topLeft, boxSize, radius, style = Stroke(width = stroke.toPx()))
            }
        }
        lines.forEachIndexed { index, line ->
            Box(
                Modifier
                    .lineBounds(line)
                    .semantics {
                        contentDescription = line.text
                        role = Role.Checkbox
                        toggleableState = ToggleableState(index in selected)
                        onClick {
                            toggle(index)
                            true
                        }
                    },
            )
        }
    }
}

/**
 * Řádek nejblíž bodu [point], pokud leží nejvýš [reach] od jeho obdélníku; `null` = ťuknutí
 * vedle všech. Bod uvnitř víc řádků (překrývají se) připadne tomu, ke kterému má nejblíž střed.
 */
private fun lineAt(lines: List<RecognizedLine>, point: Offset, width: Float, height: Float, reach: Float): Int? {
    var best: Int? = null
    var bestEdge = Float.MAX_VALUE
    var bestCenter = Float.MAX_VALUE
    lines.forEachIndexed { index, line ->
        val left = line.left * width
        val right = line.right * width
        val top = line.top * height
        val bottom = line.bottom * height
        val edge = hypot(maxOf(left - point.x, point.x - right, 0f), maxOf(top - point.y, point.y - bottom, 0f))
        if (edge > reach) return@forEachIndexed
        val center = hypot((left + right) / 2 - point.x, (top + bottom) / 2 - point.y)
        if (edge < bestEdge || (edge == bestEdge && center < bestCenter)) {
            best = index
            bestEdge = edge
            bestCenter = center
        }
    }
    return best
}

/** Box na místě řádku uvnitř karty (souřadnice jsou podíly její velikosti). */
private fun Modifier.lineBounds(line: RecognizedLine) = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(
        Constraints.fixed(
            ((line.right - line.left) * width).roundToInt().coerceAtLeast(0),
            ((line.bottom - line.top) * height).roundToInt().coerceAtLeast(0),
        ),
    )
    layout(width, height) { placeable.place((line.left * width).roundToInt(), (line.top * height).roundToInt()) }
}

/**
 * Rozpoznaný text pod fotkou, na místě pruhu reakcí: jde v něm vybrat úsek (systémová nabídka
 * nad výběrem nabídne Kopírovat) nebo jedním tlačítkem zkopírovat všechno. Když jsou na fotce
 * vybrané řádky, ukazuje a kopíruje jen ty.
 *
 * Panel, ne modální sheet: sheet by fotku překryl závojem a zvýraznění na ní by nebylo vidět —
 * a právě podle něj uživatel pozná, co se přečetlo, a na něm se vybírá.
 */
@Composable
internal fun PhotoTextPanel(
    text: PhotoText,
    onCopy: () -> Unit,
    onClearSelection: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = text.selected.isNotEmpty()
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
            // Že jde vybírat na fotce, by jinak nikdo nezjistil; s jediným řádkem není co vybírat.
            if (!hasSelection && text.text.lines.size > 1) {
                Text(
                    stringResource(Res.string.media_text_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp, bottom = 8.dp),
                )
            }
            SelectionContainer {
                Text(
                    text.shownText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .heightIn(max = TEXT_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.padding(top = 12.dp))
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (hasSelection) {
                    TextButton(onClick = onClearSelection) {
                        Text(stringResource(Res.string.media_text_clear_selection))
                    }
                }
                FilledTonalButton(onClick = onCopy) {
                    Icon(painterResource(Res.drawable.content_copy_24dp), contentDescription = null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(stringResource(if (hasSelection) Res.string.media_text_copy_selection else Res.string.media_text_copy))
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
private val SELECTED_STROKE = 2.5.dp

/** O kolik vedle řádku se ťuknutí ještě počítá jako na něj. */
private val HIT_SLOP = 12.dp

/** Nad tuhle výšku se text v panelu posouvá; fotka nad ním musí zůstat vidět. */
private val TEXT_MAX_HEIGHT = 180.dp
private val PANEL_MAX_WIDTH = 560.dp
