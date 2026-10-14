package eu.tintera.plume.core.calls

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Obraz hovoru na desktopu: poslední snímek naší kamery a protistrany jako bitmapa, kterou
 * kreslí `CallVideo`. Plní ho [DesktopCallMedia] z vlákna knihovny.
 *
 * Android má renderer WebRTC nad OpenGL; tady stačí převést I420 na BGRA v Kotlinu a dát ho
 * Skii — 1280 × 720 při 30 snímcích za vteřinu je pro JVM pár milisekund na snímek.
 */
internal class VideoFrames {

    private val frames = CallVideoSource.entries.associateWith { MutableStateFlow<ImageBitmap?>(null) }

    /** Převodní buffer pro každý zdroj; snímky jednoho zdroje chodí z jednoho vlákna po sobě. */
    private val buffers = CallVideoSource.entries.associateWith { FrameBuffer() }

    fun frames(source: CallVideoSource): StateFlow<ImageBitmap?> = frames.getValue(source).asStateFlow()

    /** Snímek I420 ([i420] o délce aspoň `w·h·1,5`), otočený o [rotation] stupňů. */
    fun push(source: CallVideoSource, i420: ByteArray, width: Int, height: Int, rotation: Int) {
        val buffer = buffers.getValue(source)
        synchronized(buffer) {
            val (outWidth, outHeight) = rotatedSize(width, height, rotation)
            val pixels = buffer.pixels(outWidth * outHeight * 4)
            i420ToBgra(i420, width, height, rotation, pixels)
            // `makeRaster` pixely kopíruje, buffer se tedy smí přepsat dalším snímkem.
            val image = Image.makeRaster(ImageInfo(outWidth, outHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), pixels, outWidth * 4)
            frames.getValue(source).value = image.toComposeImageBitmap()
        }
    }

    /**
     * Snímek BGRA s řádky po [stride] bajtech (náhled z [MacCamera]); [pixels] platí jen
     * během volání, proto se hned kopíruje.
     */
    fun pushBgra(source: CallVideoSource, pixels: ByteBuffer, width: Int, height: Int, stride: Int) {
        val buffer = buffers.getValue(source)
        synchronized(buffer) {
            val rowBytes = width * 4
            val out = buffer.pixels(rowBytes * height)
            for (row in 0 until height) {
                pixels.position(row * stride)
                pixels.get(out, row * rowBytes, rowBytes)
            }
            val image = Image.makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), out, rowBytes)
            frames.getValue(source).value = image.toComposeImageBitmap()
        }
    }

    fun clear(source: CallVideoSource) {
        frames.getValue(source).value = null
    }

    fun clearAll() = CallVideoSource.entries.forEach(::clear)

    private class FrameBuffer {
        private var bytes = ByteArray(0)

        fun pixels(size: Int): ByteArray {
            if (bytes.size != size) bytes = ByteArray(size)
            return bytes
        }
    }
}

internal fun rotatedSize(width: Int, height: Int, rotation: Int): Pair<Int, Int> =
    if (rotation == 90 || rotation == 270) height to width else width to height

/**
 * I420 (plná rovina Y, pak U a V v polovičním rozlišení) na BGRA, rovnou otočené o [rotation]
 * stupňů po směru hodinových ručiček — tak WebRTC rotaci myslí: o kolik snímek otočit, aby
 * stál rovně. Telefon na výšku posílá snímek na šířku s rotací 90 nebo 270.
 *
 * Barvy podle BT.601 v omezeném rozsahu (Y 16–235), jak je posílá WebRTC; celočíselně.
 */
internal fun i420ToBgra(i420: ByteArray, width: Int, height: Int, rotation: Int, out: ByteArray) {
    val chromaWidth = (width + 1) / 2
    val uPlane = width * height
    val vPlane = uPlane + chromaWidth * ((height + 1) / 2)
    val (outWidth, _) = rotatedSize(width, height, rotation)
    for (y in 0 until height) {
        // Kam padne první pixel řádku a o kolik se posouvá každý další (v pixelech výstupu).
        val start: Int
        val step: Int
        when (rotation) {
            90 -> { start = height - 1 - y; step = outWidth }
            180 -> { start = (height - 1 - y) * width + width - 1; step = -1 }
            270 -> { start = (width - 1) * outWidth + y; step = -outWidth }
            else -> { start = y * width; step = 1 }
        }
        val yRow = y * width
        val chromaRow = (y shr 1) * chromaWidth
        var target = start
        for (x in 0 until width) {
            val c = 298 * ((i420[yRow + x].toInt() and 0xFF) - 16)
            val chroma = chromaRow + (x shr 1)
            val d = (i420[uPlane + chroma].toInt() and 0xFF) - 128
            val e = (i420[vPlane + chroma].toInt() and 0xFF) - 128
            val index = target * 4
            out[index] = clamp((c + 516 * d + 128) shr 8)
            out[index + 1] = clamp((c - 100 * d - 208 * e + 128) shr 8)
            out[index + 2] = clamp((c + 409 * e + 128) shr 8)
            out[index + 3] = -1
            target += step
        }
    }
}

private fun clamp(value: Int): Byte = (if (value < 0) 0 else if (value > 255) 255 else value).toByte()
