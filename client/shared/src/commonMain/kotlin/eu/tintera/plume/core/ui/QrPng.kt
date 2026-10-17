package eu.tintera.plume.core.ui

/**
 * QR kód jako PNG: černé moduly na bílém, klidová zóna [quietZone] modulů (standard chce čtyři),
 * každý modul [moduleSize] × [moduleSize] pixelů. Pro sdílení QR kódu jako obrázku — QR platbu
 * z obrazovky telefonu týž telefon nenaskenuje, ale bankovní aplikace umí QR z obrázku.
 *
 * **Vlastní zápis PNG, bez komprese.** Ve společném kódu kodér obrázků ani deflate není (okio ho
 * na všech platformách nemá) a QR kód je černobílý: obrázek 1 bit na pixel s nekomprimovanými
 * bloky deflate má pár desítek kB a čte ho každý prohlížeč. Formát: signatura, IHDR (šedá,
 * hloubka 1), IDAT (zlib s uloženými bloky), IEND; kontrolní součty CRC-32 a Adler-32.
 */
fun qrPng(qr: QrModules, moduleSize: Int = 12, quietZone: Int = 4): ByteArray {
    val side = (qr.size + 2 * quietZone) * moduleSize
    val rowBytes = (side + 7) / 8
    // Řádek: bajt filtru (0 = žádný) a pixely po osmi v bajtu, nejvyšší bit vlevo; 1 = bílá.
    val raw = ByteArray(side * (rowBytes + 1))
    for (y in 0 until side) {
        val rowStart = y * (rowBytes + 1)
        val moduleY = y / moduleSize - quietZone
        for (x in 0 until side) {
            val moduleX = x / moduleSize - quietZone
            val dark = moduleY in 0 until qr.size && moduleX in 0 until qr.size && qr.modules[moduleY][moduleX]
            if (!dark) {
                val index = rowStart + 1 + x / 8
                raw[index] = (raw[index].toInt() or (0x80 ushr (x % 8))).toByte()
            }
        }
    }
    val out = ByteBuilder()
    out.bytes(PNG_SIGNATURE)
    out.chunk("IHDR", ByteBuilder().apply {
        int(side)
        int(side)
        byte(1) // hloubka 1 bit
        byte(0) // šedá
        byte(0) // komprese deflate
        byte(0) // filtrování standardní
        byte(0) // bez prokládání
    }.toByteArray())
    out.chunk("IDAT", zlibStored(raw))
    out.chunk("IEND", ByteArray(0))
    return out.toByteArray()
}

/** Zlib s nekomprimovanými („uloženými") bloky deflate po nejvýš 65 535 bajtech. */
private fun zlibStored(data: ByteArray): ByteArray {
    val out = ByteBuilder()
    out.byte(0x78)
    out.byte(0x01)
    var offset = 0
    do {
        val length = minOf(MAX_STORED_BLOCK, data.size - offset)
        val last = offset + length >= data.size
        out.byte(if (last) 1 else 0)
        out.byte(length and 0xFF)
        out.byte(length ushr 8)
        out.byte(length.inv() and 0xFF)
        out.byte((length.inv() ushr 8) and 0xFF)
        out.bytes(data, offset, length)
        offset += length
    } while (!last)
    out.int(adler32(data))
    return out.toByteArray()
}

private fun ByteBuilder.chunk(type: String, data: ByteArray) {
    val typeBytes = type.encodeToByteArray()
    int(data.size)
    bytes(typeBytes)
    bytes(data)
    int(crc32(typeBytes + data))
}

internal fun crc32(data: ByteArray): Int {
    var crc = 0xFFFFFFFF.toInt()
    for (b in data) crc = CRC_TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
    return crc.inv()
}

internal fun adler32(data: ByteArray): Int {
    var a = 1
    var b = 0
    for (byte in data) {
        a = (a + (byte.toInt() and 0xFF)) % ADLER_MOD
        b = (b + a) % ADLER_MOD
    }
    return (b shl 16) or a
}

private val CRC_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else c ushr 1 }
    c
}

/** Rostoucí pole bajtů; `ByteArrayOutputStream` v commonMain není. */
private class ByteBuilder {
    private var buffer = ByteArray(1024)
    private var size = 0

    fun byte(value: Int) {
        ensure(1)
        buffer[size++] = value.toByte()
    }

    fun int(value: Int) {
        byte(value ushr 24)
        byte(value ushr 16)
        byte(value ushr 8)
        byte(value)
    }

    fun bytes(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        ensure(length)
        data.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private const val MAX_STORED_BLOCK = 65_535
private const val ADLER_MOD = 65_521
