package eu.tintera.plume.core.video

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSThread
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import kotlin.concurrent.Volatile

/**
 * Čtení rozestavěného souboru TDLib po rozsazích bajtů s čekáním na data — společné pro oba
 * zdroje AVPlayeru: [StreamingResourceLoader] (progresivní MP4 přes vlastní schéma) a
 * [HlsServer] (přepočítané kvality jako HLS přes místní HTTP).
 *
 * Volá se z vláken obsluhy (čekání blokuje), nikdy z hlavního vlákna.
 */
@OptIn(ExperimentalForeignApi::class)
internal class StreamingReader(val source: StreamingSource) {

    /** Přehrávač zdroj opustil (jiné video, jiná kvalita) — čekající požadavky skončí. */
    @Volatile
    var released = false

    /**
     * Počká, až bude od [offset] co číst, a vrátí počet souvisle dostupných bajtů (nejvýš do
     * [end]); `null` po [READ_TIMEOUT_MS] bez dat, zrušení ([cancelled]) nebo uvolnění.
     *
     * Mimo stažený úsek přesune stahování na [offset]. **Na rozdíl od Androidu nestačí jednou**:
     * AVPlayer posílá víc požadavků najednou (začátek souboru a index na konci) a TDLib stahuje
     * jen od jednoho místa, takže si ho požadavky přetahují. Kdo čeká a stahování jde jinde, než
     * potřebuje, řekne si o ně znovu — nejdřív po [REREQUEST_MS], ať ten druhý stihne kus stáhnout.
     * Proto musí opuštěný požadavek přestat čekat ([cancelled]): jinak by stahování táhl k sobě.
     */
    fun awaitRange(offset: Long, end: Long, cancelled: () -> Boolean = { false }): Long? {
        var waited = 0L
        var sinceRequest = REREQUEST_MS
        while (waited < READ_TIMEOUT_MS && !released && !cancelled()) {
            val range = source.downloaded()
            if (offset in range) return minOf(range.last + 1, end) - offset
            // Stahuje se k nám, jen ještě nedorazilo (úsek končí těsně před [offset]): čekat.
            val headingHere = !range.isEmpty() && range.first <= offset && offset <= range.last + 1
            if (!headingHere && sinceRequest >= REREQUEST_MS) {
                source.requestFrom(offset)
                sinceRequest = 0
            }
            NSThread.sleepForTimeInterval(POLL_MS / 1000.0)
            waited += POLL_MS
            sinceRequest += POLL_MS
        }
        return null
    }

    /** Přečte [length] bajtů od [offset]; smí jen uvnitř úseku z [awaitRange]. */
    fun read(offset: Long, length: Long): NSData? = NSFileHandle.fileHandleForReadingAtPath(source.path())?.let { handle ->
        try {
            handle.seekToFileOffset(offset.toULong())
            handle.readDataOfLength(length.toULong())
        } finally {
            handle.closeFile()
        }
    }
}

/** Kus dat na jednu odpověď; mezi kusy se kontroluje zrušení požadavku. */
internal const val STREAM_CHUNK_BYTES = 256 * 1024

/** Jak dlouho se čeká na data, než se požadavek vzdá (jako na Androidu). */
private const val READ_TIMEOUT_MS = 30_000L

/** Jak často se kouká, jestli data dorazila. */
private const val POLL_MS = 50L

/** Jak dlouho nechat stahování jinému požadavku, než si o ně čekající řekne znovu. */
private const val REREQUEST_MS = 1_000L
