package eu.tintera.plume.core.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** ZXing jako na Androidu; s nulovým okrajem dává rovnou moduly. */
actual fun encodeQrCode(content: String): QrModules? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0),
    )
    QrModules(Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y) } })
}.getOrNull()
