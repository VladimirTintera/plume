package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.audio.RecordPermission
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.camera.VideoNoteCamera
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.ContentAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Držáky stavu obrazovky konverzace: otevřené sheety ([ConversationSheets]) a nahrávání ([ConversationRecording]). */
class ConversationScreenHoldersTest {

    @Test
    fun otevreneSheetyPrezijiOtoceniVeSpravnychPolich() {
        val sheets = ConversationSheets().apply {
            stickerPicker = true
            locationPicker = true
            previewAttachmentIndex = 2
            openStickerSetId = 77L
            allPinned = true
            effectPicker = true
            scheduleDay = 1_000L
        }
        val saved = with(ConversationSheets.Saver) { SaverScope { true }.save(sheets) }
        val restored = ConversationSheets.Saver.restore(saved!!)!!

        assertTrue(restored.stickerPicker)
        assertFalse(restored.mediaPicker)
        assertTrue(restored.locationPicker)
        assertFalse(restored.livePeriod)
        assertEquals(2, restored.previewAttachmentIndex)
        assertEquals(77L, restored.openStickerSetId)
        assertTrue(restored.allPinned)
        // Plánování a efekt se zapomenou: cíl (samolepka, GIF) uložit nejde a den bez cíle nedává smysl.
        assertFalse(restored.effectPicker)
        assertNull(restored.scheduleDay)
        assertNull(restored.scheduleTarget)
    }

    @Test
    fun zavreneSheetyZustanouZavrene() {
        val saved = with(ConversationSheets.Saver) { SaverScope { true }.save(ConversationSheets()) }
        val restored = ConversationSheets.Saver.restore(saved!!)!!
        assertFalse(restored.stickerPicker)
        assertNull(restored.previewAttachmentIndex)
        assertNull(restored.openStickerSetId)
    }

    private object NoPermission : RecordPermission {
        override fun request(onResult: (granted: Boolean) -> Unit) = error("oprávnění se v testu nežádá")
    }

    private object FakeCamera : VideoNoteCamera {
        @Composable
        override fun Preview(modifier: Modifier) = Unit
        override val canSwitch: Boolean = false
        override fun switchCamera() = Unit
        override val level: Float = 0f
        override fun start() = Unit
        override suspend fun stop(): String = error("stop")
        override fun cancel() = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override suspend fun join(previous: String, next: String): String = error("join")
    }

    private val voice = mutableStateOf<VoiceRecording?>(null)
    private val video = mutableStateOf<VoiceRecording?>(null)
    private val preferred = mutableStateOf(RecordMode.VideoNote)

    private fun recording(camera: VideoNoteCamera?) = ConversationRecording(
        voice = voice,
        video = video,
        voicePreviewState = mutableStateOf(null),
        videoNotePreviewState = mutableStateOf(null),
        preferredMode = preferred,
        gesture = RecordGestureState(),
        recordPermission = NoPermission,
        videoNotePermission = NoPermission,
        camera = camera,
    )

    @Test
    fun koleckoJenKdyzHoChatDovoliAJeCimHoNatocit() {
        val all = ContentAccess.All
        val withCamera = recording(FakeCamera)
        assertTrue(withCamera.videoNotesAllowed(all))
        assertEquals(RecordMode.VideoNote, withCamera.mode(all))
        assertFalse(withCamera.videoNotesAllowed(all.copy(videoNotes = false)))
        assertEquals(RecordMode.Voice, withCamera.mode(all.copy(videoNotes = false)))

        val noCamera = recording(camera = null)
        assertFalse(noCamera.videoNotesAllowed(all), "bez kamery (desktop bez webkamery) kolečko není")
        assertEquals(RecordMode.Voice, noCamera.mode(all), "uložená volba kolečka spadne na hlasovku")
        assertNull(noCamera.mode(all.copy(voiceNotes = false)), "nedá se nahrát nic")
    }

    @Test
    fun nahravaniSeRozlisujeNaHlasovkuAKolecko() {
        val recording = recording(FakeCamera)
        assertFalse(recording.isRecording)

        voice.value = VoiceRecording(elapsedMs = 1_000, recentLevels = emptyList())
        assertTrue(recording.isRecording)
        assertFalse(recording.isVideoRecording)
        assertEquals(1_000, recording.current()?.elapsedMs)
        assertNull(recording.video())

        voice.value = null
        video.value = VoiceRecording(elapsedMs = 2_000, recentLevels = emptyList())
        assertTrue(recording.isVideoRecording)
        assertEquals(2_000, recording.video()?.elapsedMs)
        assertEquals(2_000, recording.current()?.elapsedMs)
    }
}
