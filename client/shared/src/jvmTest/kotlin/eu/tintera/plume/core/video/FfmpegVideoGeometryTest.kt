package eu.tintera.plume.core.video

import androidx.compose.ui.unit.IntSize
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tvar obrazu na desktopu: poměr pixelu (SAR) a otočení z matice zobrazení. Vzorky jsou
 * z `ffmpeg -f lavfi -i testsrc`: `anamorphic.mp4` 64 × 64 s SAR 9 : 16 (`setsar` a
 * `h264_metadata`), `rotated.mp4` 64 × 32 s `-display_rotation 90` (ffprobe: rotation=90).
 */
class FfmpegVideoGeometryTest {

    @Test
    fun anamorphicVideoIsShownPortrait() = withDecoder("anamorphic.mp4") { decoder ->
        assertEquals(0, decoder.rotation)
        assertEquals(9f / 16f, assertNotNull(decoder.displayAspectRatio), 0.001f)
        // Pixely obrázku jsou čtvercové: šířka stažená na 9/16, výška beze změny.
        decoder.toImage(IntSize.Zero).use { image ->
            assertEquals(36, image.width)
            assertEquals(64, image.height)
        }
    }

    @Test
    fun rotatedVideoIsTurnedWhenDrawn() = withDecoder("rotated.mp4") { decoder ->
        // 90° proti směru hodinových ručiček = 270° po směru.
        assertEquals(270, decoder.rotation)
        assertEquals(0.5f, assertNotNull(decoder.displayAspectRatio), 0.001f)
        // Obrázek zůstává neotočený (otáčí se při kreslení) a do plochy se vepisuje otočený:
        // plocha 16 × 100 na výšku pojme video 64 × 32 otočené na 32 × 64 v polovině.
        decoder.toImage(IntSize.Zero).use { image ->
            assertEquals(64, image.width)
            assertEquals(32, image.height)
        }
        decoder.toImage(IntSize(16, 100)).use { image ->
            assertEquals(32, image.width)
            assertEquals(16, image.height)
        }
    }

    @Test
    fun clockwiseRotationFromFfmpegAngle() {
        assertEquals(0, clockwiseRotation(Double.NaN))
        assertEquals(0, clockwiseRotation(0.0))
        assertEquals(0, clockwiseRotation(-0.0))
        // Telefon na výšku: matice otáčí o 90° po směru, FFmpeg hlásí −90.
        assertEquals(90, clockwiseRotation(-90.0))
        assertEquals(270, clockwiseRotation(90.0))
        assertEquals(180, clockwiseRotation(180.0))
        assertEquals(180, clockwiseRotation(-180.0))
        assertEquals(90, clockwiseRotation(-89.6))
    }

    @Test
    fun displayAspectRatioCombinesPixelsAndRotation() {
        assertEquals(9f / 16f, assertNotNull(displayAspectRatio(720, 720, 9f / 16f, 0)), 0.001f)
        assertEquals(9f / 16f, assertNotNull(displayAspectRatio(1920, 1080, 1f, 90)), 0.001f)
        assertEquals(16f / 9f, assertNotNull(displayAspectRatio(1920, 1080, 1f, 180)), 0.001f)
        assertNull(displayAspectRatio(0, 720, 1f, 0))
    }

    @Test
    fun squarePixelWidthNeverCollapses() {
        assertEquals(405, squarePixelWidth(720, 9f / 16f))
        assertEquals(1, squarePixelWidth(1, 0.1f))
    }

    private fun withDecoder(name: String, block: (FfmpegVideoDecoder) -> Unit) {
        val file = File.createTempFile("geometry", ".mp4")
        file.writeBytes(checkNotNull(javaClass.getResourceAsStream("/video/$name")).readBytes())
        try {
            FfmpegVideoDecoder.open(file.path).use { decoder ->
                checkNotNull(decoder.decodeNext()) { "žádný snímek" }
                block(decoder)
            }
        } finally {
            file.delete()
        }
    }
}
