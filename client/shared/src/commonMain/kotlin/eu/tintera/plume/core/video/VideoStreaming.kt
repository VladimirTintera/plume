package eu.tintera.plume.core.video

/**
 * Leží v MP4 index (`moov`) před daty (`mdat`)? Tedy: dá se soubor přehrávat od začátku, jak
 * se stahuje, bez skoku na konec — to je význam `supportsStreaming` při odesílání.
 *
 * Projde jen hlavičky boxů nejvyšší úrovně (`ftyp`, `free`, `moov`, `mdat`…, po 8 nebo 16
 * bajtech), obsah nečte: i u gigabajtového videa pár čtení. [read] vrátí [count] bajtů od
 * [offset], nebo méně na konci souboru. Rozbitá hlavička, neznámý formát nebo soubor bez obou
 * boxů = `false` (příznak se nepošle, jako dřív).
 *
 * Fragmentované MP4 (`moov` napřed, pak dvojice `moof`/`mdat`) vyjde `true`.
 */
fun mp4IndexBeforeData(size: Long, read: (offset: Long, count: Int) -> ByteArray): Boolean {
    var offset = 0L
    repeat(MAX_TOP_LEVEL_BOXES) {
        if (offset + BOX_HEADER > size) return false
        val header = read(offset, LARGE_BOX_HEADER.toInt().coerceAtMost((size - offset).toInt()))
        if (header.size < BOX_HEADER) return false
        val type = header.decodeToString(4, 8)
        when (type) {
            "moov" -> return true
            "mdat" -> return false
        }
        val boxSize = when (val short = header.uint32(0)) {
            // Box až do konce souboru: za ním už nic není.
            0L -> return false
            1L -> if (header.size < LARGE_BOX_HEADER) return false else header.uint64(8)
            else -> short
        }
        if (boxSize < BOX_HEADER) return false
        offset += boxSize
    }
    return false
}

private const val BOX_HEADER = 8L
private const val LARGE_BOX_HEADER = 16L

/** Pojistka proti nekonečnému čtení rozbitého souboru; skutečné MP4 má boxů nahoře pár. */
private const val MAX_TOP_LEVEL_BOXES = 1024

private fun ByteArray.uint32(at: Int): Long =
    (0 until 4).fold(0L) { value, i -> (value shl 8) or (this[at + i].toLong() and 0xFF) }

private fun ByteArray.uint64(at: Int): Long =
    (0 until 8).fold(0L) { value, i -> (value shl 8) or (this[at + i].toLong() and 0xFF) }
