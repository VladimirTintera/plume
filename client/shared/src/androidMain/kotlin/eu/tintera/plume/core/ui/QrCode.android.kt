package eu.tintera.plume.core.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

actual fun encodeQrCode(content: String): QrModules? = try {
    // Rozměr 0 = modul na bod; škáluje se až při kreslení.
    val bits = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 0),
    )
    trimQrMargin(bits.width, bits.height) { x, y -> bits[x, y] }
} catch (e: WriterException) {
    null
}
