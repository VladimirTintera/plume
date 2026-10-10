package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.SpeechTranscript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** Hotový přepis z modelu v telefonu, uložený v [AppSettings]. [accountId] drží účty od sebe. */
@Serializable
data class StoredTranscript(
    val accountId: Long,
    val chatId: Long,
    val messageId: Long,
    val text: String,
    val savedAt: Long,
)

/**
 * Přepisy hlasovek a koleček z modelu v telefonu. Server přepis ukládá do zprávy
 * (`speechRecognitionResult`), tenhle ne — zprávu na serveru měnit nejde.
 *
 * - **Hotový přepis se ukládá** ([AppSettings.storedTranscripts]), takže přežije restart:
 *   přepis v telefonu trvá vteřiny až desítky vteřin a dělat ho kvůli restartu znovu by
 *   zbytečně pálilo baterii. **Rozpracovaný a nepovedený jen v paměti** — po restartu se
 *   rozpracovaný stejně nedokončí a nepovedený jde zkusit znovu.
 * - **Nejvýš [MAX_STORED] přepisů**, nejstarší pryč. Text hlasovky je obsah zprávy, ne nastavení;
 *   neomezený seznam by úložiště nastavení nafukoval a četl se při každém startu.
 * - **Účet je id Uložených zpráv**, jako u záložek ([eu.tintera.plume.core.bookmarks.MessageBookmarks]).
 *   Dokud není známý, hotový přepis zůstane jen v paměti.
 *
 * Jeden na proces (Koin `single`): odchod z chatu přepis nezahodí ani rozpracovaný.
 */
class LocalTranscripts(private val settings: AppSettings, private val chats: ChatRepository) {
    private val memory = MutableStateFlow<Map<Pair<Long, Long>, SpeechTranscript>>(emptyMap())

    /** Přepisy přihlášeného účtu podle (chat, zpráva); rozpracovaný v paměti přebíjí uložený. */
    val state: Flow<Map<Pair<Long, Long>, SpeechTranscript>> =
        combine(memory, settings.storedTranscripts, chats.savedMessagesChatId) { inMemory, stored, account ->
            storedFor(stored, account) + inMemory
        }

    fun get(chatId: Long, messageId: Long): SpeechTranscript? =
        memory.value[chatId to messageId]
            ?: storedFor(settings.storedTranscripts.value, chats.savedMessagesChatId.value)[chatId to messageId]

    fun set(chatId: Long, messageId: Long, transcript: SpeechTranscript) {
        // Do paměti vždycky, i hotový: zápis do úložiště je asynchronní a bez toho by přepis
        // mezi koncem generování a zápisem na okamžik zmizel.
        memory.update { it + ((chatId to messageId) to transcript) }
        val account = chats.savedMessagesChatId.value
        if (transcript !is SpeechTranscript.Done || account == null) return
        val saved = StoredTranscript(account, chatId, messageId, transcript.text, Clock.System.now().toEpochMilliseconds())
        settings.setStoredTranscripts(
            (settings.storedTranscripts.value.filterNot { it.accountId == account && it.chatId == chatId && it.messageId == messageId } + saved)
                .sortedBy { it.savedAt }
                .takeLast(MAX_STORED),
        )
    }

    private fun storedFor(stored: List<StoredTranscript>, account: Long?): Map<Pair<Long, Long>, SpeechTranscript> =
        if (account == null) {
            emptyMap()
        } else {
            stored.filter { it.accountId == account }.associate { (it.chatId to it.messageId) to SpeechTranscript.Done(it.text) }
        }

    private companion object {
        const val MAX_STORED = 300
    }
}
