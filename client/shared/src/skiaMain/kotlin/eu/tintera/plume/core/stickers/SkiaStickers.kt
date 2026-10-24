package eu.tintera.plume.core.stickers

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/*
 * Výroba samolepky ve Skie — společná pro iOS a desktop, kde ji Compose přibaluje. Platforma
 * dodá obrázek (fotku, případně už bez pozadí) a uloží výsledek; zmenšení, obrys, obálka
 * a zakódování do WebP jsou tady. WebP na obou umí zakódovat jen Skia: ImageIO na iOS WebP
 * jen čte, `javax.imageio` na desktopu ho nezná vůbec.
 */

/** Hotová samolepka nebo obálka jako WebP, ještě neuložená. */
internal class EncodedSticker(val bytes: ByteArray, val width: Int, val height: Int)

/**
 * Samolepka z [content]: s [outline] bílý obrys po tvaru (jen u objektu bez pozadí — kolem
 * celé fotky by to byl rámeček), jinak jen zmenšení na [STICKER_SIDE].
 */
internal fun encodeSticker(content: Image, outline: Boolean): EncodedSticker {
    val sticker = if (outline) withOutline(content) else scaled(content, stickerSize(content.width, content.height))
    val bytes = sticker.encodeToData(EncodedImageFormat.WEBP, WEBP_QUALITY)?.bytes ?: error("WebP nejde zakódovat.")
    return EncodedSticker(bytes, sticker.width, sticker.height)
}

/** Obálka sady ze samolepky [sticker] (zakódované): vepsaná do čtverce [COVER_SIDE], do [COVER_MAX_BYTES]. */
internal fun encodeCover(sticker: ByteArray): EncodedSticker {
    val image = Image.makeFromEncoded(sticker)
    val (width, height) = fitInSquare(image.width, image.height, COVER_SIDE)
    val surface = Surface.makeRaster(rgbaInfo(COVER_SIDE, COVER_SIDE))
    val left = (COVER_SIDE - width) / 2f
    val top = (COVER_SIDE - height) / 2f
    surface.canvas.drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeXYWH(left, top, width.toFloat(), height.toFloat()),
        SMOOTH,
        null,
        true,
    )
    val cover = surface.makeImageSnapshot()
    // Strop 32 kB: v 100 px ho WebP s kvalitou 90 přeleze jen výjimečně, pak se ubírá.
    var quality = WEBP_QUALITY
    var encoded: ByteArray
    do {
        encoded = cover.encodeToData(EncodedImageFormat.WEBP, quality)?.bytes ?: error("WebP nejde zakódovat.")
        quality -= COVER_QUALITY_STEP
    } while (encoded.size > COVER_MAX_BYTES && quality > 0)
    return EncodedSticker(encoded, COVER_SIDE, COVER_SIDE)
}

/**
 * Vyříznutý objekt s bílým obrysem po svém tvaru — totéž jako na Androidu: objekt se zmenší,
 * aby se s obrysem vešel do [STICKER_SIDE], bílá maska (jen pixely nad [ALPHA_THRESHOLD]) se
 * nakreslí na každém posunu z [outlineOffsets] a objekt přijde navrch.
 */
internal fun withOutline(content: Image): Image {
    val (width, height) = fitInSquare(content.width, content.height, STICKER_SIDE - 2 * OUTLINE_WIDTH)
    val scaled = scaled(content, width to height)
    val mask = whiteMask(scaled)
    val surface = Surface.makeRaster(rgbaInfo(width + 2 * OUTLINE_WIDTH, height + 2 * OUTLINE_WIDTH))
    val source = Rect.makeWH(width.toFloat(), height.toFloat())
    for ((dx, dy) in outlineOffsets()) {
        surface.canvas.drawImageRect(mask, source, Rect.makeXYWH(dx, dy, width.toFloat(), height.toFloat()), SMOOTH, null, true)
    }
    surface.canvas.drawImage(scaled, OUTLINE_WIDTH.toFloat(), OUTLINE_WIDTH.toFloat())
    return surface.makeImageSnapshot()
}

/** Bílá silueta (premultiplied: bílá s alfou `a` je `a,a,a,a`) jen z pixelů, které jsou vidět. */
private fun whiteMask(image: Image): Image {
    val info = rgbaInfo(image.width, image.height)
    val pixels = Bitmap().apply {
        allocPixels(info)
        check(image.readPixels(this)) { "Pixely nejde přečíst." }
    }.readPixels(info, image.width * 4, 0, 0) ?: error("Pixely nejde přečíst.")
    for (index in pixels.indices step 4) {
        val alpha = pixels[index + 3].toInt() and 0xFF
        val value = (if (alpha > ALPHA_THRESHOLD) alpha else 0).toByte()
        pixels[index] = value
        pixels[index + 1] = value
        pixels[index + 2] = value
        pixels[index + 3] = value
    }
    return Image.makeRaster(info, pixels, image.width * 4)
}

internal fun scaled(image: Image, size: Pair<Int, Int>): Image {
    val (width, height) = size
    val surface = Surface.makeRaster(rgbaInfo(width, height))
    surface.canvas.drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeWH(width.toFloat(), height.toFloat()),
        SMOOTH,
        null,
        true,
    )
    return surface.makeImageSnapshot()
}

internal fun rgbaInfo(width: Int, height: Int) = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)

/** Zmenšení přes mipmapy: z velké fotky na 512 by holé lineární vzorkování zubatělo. */
private val SMOOTH = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
