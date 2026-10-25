package eu.tintera.plume.core.video

import java.io.File
import java.io.RandomAccessFile
import java.util.BitSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Přehrávání rozestavěného souboru na desktopu ([StreamingIo]) nad [FakeDownload], který
 * stahuje jako TDLib: souvisle od jednoho místa, pomalu, a přesune se, když si o to přehrávač
 * řekne. `stream.mp4` (4 s, h264 + AAC, 200 kB, klíčový snímek každou vteřinu) má index (`moov`) na KONCI, takže bez přesunu by se
 * nedalo začít. Musí být velký: skok vpřed kratší než buffer vstupu a 32 kB navíc FFmpeg
 * nepřeskočí, ale dočte (`avio_seek`), a o konec ani o cíl skoku by si neřekl.
 */
class DesktopVideoStreamingTest {

    @Test
    fun decoderReadsGrowingFileWithIndexAtEnd() = withDownload("stream.mp4") { download ->
        FfmpegVideoDecoder.open(FfmpegInput.Streaming(download.source)).use { decoder ->
            var frames = 0
            while (decoder.decodeNext() != null) frames++
            assertEquals(100, frames, "snímků za 4 s při 25 snímcích za vteřinu")
        }
        assertTrue(download.requests.any { it > download.size / 2 }, "o index na konci si neřekl: ${download.requests}")
    }

    @Test
    fun playerStreamsVideoWithSound() = withDownload("stream.mp4") { download ->
        runBlocking {
            val player = VideoPlayer()
            try {
                player.loadStreaming(download.source, loop = false, muted = false)
                player.play()
                awaitUntil(15_000, "konec videa") { player.isEnded || player.isFailed }
                assertEquals(false, player.isFailed, "přehrávání rozestavěného souboru selhalo")
                if (soundOutput()) assertTrue(player.playsSound, "rozestavěné video hraje bez zvuku")
            } finally {
                player.release()
            }
        }
    }

    /** Skok za stažený úsek přesune stahování k cíli a video odtamtud dohraje. */
    @Test
    fun seekBeyondDownloadedMovesDownload() = withDownload("stream.mp4") { download ->
        runBlocking {
            val player = VideoPlayer()
            try {
                player.loadStreaming(download.source, loop = false, muted = true)
                awaitUntil(10_000, "první snímek") { player.image != null || player.isFailed }
                player.seekTo(3000)
                player.play()
                awaitUntil(10_000, "konec videa") { player.isEnded || player.isFailed }
                assertEquals(false, player.isFailed, "skok v rozestavěném souboru selhal")
                // Data kolem 3 s leží zhruba ve třech čtvrtinách souboru, před indexem.
                assertTrue(
                    download.requests.any { it in download.size / 2 until download.size * 9 / 10 },
                    "stahování se ke skoku nepřesunulo: ${download.requests}",
                )
            } finally {
                player.release()
            }
        }
    }

    /** Čtení, které čeká na data, skončí po zrušení hned — ne až po 30 s. */
    @Test
    fun cancelStopsWaitingForData() {
        val stalled = StreamingSource(
            path = { File.createTempFile("stalled", ".mp4").path },
            totalSize = 1_000_000,
            downloaded = { LongRange.EMPTY },
            requestFrom = {},
        )
        val cancelled = AtomicBoolean(false)
        var failure: Throwable? = null
        val opener = thread {
            failure = runCatching { FfmpegVideoDecoder.open(FfmpegInput.Streaming(stalled, cancelled)).close() }.exceptionOrNull()
        }
        Thread.sleep(300)
        val started = System.nanoTime()
        cancelled.set(true)
        opener.join(2_000)
        val waitedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(false, opener.isAlive, "čtení po zrušení dál čeká")
        assertTrue(waitedMs < 1_000, "zrušení trvalo $waitedMs ms")
        assertTrue(failure != null, "otevření bez dat nemělo projít")
    }

    private fun withDownload(name: String, block: (FakeDownload) -> Unit) {
        val data = checkNotNull(javaClass.getResourceAsStream("/video/$name")).readBytes()
        val download = FakeDownload(data)
        try {
            block(download)
        } finally {
            download.close()
        }
    }

    private fun soundOutput(): Boolean {
        val format = AudioFormat(48_000f, 16, 2, true, false)
        return AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, format))
    }

    private suspend fun awaitUntil(timeoutMs: Long, what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Nedočkal jsem se: $what" }
            delay(10)
        }
    }

    /**
     * Stahování jako v TDLib: řídký soubor na disku, který se plní souvisle od [offset]
     * ([BYTES_PER_STEP] za [STEP_MS]) do konce a pak stojí; [StreamingSource.downloaded] je
     * souvislý úsek od něj (i s tím, co už bylo staženo dřív). Přesun
     * ([StreamingSource.requestFrom]) se zapíše.
     */
    private class FakeDownload(private val data: ByteArray) : AutoCloseable {
        val size = data.size.toLong()
        private val file = File.createTempFile("stream", ".mp4")
        private val output = RandomAccessFile(file, "rw")
        private val present = BitSet(data.size)
        private val lock = Object()

        @Volatile private var offset = 0L

        @Volatile private var running = true
        val requests = CopyOnWriteArrayList<Long>()

        val source = StreamingSource(
            path = { file.path },
            totalSize = size,
            downloaded = ::downloaded,
            requestFrom = { from ->
                requests += from
                offset = from
            },
        )

        private val worker = thread(isDaemon = true) {
            while (running) {
                synchronized(lock) {
                    val start = present.nextClearBit(offset.toInt())
                    if (start < data.size) {
                        val end = minOf(start + BYTES_PER_STEP, data.size)
                        output.seek(start.toLong())
                        output.write(data, start, end - start)
                        present.set(start, end)
                    }
                }
                Thread.sleep(STEP_MS)
            }
        }

        private fun downloaded(): LongRange = synchronized(lock) {
            val from = offset.toInt()
            val end = present.nextClearBit(from)
            if (end <= from) LongRange.EMPTY else from.toLong() until end.toLong()
        }

        override fun close() {
            running = false
            worker.join()
            output.close()
            file.delete()
        }

        companion object {
            const val BYTES_PER_STEP = 768
            const val STEP_MS = 10L
        }
    }
}
