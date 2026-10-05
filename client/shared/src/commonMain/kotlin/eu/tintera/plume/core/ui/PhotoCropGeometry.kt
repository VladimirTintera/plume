package eu.tintera.plume.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/**
 * Výřez fotky jako podíly jejích rozměrů (0..1): kde začíná a jak je široký a vysoký. Podíly,
 * ne pixely — obrazovka zná fotku zmenšenou, platforma ji ořezává v plné velikosti a poměr
 * stran mají obě stejný.
 */
data class CropRegion(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Jak stojí fotka pod kruhem ořezu: [scale] je přiblížení nad výchozí velikostí (1 = kratší
 * strana fotky vyplní kruh), [offset] posun středu fotky od středu kruhu v pixelech obrazovky.
 */
data class CropPlacement(val scale: Float = 1f, val offset: Offset = Offset.Zero)

/** Víc přiblížit nedává smysl — z výřezu by zbylo pár set pixelů. */
const val MAX_CROP_SCALE = 5f

/**
 * Ořez profilovky: kruh o průměru [circle] uprostřed plochy, fotka [image] pod ním.
 * Fotka musí kruh vždycky celý zakrývat — výřez nesmí sahat mimo ni.
 */
class PhotoCropGeometry(private val image: Size, private val circle: Float) {

    /** Velikost fotky na obrazovce při přiblížení 1: kratší strana = průměr kruhu. */
    private val baseScale: Float = circle / min(image.width, image.height)

    fun displayedSize(scale: Float): Size =
        Size(image.width * baseScale * scale, image.height * baseScale * scale)

    /**
     * Posun a přiblížení gestem kolem bodu [centroid] (vůči středu kruhu): bod pod prsty zůstane
     * na místě, jinak by fotka při štípnutí ujížděla. Výsledek je sevřený tak, aby fotka kruh
     * zakrývala.
     */
    fun transformed(placement: CropPlacement, centroid: Offset, pan: Offset, zoom: Float): CropPlacement {
        val scale = (placement.scale * zoom).coerceIn(1f, MAX_CROP_SCALE)
        val applied = scale / placement.scale
        val offset = (placement.offset - centroid) * applied + centroid + pan
        return clamped(CropPlacement(scale, offset))
    }

    /** Posun sevřený tak, aby okraj fotky nikde nevjel do kruhu. */
    fun clamped(placement: CropPlacement): CropPlacement {
        val scale = placement.scale.coerceIn(1f, MAX_CROP_SCALE)
        val shown = displayedSize(scale)
        val maxX = max(0f, (shown.width - circle) / 2)
        val maxY = max(0f, (shown.height - circle) / 2)
        return CropPlacement(
            scale = scale,
            offset = Offset(placement.offset.x.coerceIn(-maxX, maxX), placement.offset.y.coerceIn(-maxY, maxY)),
        )
    }

    /** Co z fotky leží v kruhu, jako podíly jejích rozměrů. */
    fun region(placement: CropPlacement): CropRegion {
        val clamped = clamped(placement)
        val shown = displayedSize(clamped.scale)
        // Levý horní roh kruhu v souřadnicích zobrazené fotky (její levý horní roh = 0).
        val left = (shown.width - circle) / 2 - clamped.offset.x
        val top = (shown.height - circle) / 2 - clamped.offset.y
        val width = (circle / shown.width).coerceIn(0f, 1f)
        val height = (circle / shown.height).coerceIn(0f, 1f)
        return CropRegion(
            left = (left / shown.width).coerceIn(0f, 1f - width),
            top = (top / shown.height).coerceIn(0f, 1f - height),
            width = width,
            height = height,
        )
    }
}

/** Výřez ze středu — dřívější výchozí ořez, a co zbyde, když fotka nemá známé rozměry. */
fun centerCropRegion(width: Float, height: Float): CropRegion {
    val side = min(width, height)
    if (side <= 0f) return CropRegion(0f, 0f, 1f, 1f)
    return CropRegion(
        left = (width - side) / 2 / width,
        top = (height - side) / 2 / height,
        width = side / width,
        height = side / height,
    )
}
