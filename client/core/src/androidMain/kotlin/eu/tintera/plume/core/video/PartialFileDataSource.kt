package eu.tintera.plume.core.video

import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.RandomAccessFile

// V `core`: zdroj pro ExoPlayer nad souborem TDLib používá telefon (`VideoPlayer.android.kt`
// ve `shared`) i televize (`:client:tvApp`). Tahá jen `media3-datasource`, ne celý přehrávač.

/**
 * Čtení rozestavěného souboru TDLib.
 *
 * Soubor na disku roste, ale číst se z něj smí jen po `available()`. Když přehrávač chce data,
 * která ještě nedorazila, **čeká** — `FileDataSource` by místo toho ohlásil konec vstupu
 * a přehrávání by skončilo uprostřed.
 *
 * Čekání blokuje vlákno ExoPlayeru, a tak to má být: je to jeho vlastní vlákno pro načítání.
 * Po [READ_TIMEOUT_MS] bez nových dat se vzdá — stahování mohlo spadnout a věčné čekání by
 * vypadalo jako zamrzlý přehrávač.
 */
class PartialFileDataSource(private val source: StreamingSource) : BaseDataSource(/* isNetwork = */ false) {

    private var file: RandomAccessFile? = null
    private var uri: android.net.Uri? = null
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        // Cesta se čte při KAŽDÉM otevření: dostažený soubor TDLib přesune z `temp/` jinam.
        val opened = RandomAccessFile(source.path(), "r")
        opened.seek(dataSpec.position)
        file = opened
        position = dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            (source.totalSize - dataSpec.position).coerceAtLeast(0)
        } else {
            dataSpec.length
        }
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT

        val ready = awaitBytes()
        if (ready <= 0) throw IOException("Data se nestihla stáhnout (pozice $position).")

        val toRead = minOf(length.toLong(), remaining, ready).toInt()
        val read = file?.read(buffer, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        if (read <= 0) return C.RESULT_END_OF_INPUT

        position += read
        remaining -= read
        bytesTransferred(read)
        return read
    }

    /**
     * Počká, až bude na [position] co číst, a vrátí počet souvisle dostupných bajtů.
     *
     * Když pozice leží mimo stažený úsek, **přesune stahování na ni** — jinak by se čekalo na
     * data, která TDLib vůbec nestahuje. Požádá se jen jednou za skok: `downloadFile` s týmž
     * offsetem sice nevadí, ale volat ho dvacetkrát za vteřinu je zbytečné. A vůbec ne, když
     * čtení jen dohnalo konec staženého úseku ([downloadHeadsTo]) — tam se stahuje i bez žádosti.
     */
    private fun awaitBytes(): Long {
        var waited = 0L
        var requested = false
        while (waited < READ_TIMEOUT_MS) {
            val range = source.downloaded()
            if (position in range) return range.last - position + 1

            if (!requested && !downloadHeadsTo(range, position)) {
                source.requestFrom(position)
                requested = true
            }
            Thread.sleep(POLL_MS)
            waited += POLL_MS
        }
        return 0
    }

    override fun getUri(): android.net.Uri? = uri

    override fun close() {
        file?.close()
        file = null
        uri?.let { transferEnded() }
        uri = null
    }
}

/** Jak dlouho se čeká na data, než se přehrávání vzdá. */
private const val READ_TIMEOUT_MS = 30_000L

/** Jak často se kouká, jestli data dorazila. */
private const val POLL_MS = 50L
