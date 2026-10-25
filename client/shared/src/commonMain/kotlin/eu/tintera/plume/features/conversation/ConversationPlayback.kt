package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.audio.MusicQueueControl
import eu.tintera.plume.core.audio.NowPlaying
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayerControl
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.speech.SpeechDownload
import eu.tintera.plume.core.speech.SpeechToText
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.SpeechTrial
import eu.tintera.plume.core.telegram.MAX_ON_DEVICE_SECONDS
import eu.tintera.plume.core.telegram.OnDeviceFix
import eu.tintera.plume.core.telegram.TranscribeOffer
import eu.tintera.plume.core.telegram.transcribeOffer
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.transcribe_failed
import eu.tintera.plume.resources.transcript_language_download_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Přehrávání v konverzaci: hlasovky (i řetězení neposlechnutých), kolečko se zvukem, zvuk
 * v článku, čas v médiu („1:23" v textu), hudba v mini přehrávači, hlášení poslechu (s ohledem
 * na diskrétní čtení) a přepis řeči na serveru nebo v telefonu.
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
    private val voicePlayer: VoicePlayerControl,
    private val musicQueue: MusicQueueControl,
    private val fileRepository: FileRepository,
    private val localTranscripts: LocalTranscripts?,
    private val onDeviceAi: OnDeviceAi?,
    private val speechToText: SpeechToText?,
    /** Video v náhledu odkazu se od času v textu otevře na webu, touž cestou jako odkaz. */
    private val openLink: (url: String) -> Unit,
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

    /** Dlouhé nahrávky, u kterých už „Pokračuje od…" zaznělo — po pauze a znovu se neopakuje. */
    private val announcedResumes = HashSet<ResumedAudio>()

    /**
     * Hlasovka nebo skladba z TOHOTO chatu se rozehrála z pamatované pozice (viz `audioStart`):
     * obrazovka ukáže „Pokračuje od 12:34" s „Od začátku". Bez toho by nahrávka hrála uprostřed
     * bez vysvětlení — stejná úvaha jako u videa.
     *
     * Jen na začátku poslechu ([RESUME_NOTICE_WINDOW_MS]): kdo se do chatu vrátí k nahrávce, která
     * hraje už pět minut, nepotřebuje slyšet, odkud začala.
     */
    val resumes = voicePlayer.state
        .map { playback ->
            val from = playback?.resumedFromMs ?: return@map null
            if (playback.key.chatId != chatId || playback.loading) return@map null
            // Mimo okno i PŘED ním: po „Od začátku" (nebo posunu zpět) nahrávka hraje před pozicí,
            // od které se pokračovalo, a návrat do chatu by hlášení ukázal znovu.
            if (playback.positionMs !in from..from + RESUME_NOTICE_WINDOW_MS) return@map null
            ResumedAudio(playback.key.messageId, from)
        }
        .distinctUntilChanged()
        .filterNotNull()
        .filter { announcedResumes.add(it) }

    /** „Od začátku" ze snackbaru: tatáž nahrávka hraje dál, jen od nuly. */
    fun restartAudio(messageId: Long) {
        voicePlayer.seek(VoiceKey(chatId, messageId), 0f)
    }

    /**
     * Pamatovat pozici v nahrávce této zprávy? V tajném chatu ne (po něm nemá v zařízení zůstat
     * stopa) a u mizející zprávy ne — stejně jako u videa.
     */
    private fun remembersPosition(messageId: Long): Boolean {
        if (state.value.info?.isSecret == true) return false
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return false
        return message.selfDestructType == null
    }

    private fun resumeKey(messageId: Long, fileKey: String?): String? = fileKey?.takeIf { remembersPosition(messageId) }

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

    /** Přepis v telefonu (Gemma nebo systém), sdílený s přepisem pro hodinky. */
    private val onDeviceTranscription = localTranscripts?.let {
        OnDeviceTranscription(settings, fileRepository, it, onDeviceAi, speechToText)
    }

    /**
     * Podnět k nové kontrole přepisu v telefonu: stažená Gemma nastavení nemění, takže by
     * otevřená konverzace dál radila „stáhni Gemmu". Zvedne ho ťuknutí na nedostupný přepis.
     */
    private val onDeviceRecheck = MutableStateFlow(0)

    /** Stahuje se jazyk pro rozpoznávač na žádost z bubliny ([downloadSpeechLanguage])? */
    private val speechDownloading = MutableStateFlow(false)

    /**
     * Všechno, co rozhoduje o přepisu záznamu, v jednom toku: Premium, zkušební přepisy, jestli
     * přepíše telefon, a přepisy z telefonu v tomhle chatu. Jeden tok: bubliny ho čtou přes
     * `ScreenItemScope` jedním odběrem, ne čtyřmi.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val transcribeContext: StateFlow<TranscribeContext> = combine(
        chatRepository.isPremium,
        chatRepository.speechTrial,
        combine(
            combine(settings.onDeviceAiEnabled, settings.onDeviceAiVariant, onDeviceRecheck) { enabled, variant, _ -> enabled to variant }
                .mapLatest { (enabled, variant) -> onDeviceTranscription?.check(enabled, variant) ?: OnDeviceCheck(available = false) },
            speechDownloading,
        ) { check, downloading ->
            // Než systém stažení ohlásí, říká bublina „stahuje se" podle vlastní žádosti.
            if (downloading && !check.available) check.copy(fix = OnDeviceFix.SpeechLanguageDownloading) else check
        },
        localTranscripts?.state?.map { all -> all.filterKeys { it.first == chatId }.mapKeys { it.key.second } }
            ?: flowOf(emptyMap()),
    ) { premium, trial, onDevice, local -> TranscribeContext(premium, trial, onDevice.available, onDevice.fix, local) }
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
            musicQueue.toggle(chatId, messageId, audio, rememberPosition = remembersPosition(messageId))
            return
        }
        val voice = message.media as? MessageMedia.VoiceNote ?: return

        // Zvuk hraje vždycky jen jeden: hlasovka umlčí kolečko a naopak.
        state.update { it.copy(soundingVideoNote = null) }
        voicePlayer.toggle(VoiceKey(chatId, messageId), resumeKey = resumeKey(messageId, voice.fileKey)) { voicePath(voice) }
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
        when (transcribeOffer(context.isPremium, context.trial, duration, context.onDevice, context.onDeviceFix)) {
            TranscribeOffer.OnDevice -> {
                transcribeOnDevice(messageId, fileId)
                return
            }
            // Bublina vysvětluje, co zapnout; uživatel to mohl mezitím udělat (stáhnout Gemmu),
            // takže se to ověří znovu a rovnou přepíše.
            is TranscribeOffer.Unavailable -> {
                scope.launch { recheckAndTranscribe(messageId, fileId, duration) }
                return
            }
            else -> Unit
        }
        scope.launch {
            repository.recognizeSpeech(chatId, messageId).onFailure { failure ->
                logger.w(failure) { "Přepis zprávy $messageId se nepodařilo spustit." }
                state.update { it.copy(notice = failure.userMessage(Res.string.transcribe_failed)) }
            }
        }
    }

    /**
     * Stáhne jazyk aplikace pro systémový rozpoznávač (tlačítko v bublině u
     * [OnDeviceFix.DownloadSpeechLanguage]) a po stažení zprávu [messageId] rovnou přepíše.
     * Běží v [appScope]: stahuje se desítky vteřin a odchod z chatu to nemá zahodit.
     */
    fun downloadSpeechLanguage(messageId: Long) {
        val transcription = onDeviceTranscription ?: return
        val media = state.value.messages.firstOrNull { it.id == messageId }?.media
        val (fileId, duration) = when (media) {
            is MessageMedia.VoiceNote -> media.fileId to media.durationSeconds
            is MessageMedia.VideoNote -> media.fileId to media.durationSeconds
            else -> return
        }
        appScope.launch {
            // Napřed „stahuje se", ať tlačítko nejde ťuknout dvakrát a bublina neradí to, co už běží.
            speechDownloading.value = true
            val result = try {
                transcription.downloadSpeechLanguage()
            } finally {
                speechDownloading.value = false
            }
            if (result == SpeechDownload.Failed) {
                state.update { it.copy(notice = UiText(Res.string.transcript_language_download_failed)) }
            }
            recheckAndTranscribe(messageId, fileId, duration)
        }
    }

    /** Ověří přepis v telefonu znovu (obrazovka se překreslí) a když už jde, rovnou přepíše. */
    private suspend fun recheckAndTranscribe(messageId: Long, fileId: Int, duration: Int) {
        val transcription = onDeviceTranscription ?: return
        val check = transcription.check()
        onDeviceRecheck.update { it + 1 }
        if (check.available && duration <= MAX_ON_DEVICE_SECONDS) transcribeOnDevice(messageId, fileId)
    }

    /**
     * Přepis v telefonu ([OnDeviceTranscription]): soubor se stáhne, model ho přepíše a výsledek
     * jde do [LocalTranscripts] — zprávu na serveru měnit nejde. Běží v [appScope]: přepis trvá
     * vteřiny až desítky vteřin a odchod z chatu ho nemá zahodit.
     */
    private fun transcribeOnDevice(messageId: Long, fileId: Int) {
        val transcription = onDeviceTranscription ?: return
        if (localTranscripts?.get(chatId, messageId) is SpeechTranscript.Pending) return
        appScope.launch { transcription.transcribe(chatId, messageId, fileId) }
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

        voicePlayer.play(VoiceKey(chatId, next.id), resumeKey = resumeKey(next.id, voice.fileKey)) { voicePath(voice) }
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

    /** Obrazovka chat otevřela. Bez toho by se po otočení displeje otevřel znovu. */
    /**
     * Čas v médiu („1:23"). Médium je v téže zprávě, nebo ve zprávě, na kterou odpovídá
     * (`hasTimestampedMedia`) — tak to definuje TDLib. Video se otevře v prohlížeči od toho
     * místa (navigace je věc obrazovky, proto stav [ConversationState.openMediaAt]); hlasovka,
     * skladba a kolečko hrají v chatu, jako po ťuknutí na ně; video z náhledu odkazu se otevře na
     * webu od toho místa.
     */
    fun playMediaAt(messageId: Long, seconds: Int) {
        val current = state.value
        val message = current.messages.firstOrNull { it.id == messageId } ?: return
        val target = timestampTarget(message, current.messages, current.quotes) ?: return
        val startMs = seconds * 1000L
        when (val media = target.media) {
            is MessageMedia.Video -> state.update { it.copy(openMediaAt = MediaAt(target.id, seconds)) }
            is MessageMedia.VoiceNote -> {
                state.update { it.copy(soundingVideoNote = null) }
                voicePlayer.play(VoiceKey(chatId, target.id), startMs = startMs, resumeKey = resumeKey(target.id, media.fileKey)) {
                    voicePath(media)
                }
                if (!target.isOutgoing && !media.isListened) reportListened(target.id)
            }
            is MessageMedia.Audio -> {
                state.update { it.copy(soundingVideoNote = null) }
                musicQueue.play(chatId, target.id, media, startMs, rememberPosition = remembersPosition(target.id))
            }
            is MessageMedia.VideoNote -> {
                voicePlayer.stop()
                state.update { it.copy(soundingVideoNote = target.id, soundingVideoNoteFromMs = startMs) }
                if (!target.isOutgoing && !media.isViewed) reportListened(target.id)
            }
            // Video v náhledu odkazu (YouTube…) v aplikaci nepřehráváme: otevře se jeho web
            // od toho místa, kde to adresa umí (`videoLinkAt`).
            else -> target.linkPreview?.takeIf { it.isVideo }?.let { openLink(videoLinkAt(it.url, seconds)) }
        }
    }
}

/** Nahrávka ([messageId]), která se rozehrála z pamatované pozice [fromMs]. */
data class ResumedAudio(val messageId: Long, val fromMs: Long)

/** Jak dlouho od začátku poslechu má smysl hlásit, odkud se pokračuje. */
private const val RESUME_NOTICE_WINDOW_MS = 6_000L
