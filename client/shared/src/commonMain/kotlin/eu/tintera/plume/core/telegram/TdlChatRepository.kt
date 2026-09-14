package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatList
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatPosition
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVoiceNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Živý seznam chatů nad TDLib.
 *
 * **Proč to není prosté `getChats()`:** TDLib nefunguje jako REST. `getChats` vrátí jen id těch
 * chatů, které už má v lokální databázi, a od té chvíle se **celý další vývoj děje updaty** —
 * nový chat, změna pořadí, nová poslední zpráva, přečtení. Klient si proto musí držet vlastní
 * mapu a řadit ji sám. Kdo tohle obejde periodickým dotazováním, dostane blikající seznam
 * a zbytečný provoz.
 *
 * **Řazení:** `ChatPosition.order` je int64, do kterého Telegram kóduje i připnutí — připnuté chaty
 * dostanou velmi vysoký order. Takže stačí řadit sestupně podle `order` a nic dopočítávat.
 * `order == 0` znamená, že chat v dané složce **není** — musí ze seznamu zmizet.
 */
class TdlChatRepository(
    holder: TelegramClientHolder,
    private val session: TelegramSession,
    private val scope: CoroutineScope,
) : ChatRepository {

    private val logger = Logger.withTag("Chats")

    private val client: TdlClient = holder.client

    /** Zdroj pravdy. Klíč je chatId; pořadí drží [ChatSummary.order], ne pozice v mapě. */
    private val known = mutableMapOf<Long, ChatSummary>()
    private val mutex = Mutex()

    private val _chats = MutableStateFlow<ChatListState>(ChatListState.Loading)
    override val chats: StateFlow<ChatListState> = _chats.asStateFlow()

    private var exhausted = false
    private var started = false

    init {
        start()
    }

    private fun start() {
        if (started) return
        started = true

        // Odběry musí stát dřív, než odejde první `loadChats` — jinak se první dávka updatů ztratí
        // a seznam zůstane prázdný, i když data v databázi jsou. Stejný důvod jako u auth automatu.
        logger.d { "Repository startuje — odběry se registrují." }
        collectUpdates()

        // Chaty se dají číst až po přihlášení; předtím TDLib odpoví chybou.
        scope.launch {
            session.authStage.collect { stage ->
                when (stage) {
                    AuthStage.Ready -> loadMore()
                    AuthStage.LoggedOut -> clear()
                    else -> Unit
                }
            }
        }
    }

    private fun collectUpdates() {
        // Nový chat. Pozice může být prázdná (chat je znám, ale v žádné složce) — pak se neukáže.
        scope.launch {
            client.newChatUpdates.collect { update -> upsert(update.chat) }
        }

        // Změna pozice v konkrétní složce. Tohle je hlavní zdroj přeskládání seznamu.
        scope.launch {
            client.chatPositionUpdates.collect { update ->
                applyPosition(update.chatId, update.position)
            }
        }

        // Nová poslední zpráva. Nese s sebou i nové pozice, takže se aktualizuje obojí najednou.
        scope.launch {
            client.chatLastMessageUpdates.collect { update ->
                edit(update.chatId) { current ->
                    val position = update.positions.mainPosition()
                    current.copy(
                        lastMessagePreview = update.lastMessage?.preview(),
                        order = position?.order ?: current.order,
                        isPinned = position?.isPinned ?: current.isPinned,
                    )
                }
            }
        }

        scope.launch {
            client.chatReadInboxUpdates.collect { update ->
                edit(update.chatId) { it.copy(unreadCount = update.unreadCount) }
            }
        }

        scope.launch {
            client.chatTitleUpdates.collect { update ->
                edit(update.chatId) { it.copy(title = update.title) }
            }
        }

        // Odebrání ze složky — chat sice dál existuje, ale v hlavním seznamu nemá co dělat.
        scope.launch {
            client.chatRemovedFromListUpdates.collect { update ->
                if (update.chatList.isMain()) remove(update.chatId)
            }
        }
    }

    override suspend fun loadMore() {
        if (exhausted) return

        // `loadChats` vrací Ok, ne data — jen řekne TDLib, ať dotáhne další kus ze serveru.
        // Chyba 404 znamená „už není co načítat", což není selhání, ale konec seznamu.
        when (val result = client.loadChats(chatList = ChatListMain(), limit = PAGE_SIZE)) {
            is TdlResult.Failure -> {
                if (result.code == NOT_FOUND) {
                    exhausted = true
                    logger.d { "Seznam chatů je celý načtený." }
                } else {
                    logger.e { "loadChats selhalo: ${result.code} ${result.message}" }
                    // Když už nějaké chaty máme, chybu nedeklarujeme jako prázdný stav —
                    // uživatel by přišel o seznam kvůli selhání dotažení dalších.
                    if (known.isEmpty()) {
                        _chats.value = ChatListState.Failed(result.message)
                        return
                    }
                }
            }

            is TdlResult.Success -> Unit
        }

        syncFromLocalDatabase()
    }

    /**
     * Přečte aktuální obsah hlavní složky z **lokální databáze** TDLib a doplní ho do mapy.
     *
     * Proč to nestačí nechat na updatech: updaty chodí jen na ZMĚNY. Když se repository vytvoří
     * (nebo se odběr zaregistruje) až po `AuthorizationStateReady`, první dávka `UpdateNewChat`
     * už proletěla do prázdna a seznam by zůstal navždy prázdný. Tohle je ta pojistka — updaty
     * pak drží seznam živý, tenhle průchod ho jen spolehlivě nastartuje.
     */
    private suspend fun syncFromLocalDatabase() {
        val limit = known.size + PAGE_SIZE

        val chatIds = when (val result = client.getChats(chatList = ChatListMain(), limit = limit)) {
            is TdlResult.Failure -> {
                logger.e { "getChats selhalo: ${result.code} ${result.message}" }
                return
            }

            is TdlResult.Success -> result.result.chatIds
        }

        // Načte se všechno, teprve pak se zapíše a publikuje JEDNOU. Publikovat po každém chatu
        // by znamenalo tolik emisí, kolik je chatů (naměřeno 77 při 30 chatech) a stejný počet
        // zbytečných přeřazení celého seznamu.
        // `toList()` je nutný: `chatIds` je LongArray a `mapNotNull` je definované jen pro
        // `Array<T>` a `Iterable`, ne pro primitivní pole.
        val summaries = chatIds.toList().mapNotNull { chatId ->
            when (val chat = client.getChat(chatId)) {
                is TdlResult.Success -> chat.result.toSummaryOrNull()
                is TdlResult.Failure -> {
                    logger.w { "getChat($chatId) selhalo: ${chat.code} ${chat.message}" }
                    null
                }
            }
        }

        mutex.withLock { summaries.forEach { known[it.id] = it } }

        // I prázdný výsledek je výsledek — bez tohohle by seznam bez chatů zůstal viset na spinneru.
        publish()
    }

    private suspend fun upsert(chat: Chat) {
        val summary = chat.toSummaryOrNull() ?: return
        put(summary)
    }

    private suspend fun applyPosition(chatId: Long, position: ChatPosition) {
        if (!position.list.isMain()) return

        if (position.order == 0L) {
            remove(chatId)
            return
        }

        edit(chatId) { it.copy(order = position.order, isPinned = position.isPinned) }
    }

    private suspend fun put(summary: ChatSummary) {
        mutex.withLock { known[summary.id] = summary }
        publish()
    }

    /**
     * Upraví existující záznam. Když chat ještě neznáme, update se **zahodí** — bez `UpdateNewChat`
     * nemáme titulek ani zbytek, a vyrábět poloprázdný řádek by v seznamu problikávalo. TDLib
     * `UpdateNewChat` vždycky pošle dřív, než začne posílat změny k tomu chatu.
     */
    private suspend fun edit(chatId: Long, transform: (ChatSummary) -> ChatSummary) {
        val changed = mutex.withLock {
            val current = known[chatId] ?: return@withLock false
            known[chatId] = transform(current)
            true
        }
        if (changed) publish()
    }

    private suspend fun remove(chatId: Long) {
        mutex.withLock { known.remove(chatId) }
        publish()
    }

    private suspend fun clear() {
        mutex.withLock { known.clear() }
        exhausted = false
        _chats.value = ChatListState.Loading
    }

    private suspend fun publish() {
        val sorted = mutex.withLock { known.values.sortedByDescending { it.order } }
        _chats.value = ChatListState.Ready(chats = sorted, canLoadMore = !exhausted)
    }

    private companion object {
        const val PAGE_SIZE = 30

        /** TDLib hlásí „už není co načítat" jako 404. */
        const val NOT_FOUND = 404
    }
}

/**
 * Převod na doménový model. Vrací `null`, když chat v hlavní složce není (`order == 0`) — takový
 * do seznamu nepatří.
 */
private fun Chat.toSummaryOrNull(): ChatSummary? {
    val position = positions.mainPosition() ?: return null
    if (position.order == 0L) return null

    return ChatSummary(
        id = id,
        title = title,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessage?.preview(),
        isPinned = position.isPinned,
        order = position.order,
    )
}

private fun ChatList?.isMain(): Boolean = this is ChatListMain

private fun Array<ChatPosition>.mainPosition(): ChatPosition? = firstOrNull { it.list.isMain() }

/**
 * Náhled poslední zprávy v seznamu. Text se bere doslova, u médií se ukáže popisek nebo typ —
 * prázdný řádek u fotky vypadá jako chyba načítání.
 */
private fun Message.preview(): String = when (val content = this.content) {
    is MessageText -> content.text.text
    is MessagePhoto -> content.caption.text.ifBlank { "Fotka" }
    is MessageVideo -> content.caption.text.ifBlank { "Video" }
    is MessageAnimation -> content.caption.text.ifBlank { "GIF" }
    is MessageDocument -> content.caption.text.ifBlank { "Soubor" }
    is MessageAudio -> content.caption.text.ifBlank { "Audio" }
    is MessageVoiceNote -> content.caption.text.ifBlank { "Hlasová zpráva" }
    is MessageSticker -> content.sticker.emoji.ifBlank { "Samolepka" }
    else -> "Zpráva"
}
