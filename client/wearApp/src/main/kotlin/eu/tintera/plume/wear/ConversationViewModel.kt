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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = Logger.withTag("WearConversation")

/** Kolik zpráv se načte při otevření a při každém dočtení — na hodinkách se dlouhá historie nečte. */
private const val PAGE = 30

/**
 * Stav konverzace: zprávy, dočítání starší historie a citované zprávy, s odběry od TDLib.
 *
 * **Ve ViewModelu, ne v kompozici obrazovky.** Výběr reakce, fotka přes celý displej a nahrávání
 * hlasovky jsou samostatné cesty navigace; obrazovka konverzace při nich opustí kompozici. Se
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
