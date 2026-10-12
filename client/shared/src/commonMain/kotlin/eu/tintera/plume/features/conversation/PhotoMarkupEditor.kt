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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.rememberAsyncImagePainter
import eu.tintera.plume.core.ui.CropBox
import eu.tintera.plume.core.ui.localImage
import eu.tintera.plume.core.ui.CropHandle
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.MARKUP_PEN_COLORS
import eu.tintera.plume.core.ui.MarkupStroke
import eu.tintera.plume.core.ui.MarkupTool
import eu.tintera.plume.core.ui.PhotoMarkup
import eu.tintera.plume.core.ui.dragged
import eu.tintera.plume.core.ui.normalizedTurns
import eu.tintera.plume.core.ui.resultSize
import eu.tintera.plume.core.ui.resultToOriginal
import eu.tintera.plume.core.ui.rotated
import eu.tintera.plume.core.ui.strokeColor
import eu.tintera.plume.core.ui.strokeWidth
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_done
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.crop_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.edit_filled_24dp
import eu.tintera.plume.resources.imagesearch_roller_24dp
import eu.tintera.plume.resources.imagesearch_roller_filled_24dp
import eu.tintera.plume.resources.ink_highlighter_24dp
import eu.tintera.plume.resources.ink_highlighter_filled_24dp
import eu.tintera.plume.resources.markup_color_black
import eu.tintera.plume.resources.markup_color_white
import eu.tintera.plume.resources.markup_crop
import eu.tintera.plume.resources.markup_highlighter
import eu.tintera.plume.resources.markup_pen
import eu.tintera.plume.resources.markup_redact
import eu.tintera.plume.resources.markup_reset
import eu.tintera.plume.resources.markup_rotate
import eu.tintera.plume.resources.markup_title
import eu.tintera.plume.resources.markup_undo
import eu.tintera.plume.resources.rotate_right_24dp
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
 * Úprava fotky před odesláním: pero, zvýrazňovač, začernění (`MarkupTool`) a ořez s otočením.
 *
 * Upravuje se ORIGINÁL ([path], [width] × [height]) a [initial] je úprava z minula, takže jde
 * vzít zpátky i to, co už jednou bylo „hotové". Tahy se drží v podílech originálu, otočení
 * a výřez se přes ně jen promítají (`PhotoMarkup`); do souboru je v plném rozlišení vykreslí
 * `PhotoMarkupRenderer` stejně, jak jsou vidět tady.
 *
 * - **Při kreslení je vidět výsledek** (otočený a oříznutý) — kreslí se do toho, co odejde.
 * - **Při ořezu je vidět celá otočená fotka** s rámečkem výřezu; rámeček se táhne za rohy nebo
 *   celý. „Otočit" otočí fotku i rámeček o čtvrt otáčky po směru hodin.
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
    initial: PhotoMarkup,
    onCancel: () -> Unit,
    onDone: (PhotoMarkup) -> Unit,
) {
    val strokes = remember(path) { mutableStateListOf<MarkupStroke>().apply { addAll(initial.strokes) } }
    var quarterTurns by remember(path) { mutableStateOf(normalizedTurns(initial.quarterTurns)) }
    var crop by remember(path) { mutableStateOf(initial.crop) }
    var tool by remember { mutableStateOf(MarkupTool.Redact) }
    // Ořez je režim místo nástroje: kreslit se v něm nedá, tahy prstem posouvají rámeček.
    var cropping by remember { mutableStateOf(false) }
    var penColor by remember { mutableStateOf(MARKUP_PEN_COLORS.first()) }
    val markup = PhotoMarkup(strokes.toList(), quarterTurns, crop)

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
            TextButton(onClick = { onDone(markup) }) {
                Text(stringResource(Res.string.attachment_done), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Při ořezu celá otočená fotka, při kreslení výsledek; vepsáno se zachovaným poměrem.
            val view = if (cropping) markup.copy(crop = CropBox.Full) else markup
            val (viewWidth, viewHeight) = resultSize(width, height, view)
            val ratio = viewWidth.toFloat() / viewHeight
            val areaWidth = maxWidth - IMAGE_MARGIN * 2
            val areaHeight = maxHeight - IMAGE_MARGIN * 2
            val fitsWidth = areaWidth / areaHeight < ratio
            val boxWidth = if (fitsWidth) areaWidth else areaHeight * ratio
            val boxHeight = if (fitsWidth) areaWidth / ratio else areaHeight
            Box(Modifier.size(boxWidth, boxHeight)) {
                MarkupCanvas(
                    path = path,
                    view = view,
                    drawing = !cropping,
                    tool = tool,
                    penColor = penColor,
                    imageWidth = width,
                    imageHeight = height,
                    onStroke = { strokes += it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            /*
             * Rámeček má plochu přes CELÉ okolí fotky, ne jen přes ni: úchyty leží na okraji
             * fotky a polovina dotykové plochy by byla venku — dotyk těsně vedle rohu by se do
             * rámečku vůbec nedostal (stalo se na emulátoru).
             */
            if (cropping) {
                CropFrame(
                    crop = crop,
                    onCrop = { crop = it },
                    imageSize = DpSize(boxWidth, boxHeight),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            /*
             * Řada nad nástroji drží místo vždycky — kdyby se vysouvala, fotka nad ní by se při
             * přepnutí nástroje zmenšila a poskočila. U pera v ní jsou barvy, u ořezu otočení
             * a návrat k celé fotce, jinak je neviditelná a neťukací.
             */
            Box(Modifier.height(SWATCH_ROW_HEIGHT), contentAlignment = Alignment.Center) {
                val penTool = !cropping && tool == MarkupTool.Pen
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                if (cropping) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            quarterTurns = normalizedTurns(quarterTurns + 1)
                            crop = crop.rotated(1)
                        }) {
                            Icon(painterResource(Res.drawable.rotate_right_24dp), contentDescription = null, tint = Color.White)
                            Text(
                                stringResource(Res.string.markup_rotate),
                                color = Color.White,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        TextButton(
                            onClick = {
                                quarterTurns = 0
                                crop = CropBox.Full
                            },
                            enabled = quarterTurns != 0 || crop != CropBox.Full,
                        ) {
                            Text(
                                stringResource(Res.string.markup_reset),
                                color = if (quarterTurns != 0 || crop != CropBox.Full) Color.White else Color.White.copy(alpha = DISABLED_ALPHA),
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TOOLS.forEach { (option, icons) ->
                    val (outline, filled, label) = icons
                    val checked = !cropping && tool == option
                    FilledTonalIconToggleButton(
                        checked = checked,
                        onCheckedChange = {
                            tool = option
                            cropping = false
                        },
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        Icon(painterResource(if (checked) filled else outline), contentDescription = stringResource(label))
                    }
                }
                FilledTonalIconToggleButton(
                    checked = cropping,
                    onCheckedChange = { cropping = true },
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(painterResource(Res.drawable.crop_24dp), contentDescription = stringResource(Res.string.markup_crop))
                }
            }
        }
    }
}

/**
 * Fotka a tahy nad ní, promítnuté přes otočení a výřez [view]. S [drawing] tah prstem přidá nový
 * tah aktuálním nástrojem — dotyk se převede zpátky do souřadnic originálu.
 */
@Composable
private fun MarkupCanvas(
    path: String,
    view: PhotoMarkup,
    drawing: Boolean,
    tool: MarkupTool,
    penColor: Long,
    imageWidth: Int,
    imageHeight: Int,
    onStroke: (MarkupStroke) -> Unit,
    modifier: Modifier,
) {
    val currentTool by rememberUpdatedState(tool)
    val currentColor by rememberUpdatedState(penColor)
    val currentView by rememberUpdatedState(view)
    val currentOnStroke by rememberUpdatedState(onStroke)
    // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
    val painter = rememberAsyncImagePainter(localImage(path))
    // Rozkreslený tah, v podílech ORIGINÁLU; po zvednutí prstu se z něj stane hotový tah.
    val current = remember { mutableStateListOf<Float>() }

    Canvas(
        modifier
            .clipToBounds()
            .then(
                if (!drawing) Modifier else Modifier.pointerInput(Unit) {
                    fun original(position: Offset): Pair<Float, Float> = resultToOriginal(
                        (position.x / size.width).coerceIn(0f, 1f),
                        (position.y / size.height).coerceIn(0f, 1f),
                        currentView,
                    )
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        current.clear()
                        original(down.position).let { (x, y) -> current += x; current += y }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            original(change.position).let { (x, y) -> current += x; current += y }
                        }
                        val tool = currentTool
                        currentOnStroke(MarkupStroke(tool, tool.strokeColor(currentColor), current.toFloatArray()))
                        current.clear()
                    }
                },
            ),
    ) {
        inOriginalFrame(view, imageWidth, imageHeight) { original ->
            with(painter) { draw(original) }
            view.strokes.forEach { drawStroke(it.tool, it.color, it.points, original, imageWidth, imageHeight) }
            if (current.isNotEmpty()) {
                val tool = currentTool
                drawStroke(tool, tool.strokeColor(currentColor), current.toFloatArray(), original, imageWidth, imageHeight)
            }
        }
    }
}

/**
 * Kreslení v rámci ORIGINÁLU promítnutého do plochy: posun o výřez, pak otočení o čtvrtotáčky
 * kolem levého horního rohu s posunem, který otočenou fotku vrátí do plochy. [block] dostane
 * velikost originálu v pixelech plochy — fotka i tahy se kreslí do obdélníku 0..size.
 */
private fun DrawScope.inOriginalFrame(
    view: PhotoMarkup,
    imageWidth: Int,
    imageHeight: Int,
    block: DrawScope.(original: Size) -> Unit,
) {
    val turns = normalizedTurns(view.quarterTurns)
    // Otočená fotka celá, v pixelech plochy (plocha je jen výřez z ní).
    val rotatedWidth = size.width / view.crop.width
    val rotatedHeight = size.height / view.crop.height
    val original = if (turns % 2 == 0) Size(rotatedWidth, rotatedHeight) else Size(rotatedHeight, rotatedWidth)
    translate(-view.crop.left * rotatedWidth, -view.crop.top * rotatedHeight) {
        val shift = when (turns) {
            1 -> Offset(rotatedWidth, 0f)
            2 -> Offset(rotatedWidth, rotatedHeight)
            3 -> Offset(0f, rotatedHeight)
            else -> Offset.Zero
        }
        translate(shift.x, shift.y) {
            rotate(degrees = 90f * turns, pivot = Offset.Zero) {
                block(original)
            }
        }
    }
}

/**
 * Tah stejně jako ve vykreslené fotce: jedna cesta s kulatými konci a spoji, šířka podílem kratší
 * strany originálu, body v podílech originálu velkého [original] pixelů plochy.
 */
private fun DrawScope.drawStroke(
    tool: MarkupTool,
    color: Long,
    points: FloatArray,
    original: Size,
    imageWidth: Int,
    imageHeight: Int,
) {
    val strokeWidth = tool.strokeWidth(imageWidth, imageHeight) * (original.width / imageWidth)
    val paint = Color(color.toInt())
    if (points.size == 2) {
        drawCircle(paint, radius = strokeWidth / 2, center = Offset(points[0] * original.width, points[1] * original.height))
        return
    }
    val line = Path().apply {
        moveTo(points[0] * original.width, points[1] * original.height)
        for (i in 2 until points.size step 2) lineTo(points[i] * original.width, points[i + 1] * original.height)
    }
    drawPath(line, paint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Rámeček výřezu nad celou otočenou fotkou: mimo výřez ztmaveno, rohy jsou úchyty. Tah za roh
 * mění velikost, tah uvnitř posouvá celý výřez; o okraj fotky se obojí zastaví.
 */
@Composable
private fun CropFrame(crop: CropBox, onCrop: (CropBox) -> Unit, imageSize: DpSize, modifier: Modifier) {
    val currentCrop by rememberUpdatedState(crop)
    val currentOnCrop by rememberUpdatedState(onCrop)
    val density = LocalDensity.current
    val handleRadius = with(density) { HANDLE_TOUCH_RADIUS.toPx() }
    val image = with(density) { Size(imageSize.width.toPx(), imageSize.height.toPx()) }
    val currentImage by rememberUpdatedState(image)
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val box = currentCrop
                val img = currentImage
                // Fotka je v ploše vystředěná; rohy výřezu v pixelech plochy.
                val origin = Offset((size.width - img.width) / 2, (size.height - img.height) / 2)
                fun at(x: Float, y: Float) = origin + Offset(x * img.width, y * img.height)
                val corners = listOf(
                    CropHandle.TopLeft to at(box.left, box.top),
                    CropHandle.TopRight to at(box.right, box.top),
                    CropHandle.BottomLeft to at(box.left, box.bottom),
                    CropHandle.BottomRight to at(box.right, box.bottom),
                )
                val inside = down.position.x in at(box.left, 0f).x..at(box.right, 0f).x &&
                    down.position.y in at(0f, box.top).y..at(0f, box.bottom).y
                val handle = corners.minByOrNull { (it.second - down.position).getDistance() }
                    ?.takeIf { (it.second - down.position).getDistance() <= handleRadius }?.first
                    ?: if (inside) CropHandle.Move else return@awaitEachGesture
                down.consume()
                var last = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    val delta = change.position - last
                    last = change.position
                    currentOnCrop(currentCrop.dragged(handle, delta.x / img.width, delta.y / img.height))
                }
            }
        },
    ) {
        val imageLeft = (size.width - image.width) / 2
        val imageTop = (size.height - image.height) / 2
        val imageRight = imageLeft + image.width
        val imageBottom = imageTop + image.height
        val left = imageLeft + crop.left * image.width
        val top = imageTop + crop.top * image.height
        val right = imageLeft + crop.right * image.width
        val bottom = imageTop + crop.bottom * image.height
        val shade = Color.Black.copy(alpha = CROP_SHADE_ALPHA)
        // Čtyři pruhy fotky kolem výřezu, ne „všechno a pak díra" — bez vrstvy s průhlednou výplní.
        drawRect(shade, Offset(imageLeft, imageTop), Size(image.width, top - imageTop))
        drawRect(shade, Offset(imageLeft, bottom), Size(image.width, imageBottom - bottom))
        drawRect(shade, Offset(imageLeft, top), Size(left - imageLeft, bottom - top))
        drawRect(shade, Offset(right, top), Size(imageRight - right, bottom - top))
        drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(width = 2.dp.toPx()))
        val handle = 4.dp.toPx()
        val arm = 20.dp.toPx()
        listOf(left to top, right to top, left to bottom, right to bottom).forEach { (x, y) ->
            val dx = if (x == left) arm else -arm
            val dy = if (y == top) arm else -arm
            drawLine(Color.White, Offset(x, y), Offset(x + dx, y), strokeWidth = handle)
            drawLine(Color.White, Offset(x, y), Offset(x, y + dy), strokeWidth = handle)
        }
    }
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

/** Výška řady nad nástroji (barvy pera, otočení) — drží se, i když je řada prázdná. */
private val SWATCH_ROW_HEIGHT = 48.dp

/** Okraj kolem fotky v editoru — místo, kde se chytají úchyty výřezu ležící na jejím okraji. */
private val IMAGE_MARGIN = 24.dp

/** Jak daleko od rohu výřezu se ještě chytí úchyt. */
private val HANDLE_TOUCH_RADIUS = 32.dp

/** Ztmavení fotky mimo výřez. */
private const val CROP_SHADE_ALPHA = 0.6f
