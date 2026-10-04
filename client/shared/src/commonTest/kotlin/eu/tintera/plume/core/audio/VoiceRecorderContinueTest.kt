package eu.tintera.plume.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 */
class VoiceRecorderContinueTest {
    private val fileSystem = FileSystem.SYSTEM
    private val folder: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "voice-continue-${Random.nextLong()}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
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
        val recorder = VoiceRecorder(FakeCapture(packets = 30), scope)
        recorder.start().getOrThrow()
        delay(1_100)
        val first = assertNotNull(recorder.finish())
        assertTrue(first.canContinue)

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
        val recorder = VoiceRecorder(FakeCapture(packets = 30), scope)
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
        val recorder = VoiceRecorder(FakeCapture(packets = 30), scope)
        recorder.start().getOrThrow()
        delay(1_100)
        val first = assertNotNull(recorder.finish())

        recorder.start(continuing = first).getOrThrow()
        recorder.cancel()

        assertFalse(fileSystem.exists(first.path.toPath()))
    }

    @Test
    fun naM4aNavazatNejde() {
        assertFalse(RecordedVoice(path = "voice.m4a", durationSeconds = 3, waveform = ByteArray(0)).canContinue)
    }
}
