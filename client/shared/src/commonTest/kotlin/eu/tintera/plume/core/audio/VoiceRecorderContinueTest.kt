package eu.tintera.plume.core.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Navázání nahrávání po poslechu náhledu ([VoiceRecorder.start] s `continuing`).
 *
 * Na skutečném čase (`runBlocking`): [VoiceRecorder] měří délku monotónními hodinami, ne
 * plánovačem korutin. Nahrávač je podvržený a „nahrává" Ogg s pevným počtem paketů.
 *
 * Nahrávač běží v rozsahu `runBlocking` (`this`), tedy na TÉMŽ vlákně jako test — stejně jako
 * v aplikaci na `viewModelScope`. [VoiceRecorder] není vláknově bezpečný: s `Dispatchers.Default`
 * tik úrovní, který už prošel `delay`, po `finish` zapsal stav zpátky, další `start` ho viděl jako
 * běžící nahrávání, nic nespustil a druhé `finish` vrátilo `null`.
 */
class VoiceRecorderContinueTest {
    private val fileSystem = FileSystem.SYSTEM
    private val folder: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "voice-continue-${Random.nextLong()}"

    @AfterTest
    fun cleanUp() {
        fileSystem.deleteRecursively(folder, mustExist = false)
    }

    /** Každé nahrávání je nový soubor se [packets] pakety po 20 ms, jako úsek z mikrofonu. */
    private inner class FakeCapture(private val packets: Int) : MediaCapture {
        var current: Path? = null
        private var counter = 0

        override fun start() {
            fileSystem.createDirectories(folder)
            current = folder / "voice-${counter++}.ogg"
        }

        override val level: Float get() = 0.5f

        override suspend fun stop(): String {
            val path = current ?: error("Nic se nenahrává.")
            val audio = List(packets) { byteArrayOf(0xF8.toByte(), it.toByte()) }
            fileSystem.write(path) { write(oggOpusFile(audio, channels = 1, preSkip = 312, samplesPerPacket = 960)) }
            current = null
            return path.toString()
        }

        override fun cancel() {
            current?.let { fileSystem.delete(it, mustExist = false) }
            current = null
        }

        override fun pause() = Unit

        override fun resume() = Unit
    }

    private fun packetsIn(path: String): Int =
        parseOggOpus(fileSystem.read(path.toPath()) { readByteArray() }).packets.size

    @Test
    fun navazanyUsekSeSpojiSPuvodniNahravkou() = runBlocking {
        val recorder = VoiceRecorder(FakeCapture(packets = 30), this)
        recorder.start().getOrThrow()
        delay(1_100)
        val first = assertNotNull(recorder.finish())
        assertTrue(recorder.canContinue(first))

        recorder.start(continuing = first).getOrThrow()
        // Čas pokračuje od konce původní nahrávky.
        assertTrue(recorder.state.value!!.elapsedMs >= first.durationMs)
        delay(500)
        val joined = assertNotNull(recorder.finish())

        assertEquals(60, packetsIn(joined.path))
        assertFalse(fileSystem.exists(first.path.toPath()))
        assertTrue(joined.durationMs >= first.durationMs + 500)
        // Waveform se počítá ze všech úrovní, ne jen z dovětku.
        assertTrue(joined.levels.size > first.levels.size)
    }

    @Test
    fun prilisKratkyDovetekNechaPuvodniNahravku() = runBlocking {
        val recorder = VoiceRecorder(FakeCapture(packets = 30), this)
        recorder.start().getOrThrow()
        delay(1_100)
        val first = assertNotNull(recorder.finish())

        recorder.start(continuing = first).getOrThrow()
        val result = recorder.finish()

        assertSame(first, result)
        assertEquals(30, packetsIn(first.path))
    }

    @Test
    fun kosZahodiIPuvodniNahravku() = runBlocking {
        val recorder = VoiceRecorder(FakeCapture(packets = 30), this)
        recorder.start().getOrThrow()
        delay(1_100)
        val first = assertNotNull(recorder.finish())

        recorder.start(continuing = first).getOrThrow()
        recorder.cancel()

        assertFalse(fileSystem.exists(first.path.toPath()))
    }

    @Test
    fun naM4aNavazatNejde() = runBlocking {
        val recorder = VoiceRecorder(FakeCapture(packets = 30), this)
        assertFalse(recorder.canContinue(RecordedVoice(path = "voice.m4a", durationSeconds = 3, waveform = ByteArray(0))))
    }

    @Test
    fun nahravacSpojujeUsekyPoSvem() = runBlocking {
        // Kamera spojuje MP4 sama — VoiceRecorder jen volá její join a bere jeho výsledek.
        val capture = object : MediaCapture by FakeCapture(packets = 30) {
            var joined: Pair<String, String>? = null
            override fun canJoin(path: String) = path.endsWith(".ogg")
            override suspend fun join(previous: String, next: String): String {
                joined = previous to next
                return (folder / "joined.ogg").toString()
            }
        }
        val recorder = VoiceRecorder(capture, this)
        recorder.start().getOrThrow()
        delay(1_100)
        val first = assertNotNull(recorder.finish())
        recorder.start(continuing = first).getOrThrow()
        delay(500)
        val result = assertNotNull(recorder.finish())

        assertEquals(first.path, capture.joined?.first)
        assertTrue(result.path.endsWith("joined.ogg"))
    }

    @Test
    fun selhaneSpojeniNechaPuvodniNahravku() = runBlocking {
        val capture = object : MediaCapture by FakeCapture(packets = 30) {
            override fun canJoin(path: String) = true
            override suspend fun join(previous: String, next: String): String = error("Kodér selhal.")
        }
        val recorder = VoiceRecorder(capture, this)
        recorder.start().getOrThrow()
        delay(1_100)
        val first = assertNotNull(recorder.finish())
        recorder.start(continuing = first).getOrThrow()
        delay(500)

        assertSame(first, recorder.finish())
        assertTrue(fileSystem.exists(first.path.toPath()))
    }
}
