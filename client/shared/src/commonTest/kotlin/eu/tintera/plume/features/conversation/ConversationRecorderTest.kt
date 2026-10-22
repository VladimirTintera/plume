package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.audio.MIN_DURATION_MS
import eu.tintera.plume.core.audio.MediaCapture
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayerControl
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.audio.oggOpusFile
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.LiveUpload
import eu.tintera.plume.core.telegram.LiveUploads
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.StringResource
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Nahrávání hlasovky v konverzaci ([ConversationRecorder]).
 *
 * Na skutečném čase (`runBlocking`), jako `VoiceRecorderContinueTest`: [VoiceRecorder] měří
 * délku monotónními hodinami, ne plánovačem korutin. Mikrofon je podvržený a „nahrává" Ogg
 * s pevným počtem paketů.
 */
class ConversationRecorderTest {
    private val fileSystem = FileSystem.SYSTEM
    private val folder: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "conversation-recorder-${Random.nextLong()}"

    @AfterTest
    fun cleanUp() {
        fileSystem.deleteRecursively(folder, mustExist = false)
    }

    private inner class FakeCapture : MediaCapture {
        var current: Path? = null
        val calls = mutableListOf<String>()
        private var counter = 0

        override fun start() {
            fileSystem.createDirectories(folder)
            current = folder / "voice-${counter++}.ogg"
            calls += "start"
        }

        override val level: Float get() = 0.5f

        override suspend fun stop(): String {
            val path = current ?: error("Nic se nenahrává.")
            val audio = List(60) { byteArrayOf(0xF8.toByte(), it.toByte()) }
            fileSystem.write(path) { write(oggOpusFile(audio, channels = 1, preSkip = 312, samplesPerPacket = 960)) }
            current = null
            return path.toString()
        }

        override fun cancel() {
            current?.let { fileSystem.delete(it, mustExist = false) }
            current = null
            calls += "cancel"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun resume() {
            calls += "resume"
        }

        /** Jako Android Ogg/Opus: soubor, do kterého se nahrává, je i výsledek. */
        var live = false
        override val livePath: String? get() = current?.takeIf { live }?.toString()
    }

    /** Předběžné nahrávání: zapisuje, co se s ním dělo, a [complete] vrací [fileId]. */
    private class FakeLiveUploads : LiveUploads {
        val events = mutableListOf<String>()
        var fileId: Int? = 77

        override fun startVoiceNote(path: String): LiveUpload = start(path)

        override fun startVideoNote(path: String): LiveUpload = start(path)

        private fun start(path: String): LiveUpload {
            events += "start"
            return object : LiveUpload {
                override suspend fun complete(finalPath: String): Int? {
                    events += "complete"
                    return fileId.takeIf { finalPath == path }
                }

                override fun abandon() {
                    events += "abandon"
                }
            }
        }

        override fun discard(fileId: Int) {
            events += "discard $fileId"
        }
    }

    private class FakePlayer : VoicePlayerControl {
        override val state = MutableStateFlow<VoicePlayback?>(null)
        override val speed: StateFlow<VoiceSpeed> = MutableStateFlow(VoiceSpeed.entries.first())
        var stops = 0
        override fun toggle(key: VoiceKey, music: Boolean, resumeKey: String?, path: suspend () -> String) = Unit
        override fun play(
            key: VoiceKey,
            music: Boolean,
            startMs: Long,
            resumeKey: String?,
            onFinished: (() -> Unit)?,
            path: suspend () -> String,
        ) = Unit
        override fun seek(key: VoiceKey, fraction: Float) = Unit
        override fun stop(chatId: Long?, keepMusic: Boolean) {
            stops++
        }
        override fun pause() = Unit
        override fun resume() = Unit
    }

    private class FakeRepository : UnimplementedConversationRepository() {
        val voiceNotes = mutableListOf<Pair<String, Int>>()
        val uploadedIds = mutableListOf<Int?>()

        override suspend fun sendVoiceNote(
            chatId: Long,
            path: String,
            durationSeconds: Int,
            waveform: ByteArray,
            replyToMessageId: Long?,
            quote: ReplyQuote?,
            options: SendOptions,
            uploadedFileId: Int?,
        ): Result<ChatMessage> {
            voiceNotes += path to durationSeconds
            uploadedIds += uploadedFileId
            return Result.success(testMessage(600, outgoing = true))
        }
    }

    private val capture = FakeCapture()
    private val player = FakePlayer()
    private val repository = FakeRepository()
    private var readAfterOwn = 0
    private val liveUploads = FakeLiveUploads()

    /** Jedno nahrávání: části se dá rozsah, který se po testu zruší (v `init` drží nekonečný odběr). */
    private fun recording(
        state: MutableStateFlow<ConversationState> = testState(),
        test: suspend CoroutineScope.(ConversationRecorder, MutableStateFlow<ConversationState>) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val recorder = ConversationRecorder(
            chatId = TEST_CHAT,
            state = state,
            scope = scope,
            appScope = scope,
            repository = repository,
            settings = object : UnimplementedAppSettings() {},
            voicePlayer = player,
            audioRecorder = capture,
            media = object : MediaSender {
                override fun sendMedia(
                    failureMessage: StringResource,
                    scheduled: Boolean,
                    send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
                ) {
                    scope.launch { send(null, null) }
                }
            },
            readAfterOwnMessage = { readAfterOwn++ },
            logger = testLogger,
            liveUploads = liveUploads,
        )
        try {
            test(recorder, state)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun kdeHlasovkyNejsouDovoleneNahravaniNezacne() = recording(
        MutableStateFlow(ConversationState(info = testChatInfo().copy(content = ContentAccess.All.copy(voiceNotes = false)))),
    ) { recorder, state ->
        recorder.startRecording()

        assertNull(recorder.voiceRecording.value)
        assertNotNull(state.value.notice)
        assertTrue(capture.calls.isEmpty())
    }

    @Test
    fun nahravaniUmlciHrajiciZvuk() = recording { recorder, state ->
        state.value = state.value.copy(soundingVideoNote = 5)

        recorder.startRecording()

        assertNotNull(recorder.voiceRecording.value)
        assertEquals(1, player.stops, "jinak by se nahrál zvuk z reproduktoru")
        assertNull(state.value.soundingVideoNote)
        recorder.cancelRecording()
    }

    @Test
    fun zrusenaNahravkaNicNeposleASmazeSe() = recording { recorder, _ ->
        recorder.startRecording()
        val path = assertNotNull(capture.current)

        recorder.cancelRecording()

        assertNull(recorder.voiceRecording.value)
        assertFalse(fileSystem.exists(path))
        assertTrue(repository.voiceNotes.isEmpty())
    }

    @Test
    fun prilisKratkaNahravkaSeNeposleAOhlasiSe() = recording { recorder, state ->
        recorder.startRecording()

        recorder.sendRecording()
        delay(200)

        assertTrue(repository.voiceNotes.isEmpty())
        assertNotNull(state.value.notice)
        assertNull(state.value.error, "krátká nahrávka je omyl, ne chyba")
    }

    @Test
    fun delsiNahravkaOdejde() = recording { recorder, _ ->
        recorder.startRecording()
        delay(MIN_DURATION_MS + 100)

        recorder.sendRecording()
        delay(300)

        assertEquals(1, repository.voiceNotes.size)
        assertEquals(1, readAfterOwn)
        assertNull(recorder.voiceRecording.value)
    }

    @Test
    fun nahledSeDaPoslechnoutAPotomOdeslat() = recording { recorder, _ ->
        recorder.startRecording()
        delay(MIN_DURATION_MS + 100)

        recorder.stopRecordingForPreview()
        delay(300)
        val preview = assertNotNull(recorder.voicePreview.value)
        assertNotNull(preview.voice)
        assertTrue(repository.voiceNotes.isEmpty(), "náhled se sám neodešle")

        recorder.sendRecording()
        delay(300)
        assertEquals(1, repository.voiceNotes.size)
        assertNull(recorder.voicePreview.value)
    }

    @Test
    fun kosUNahleduSmazeSoubor() = recording { recorder, _ ->
        recorder.startRecording()
        delay(MIN_DURATION_MS + 100)
        recorder.stopRecordingForPreview()
        delay(300)
        val path = assertNotNull(recorder.voicePreview.value?.voice).path.toPath()

        recorder.discardVoicePreview()

        assertNull(recorder.voicePreview.value)
        assertFalse(fileSystem.exists(path))
        assertTrue(repository.voiceNotes.isEmpty())
    }

    @Test
    fun nabidkaNadHlasovkouNahravaniPozastavi() = recording { recorder, _ ->
        recorder.startRecording()

        recorder.pauseRecording()
        recorder.resumeRecording()

        assertEquals(listOf("start", "pause", "resume"), capture.calls)
        recorder.cancelRecording()
    }

    @Test
    fun odchodZChatuZahodiNahravku() = recording { recorder, _ ->
        recorder.startRecording()
        val path = assertNotNull(capture.current)

        recorder.clear()

        assertNull(recorder.voiceRecording.value)
        assertFalse(fileSystem.exists(path))
        assertTrue(repository.voiceNotes.isEmpty())
    }

    /** Nahrávání vcelku se nahrává předem a odejde podle id souboru, ne cesty. */
    @Test
    fun predemNahranaHlasovkaOdejdePodleId() = recording { recorder, _ ->
        capture.live = true
        recorder.startRecording()
        delay(MIN_DURATION_MS + 100)

        recorder.sendRecording()
        delay(300)

        assertEquals(listOf("start", "complete"), liveUploads.events)
        assertEquals(listOf<Int?>(77), repository.uploadedIds)
    }

    /** Nahrávač, který soubor dopisuje až na konci (iOS, kamera), předem nenahrává. */
    @Test
    fun bezZivehoSouboruSeNahravaAzHotova() = recording { recorder, _ ->
        recorder.startRecording()
        delay(MIN_DURATION_MS + 100)

        recorder.sendRecording()
        delay(300)

        assertTrue(liveUploads.events.isEmpty())
        assertEquals(listOf<Int?>(null), repository.uploadedIds)
    }

    /** Pauza (nabídka odeslání) dá jiný soubor — předem nahrané se zahodí a pošle se celá. */
    @Test
    fun pauzaPredemNahraneZahodi() = recording { recorder, _ ->
        capture.live = true
        recorder.startRecording()
        delay(MIN_DURATION_MS + 100)

        recorder.pauseRecording()
        recorder.sendRecording()
        delay(300)

        assertEquals(listOf("start", "abandon"), liveUploads.events)
        assertEquals(listOf<Int?>(null), repository.uploadedIds)
    }

    @Test
    fun zrusenaNahravkaZrusiIPredemNahravani() = recording { recorder, _ ->
        capture.live = true
        recorder.startRecording()

        recorder.cancelRecording()

        assertEquals(listOf("start", "abandon"), liveUploads.events)
    }

    /** Krátká nahrávka se neposílá, předem nahrávané se zruší. */
    @Test
    fun kratkaNahravkaZrusiPredemNahravani() = recording { recorder, _ ->
        capture.live = true
        recorder.startRecording()

        recorder.sendRecording()
        delay(200)

        assertEquals(listOf("start", "abandon"), liveUploads.events)
        assertTrue(repository.uploadedIds.isEmpty())
    }

    /** Zahozený náhled nikdo neodešle — předem nahraný soubor se zahodí taky. */
    @Test
    fun zahozenyNahledZahodiPredemNahrane() = recording { recorder, _ ->
        capture.live = true
        recorder.startRecording()
        delay(MIN_DURATION_MS + 100)

        recorder.stopRecordingForPreview()
        delay(300)
        assertEquals(77, recorder.voicePreview.value?.voice?.uploadedFileId)

        recorder.discardVoicePreview()

        assertEquals(listOf("start", "complete", "discard 77"), liveUploads.events)
    }
}
