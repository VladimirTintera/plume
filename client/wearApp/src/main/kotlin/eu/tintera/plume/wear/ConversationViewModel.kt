package eu.tintera.plume.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.SentMessageUpdate
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.TranscribeOffer
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.telegram.transcribeOffer
import eu.tintera.plume.core.speech.SpeechToText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = Logger.withTag("WearConversation")

/** Jak dlouho se čeká na přepis ze serveru, než přijde na řadu telefon. */
private val SERVER_TIMEOUT = 60.seconds

/** Kolik zpráv se načte při otevření a při každém dočtení — na hodinkách se dlouhá historie nečte. */
private const val PAGE = 30

/**
 * Stav konverzace: zprávy, dočítání starší historie a citované zprávy, s odběry od TDLib.
 *
 * **Ve ViewModelu, ne v kompozici obrazovky.** Výběr reakce a nahrávání hlasovky jsou
 * samostatné cesty navigace; obrazovka konverzace při nich opustí kompozici. Se
 * stavem v `remember` se po návratu sestavila znovu, načetla zprávy a sjela na nejnovější —
 * reakce na starší zprávu tak uživatele vždycky vrátila dolů (nahlášeno z Pixel Watch).
 * ViewModel patří položce navigace a žije, dokud je konverzace v zásobníku.
 *
 * Otevřený chat se TDLib ohlásí (`openChat`) při vzniku a zavře (`closeChat`) až při zániku —
 * i během výběru reakce je chat pořád otevřený. Zobrazené a příchozí zprávy se hlásí jako přečtené.
 */
class ConversationViewModel(
    private val chatId: Long,
    private val conversations: ConversationRepository,
    private val appScope: CoroutineScope,
    private val chats: ChatRepository,
    private val files: FileRepository,
    private val phone: PhoneTranscription,
    private val speech: SpeechToText,
) : ViewModel() {

    var messages by mutableStateOf<List<ChatMessage>?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    /** Dočítání starší historie: běží-li zrovna, a jestli už začátek chatu přišel. */
    var loadingOlder by mutableStateOf(false)
        private set
    private var historyEnd = false

    /** Citované zprávy, které v načtené historii nejsou (dočtené z TDLib); `null` = zpráva neexistuje. */
    var quoted by mutableStateOf<Map<Long, ChatMessage?>>(emptyMap())
        private set

    /**
     * Ukazatel „protějšek přečetl až sem" (`lastReadOutboxMessageId` z TDLib): odchozí zprávy
     * s menším nebo rovným id jsou přečtené. Přečtení není ve zprávě, ale na chatu — proto
     * zvlášť, ne v [messages].
     */
    var lastReadOutboxId by mutableStateOf(0L)
        private set

    /**
     * Přepisy hlasovek vyžádané na hodinkách (z telefonu nebo z hodinek), podle id zprávy. Přepis
     * ze serveru nese sama zpráva (`VoiceNote.transcript`); tady je, co server nedal. Jen v paměti
     * konverzace — telefon si svůj přepis uloží sám (`LocalTranscripts`).
     */
    var transcripts by mutableStateOf<Map<Long, SpeechTranscript>>(emptyMap())
        private set

    /** Proč nevyšel nepovedený přepis v [transcripts]; hodinky ho ukážou místo obecné hlášky. */
    var transcribeFailures by mutableStateOf<Map<Long, TranscribeFailure>>(emptyMap())
        private set

    /**
     * Nejnovější zpráva, na kterou už seznam sjel. Po návratu z vedlejší obrazovky se nesjíždí
     * znovu (pozici drží uložený stav seznamu), dolů jen s opravdu novou zprávou.
     */
    var scrolledToId: Long? = null

    init {
        viewModelScope.launch {
            conversations.openChat(chatId)
            // Nové, upravené, smazané, potvrzené a reakce se odebírají DŘÍV než historie, ať se
            // nic mezi tím neztratí.
            launch {
                conversations.newMessages(chatId).collect { message ->
                    upsert(message)
                    if (!message.isOutgoing) conversations.markRead(chatId, listOf(message.id))
                }
            }
            launch {
                conversations.editedMessages(chatId).collect { edited ->
                    messages = messages?.map { if (it.id == edited.id) edited else it }
                }
            }
            launch {
                conversations.deletedMessages(chatId).collect { ids -> messages = messages?.filterNot { it.id in ids } }
            }
            launch {
                // TDLib po potvrzení zprávě změní id: dočasnou je potřeba nahradit, ne aktualizovat.
                conversations.sentMessageUpdates(chatId).collect { update ->
                    val (oldId, message) = when (update) {
                        is SentMessageUpdate.Succeeded -> update.oldMessageId to update.message
                        is SentMessageUpdate.Failed -> update.oldMessageId to update.message
                    }
                    messages = (messages.orEmpty().filterNot { it.id == oldId || it.id == message.id } + message).sortedBy { it.id }
                }
            }
            launch {
                // Ukazatel jen roste; update i počáteční čtení můžou dorazit v libovolném pořadí.
                conversations.readOutboxUpdates(chatId).collect { lastReadOutboxId = maxOf(lastReadOutboxId, it) }
            }
            launch {
                conversations.chatInfo(chatId)
                    .onSuccess { lastReadOutboxId = maxOf(lastReadOutboxId, it.lastReadOutboxMessageId) }
                    .onFailure { logger.w(it) { "Stav přečtení chatu $chatId se na hodinkách nenačetl." } }
            }
            launch {
                conversations.interactionUpdates(chatId).collect { change ->
                    messages = messages?.map { if (it.id == change.messageId) it.copy(reactions = change.reactions) else it }
                }
            }
            // Citace odpovědi bere zprávu z načtené historie; co v ní není, dočte jednou z TDLib.
            launch {
                snapshotFlow { messages }.collect { loaded -> fetchMissingQuotes(loaded.orEmpty()) }
            }
            loadHistory()
                .onSuccess { loaded ->
                    messages = (loaded + messages.orEmpty()).distinctBy { it.id }.sortedBy { it.id }
                    if (loaded.isNotEmpty()) conversations.markRead(chatId, loaded.map { it.id })
                }
                .onFailure {
                    logger.w(it) { "Historie chatu $chatId se na hodinkách nenačetla." }
                    failed = true
                }
        }
    }

    /**
     * Přepíše hlasovku: napřed server (Premium nebo zkušební přepisy — umí to nejlíp), pak telefon
     * (Gemma nebo systémový rozpoznávač, [PhoneTranscription]), nakonec systémový rozpoznávač
     * hodinek ([speech]). Každý krok, který nejde nebo selže, předá dalšímu; až když nevyjde
     * žádný, je přepis nepovedený.
     */
    fun transcribe(messageId: Long) {
        val voice = messages?.firstOrNull { it.id == messageId }?.media as? MessageMedia.VoiceNote ?: return
        if (transcripts[messageId] is SpeechTranscript.Pending) return
        setTranscript(messageId, SpeechTranscript.Pending(""))
        viewModelScope.launch {
            fromServer(messageId, voice.durationSeconds)?.let { return@launch setTranscript(messageId, SpeechTranscript.Done(it)) }
            val phone = fromPhone(messageId)
            if (phone is PhoneResult.Done) return@launch setTranscript(messageId, SpeechTranscript.Done(phone.text))
            val watchTries = watchTranscribes()
            if (watchTries) onWatch(messageId, voice.fileId)?.let { return@launch setTranscript(messageId, SpeechTranscript.Done(it)) }
            // Co poradit: důvod z telefonu, když se dá něco udělat (zapnout Gemmu, přihlásit
            // týž účet). Telefon mimo dosah s rozběhnutým přepisem na hodinkách ale znamená, že
            // se nepovedl ten na hodinkách, a „telefon není v dosahu" by mátlo.
            val reason = (phone as PhoneResult.Failed).reason
            val shown = if (watchTries && reason !is TranscribeFailure.PhoneCantTranscribe && reason != TranscribeFailure.WrongAccount) {
                TranscribeFailure.Other
            } else {
                reason
            }
            transcribeFailures = transcribeFailures + (messageId to shown)
            setTranscript(messageId, SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other))
        }
    }

    private fun setTranscript(messageId: Long, transcript: SpeechTranscript) {
        transcripts = transcripts + (messageId to transcript)
    }

    /**
     * Přepis ze serveru, jestli ho server dá ([transcribeOffer]). Výsledek nevrací volání, přijde
     * úpravou zprávy — čeká se na hotový nebo nepovedený přepis v obsahu zprávy.
     */
    private suspend fun fromServer(messageId: Long, durationSeconds: Int): String? {
        if (transcribeOffer(chats.isPremium.value, chats.speechTrial.value, durationSeconds) != TranscribeOffer.Available) return null
        conversations.recognizeSpeech(chatId, messageId).onFailure {
            logger.i { "Server přepis zprávy $messageId nepřijal, zkusí se telefon." }
            return null
        }
        val result = withTimeoutOrNull(SERVER_TIMEOUT) {
            snapshotFlow { (messages?.firstOrNull { it.id == messageId }?.media as? MessageMedia.VoiceNote)?.transcript }
                .first { it is SpeechTranscript.Done || it is SpeechTranscript.Failed }
        }
        return (result as? SpeechTranscript.Done)?.text
    }

    /** Přepis v telefonu; účet hodinek jde s žádostí, telefon na jiném účtu nepřepisuje. */
    private suspend fun fromPhone(messageId: Long): PhoneResult {
        val account = chats.savedMessagesChatId.value ?: return PhoneResult.Failed(TranscribeFailure.Other)
        return phone.transcribe(account, chatId, messageId)
    }

    /** Umí systémový rozpoznávač hodinek jazyk aplikace (Wear OS 4+, nainstalovaný jazyk)? */
    private suspend fun watchTranscribes(): Boolean = runCatching { speech.canTranscribe(platformLanguageCode) }.getOrDefault(false)

    /** Systémový rozpoznávač hodinek, průběh rovnou ukazuje. */
    private suspend fun onWatch(messageId: Long, fileId: Int): String? = runCatching {
        val file = files.file(fileId).first { it is FileState.Ready || it is FileState.Failed }
        val path = (file as? FileState.Ready)?.path ?: return null
        var text = ""
        speech.transcribe(path, platformLanguageCode).collect { chunk ->
            text = chunk
            setTranscript(messageId, SpeechTranscript.Pending(chunk))
        }
        text.trim().takeIf { it.isNotEmpty() }
    }.onFailure { logger.w(it) { "Přepis zprávy $messageId na hodinkách se nepovedl." } }.getOrNull()

    /** Zpráva odeslaná z hodinek (dočasné id) nebo nově příchozí, na své místo podle id. */
    fun upsert(message: ChatMessage) {
        messages = (messages.orEmpty().filterNot { it.id == message.id } + message).sortedBy { it.id }
    }

    /** Dočte [PAGE] starších zpráv od nejstarší načtené; prázdná stránka = začátek chatu. */
    fun loadOlder() {
        val oldest = messages?.firstOrNull { it.sendState == null }?.id ?: return
        if (loadingOlder || historyEnd) return
        loadingOlder = true
        viewModelScope.launch {
            loadHistory(fromMessageId = oldest)
                .onSuccess { older ->
                    if (older.isEmpty()) historyEnd = true
                    messages = (older + messages.orEmpty()).distinctBy { it.id }.sortedBy { it.id }
                }
                .onFailure { logger.w(it) { "Starší historie chatu $chatId se na hodinkách nenačetla." } }
            loadingOlder = false
        }
    }

    private suspend fun fetchMissingQuotes(loaded: List<ChatMessage>) {
        val missing = loaded.mapNotNull { it.replyToMessageId }
            .filter { id -> id !in quoted && loaded.none { it.id == id } }
            .distinct()
        if (missing.isEmpty()) return
        quoted = quoted + missing.associateWith { id -> conversations.message(chatId, id).getOrNull() }
    }

    /**
     * [PAGE] zpráv starších než [fromMessageId] (0 = nejnovější). `getChatHistory` vrací TDLib často
     * jen to, co má v lokální databázi (klidně jednu zprávu), takže se dočítá od nejstarší, dokud
     * stránka není plná nebo historie neskončí.
     */
    private suspend fun loadHistory(fromMessageId: Long = 0L): Result<List<ChatMessage>> = runCatching {
        val loaded = mutableListOf<ChatMessage>()
        var from = fromMessageId
        repeat(3) {
            if (loaded.size >= PAGE) return@runCatching loaded
            val page = conversations.history(chatId, fromMessageId = from, limit = PAGE - loaded.size).getOrThrow()
                .filterNot { message -> loaded.any { it.id == message.id } }
            if (page.isEmpty()) return@runCatching loaded
            loaded += page
            from = loaded.minOf { it.id }
        }
        loaded
    }

    // Zavření v aplikačním scope: viewModelScope je v tu chvíli už zrušený.
    override fun onCleared() {
        appScope.launch { conversations.closeChat(chatId) }
    }
}
