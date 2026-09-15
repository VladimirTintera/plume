package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.ChatList
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.ChatPosition
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatAction
import dev.g000sha256.tdl.dto.ChatActionCancel
import dev.g000sha256.tdl.dto.ChatActionChoosingSticker
import dev.g000sha256.tdl.dto.ChatActionRecordingVideo
import dev.g000sha256.tdl.dto.ChatActionRecordingVideoNote
import dev.g000sha256.tdl.dto.ChatActionRecordingVoiceNote
import dev.g000sha256.tdl.dto.ChatActionTyping
import dev.g000sha256.tdl.dto.ChatActionUploadingDocument
import dev.g000sha256.tdl.dto.ChatActionUploadingPhoto
import dev.g000sha256.tdl.dto.ChatActionUploadingVideo
import dev.g000sha256.tdl.dto.ChatActionUploadingVideoNote
import dev.g000sha256.tdl.dto.ChatActionUploadingVoiceNote
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.UserStatus
import dev.g000sha256.tdl.dto.UserStatusLastWeek
import dev.g000sha256.tdl.dto.UserStatusOffline
import dev.g000sha256.tdl.dto.UserStatusOnline
import dev.g000sha256.tdl.dto.UserStatusRecently
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Id přihlášeného účtu; `null`, dokud přihlášení neproběhne. */
    private var myUserId: Long? = null

    /** Odpočty zhasnutí „píše…", jeden na chat. */
    private val activityTimers = mutableMapOf<Long, Job>()

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
                    AuthStage.Ready -> {
                        // Vlastní id se potřebuje u „píše…", aby se nehlásila naše vlastní
                        // činnost. Zjišťuje se jednou, ne při každém updatu.
                        myUserId = session.me().getOrNull()?.id
                        loadMore()
                    }

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
                        lastMessagePreview = update.lastMessage?.previewText(),
                        lastMessageTimestamp = update.lastMessage?.date,
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

        // Ztlumení a ruční nepřečtení se mění z nabídky akcí, ale potvrzení chodí updatem —
        // stav se tedy nepřepisuje lokálně, čeká se na TDLib jako u všeho ostatního.
        scope.launch {
            client.chatNotificationSettingsUpdates.collect { update ->
                edit(update.chatId) { it.copy(isMuted = update.notificationSettings.isMuted()) }
            }
        }

        scope.launch {
            client.chatIsMarkedAsUnreadUpdates.collect { update ->
                edit(update.chatId) { it.copy(isMarkedAsUnread = update.isMarkedAsUnread) }
            }
        }

        /*
         * Dostupnost protějšku.
         *
         * V soukromém chatu je `chatId` shodné s `userId`, takže se update mapuje přímo na
         * položku seznamu. U skupin se stav uživatelů hlásí taky, ale k ničemu tam není —
         * `edit` prostě nenajde chat s takovým id a nic se nestane.
         */
        scope.launch {
            client.userStatusUpdates.collect { update ->
                edit(update.userId) { it.copy(presence = update.status.toPresence()) }
            }
        }

        /*
         * „Píše…" a spol.
         *
         * Vlastní činnost se zahazuje — v seznamu by vypadalo divně, že chat hlásí psaní,
         * když píšeme my. TDLib činnost ukončuje samostatným `ChatActionCancel`, ale spolehnout
         * se na něj nestačí: kdyby se ztratil, zůstalo by „píše…" viset napořád. Proto se
         * každá činnost sama zhasne, když ji TDLib do [ACTIVITY_TIMEOUT_MS] nezopakuje.
         */
        scope.launch {
            client.chatActionUpdates.collect { update ->
                val fromMe = (update.senderId as? MessageSenderUser)?.userId == myUserId
                val activity = if (fromMe) null else update.action.toActivity()

                edit(update.chatId) { it.copy(activity = activity) }

                if (activity != null) scheduleActivityExpiry(update.chatId, activity)
            }
        }

        // Odebrání ze složky — chat sice dál existuje, ale v hlavním seznamu nemá co dělat.
        scope.launch {
            client.chatRemovedFromListUpdates.collect { update ->
                if (update.chatList.isMain()) remove(update.chatId)
            }
        }
    }

    /**
     * Zhasnutí činnosti, pokud ji TDLib mezitím nezopakoval.
     *
     * Běží vždycky jen jedna úloha na chat: nová zpráva o psaní tu předchozí zruší, takže se
     * odpočet posouvá a při souvislém psaní nikdy nedoběhne.
     */
    private fun scheduleActivityExpiry(chatId: Long, activity: ChatActivity) {
        activityTimers.remove(chatId)?.cancel()

        activityTimers[chatId] = scope.launch {
            delay(ACTIVITY_TIMEOUT_MS)
            edit(chatId) { if (it.activity == activity) it.copy(activity = null) else it }
        }
    }

    /**
     * Doplní počáteční dostupnost protějšku.
     *
     * `updateUserStatus` chodí jen na ZMĚNU, takže bez tohohle by zelená tečka naskočila až ve
     * chvíli, kdy se protějšek odpojí a zase připojí. V soukromém chatu je `chatId` shodné
     * s `userId`, takže není co dohledávat.
     */
    private suspend fun ChatSummary.withPresence(): ChatSummary {
        if (!isPrivate) return this

        val status = client.getUser(id).toResult().getOrNull()?.status ?: return this
        return copy(presence = status.toPresence())
    }

    override suspend fun setPinned(chatId: Long, pinned: Boolean): Result<Unit> =
        client.toggleChatIsPinned(chatList = ChatListMain(), chatId = chatId, isPinned = pinned)
            .toResult()
            .map { }

    /**
     * Ztlumení se nastavuje **celým objektem nastavení**, ne jedním polem.
     *
     * Ostatní hodnoty se proto musí přenést z aktuálního stavu chatu — poslat nový objekt
     * s výchozími hodnotami by uživateli tiše přepsalo třeba vlastní zvuk nebo náhledy.
     *
     * `useDefaultMuteFor = false` je nutné: dokud je `true`, jede chat podle globálního
     * nastavení a `muteFor` se ignoruje.
     */
    override suspend fun setMuted(chatId: Long, muted: Boolean): Result<Unit> {
        val current = when (val result = client.getChat(chatId)) {
            is TdlResult.Failure -> return Result.failure(IllegalStateException(result.message))
            is TdlResult.Success -> result.result.notificationSettings
        }

        val updated = ChatNotificationSettings(
            useDefaultMuteFor = false,
            muteFor = if (muted) MUTE_FOREVER else 0,
            useDefaultSound = current.useDefaultSound,
            soundId = current.soundId,
            useDefaultShowPreview = current.useDefaultShowPreview,
            showPreview = current.showPreview,
            useDefaultMuteStories = current.useDefaultMuteStories,
            muteStories = current.muteStories,
            useDefaultStorySound = current.useDefaultStorySound,
            storySoundId = current.storySoundId,
            useDefaultShowStoryPoster = current.useDefaultShowStoryPoster,
            showStoryPoster = current.showStoryPoster,
            useDefaultDisablePinnedMessageNotifications = current.useDefaultDisablePinnedMessageNotifications,
            disablePinnedMessageNotifications = current.disablePinnedMessageNotifications,
            useDefaultDisableMentionNotifications = current.useDefaultDisableMentionNotifications,
            disableMentionNotifications = current.disableMentionNotifications,
        )

        return client.setChatNotificationSettings(chatId = chatId, notificationSettings = updated)
            .toResult()
            .map { }
    }

    override suspend fun setMarkedAsUnread(chatId: Long, unread: Boolean): Result<Unit> =
        client.toggleChatIsMarkedAsUnread(chatId = chatId, isMarkedAsUnread = unread)
            .toResult()
            .map { }

    override suspend fun deleteOrLeave(chatId: Long): Result<Unit> {
        val summary = mutex.withLock { known[chatId] }
            ?: return Result.failure(IllegalStateException("Chat není v seznamu."))

        val result = if (summary.isPrivate) {
            client.deleteChatHistory(
                chatId = chatId,
                // Chat zmizí i ze seznamu, ne jen vyprázdní.
                removeFromChatList = true,
                // `true` by smazalo zprávy i protějšku. Z položky v seznamu to nikdo nečeká.
                revoke = false,
            )
        } else {
            client.leaveChat(chatId = chatId)
        }

        return result.toResult().map { }
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
                is TdlResult.Success -> chat.result.toSummaryOrNull()?.withPresence()
                is TdlResult.Failure -> {
                    logger.w { "getChat($chatId) selhalo: ${chat.code} ${chat.message}" }
                    null
                }
            }
        }

        mutex.withLock { summaries.forEach { known[it.id] = it.keepingLiveState(known[it.id]) } }

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
        mutex.withLock { known[summary.id] = summary.keepingLiveState(known[summary.id]) }
        publish()
    }

    /**
     * Dostupnost a činnost přežijí přepsání chatu.
     *
     * `Chat` od TDLib je nenese — chodí samostatnými updaty. Kdyby se položka přepsala rovnou,
     * každá nová zpráva (`UpdateNewChat` při dotažení další stránky, seed z databáze) by zhasla
     * zelenou tečku i „píše…", protože obojí by se vrátilo na `null`.
     */
    private fun ChatSummary.keepingLiveState(previous: ChatSummary?): ChatSummary =
        if (previous == null) this else copy(
            presence = presence ?: previous.presence,
            activity = activity ?: previous.activity,
        )

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

        /**
         * Ztlumení „navždy". TDLib bere sekundy, ne příznak, takže se dává maximum Intu —
         * Telegram to takhle sám interpretuje jako trvalé ztlumení.
         */
        const val MUTE_FOREVER = Int.MAX_VALUE

        /**
         * Po jak dlouhém tichu se „píše…" zhasne samo. TDLib činnost při psaní opakuje
         * zhruba po pěti sekundách, takže tohle nechytne souvislé psaní, jen ztracený konec.
         */
        const val ACTIVITY_TIMEOUT_MS = 8_000L
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
        lastMessagePreview = lastMessage?.previewText(),
        isPinned = position.isPinned,
        order = position.order,
        avatarFileId = photo?.small?.id,
        isMuted = notificationSettings.isMuted(),
        isMarkedAsUnread = isMarkedAsUnread,
        isPrivate = type is ChatTypePrivate || type is ChatTypeSecret,
        lastMessageTimestamp = lastMessage?.date,
        // Dostupnost `Chat` nenese — dorazí updatem. Do té doby je `null`, což UI kreslí
        // prostě bez tečky.
        presence = null,
        activity = null,
    )
}

private fun ChatList?.isMain(): Boolean = this is ChatListMain

private fun Array<ChatPosition>.mainPosition(): ChatPosition? = firstOrNull { it.list.isMain() }


/**
 * Ztlumení se pozná z `muteFor`, ne z nějakého příznaku.
 *
 * `useDefaultMuteFor` znamená, že chat jede podle globálního nastavení a sám ztlumený není —
 * číst v tom případě `muteFor` nemá smysl, TDLib tam dává zbytek po předchozím nastavení.
 */
private fun ChatNotificationSettings.isMuted(): Boolean = !useDefaultMuteFor && muteFor > 0

/** Stav uživatele na doménovou dostupnost. Neznámé a prázdné stavy se tváří jako „dávno". */
internal fun UserStatus.toPresence(): Presence = when (this) {
    is UserStatusOnline -> Presence.Online
    is UserStatusOffline -> Presence.Offline(wasOnline)
    is UserStatusRecently -> Presence.Recently
    is UserStatusLastWeek -> Presence.LastWeek
    else -> Presence.LastMonth
}

/**
 * Činnost v chatu.
 *
 * `ChatActionCancel` znamená konec, ne druh činnosti — proto `null`. Nahrávání a odesílání se
 * rozlišuje, protože „nahrává hlasovku" je pro čekajícího jiná informace než „píše".
 */
internal fun ChatAction.toActivity(): ChatActivity? = when (this) {
    is ChatActionCancel -> null
    is ChatActionTyping -> ChatActivity.Typing
    is ChatActionRecordingVoiceNote -> ChatActivity.RecordingVoice
    is ChatActionRecordingVideo, is ChatActionRecordingVideoNote -> ChatActivity.RecordingVideo
    is ChatActionUploadingPhoto -> ChatActivity.SendingPhoto
    is ChatActionUploadingVideo, is ChatActionUploadingVideoNote -> ChatActivity.SendingVideo
    is ChatActionUploadingDocument, is ChatActionUploadingVoiceNote -> ChatActivity.SendingFile
    is ChatActionChoosingSticker -> ChatActivity.ChoosingSticker
    else -> ChatActivity.Other
}
