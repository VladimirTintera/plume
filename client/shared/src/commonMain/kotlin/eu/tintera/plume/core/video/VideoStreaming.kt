package eu.tintera.plume.core.video

/**
 * Zkusit u původního souboru streamování z rozestavěného souboru?
 *
 * **Příznak `supportsStreaming` nestačí jako podmínka.** Říká jen, co tvrdí odesílatel (bot
 * s příznakem poslal video s `moov` na konci), a naopak ho nemá spousta MP4, které streamovat
 * jde — třeba video z foťáku, poslané bez přebalení. Přehrávače na všech platformách umí MP4
 * i s indexem na konci: o konec souboru si řeknou a [StreamingSource.requestFrom] tam přesune
 * stahování. MP4 a MOV se proto streamují i bez příznaku; když stream selže dřív, než se video
 * rozběhne, čeká `VideoPage` na celý soubor (viz „Video bez příznaku supportsStreaming"
 * v docs/poznatky/prohlizec-medii.md).
 *
 * Jiné kontejnery (WebM, MKV, AVI…) jen s příznakem: tam se rozestavěný soubor neověřoval.
 */
fun streamOriginalVideo(supportsStreaming: Boolean, mimeType: String): Boolean =
    supportsStreaming || mimeType.substringBefore(';').trim().lowercase() in MP4_FAMILY

/** MP4 a QuickTime (týž formát boxů), jak je TDLib hlásí v `Video.mimeType` / `Document.mimeType`. */
private val MP4_FAMILY = setOf("video/mp4", "video/quicktime", "video/x-m4v")

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
