package eu.tintera.plume.core.stickers

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Rozměry a obrys samolepky společné všem výrobcům ([StickerMaker] na Androidu i iOS), ať
 * samolepka z telefonu vypadá stejně, ať vznikla kdekoli.
 */

/** Telegram chce u statické samolepky jednu stranu přesně 512 px a druhou nejvýš 512. */
internal const val STICKER_SIDE = 512

/** Tloušťka bílého obrysu v hotové samolepce (px z 512); 12 vedle oficiálního klienta vypadalo tence. */
internal const val OUTLINE_WIDTH = 16

/**
 * Kružnice, po kterých se maska objektu posouvá, když se kreslí obrys: poměr k
 * [OUTLINE_WIDTH] a počet kroků. Jen jedna kružnice by kolem tenkých výběžků nechala mezeru.
 */
private val OUTLINE_RINGS = listOf(1f to 48, 0.66f to 32, 0.33f to 16)

/** Téměř průhledné okraje masky (vyhlazení) se do ořezu ani do obrysu nepočítají. */
internal const val ALPHA_THRESHOLD = 16

/** Obálka sady: čtverec 100 px, nejvýš 32 kB (víc server nevezme). */
internal const val COVER_SIDE = 100
internal const val COVER_MAX_BYTES = 32 * 1024

/** Kvalita WebP: v 512 px s průhledností drží samolepku hluboko pod 512 kB. */
internal const val WEBP_QUALITY = 90

/** O kolik se ubírá kvalita, když obálka přeleze [COVER_MAX_BYTES]. */
internal const val COVER_QUALITY_STEP = 15

/** Rozměry vepsané do čtverce [side]: delší strana přesně [side], poměr stran zůstává. */
internal fun fitInSquare(width: Int, height: Int, side: Int): Pair<Int, Int> {
    val scale = side.toFloat() / maxOf(width, height)
    return (width * scale).roundToInt().coerceIn(1, side) to (height * scale).roundToInt().coerceIn(1, side)
}

/** Delší strana přesně [STICKER_SIDE]. */
internal fun stickerSize(width: Int, height: Int): Pair<Int, Int> = fitInSquare(width, height, STICKER_SIDE)

/**
 * Posuny masky (od levého horního rohu objektu v samolepce s obrysem), ze kterých se skládá
 * obrys: objekt leží o [OUTLINE_WIDTH] od okraje a maska se kolem něj nakreslí na každém posunu.
 */
internal fun outlineOffsets(): List<Pair<Float, Float>> = OUTLINE_RINGS.flatMap { (radius, steps) ->
    (0 until steps).map { step ->
        val angle = 2 * PI * step / steps
        (OUTLINE_WIDTH + radius * OUTLINE_WIDTH * cos(angle).toFloat()) to (OUTLINE_WIDTH + radius * OUTLINE_WIDTH * sin(angle).toFloat())
    }
}
