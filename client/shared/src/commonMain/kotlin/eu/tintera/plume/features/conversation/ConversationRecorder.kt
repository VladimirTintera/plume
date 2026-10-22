package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.MediaCapture
import eu.tintera.plume.core.audio.MIN_DURATION_MS
import eu.tintera.plume.core.audio.RecordedVoice
import eu.tintera.plume.core.audio.VoicePlayerControl
import eu.tintera.plume.core.audio.VoiceRecorder
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.camera.CaptureSlot
import eu.tintera.plume.core.camera.VIDEO_NOTE_MAX_DURATION_MS
import eu.tintera.plume.core.camera.VideoNoteCamera
import eu.tintera.plume.core.camera.prepareVideoNote
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.LiveUpload
import eu.tintera.plume.core.telegram.LiveUploads
import eu.tintera.plume.core.telegram.OutgoingVideoNote
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_content_not_allowed
import eu.tintera.plume.resources.conversation_recording_finish_failed
import eu.tintera.plume.resources.conversation_recording_start_failed
import eu.tintera.plume.resources.conversation_recording_too_short
import eu.tintera.plume.resources.conversation_video_note_send_failed
import eu.tintera.plume.resources.conversation_video_note_start_failed
import eu.tintera.plume.resources.conversation_voice_send_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.StringResource

/**
 * Nahrávání hlasovky a kolečka v konverzaci: start a konec, pozastavení po dobu nabídky,
 * náhled před odesláním s možností nahrávat dál a odeslání.
 *
 * Část [ConversationViewModel] — ten na ni deleguje a drží její toky ve veřejném API.
 */
internal class ConversationRecorder(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    /** Zápis voleb musí doběhnout i po odchodu z chatu. */
    private val appScope: CoroutineScope,
    private val repository: ConversationRepository,
    private val settings: AppSettings,
    private val voicePlayer: VoicePlayerControl,
    /** Mikrofon (`AudioRecorder`); rozhraní, aby šel v testu podvrhnout. */
    private val audioRecorder: MediaCapture,
    private val media: MediaSender,
    /** Vlastní zpráva přečte konverzaci až k sobě (viz `ConversationViewModel.readAfterOwnMessage`). */
    private val readAfterOwnMessage: () -> Unit,
    private val logger: Logger,
    /** Nahrávání hlasovky do Telegramu už během nahrávání; `null` = posílá se až hotový soubor. */
    private val liveUploads: LiveUploads? = null,
) {
    private val voiceRecorder = VoiceRecorder(audioRecorder, scope)

    /**
     * Hlasovka, která se nahrává do Telegramu, zatímco uživatel mluví ([LiveUploads]). Jen
     * u nahrávání vcelku (`MediaCapture.livePath`): pauza i navázání na náhled dají jiný soubor,
     * tak se zahodí a pošle se hotová nahrávka.
     */
    private var liveUpload: LiveUpload? = null

    /** Probíhající nahrávání hlasovky; samostatný tok ze stejného důvodu jako [voicePlayback]. */
    val voiceRecording: StateFlow<VoiceRecording?> = voiceRecorder.state

    /** Zastavená hlasovka k poslechu před odesláním ([stopRecordingForPreview]); `null` = žádná. */
    private val _voicePreview = MutableStateFlow<VoicePreview?>(null)
    val voicePreview: StateFlow<VoicePreview?> = _voicePreview.asStateFlow()

    /** Zastavené kolečko k přehrání před odesláním ([stopRecordingForPreview]); `null` = žádné. */
    private val _videoNotePreview = MutableStateFlow<VideoNotePreview?>(null)
    val videoNotePreview: StateFlow<VideoNotePreview?> = _videoNotePreview.asStateFlow()

    /** Kamera, kterou připojila obrazovka (viz [CaptureSlot]). */
    private val cameraSlot = CaptureSlot()

    /** Kolečko jde tímtéž [VoiceRecorder] jako hlasovka: čas, pauzy i zahození krátkého. */
    private val videoRecorder = VoiceRecorder(cameraSlot, scope)

    /** Probíhající nahrávání kolečka; samostatný tok jako [voiceRecording]. */
    val videoRecording: StateFlow<VoiceRecording?> = videoRecorder.state

    /**
     * Začne nahrávat hlasovku. Oprávnění k mikrofonu řeší obrazovka dřív, než sem dojde.
     * Hrající hlasovka se zastaví — nahrával by se i její zvuk z reproduktoru.
     */
    fun startRecording() {
        if (!state.value.content.voiceNotes) {
            state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        if (videoRecorder.state.value != null) return
        silencePlayback()
        voiceRecorder.start()
            .onSuccess { liveUpload = audioRecorder.livePath?.let { path -> liveUploads?.startVoiceNote(path) } }
            .onFailure { state.update { it.copy(error = UiText(Res.string.conversation_recording_start_failed)) } }
    }

    /** Předem nahrávaná hlasovka se nepoužije (zahozená, pozastavená). */
    private fun abandonLiveUpload() {
        liveUpload?.abandon()
        liveUpload = null
    }

    /**
     * Hotová [recorded] převezme předem nahraný soubor, když sedí ([LiveUpload.complete]);
     * jinak se pošle celá. [live] se bere dřív, než nahrávání skončí — [finish] je suspend.
     */
    private suspend fun withLiveUpload(live: LiveUpload?, recorded: RecordedVoice?): RecordedVoice? {
        if (recorded == null) {
            live?.abandon()
            return null
        }
        val fileId = live?.complete(recorded.path) ?: return recorded
        return RecordedVoice(
            path = recorded.path,
            durationSeconds = recorded.durationSeconds,
            waveform = recorded.waveform,
            durationMs = recorded.durationMs,
            levels = recorded.levels,
            uploadedFileId = fileId,
        )
    }

    /**
     * Připojí kameru, kterou drží obrazovka. Odpojením (obrazovka se rozkládá — otočení, odchod)
     * se rozpracované kolečko zahodí: kamera s kompozicí končí a nahrávat by nebylo do čeho.
     */
    fun attachCamera(camera: VideoNoteCamera) {
        cameraSlot.attach(camera) { videoRecorder.cancel() }
    }

    fun detachCamera(camera: VideoNoteCamera) {
        cameraSlot.detach(camera)
    }

    /** Začne nahrávat kolečko. Oprávnění ke kameře a mikrofonu řeší obrazovka, jako u hlasovky. */
    fun startVideoRecording() {
        if (!state.value.content.videoNotes) {
            state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        if (voiceRecorder.state.value != null) return
        silencePlayback()
        videoRecorder.start().onFailure {
            state.update { it.copy(error = UiText(Res.string.conversation_video_note_start_failed)) }
        }
    }

    /** Přepne, co nahraje podržení tlačítka. Zápis jde přes [appScope], jako ostatní volby. */
    fun setRecordMode(mode: RecordMode) {
        appScope.launch { settings.setRecordMode(mode) }
    }

    /** Hrající hlasovka i kolečko se zvukem by se nahrály z reproduktoru. */
    private fun silencePlayback() {
        voicePlayer.stop()
        state.update { it.copy(soundingVideoNote = null) }
    }

    fun cancelRecording() {
        abandonLiveUpload()
        voiceRecorder.cancel()
        videoRecorder.cancel()
    }

    /**
     * Pozastaví nahrávání po dobu nabídky odeslání a výběru času. Jinak by se do hlasovky
     * nahrálo, jak uživatel vybírá — a šustění na konci by se z ní nedalo dostat.
     */
    fun pauseRecording() {
        // Pauza ukončí úsek a na konci se úseky spojí do jiného souboru (viz `AudioRecorder`).
        if (voiceRecorder.state.value != null) abandonLiveUpload()
        voiceRecorder.pause()
        videoRecorder.pause()
    }

    /** Nabídka se zavřela bez volby — nahrává se dál. */
    fun resumeRecording() {
        voiceRecorder.resume()
        videoRecorder.resume()
    }

    /**
     * Ukončí nahrávání a hlasovku nebo kolečko odešle — jako samolepka: bez letu ze vstupního
     * pole, rozepsaný text zůstává, citace se spotřebuje. Příliš krátká nahrávka se zahodí
     * s vysvětlením.
     */
    fun sendRecording(options: SendOptions = SendOptions()) {
        readAfterOwnMessage()
        when {
            videoRecorder.state.value != null -> sendVideoRecording(options)
            voiceRecorder.state.value != null -> sendVoiceRecording(options)
            _voicePreview.value != null -> sendVoicePreview(options)
            _videoNotePreview.value != null -> sendVideoNotePreview(options)
            // Dvojí ťuknutí na odeslání: druhé by našlo nahrávání už ukončené a hlásilo nesmysl.
            else -> Unit
        }
    }

    private fun sendVoiceRecording(options: SendOptions) {
        val live = liveUpload.also { liveUpload = null }
        scope.launch {
            val tooShort = (voiceRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
            val recorded = withLiveUpload(live, voiceRecorder.finish())
            if (recorded == null) {
                reportRecordingLost(tooShort)
                return@launch
            }
            sendVoice(recorded, options)
        }
    }

    private suspend fun sendVoice(recorded: RecordedVoice, options: SendOptions) {
        media.sendMedia(Res.string.conversation_voice_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
            repository.sendVoiceNote(
                chatId, recorded.path, recorded.durationSeconds, recorded.waveform, replyTo, quote, options,
                uploadedFileId = recorded.uploadedFileId,
            )
        }
    }

    /**
     * Zastaví zamčené nahrávání a nechá hlasovku k poslechu, místo aby ji rovnou odeslal.
     * Odejde až tlačítkem odeslání ([sendRecording]), koš ji zahodí ([discardVoicePreview]).
     */
    fun stopRecordingForPreview() {
        if (videoRecorder.state.value != null) {
            stopVideoRecordingForPreview()
            return
        }
        if (voiceRecorder.state.value == null || _voicePreview.value != null) return
        val tooShort = (voiceRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
        // Mezistav hned: dokončení (na iOS kódování) chvíli trvá a karta mezitím nemá ukázat pole.
        val finishing = VoicePreview(voice = null)
        _voicePreview.value = finishing
        val live = liveUpload.also { liveUpload = null }
        scope.launch {
            val recorded = withLiveUpload(live, voiceRecorder.finish())
            // Koš během dokončování: náhled už nikdo nechce, soubor pryč.
            if (_voicePreview.value !== finishing) {
                recorded?.let { deleteQuietly(it.path) }
                recorded?.uploadedFileId?.let { liveUploads?.discard(it) }
                return@launch
            }
            if (recorded == null) {
                _voicePreview.value = null
                reportRecordingLost(tooShort)
                return@launch
            }
            _voicePreview.value = VoicePreview(recorded, canContinue = voiceRecorder.canContinue(recorded))
        }
    }

    /** Pustí nebo pozastaví náhled. Hrající hlasovka v konverzaci se tím zastaví, jako vždy. */
    fun toggleVoicePreview() {
        val voice = _voicePreview.value?.voice ?: return
        voicePlayer.toggle(voicePreviewKey(chatId)) { voice.path }
    }

    fun seekVoicePreview(fraction: Float) {
        voicePlayer.seek(voicePreviewKey(chatId), fraction)
    }

    /**
     * Nahrávat dál po poslechu náhledu: nové nahrávání naváže na hotovou hlasovku (čas běží od
     * jejího konce) a po ■ nebo odeslání se oba úseky spojí do jedné ([VoiceRecorder.start]).
     * Nahrávání je rovnou zamčené — spouští ho ťuknutí, ne podržení; zamčení drží obrazovka.
     */
    fun continueVoicePreview() {
        val preview = _voicePreview.value?.takeIf { it.canContinue } ?: return
        val voice = preview.voice ?: return
        if (videoRecorder.state.value != null) return
        silencePlayback()
        voiceRecorder.start(continuing = voice)
            .onSuccess {
                _voicePreview.value = null
                // Po navázání vznikne spojený soubor; předem nahraný začátek nikdo nepošle.
                voice.uploadedFileId?.let { liveUploads?.discard(it) }
            }
            .onFailure { state.update { it.copy(error = UiText(Res.string.conversation_recording_start_failed)) } }
    }

    /** Koš u náhledu, i odchod z chatu: nic, co uživatel neodeslal, neodejde, a soubor se smaže. */
    fun discardVoicePreview() {
        val preview = _voicePreview.value ?: return
        _voicePreview.value = null
        stopVoicePreviewPlayback()
        preview.voice?.path?.let(::deleteQuietly)
        preview.voice?.uploadedFileId?.let { liveUploads?.discard(it) }
    }

    private fun deleteQuietly(path: String) {
        runCatching { FileSystem.SYSTEM.delete(path.toPath(), mustExist = false) }
            .onFailure { failure -> logger.w(failure) { "Zahozený náhled nahrávky nejde smazat." } }
    }

    private fun sendVoicePreview(options: SendOptions) {
        val voice = _voicePreview.value?.voice ?: return
        _voicePreview.value = null
        stopVoicePreviewPlayback()
        scope.launch { sendVoice(voice, options) }
    }

    /** Hraje-li právě náhled, zastaví ho; cizí hlasovky ani hudby se to netýká. */
    private fun stopVoicePreviewPlayback() {
        if (voicePlayer.state.value?.key == voicePreviewKey(chatId)) voicePlayer.stop()
    }

    /**
     * Kolečko se po skončení ještě připraví (délka, strana, první snímek) — až tady, ne během
     * nahrávání: soubor je hotový teprve teď a na iOS se navíc ořezává do čtverce.
     */
    private fun sendVideoRecording(options: SendOptions) {
        scope.launch {
            val tooShort = (videoRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
            val recorded = videoRecorder.finish()
            if (recorded == null) {
                reportRecordingLost(tooShort)
                return@launch
            }
            media.sendMedia(Res.string.conversation_video_note_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
                runCatching { prepareVideoNote(recorded.path) }
                    .onFailure { failure -> logger.w(failure) { "Kolečko nejde připravit k odeslání." } }
                    .fold(
                        onSuccess = { note -> repository.sendVideoNote(chatId, note, replyTo, quote, options) },
                        onFailure = { failure -> Result.failure(failure) },
                    )
            }
        }
    }

    /**
     * Zastaví zamčené kolečko a nechá ho k přehrání, místo aby ho rovnou odeslalo — jako
     * [stopRecordingForPreview] u hlasovky. Připraví se hned (délka, strana, první snímek):
     * náhled přehrává hotový soubor a odeslání pak nemá na co čekat.
     */
    private fun stopVideoRecordingForPreview() {
        if (_videoNotePreview.value != null) return
        val tooShort = (videoRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
        // Mezistav hned: dokončení (zmenšení na Androidu, ořez na iOS) trvá vteřiny.
        val finishing = VideoNotePreview(note = null)
        _videoNotePreview.value = finishing
        scope.launch {
            val recorded = videoRecorder.finish()
            val note = recorded?.let {
                runCatching { prepareVideoNote(it.path) }
                    .onFailure { failure -> logger.w(failure) { "Kolečko nejde připravit k náhledu." } }
                    .getOrNull()
            }
            // Koš během dokončování: náhled už nikdo nechce, soubory pryč.
            if (_videoNotePreview.value !== finishing) {
                note?.let(::deleteVideoNote) ?: recorded?.let { deleteQuietly(it.path) }
                return@launch
            }
            if (note == null) {
                _videoNotePreview.value = null
                recorded?.let { deleteQuietly(it.path) }
                reportRecordingLost(tooShort && recorded == null)
                return@launch
            }
            // Kolečko těsně pod minutovým stropem: navázaný úsek by po zlomku vteřiny odešel sám.
            val roomLeft = recorded.durationMs < VIDEO_NOTE_MAX_DURATION_MS - MIN_DURATION_MS
            _videoNotePreview.value = VideoNotePreview(
                note,
                recorded,
                canContinue = roomLeft && videoRecorder.canContinue(recorded),
            )
        }
    }

    /**
     * Nahrávat dál po přehrání náhledu kolečka — jako [continueVoicePreview]: kamera se znovu
     * otevře, čas běží od konce náhledu a po ■ nebo odeslání se obě kolečka spojí do jednoho
     * ([eu.tintera.plume.core.camera.VideoNoteCamera.join]). Náhledový snímek patří starému
     * souboru, takže jde pryč hned; nový se přečte z výsledku.
     */
    fun continueVideoNotePreview() {
        val preview = _videoNotePreview.value?.takeIf { it.canContinue } ?: return
        val recorded = preview.recorded ?: return
        if (voiceRecorder.state.value != null) return
        silencePlayback()
        videoRecorder.start(continuing = recorded)
            .onSuccess {
                _videoNotePreview.value = null
                preview.note?.thumbnail?.path?.let(::deleteQuietly)
            }
            .onFailure { state.update { it.copy(error = UiText(Res.string.conversation_video_note_start_failed)) } }
    }

    /** Koš u náhledu kolečka, i odchod z chatu: nic neodejde a soubory se smažou. */
    fun discardVideoNotePreview() {
        val preview = _videoNotePreview.value ?: return
        _videoNotePreview.value = null
        preview.note?.let(::deleteVideoNote)
    }

    private fun deleteVideoNote(note: OutgoingVideoNote) {
        deleteQuietly(note.path)
        note.thumbnail?.path?.let(::deleteQuietly)
    }

    private fun sendVideoNotePreview(options: SendOptions) {
        val note = _videoNotePreview.value?.note ?: return
        _videoNotePreview.value = null
        scope.launch {
            media.sendMedia(Res.string.conversation_video_note_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
                repository.sendVideoNote(chatId, note, replyTo, quote, options)
            }
        }
    }

    private fun reportRecordingLost(tooShort: Boolean) {
        state.update {
            if (tooShort) it.copy(notice = UiText(Res.string.conversation_recording_too_short))
            else it.copy(error = UiText(Res.string.conversation_recording_finish_failed))
        }
    }

    init {
        // Delší kolečko Telegram nepřijme: po minutě nahrávání skončí a kolečko odejde samo,
        // jako v oficiálních klientech. Pozastavené (nabídka, výběr času) se nepočítá.
        scope.launch {
            videoRecorder.state.collect { recording ->
                if (recording != null && recording.elapsedMs >= VIDEO_NOTE_MAX_DURATION_MS) sendRecording()
            }
        }
    }

    /** Odchod z chatu: rozpracovaná nahrávka i náhled se zahazují — neposílat nic, co uživatel neodeslal. */
    fun clear() {
        abandonLiveUpload()
        voiceRecorder.cancel()
        videoRecorder.cancel()
        discardVoicePreview()
        discardVideoNotePreview()
    }
}
