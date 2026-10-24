package eu.tintera.plume.core.stickers

import co.touchlab.kermit.Logger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

/**
 * [StickerMaker] na desktopu: totéž co iOS ve Skie (`SkiaStickers.kt`), jen **bez odstranění
 * pozadí** — na desktopu není model segmentace (ML Kit je jen v Play službách, Vision jen na
 * Apple platformách) a přibalit vlastní by aplikaci zvětšilo o desítky MB. Samolepka je celá
 * fotka zmenšená na 512 px.
 *
 * - **Otočení podle EXIF dělá dekodér sám** (`Codec.encodedOrigin`): výběr souborů na desktopu
 *   fotku nepřevádí, takže snímek z fotoaparátu by bez toho skončil na boku.
 */
class DesktopStickerMaker(
    private val directory: File = File(System.getProperty("java.io.tmpdir"), "plume-stickers"),
) : StickerMaker {
    private val logger = Logger.withTag("StickerMaker")

    override val canRemoveBackground: Boolean = false

    override suspend fun make(path: String, removeBackground: Boolean, outline: Boolean): Result<MadeSticker> = runCatching {
        withContext(Dispatchers.Default) {
            val photo = decodeUpright(File(path).readBytes())
            // Obrys patří jen k objektu bez pozadí, a pozadí tu odstranit nejde.
            val sticker = encodeSticker(photo, outline = false)
            MadeSticker(save(sticker.bytes, "sticker.webp"), sticker.width, sticker.height)
        }
    }.onFailure { logger.w(it) { "Samolepku z $path nejde vyrobit." } }

    override suspend fun makeCover(path: String): Result<MadeSticker> = runCatching {
        withContext(Dispatchers.Default) {
            val cover = encodeCover(File(path).readBytes())
            MadeSticker(save(cover.bytes, "cover.webp"), cover.width, cover.height)
        }
    }.onFailure { logger.w(it) { "Obálku sady z $path nejde vyrobit." } }

    private fun save(bytes: ByteArray, name: String): String {
        directory.mkdirs()
        val file = File.createTempFile("sticker-", "-$name", directory)
        file.writeBytes(bytes)
        return file.absolutePath
    }
}

/** Obrázek otočený podle EXIF ([EncodedOrigin]); bez EXIF tak, jak je. */
internal fun decodeUpright(bytes: ByteArray): Image {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val image = Image.makeFromBitmap(codec.readPixels())
    val origin = codec.encodedOrigin
    if (origin == EncodedOrigin.TOP_LEFT || origin == EncodedOrigin.UNUSED) return image
    val (width, height) = if (origin.swapsWidthHeight()) image.height to image.width else image.width to image.height
    val surface = Surface.makeRaster(rgbaInfo(width, height))
    surface.canvas.concat(origin.toMatrix(image.width, image.height))
    surface.canvas.drawImage(image, 0f, 0f)
    return surface.makeImageSnapshot()
}
