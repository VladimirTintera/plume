package eu.tintera.plume.features.conversation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.MARKUP_PEN_COLORS
import eu.tintera.plume.core.ui.MarkupStroke
import eu.tintera.plume.core.ui.MarkupTool
import eu.tintera.plume.core.ui.strokeColor
import eu.tintera.plume.core.ui.strokeWidth
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_done
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.edit_filled_24dp
import eu.tintera.plume.resources.imagesearch_roller_24dp
import eu.tintera.plume.resources.imagesearch_roller_filled_24dp
import eu.tintera.plume.resources.ink_highlighter_24dp
import eu.tintera.plume.resources.ink_highlighter_filled_24dp
import eu.tintera.plume.resources.markup_color_black
import eu.tintera.plume.resources.markup_color_white
import eu.tintera.plume.resources.markup_highlighter
import eu.tintera.plume.resources.markup_pen
import eu.tintera.plume.resources.markup_redact
import eu.tintera.plume.resources.markup_title
import eu.tintera.plume.resources.markup_undo
import eu.tintera.plume.resources.theme_color_blue
import eu.tintera.plume.resources.theme_color_green
import eu.tintera.plume.resources.theme_color_red
import eu.tintera.plume.resources.theme_color_yellow
import eu.tintera.plume.resources.undo_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kreslení do fotky před odesláním: pero, zvýrazňovač a začernění (`MarkupTool`).
 *
 * Kreslí se nad ORIGINÁLEM ([path]) a [initialStrokes] jsou tahy z minulé úpravy, takže jde
 * vzít zpátky i to, co už jednou bylo „hotové". Tahy se drží v podílech rozměrů fotky; do
 * souboru je v plném rozlišení vykreslí `PhotoMarkupRenderer` stejně, jak jsou vidět tady.
 *
 * Fotka se nepřibližuje: přiblížení dvěma prsty by se pralo s kreslením jedním, a začernit
 * číslo nebo adresu jde i na fotce zmenšené na displej (šířka tahu se měří fotkou, ne prstem).
 * Zpět zavře editor bez uložení, jako „Zrušit".
 */
@Composable
internal fun PhotoMarkupEditor(
    path: String,
    width: Int,
    height: Int,
    initialStrokes: List<MarkupStroke>,
    onCancel: () -> Unit,
    onDone: (List<MarkupStroke>) -> Unit,
) {
    val strokes = remember(path) { mutableStateListOf<MarkupStroke>().apply { addAll(initialStrokes) } }
    var tool by remember { mutableStateOf(MarkupTool.Redact) }
    var penColor by remember { mutableStateOf(MARKUP_PEN_COLORS.first()) }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onCancel,
    )
    DarkSystemBars()

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_cancel), tint = Color.White)
            }
            Text(
                text = stringResource(Res.string.markup_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { strokes.removeAt(strokes.lastIndex) },
                enabled = strokes.isNotEmpty(),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painterResource(Res.drawable.undo_24dp),
                    contentDescription = stringResource(Res.string.markup_undo),
                    tint = if (strokes.isNotEmpty()) Color.White else Color.White.copy(alpha = DISABLED_ALPHA),
                )
            }
            TextButton(onClick = { onDone(strokes.toList()) }) {
                Text(stringResource(Res.string.attachment_done), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Fotka vepsaná do plochy se zachovaným poměrem stran; tahy se měří touhle plochou.
            val ratio = width.toFloat() / height.coerceAtLeast(1)
            val fitsWidth = maxWidth / maxHeight < ratio
            val boxWidth = if (fitsWidth) maxWidth else maxHeight * ratio
            val boxHeight = if (fitsWidth) maxWidth / ratio else maxHeight
            MarkupCanvas(
                path = path,
                strokes = strokes,
                tool = tool,
                penColor = penColor,
                imageWidth = width,
                imageHeight = height,
                modifier = Modifier.size(boxWidth, boxHeight),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            /*
             * Barvy jen u pera: zvýrazňovač je vždycky žlutý a začernění vždycky černé. Řada
             * ale místo drží i u ostatních nástrojů, jen je neviditelná a neťukací: kdyby se
             * vysunula, fotka nad ní by se při přepnutí nástroje zmenšila a poskočila.
             */
            val penTool = tool == MarkupTool.Pen
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .alpha(if (penTool) 1f else 0f)
                    // Neviditelná řada nemá co říkat ani čtečce obrazovky.
                    .then(if (penTool) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                MARKUP_PEN_COLORS.forEachIndexed { index, color ->
                    ColorSwatch(
                        color = Color(color.toInt()),
                        label = stringResource(PEN_COLOR_NAMES[index]),
                        selected = color == penColor,
                        enabled = penTool,
                        onClick = { penColor = color },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TOOLS.forEach { (option, icons) ->
                    val (outline, filled, label) = icons
                    FilledTonalIconToggleButton(
                        checked = tool == option,
                        onCheckedChange = { tool = option },
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        Icon(
                            painterResource(if (tool == option) filled else outline),
                            contentDescription = stringResource(label),
                        )
                    }
                }
            }
        }
    }
}

/** Fotka a tahy nad ní; tah prstem přidává nový tah aktuálním nástrojem. */
@Composable
private fun MarkupCanvas(
    path: String,
    strokes: MutableList<MarkupStroke>,
    tool: MarkupTool,
    penColor: Long,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier,
) {
    val currentTool by rememberUpdatedState(tool)
    val currentColor by rememberUpdatedState(penColor)
    // Rozkreslený tah, v podílech fotky; po zvednutí prstu se z něj stane hotový tah.
    val drawing = remember { mutableStateListOf<Float>() }

    Box(modifier) {
        AsyncImage(
            // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
            model = "file://$path",
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        drawing.clear()
                        drawing += down.position.x / size.width
                        drawing += down.position.y / size.height
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            drawing += (change.position.x / size.width).coerceIn(0f, 1f)
                            drawing += (change.position.y / size.height).coerceIn(0f, 1f)
                        }
                        val tool = currentTool
                        strokes += MarkupStroke(tool, tool.strokeColor(currentColor), drawing.toFloatArray())
                        drawing.clear()
                    }
                },
        ) {
            strokes.forEach { drawStroke(it.tool, it.color, it.points, imageWidth, imageHeight) }
            if (drawing.isNotEmpty()) {
                val tool = currentTool
                drawStroke(tool, tool.strokeColor(currentColor), drawing.toFloatArray(), imageWidth, imageHeight)
            }
        }
    }
}

/**
 * Tah stejně jako ve vykreslené fotce: jedna cesta s kulatými konci a spoji, šířka podílem kratší
 * strany. Plocha má poměr stran fotky, takže podíl jejích rozměrů sedí na podíl fotky.
 */
private fun DrawScope.drawStroke(tool: MarkupTool, color: Long, points: FloatArray, imageWidth: Int, imageHeight: Int) {
    val scale = size.width / imageWidth
    val strokeWidth = tool.strokeWidth(imageWidth, imageHeight) * scale
    val paint = Color(color.toInt())
    if (points.size == 2) {
        drawCircle(paint, radius = strokeWidth / 2, center = Offset(points[0] * size.width, points[1] * size.height))
        return
    }
    val line = Path().apply {
        moveTo(points[0] * size.width, points[1] * size.height)
        for (i in 2 until points.size step 2) lineTo(points[i] * size.width, points[i + 1] * size.height)
    }
    drawPath(line, paint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Kulatý vzorek barvy pera; vybraný má bílý prstenec. */
@Composable
private fun ColorSwatch(color: Color, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(width = if (selected) 3.dp else 1.dp, color = if (selected) Color.White else Color.White.copy(alpha = 0.4f), shape = CircleShape)
            .padding(if (selected) 5.dp else 2.dp)
            .clip(CircleShape)
            .background(color)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

private val TOOLS: List<Pair<MarkupTool, Triple<DrawableResource, DrawableResource, StringResource>>> = listOf(
    MarkupTool.Pen to Triple(Res.drawable.edit_24dp, Res.drawable.edit_filled_24dp, Res.string.markup_pen),
    MarkupTool.Highlighter to Triple(Res.drawable.ink_highlighter_24dp, Res.drawable.ink_highlighter_filled_24dp, Res.string.markup_highlighter),
    MarkupTool.Redact to Triple(Res.drawable.imagesearch_roller_24dp, Res.drawable.imagesearch_roller_filled_24dp, Res.string.markup_redact),
)

/** Jména barev pera ve stejném pořadí jako `MARKUP_PEN_COLORS` — pro čtečku obrazovky. */
private val PEN_COLOR_NAMES: List<StringResource> = listOf(
    Res.string.theme_color_red,
    Res.string.theme_color_yellow,
    Res.string.theme_color_green,
    Res.string.theme_color_blue,
    Res.string.markup_color_white,
    Res.string.markup_color_black,
)

private const val DISABLED_ALPHA = 0.38f
