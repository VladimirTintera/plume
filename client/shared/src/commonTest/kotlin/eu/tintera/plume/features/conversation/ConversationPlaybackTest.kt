package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.audio.MusicQueueControl
import eu.tintera.plume.core.audio.NowPlaying
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayerControl
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.telegram.SpeechTrial
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.UnimplementedFileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationPlaybackTest {

    /** Přehrávač, který si jen zapisuje, co po něm konverzace chtěla. */
    private class FakePlayer : VoicePlayerControl {
        override val state = MutableStateFlow<VoicePlayback?>(null)
        override val speed: StateFlow<VoiceSpeed> = MutableStateFlow(VoiceSpeed.entries.first())
        val calls = mutableListOf<String>()

        /** Klíč pamatované pozice, se kterým se hlasovka pouštěla; `null` = nepamatovat. */
        val resumeKeys = mutableListOf<String?>()

        override fun toggle(key: VoiceKey, music: Boolean, resumeKey: String?, path: suspend () -> String) {
            calls += "toggle ${key.messageId}"
            resumeKeys += resumeKey
        }

        override fun play(
            key: VoiceKey,
            music: Boolean,
            startMs: Long,
            resumeKey: String?,
            onFinished: (() -> Unit)?,
            path: suspend () -> String,
        ) {
            calls += "play ${key.messageId} od $startMs"
            resumeKeys += resumeKey
        }

        override fun seek(key: VoiceKey, fraction: Float) {
            calls += "seek ${key.messageId} $fraction"
        }

        override fun stop(chatId: Long?, keepMusic: Boolean) {
            calls += "stop"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun resume() {
            calls += "resume"
        }
    }

    private class FakeMusic : MusicQueueControl {
        override val nowPlaying = MutableStateFlow<NowPlaying?>(null)
        val calls = mutableListOf<String>()
        val remember = mutableListOf<Boolean>()
        override fun toggle(chatId: Long, messageId: Long, audio: MessageMedia.Audio, rememberPosition: Boolean) {
            calls += "toggle $messageId"
            remember += rememberPosition
        }
        override fun play(chatId: Long, messageId: Long, audio: MessageMedia.Audio, startMs: Long, rememberPosition: Boolean) {
            calls += "play $messageId od $startMs"
            remember += rememberPosition
        }
        override fun stop() {
            calls += "stop"
        }
        override fun skipToNext() {
            calls += "next"
        }
    }

    private class FakeRepository : UnimplementedConversationRepository() {
        val opened = mutableListOf<Long>()
        val recognized = mutableListOf<Long>()
        var recognizeResult: Result<Unit> = Result.success(Unit)

        override suspend fun openContent(chatId: Long, messageId: Long): Result<Unit> {
            opened += messageId
            return Result.success(Unit)
        }

        override suspend fun recognizeSpeech(chatId: Long, messageId: Long): Result<Unit> {
            recognized += messageId
            return recognizeResult
        }
    }

    private class FakeChats(premium: Boolean) : UnimplementedChatRepository() {
        override val isPremium: StateFlow<Boolean> = MutableStateFlow(premium)
        override val speechTrial: StateFlow<SpeechTrial?> = MutableStateFlow(null)
    }

    private class FakeSettings(discreet: Boolean) : UnimplementedAppSettings() {
        override val discreetReading: StateFlow<Boolean> = MutableStateFlow(discreet)
        override val onDeviceAiEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val onDeviceAiVariant: StateFlow<ModelVariant> = MutableStateFlow(ModelVariant())
    }

    private val player = FakePlayer()
    private val music = FakeMusic()
    private val repository = FakeRepository()
    private val openedLinks = mutableListOf<String>()

    private fun TestScope.playback(
        state: MutableStateFlow<ConversationState>,
        discreet: Boolean = false,
        premium: Boolean = true,
    ) = ConversationPlayback(
        chatId = TEST_CHAT,
        state = state,
        scope = detachedScope(),
        appScope = detachedScope(),
        repository = repository,
        chatRepository = FakeChats(premium),
        settings = FakeSettings(discreet),
        voicePlayer = player,
        musicQueue = music,
        fileRepository = object : UnimplementedFileRepository() {},
        localTranscripts = null,
        onDeviceAi = null,
        speechToText = null,
        openLink = { openedLinks += it },
        logger = testLogger,
    )

    private fun voice(id: Long, listened: Boolean = false, outgoing: Boolean = false): ChatMessage =
        testMessage(id, outgoing = outgoing).copy(
            media = MessageMedia.VoiceNote(fileId = id.toInt(), durationSeconds = 5, waveform = emptyList(), isListened = listened),
        )

    private fun videoNote(id: Long, viewed: Boolean = false): ChatMessage =
        testMessage(id).copy(
            media = MessageMedia.VideoNote(fileId = id.toInt(), thumbnailFileId = null, durationSeconds = 5, sizeBytes = 0, isViewed = viewed),
        )

    private fun audio(id: Long): ChatMessage =
        testMessage(id).copy(
            media = MessageMedia.Audio(
                fileId = id.toInt(),
                title = "Píseň",
                performer = null,
                fileName = "a.mp3",
                mimeType = "audio/mpeg",
                durationSeconds = 120,
                sizeBytes = 0,
            ),
        )

    private fun isListened(state: MutableStateFlow<ConversationState>, id: Long) =
        (state.value.messages.first { it.id == id }.media as MessageMedia.VoiceNote).isListened

    @Test
    fun neposlechnutaHlasovkaSePustiANahlasiJakoPoslechnuta() = runTest {
        val state = testState(voice(1))
        val playback = playback(state)

        playback.toggleVoice(1)
        advanceUntilIdle()

        assertEquals(listOf("toggle 1"), player.calls)
        assertTrue(isListened(state, 1), "poslech se ukáže hned, nečeká na server")
        assertEquals(listOf(1L), repository.opened)
    }

    @Test
    fun priDiskretnimCteniSePoslechNehlasi() = runTest {
        val state = testState(voice(1))
        val playback = playback(state, discreet = true)

        playback.toggleVoice(1)
        advanceUntilIdle()

        assertTrue(isListened(state, 1))
        assertTrue(repository.opened.isEmpty())
        assertEquals(listOf(1L), playback.discreetListened.toList(), "poslech odejde až s přečtením chatu")
    }

    @Test
    fun vlastniAPoslechnutaHlasovkaSeNehlasi() = runTest {
        val state = testState(voice(1, listened = true), voice(2, outgoing = true))
        val playback = playback(state)

        playback.toggleVoice(1)
        playback.toggleVoice(2)
        advanceUntilIdle()

        assertTrue(repository.opened.isEmpty())
    }

    @Test
    fun hlasovkaSePoustiSKlicemPamatovanePozice() = runTest {
        val message = voice(1).let { it.copy(media = (it.media as MessageMedia.VoiceNote).copy(fileKey = "soubor-1")) }
        playback(testState(message)).toggleVoice(1)

        assertEquals(listOf<String?>("soubor-1"), player.resumeKeys)
    }

    @Test
    fun vTajnemChatuSePoziceNepamatuje() = runTest {
        val message = voice(1).let { it.copy(media = (it.media as MessageMedia.VoiceNote).copy(fileKey = "soubor-1")) }
        val state = testState(message, audio(2))
        state.value = state.value.copy(info = testChatInfo().copy(isSecret = true))
        val playback = playback(state)

        playback.toggleVoice(1)
        playback.toggleVoice(2)

        assertEquals(listOf<String?>(null), player.resumeKeys, "hlasovka bez klíče")
        assertEquals(listOf(false), music.remember, "skladba bez paměti pozice")
    }

    @Test
    fun mizejiciHlasovkaSiPoziciNepamatuje() = runTest {
        val message = voice(1).let {
            it.copy(media = (it.media as MessageMedia.VoiceNote).copy(fileKey = "soubor-1"), selfDestructType = SelfDestructType.Immediately)
        }
        playback(testState(message)).toggleVoice(1)

        assertEquals(listOf<String?>(null), player.resumeKeys)
    }

    @Test
    fun pokracovaniZPamatovanePoziceSeOhlasiJednou() = runTest {
        val state = testState(voice(1))
        val playback = playback(state)
        val announced = mutableListOf<ResumedAudio>()
        val collector = detachedScope().launch { playback.resumes.collect { announced += it } }

        val resumed = VoicePlayback(VoiceKey(TEST_CHAT, 1), loading = false, positionMs = 754_000, durationMs = 1_800_000, resumedFromMs = 754_000)
        player.state.value = resumed
        advanceUntilIdle()
        // Pauza a znovu: hlášení se neopakuje.
        player.state.value = resumed.copy(playing = false)
        player.state.value = resumed.copy(playing = true, positionMs = 755_000)
        advanceUntilIdle()
        // Jiný chat se netýká.
        player.state.value = resumed.copy(key = VoiceKey(TEST_CHAT + 1, 1))
        advanceUntilIdle()

        assertEquals(listOf(ResumedAudio(1, 754_000)), announced)
        collector.cancel()
    }

    @Test
    fun nahravkaPustenaOdZacatkuSePoNavratuDoChatuNehlasi() = runTest {
        // Nahrávka pokračovala od 12:34, uživatel ji pustil od začátku a vrátil se do chatu.
        player.state.value = VoicePlayback(VoiceKey(TEST_CHAT, 1), loading = false, positionMs = 60_000, durationMs = 1_800_000, resumedFromMs = 754_000)
        val playback = playback(testState(voice(1)))
        val announced = mutableListOf<ResumedAudio>()
        val collector = detachedScope().launch { playback.resumes.collect { announced += it } }
        advanceUntilIdle()

        assertTrue(announced.isEmpty())
        collector.cancel()
    }

    @Test
    fun odZacatkuVratiNahravkuNaNulu() = runTest {
        playback(testState(voice(1))).restartAudio(1)

        assertEquals(listOf("seek 1 0.0"), player.calls)
    }

    @Test
    fun skladbaJdeDoFrontyHudby() = runTest {
        val state = testState(audio(1))
        playback(state).toggleVoice(1)

        assertEquals(listOf("toggle 1"), music.calls)
        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun koleckoSeZvukemZastaviHlasovkuADruheTuknutiHoUtisi() = runTest {
        val state = testState(videoNote(1))
        val playback = playback(state)

        playback.toggleVideoNoteSound(1)
        assertEquals(1L, state.value.soundingVideoNote)
        assertEquals(listOf("stop"), player.calls)
        playback.toggleVideoNoteSound(1)
        advanceUntilIdle()

        assertNull(state.value.soundingVideoNote)
        assertEquals(listOf(1L), repository.opened, "zhlédnutí se nahlásí jednou")
    }

    @Test
    fun poDohraniHrajeNejblizsiNovejsiNeposlechnuta() = runTest {
        val state = testState(voice(1, listened = true), voice(2), voice(3))
        val playback = playback(state)

        playback.playNextUnlistened(after = 1)
        advanceUntilIdle()

        assertEquals(listOf("play 2 od 0"), player.calls)
        assertEquals(listOf(2L), repository.opened)
    }

    @Test
    fun retezKonciUPoslechnuteNeboVlastni() = runTest {
        val state = testState(voice(1), voice(2, listened = true), voice(3), voice(4, outgoing = true))
        val playback = playback(state)

        playback.playNextUnlistened(after = 1)
        playback.playNextUnlistened(after = 3)

        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun casVTextuPustiHlasovkuOdTohoMista() = runTest {
        // Čas v médiu se váže na zprávu, která médium s časem nese (`hasTimestampedMedia` od TDLib).
        val state = testState(voice(1).copy(hasTimestampedMedia = true))
        playback(state).playMediaAt(1, seconds = 12)

        assertEquals(listOf("play 1 od 12000"), player.calls)
    }

    @Test
    fun casUVideaOtevreProhlizec() = runTest {
        val video = testMessage(1).copy(
            media = MessageMedia.Video(fileId = 1, thumbnailFileId = null, width = 1, height = 1, durationSeconds = 60),
            hasTimestampedMedia = true,
        )
        val state = testState(video)
        playback(state).playMediaAt(1, seconds = 30)

        assertEquals(MediaAt(1, 30), state.value.openMediaAt)
        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun prepisJdeNaServer() = runTest {
        val state = testState(voice(1))
        playback(state, premium = true).transcribe(1)
        advanceUntilIdle()

        assertEquals(listOf(1L), repository.recognized)
    }

    @Test
    fun nepovedenyPrepisSeOhlasi() = runTest {
        repository.recognizeResult = Result.failure(IllegalStateException("MSG_VOICE_TOO_LONG"))
        val state = testState(voice(1))
        playback(state).transcribe(1)
        advanceUntilIdle()

        assertNotNull(state.value.notice)
    }

    @Test
    fun miniPrehravacPrepinaPauzu() = runTest {
        val state = testState()
        val playback = playback(state)

        player.state.value = VoicePlayback(VoiceKey(TEST_CHAT, 1), loading = false, playing = true)
        playback.togglePlaying()
        player.state.value = VoicePlayback(VoiceKey(TEST_CHAT, 1), loading = false, playing = false)
        playback.togglePlaying()
        player.state.value = VoicePlayback(VoiceKey(TEST_CHAT, 1), loading = true)
        playback.togglePlaying()

        assertEquals(listOf("pause", "resume"), player.calls, "během načítání se nepřepíná")
    }

    @Test
    fun zastaveniHudbyJdePresFrontu() = runTest {
        val state = testState()
        val playback = playback(state)

        player.state.value = VoicePlayback(VoiceKey(TEST_CHAT, 1), music = true)
        playback.stopPlaying()
        player.state.value = VoicePlayback(VoiceKey(TEST_CHAT, 2))
        playback.stopPlaying()

        assertEquals(listOf("stop"), music.calls)
        assertEquals(listOf("stop"), player.calls)
    }
}
