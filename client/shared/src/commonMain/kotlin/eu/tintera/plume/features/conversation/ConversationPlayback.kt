package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.audio.MusicQueue
import eu.tintera.plume.core.audio.NowPlaying
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayer
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.speech.SpeechToText
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.SpeechTrial
import eu.tintera.plume.core.telegram.TranscribeOffer
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.telegram.transcribeOffer
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.transcribe_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Přehrávání v konverzaci: hlasovky (i řetězení neposlechnutých), kolečko se zvukem, zvuk
 * v článku, hudba v mini přehrávači, hlášení poslechu (s ohledem na diskrétní čtení) a přepis
 * řeči na serveru nebo v telefonu.
 *
 * Část [ConversationViewModel] — ten na ni deleguje a drží její toky ve veřejném API.
 */
internal class ConversationPlayback(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    /** Přepis v telefonu trvá a odchod z chatu ho nemá zahodit. */
    private val appScope: CoroutineScope,
    private val repository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settings: AppSettings,
    private val voicePlayer: VoicePlayer,
    private val musicQueue: MusicQueue,
    private val fileRepository: FileRepository,
    private val localTranscripts: LocalTranscripts?,
    private val onDeviceAi: OnDeviceAi?,
    private val speechToText: SpeechToText?,
    private val logger: Logger,
) {
    /**
     * Přehrávání hlasovky. Samostatný tok, ne součást [state]: pozice se mění dvacetkrát za
     * vteřinu a přepočítávat kvůli tomu celý stav konverzace by bylo zbytečné.
     */
    val voicePlayback: StateFlow<VoicePlayback?> = voicePlayer.state

    /** Zvolená rychlost přehrávání; jedna pro celou aplikaci, proto rovnou z přehrávače. */
    val voiceSpeed: StateFlow<VoiceSpeed> = voicePlayer.speed

    /** Hrající skladba (i z jiného chatu) pro mini přehrávač v docku: název, interpret, další. */
    val nowPlaying: StateFlow<NowPlaying?> = musicQueue.nowPlaying

    /** Pauza / přehrát z mini přehrávače v docku — týká se toho, co právě hraje, ať je to cokoli. */
    fun togglePlaying() {
        val playback = voicePlayer.state.value ?: return
        when {
            playback.loading -> Unit
            playback.playing -> voicePlayer.pause()
            else -> voicePlayer.resume()
        }
    }

    /** Další skladba z mini přehrávače; hraje dál fronta hudby, ne tahle konverzace. */
    fun skipToNextTrack() = musicQueue.skipToNext()

    /** Zastavení z mini přehrávače. Hudbu přes frontu, aby po ní nezůstalo systémové ovládání. */
    fun stopPlaying() {
        if (voicePlayer.state.value?.music == true) musicQueue.stop() else voicePlayer.stop()
    }

    /** Premium a zkušební přepisy řeči — rozhodují, jestli se u hlasovky nabídne přepis. */
    val isPremium: StateFlow<Boolean> = chatRepository.isPremium

    val speechTrial: StateFlow<SpeechTrial?> = chatRepository.speechTrial

    /**
     * Všechno, co rozhoduje o přepisu záznamu, v jednom toku: Premium, zkušební přepisy, jestli
     * přepíše telefon, a přepisy z telefonu v tomhle chatu. Jeden tok: bubliny ho čtou přes
     * `ScreenItemScope` jedním odběrem, ne čtyřmi.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val transcribeContext: StateFlow<TranscribeContext> = combine(
        chatRepository.isPremium,
        chatRepository.speechTrial,
        combine(settings.onDeviceAiEnabled, settings.onDeviceAiVariant) { enabled, variant -> enabled to variant }
            .mapLatest { (enabled, variant) -> gemmaTranscribes(enabled, variant) || systemTranscribes() },
        localTranscripts?.state?.map { all -> all.filterKeys { it.first == chatId }.mapKeys { it.key.second } }
            ?: flowOf(emptyMap()),
    ) { premium, trial, onDevice, local -> TranscribeContext(premium, trial, onDevice, local) }
        .stateIn(scope, SharingStarted.Eagerly, TranscribeContext(chatRepository.isPremium.value, chatRepository.speechTrial.value))

    /**
     * Zvuk nebo hlasovka v článku. Hraje TENTÝŽ přehrávač jako hlasovky (zvuk je v aplikaci
     * jeden); klíč nese pořadí v článku. Skladba z článku nejde do fronty hudby na pozadí —
     * sousedy fronta hledá mezi zprávami, a médium článku zpráva sama není.
     */
    fun toggleArticleAudio(messageId: Long, index: Int) {
        val fileId = when (val media = state.value.articleMedia(messageId, index)) {
            is MessageMedia.VoiceNote -> media.fileId
            is MessageMedia.Audio -> media.fileId
            else -> return
        }
        state.update { it.copy(soundingVideoNote = null) }
        voicePlayer.toggle(VoiceKey(chatId, messageId, part = index)) { filePath(fileId) }
    }

    /**
     * Pustí nebo pozastaví hlasovku.
     *
     * Soubor se stáhne až teď — dopředu by to byla data za každou hlasovku v historii. Příchozí
     * neposlechnutá se nahlásí jako poslechnutá (`openMessageContent`) a hned se tak i ukáže;
     * potvrzení ze serveru přijde `contentOpenedUpdates`.
     */
    fun toggleVoice(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        (message.media as? MessageMedia.Audio)?.let { audio ->
            state.update { it.copy(soundingVideoNote = null) }
            musicQueue.toggle(chatId, messageId, audio)
            return
        }
        val voice = message.media as? MessageMedia.VoiceNote ?: return

        // Zvuk hraje vždycky jen jeden: hlasovka umlčí kolečko a naopak.
        state.update { it.copy(soundingVideoNote = null) }
        voicePlayer.toggle(VoiceKey(chatId, messageId)) { voicePath(voice) }
        if (!message.isOutgoing && !voice.isListened) reportListened(messageId)
    }

    /**
     * Pošle hlasovku nebo kolečko k přepisu. Výsledek nese obsah zprávy a dorazí úpravou
     * ([ConversationRepository.editedMessages]) — napřed rozpracovaný, pak hotový.
     */
    fun transcribe(messageId: Long) {
        val media = state.value.messages.firstOrNull { it.id == messageId }?.media
        val (fileId, duration) = when (media) {
            is MessageMedia.VoiceNote -> media.fileId to media.durationSeconds
            is MessageMedia.VideoNote -> media.fileId to media.durationSeconds
            else -> return
        }
        val context = transcribeContext.value
        if (transcribeOffer(context.isPremium, context.trial, duration, context.onDevice) == TranscribeOffer.OnDevice) {
            transcribeOnDevice(messageId, fileId)
            return
        }
        scope.launch {
            repository.recognizeSpeech(chatId, messageId).onFailure { failure ->
                logger.w(failure) { "Přepis zprávy $messageId se nepodařilo spustit." }
                state.update { it.copy(notice = failure.userMessage(Res.string.transcribe_failed)) }
            }
        }
    }

    /**
     * Přepis v telefonu (`OnDeviceAi.transcribe`): soubor se stáhne, model ho přepíše a výsledek
     * jde do [LocalTranscripts] — zprávu na serveru měnit nejde. Běží v [appScope]: přepis trvá
     * vteřiny až desítky vteřin a odchod z chatu ho nemá zahodit.
     */
    private fun transcribeOnDevice(messageId: Long, fileId: Int) {
        val local = localTranscripts ?: return
        if (local.get(chatId, messageId) is SpeechTranscript.Pending) return
        local.set(chatId, messageId, SpeechTranscript.Pending(""))
        appScope.launch {
            try {
                val file = fileRepository.file(fileId).first { it is FileState.Ready || it is FileState.Failed }
                val path = (file as? FileState.Ready)?.path ?: throw IllegalStateException("Soubor $fileId se nestáhl")
                var text = ""
                // Gemma má přednost (přepisuje i interpunkci a smysl), systém je záloha pro
                // telefony bez ní — rozhoduje se až teď, stav se mohl od nabídky změnit.
                val variant = settings.onDeviceAiVariant.value
                val ai = onDeviceAi
                val speech = speechToText
                val chunks: Flow<String> = when {
                    ai != null && gemmaTranscribes(settings.onDeviceAiEnabled.value, variant) ->
                        ai.transcribe(variant, path, platformLanguageCode).map { it.text }
                    speech != null && systemTranscribes() -> speech.transcribe(path, platformLanguageCode)
                    else -> throw IllegalStateException("Přepis v telefonu už není k dispozici")
                }
                chunks.collect { chunk ->
                    text = chunk
                    local.set(chatId, messageId, SpeechTranscript.Pending(text))
                }
                local.set(
                    chatId,
                    messageId,
                    if (text.isBlank()) SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other) else SpeechTranscript.Done(text.trim()),
                )
            } catch (e: CancellationException) {
                local.set(chatId, messageId, SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other))
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Přepis zprávy $messageId v telefonu se nepovedl." }
                local.set(chatId, messageId, SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other))
            }
        }
    }

    /** Přepíše Gemma (zapnutá AI, model se zvukovým vstupem připravený)? */
    private suspend fun gemmaTranscribes(enabled: Boolean, variant: ModelVariant): Boolean {
        val ai = onDeviceAi ?: return false
        return enabled && runCatching { ai.canTranscribe(variant) }.getOrDefault(false)
    }

    /**
     * Přepíše systém telefonu (`SpeechToText`)? Nezávisle na volbě AI v zařízení: není to jazykový
     * model, nic se nestahuje a zvuk telefon neopouští — stejná povaha jako rozpoznání textu
     * z fotky, které taky žádnou volbu nemá.
     */
    private suspend fun systemTranscribes(): Boolean {
        val speech = speechToText ?: return false
        return runCatching { speech.canTranscribe(platformLanguageCode) }.getOrDefault(false)
    }

    /**
     * Pustí video kolečko se zvukem, nebo ho vrátí do tiché smyčky.
     *
     * Kolečko se zvukem je jedno na konverzaci a hrající hlasovka se zastaví. Příchozí
     * nezhlédnuté se nahlásí jako zhlédnuté — protějšek to vidí stejně jako u hlasovky.
     */
    fun toggleVideoNoteSound(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val note = message.media as? MessageMedia.VideoNote ?: return
        if (state.value.soundingVideoNote == messageId) {
            state.update { it.copy(soundingVideoNote = null) }
            return
        }
        voicePlayer.stop()
        state.update { it.copy(soundingVideoNote = messageId, soundingVideoNoteFromMs = 0L) }
        if (!message.isOutgoing && !note.isViewed) reportListened(messageId)
    }

    /**
     * Poslechnuté hlasovky a zhlédnutá kolečka, která diskrétní čtení zatím NENAHLÁSILO —
     * odejdou s přečtením chatu ([readNow]). Odchod z chatu je zahodí: protějšek se o poslechu
     * nedozví a po návratu je hlasovka zase neposlechnutá, jak ji vede server.
     */
    val discreetListened = LinkedHashSet<Long>()

    /**
     * Označí hlasovku za poslechnutou lokálně i na serveru. Při diskrétním čtení jen lokálně
     * ([discreetListened]): poslech je stejné prozrazení jako přečtení.
     */
    fun reportListened(messageId: Long) {
        markListened(messageId)
        if (settings.discreetReading.value) {
            discreetListened += messageId
            logger.d { "Diskrétní čtení: poslech zprávy $messageId se nehlásí" }
            return
        }
        scope.launch {
            repository.openContent(chatId, messageId).onFailure { failure ->
                logger.w(failure) { "Poslechnutí hlasovky nebo kolečka se nepodařilo nahlásit." }
            }
        }
    }

    fun seekVoice(messageId: Long, fraction: Float) {
        voicePlayer.seek(VoiceKey(chatId, messageId), fraction)
    }

    /**
     * Po dohrání pustí NÁSLEDUJÍCÍ hlasovku, a jen když je příchozí a neposlechnutá.
     *
     * Řetězí se tím poslech dávky hlasovek, které někdo poslal za sebou — to je ten případ, kvůli
     * kterému funkce existuje. Přeskakovat k neposlechnuté někde dál v historii by naopak
     * překvapilo: uživatel si pustil jednu starou hlasovku a najednou mu hraje něco úplně jiného.
     * Proto se bere jen ta nejbližší novější, a když poslechnutá je, řetěz končí.
     *
     * Vlastní odchozí hlasovku nikdo „neposlechl" v tomhle smyslu, takže na ní řetěz taky končí.
     */
    fun playNextUnlistened(after: Long) {
        // Skladby řetězí MusicQueue (hrají i bez konverzace); hlasovky se hudbou neřetězí.
        val finished = state.value.messages.firstOrNull { it.id == after }
        if (finished?.media is MessageMedia.Audio) return
        val next = state.value.messages
            .filter { it.id > after && it.media is MessageMedia.VoiceNote }
            .minByOrNull { it.id }
            ?: return
        val voice = next.media as? MessageMedia.VoiceNote ?: return
        if (next.isOutgoing || voice.isListened) return

        voicePlayer.play(VoiceKey(chatId, next.id)) { voicePath(voice) }
        reportListened(next.id)
    }

    /** Počká na stažený soubor hlasovky; stahování začne až tímhle odběrem. */
    suspend fun voicePath(voice: MessageMedia.VoiceNote): String = filePath(voice.fileId)

    /** Počká na stažený soubor; stahování začne až tímhle odběrem. */
    private suspend fun filePath(fileId: Int): String =
        when (val file = fileRepository.file(fileId).first { it is FileState.Ready || it is FileState.Failed }) {
            is FileState.Ready -> file.path
            is FileState.Failed -> error(file.message)
            else -> error("Nedosažitelné")
        }

    fun markListened(messageId: Long) {
        state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id != messageId) return@map message
                    when (val media = message.media) {
                        is MessageMedia.VoiceNote ->
                            if (media.isListened) message else message.copy(media = media.copy(isListened = true))
                        is MessageMedia.VideoNote ->
                            if (media.isViewed) message else message.copy(media = media.copy(isViewed = true))
                        else -> message
                    }
                },
            )
        }
    }
}
