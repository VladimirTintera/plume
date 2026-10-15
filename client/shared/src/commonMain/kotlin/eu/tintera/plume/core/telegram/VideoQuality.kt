package eu.tintera.plume.core.telegram

import kotlin.math.min

/**
 * Jedna kvalita videa: soubor, který posílal odesílatel, nebo kvalita, kterou z něj server sám
 * přepočítal (`MessageVideo.alternativeVideos`, TDLib `alternativeVideo`).
 *
 * Přepočítané kvality jsou samostatné soubory se samostatným `fileId` — stahují a streamují se
 * stejně jako původní. Server k nim posílá i HLS seznam ([hlsFileId]): kvalitu podle propustnosti
 * podle něj aplikace nemění, ale iOS ho potřebuje ke streamování — přepočítanou kvalitu (fragmentované
 * MP4) AVPlayer přehraje jen jako HLS (`HlsServer`).
 *
 * [codec] je `h264`, `h265`, `av1`… — u původního souboru `null`, TDLib ho nepopisuje (a přehrával
 * se odjakživa). [sizeBytes] je velikost na serveru (`expectedSize`), známá před stažením.
 */
data class VideoQuality(
    val fileId: Int,
    val width: Int,
    val height: Int,
    val codec: String?,
    val sizeBytes: Long,
    val isOriginal: Boolean,
    /** HLS seznam téhle kvality (`alternativeVideo.hlsFile`); u původního souboru `null`. */
    val hlsFileId: Int? = null,
) {
    /**
     * „720p": kratší strana, ne výška. Video na výšku 720 × 1280 je pořád 720p — tak to
     * jmenují přehrávače i Telegram a tak se kvality řadí.
     */
    val lines: Int get() = min(width, height)
}
