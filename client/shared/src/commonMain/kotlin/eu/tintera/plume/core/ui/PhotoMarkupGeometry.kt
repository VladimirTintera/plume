package eu.tintera.plume.core.ui

/**
 * Výřez fotky v podílech (0..1) OTOČENÉ fotky: levý, horní, pravý a dolní okraj.
 *
 * Měří se v otočené fotce, protože ji tak uživatel při ořezu vidí — výřez „horní půlka" po
 * otočení znamená horní půlku toho, co má před sebou.
 */
data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val Full = CropBox(0f, 0f, 1f, 1f)
    }
}

/**
 * Celá úprava fotky: tahy v souřadnicích ORIGINÁLU, otočení o [quarterTurns] čtvrtotáček po
 * směru hodin a výřez [crop] z otočené fotky.
 *
 * Pořadí při vykreslení je pevné: tahy → otočení → ořez. Tahy tak zůstávají přilepené k obsahu
 * fotky, ať se otočí nebo ořízne jakkoli, a ořez jde kdykoli vzít zpátky — nic se nezahazuje
 * dřív než v hotovém souboru.
 */
data class PhotoMarkup(
    val strokes: List<MarkupStroke> = emptyList(),
    val quarterTurns: Int = 0,
    val crop: CropBox = CropBox.Full,
) {
    /** Nic se nemění — vykreslovat netřeba, stačí originál. */
    val isEmpty: Boolean get() = strokes.isEmpty() && normalizedTurns(quarterTurns) == 0 && crop == CropBox.Full
}

/** Rozměry fotky [width] × [height] po otočení o [quarterTurns]. */
fun rotatedSize(width: Int, height: Int, quarterTurns: Int): Pair<Int, Int> =
    if (quarterTurns % 2 == 0) width to height else height to width

/** Bod originálu (podíly) v otočené fotce. */
fun rotatePoint(x: Float, y: Float, quarterTurns: Int): Pair<Float, Float> = when (normalizedTurns(quarterTurns)) {
    1 -> (1f - y) to x
    2 -> (1f - x) to (1f - y)
    3 -> y to (1f - x)
    else -> x to y
}

/** Bod otočené fotky (podíly) zpátky v originálu — obrácené [rotatePoint]. */
fun unrotatePoint(x: Float, y: Float, quarterTurns: Int): Pair<Float, Float> = rotatePoint(x, y, -quarterTurns)

/**
 * Bod originálu ve VÝSLEDNÉ fotce (otočené a oříznuté), v podílech výsledku. Body mimo výřez
 * vyjdou mimo 0..1 — kreslí se oříznuté plochou, ne zahozené.
 */
fun originalToResult(x: Float, y: Float, markup: PhotoMarkup): Pair<Float, Float> {
    val (rx, ry) = rotatePoint(x, y, markup.quarterTurns)
    val crop = markup.crop
    return (rx - crop.left) / crop.width to (ry - crop.top) / crop.height
}

/** Bod výsledné fotky (podíly) zpátky v originálu — obrácené [originalToResult]. */
fun resultToOriginal(x: Float, y: Float, markup: PhotoMarkup): Pair<Float, Float> {
    val crop = markup.crop
    return unrotatePoint(crop.left + x * crop.width, crop.top + y * crop.height, markup.quarterTurns)
}

/** Výsledná fotka v pixelech: otočená a oříznutá z originálu [width] × [height]. */
fun resultSize(width: Int, height: Int, markup: PhotoMarkup): Pair<Int, Int> {
    val (rw, rh) = rotatedSize(width, height, markup.quarterTurns)
    return (rw * markup.crop.width).toInt().coerceAtLeast(1) to (rh * markup.crop.height).toInt().coerceAtLeast(1)
}

/**
 * Výřez otočený spolu s fotkou o [turns] dalších čtvrtotáček, ať po „Otočit" ukazuje na týž
 * obsah. Rohy se otočí jako body a seřadí, protože levý horní se otočením stane pravým horním.
 */
fun CropBox.rotated(turns: Int): CropBox {
    val (x1, y1) = rotatePoint(left, top, turns)
    val (x2, y2) = rotatePoint(right, bottom, turns)
    return CropBox(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
}

/** Nejmenší výřez jako podíl strany — menší rámeček by šel stěží chytit prstem. */
const val MIN_CROP_FRACTION = 0.1f

/** Za co se výřez táhne: za roh, nebo celý. */
enum class CropHandle { TopLeft, TopRight, BottomLeft, BottomRight, Move }

/**
 * Výřez po tahu za [handle] o [dx], [dy] (podíly otočené fotky). Zůstává uvnitř fotky a nikdy
 * menší než [MIN_CROP_FRACTION]; posun celého výřezu se o okraj zastaví, nezmenší ho.
 */
fun CropBox.dragged(handle: CropHandle, dx: Float, dy: Float): CropBox = when (handle) {
    CropHandle.Move -> {
        val x = dx.coerceIn(-left, 1f - right)
        val y = dy.coerceIn(-top, 1f - bottom)
        CropBox(left + x, top + y, right + x, bottom + y)
    }
    CropHandle.TopLeft -> copy(
        left = (left + dx).coerceIn(0f, right - MIN_CROP_FRACTION),
        top = (top + dy).coerceIn(0f, bottom - MIN_CROP_FRACTION),
    )
    CropHandle.TopRight -> copy(
        right = (right + dx).coerceIn(left + MIN_CROP_FRACTION, 1f),
        top = (top + dy).coerceIn(0f, bottom - MIN_CROP_FRACTION),
    )
    CropHandle.BottomLeft -> copy(
        left = (left + dx).coerceIn(0f, right - MIN_CROP_FRACTION),
        bottom = (bottom + dy).coerceIn(top + MIN_CROP_FRACTION, 1f),
    )
    CropHandle.BottomRight -> copy(
        right = (right + dx).coerceIn(left + MIN_CROP_FRACTION, 1f),
        bottom = (bottom + dy).coerceIn(top + MIN_CROP_FRACTION, 1f),
    )
}

/** Čtvrtotáčky v rozsahu 0..3 (−1 = 3, 5 = 1). */
fun normalizedTurns(turns: Int): Int = ((turns % 4) + 4) % 4
