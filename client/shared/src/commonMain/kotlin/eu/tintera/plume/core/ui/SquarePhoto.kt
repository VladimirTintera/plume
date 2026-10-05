package eu.tintera.plume.core.ui

/**
 * Ořízne fotku na čtverec a zmenší ji na [PROFILE_PHOTO_EDGE] — profilová fotka.
 *
 * Kreslí se v kruhu a oficiální klienti ji vždycky ořezávají na čtverec; neořezanou fotku by
 * každý klient ořízl jinak. Vstupem je fotka už připravená výběrem příloh (otočená podle EXIF,
 * JPEG), výstupem nový soubor v cache vedle ní.
 */
interface SquarePhoto {
    /**
     * [region] je výřez, který uživatel vybral v [PhotoCropDialog]; `null` = ze středu. Výřez
     * se před ořezem srovná na čtverec uvnitř fotky (`squarePixels`) — zaokrouhlení podílů
     * by jinak z kruhu udělalo elipsu o pixel.
     */
    suspend fun crop(path: String, region: CropRegion? = null): String
}

/** Výřez v pixelech fotky [width] × [height]: levý horní roh a strana čtverce. */
internal fun squarePixels(region: CropRegion?, width: Int, height: Int): Triple<Int, Int, Int> {
    val chosen = region ?: centerCropRegion(width.toFloat(), height.toFloat())
    val side = minOf(
        (chosen.width * width).toInt(),
        (chosen.height * height).toInt(),
        width,
        height,
    ).coerceAtLeast(1)
    val left = (chosen.left * width).toInt().coerceIn(0, width - side)
    val top = (chosen.top * height).toInt().coerceIn(0, height - side)
    return Triple(left, top, side)
}

/** Telegram profilovou fotku stejně zmenšuje na 640 px; 1280 nechává rezervu na ostrost. */
const val PROFILE_PHOTO_EDGE = 1280
