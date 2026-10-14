package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.audio.RecordPermission
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.audio.rememberRecordPermission
import eu.tintera.plume.core.camera.VideoNoteCamera
import eu.tintera.plume.core.camera.rememberVideoNoteCamera
import eu.tintera.plume.core.camera.rememberVideoNotePermission
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.ContentAccess

/**
 * Nahrávání hlasovky a kolečka, jak ho vidí obrazovka: stav z ViewModelu, gesto tlačítka,
 * oprávnění a kamera. Čte ho vstupní pole, překryv natáčení kolečka i nápovědy gest.
 *
 * Nahrávání se mění po 50 ms; skládání proto čte jen [isRecording] a [isVideoRecording]
 * (odvozené, mění se dvakrát za nahrávku), čas a úrovně až pruh nahrávání přes [current].
 */
@Stable
internal class ConversationRecording(
    private val voice: State<VoiceRecording?>,
    private val video: State<VoiceRecording?>,
    private val voicePreviewState: State<VoicePreview?>,
    private val videoNotePreviewState: State<VideoNotePreview?>,
    private val preferredMode: State<RecordMode>,
    val gesture: RecordGestureState,
    val recordPermission: RecordPermission,
    val videoNotePermission: RecordPermission,
    /** Kamera žije s obrazovkou, ViewModel si ji jen půjčuje (viz `CaptureSlot`). */
    val camera: VideoNoteCamera?,
) {
    val isRecording by derivedStateOf { voice.value != null || video.value != null }
    val isVideoRecording by derivedStateOf { video.value != null }
    val voicePreview: VoicePreview? get() = voicePreviewState.value
    val videoNotePreview: VideoNotePreview? get() = videoNotePreviewState.value

    /** Běžící nahrávka; volá se až tam, kde se kreslí čas a úrovně. */
    fun current(): VoiceRecording? = voice.value ?: video.value

    /** Natáčené kolečko; volá se až v překryvu s kamerou. */
    fun video(): VoiceRecording? = video.value

    /** Kolečko jen tam, kde ho chat dovolí A kde je čím ho natočit. */
    fun videoNotesAllowed(content: ContentAccess): Boolean = content.videoNotes && camera != null

    fun mode(content: ContentAccess): RecordMode? =
        recordModeFor(preferredMode.value, content.voiceNotes, videoNotesAllowed(content))
}

@Composable
internal fun rememberConversationRecording(viewModel: ConversationViewModel): ConversationRecording {
    val voice = viewModel.voiceRecording.collectAsStateWithLifecycle()
    val video = viewModel.videoRecording.collectAsStateWithLifecycle()
    val voicePreview = viewModel.voicePreview.collectAsStateWithLifecycle()
    val videoNotePreview = viewModel.videoNotePreview.collectAsStateWithLifecycle()
    val preferredMode = viewModel.recordMode.collectAsStateWithLifecycle()
    val recordPermission = rememberRecordPermission()
    val videoNotePermission = rememberVideoNotePermission()
    val camera = rememberVideoNoteCamera()
    // Gesto zvlášť: nesmí se vynulovat, když se vymění oprávnění nebo kamera.
    val gesture = remember { RecordGestureState() }
    DisposableEffect(camera) {
        camera?.let(viewModel::attachCamera)
        onDispose { camera?.let(viewModel::detachCamera) }
    }
    return remember(voice, video, voicePreview, videoNotePreview, preferredMode, gesture, recordPermission, videoNotePermission, camera) {
        ConversationRecording(
            voice = voice,
            video = video,
            voicePreviewState = voicePreview,
            videoNotePreviewState = videoNotePreview,
            preferredMode = preferredMode,
            gesture = gesture,
            recordPermission = recordPermission,
            videoNotePermission = videoNotePermission,
            camera = camera,
        )
    }
}
