package eu.tintera.plume.core.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.ByteArrayInputStream
import java.util.zip.Adler32
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** PNG s QR kódem musí přečíst skutečný dekodér — kontroluje ho ImageIO, ne vlastní parser. */
class QrPngTest {

    private val qr = QrModules(
        arrayOf(
            booleanArrayOf(true, false, true),
            booleanArrayOf(false, true, false),
            booleanArrayOf(true, true, false),
        ),
    )

    @Test
    fun dekoderPrectePixelyModulu() {
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(qrPng(qr, moduleSize = 10, quietZone = 4))))
        assertEquals((3 + 8) * 10, image.width)
        assertEquals(image.width, image.height)
        fun dark(moduleX: Int, moduleY: Int): Boolean {
            val rgb = image.getRGB((moduleX + 4) * 10 + 5, (moduleY + 4) * 10 + 5) and 0xFFFFFF
            return rgb == 0
        }
        for (y in 0 until 3) for (x in 0 until 3) assertEquals(qr.modules[y][x], dark(x, y), "modul $x,$y")
        // Klidová zóna je bílá.
        assertEquals(0xFFFFFF, image.getRGB(2, 2) and 0xFFFFFF)
    }

    @Test
    fun velkyObrazekVicBloku() {
        // Přes 65 535 bajtů dat: deflate musí mít víc uložených bloků.
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(qrPng(qr, moduleSize = 200, quietZone = 4))))
        assertEquals(2200, image.width)
        assertEquals(0, image.getRGB(4 * 200 + 100, 4 * 200 + 100) and 0xFFFFFF)
    }

    @Test
    fun soucetSedisJava() {
        val data = "Plume QR platba".encodeToByteArray() + ByteArray(70_000) { it.toByte() }
        assertEquals(CRC32().apply { update(data) }.value.toInt(), crc32(data))
        assertEquals(Adler32().apply { update(data) }.value.toInt(), adler32(data))
    }

    @Test
    fun cteckaPrectePlatbuZObrazku() {
        // Celá cesta jako při sdílení: text platby → QR → PNG → obrázek → čtečka QR.
        val spayd = "SPD*1.0*ACC:CZ6508000000192000145399*AM:500.00*CC:CZK*X-VS:2026"
        val png = qrPng(assertNotNull(encodeQrCode(spayd)))
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(png)))
        val pixels = IntArray(image.width * image.height).also { image.getRGB(0, 0, image.width, image.height, it, 0, image.width) }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        assertEquals(spayd, QRCodeReader().decode(bitmap).text)
    }
}
