package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.SearchMessagesFilterAudio
import dev.g000sha256.tdl.dto.SearchMessagesFilterVoiceNote
import dev.g000sha256.tdl.dto.SearchMessagesFilterUrl
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterVideo
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhoto
import kotlin.time.Clock
import dev.g000sha256.tdl.dto.InternalLinkTypeSettings
import dev.g000sha256.tdl.dto.InternalLinkTypeMyProfilePage
import dev.g000sha256.tdl.dto.InternalLinkTypeNewGroupChat
import dev.g000sha256.tdl.dto.InternalLinkTypeNewChannelChat
import dev.g000sha256.tdl.dto.InternalLinkTypeNewPrivateChat
import dev.g000sha256.tdl.dto.InternalLinkTypeContactsPage
import dev.g000sha256.tdl.dto.InternalLinkTypeSearch
import dev.g000sha256.tdl.dto.InternalLinkTypeVideoChat
import dev.g000sha256.tdl.dto.InternalLinkTypeAuthenticationCode
import dev.g000sha256.tdl.dto.InternalLinkTypeQrCodeAuthentication
import dev.g000sha256.tdl.dto.SettingsSection
import dev.g000sha256.tdl.dto.SettingsSectionEditProfile
import dev.g000sha256.tdl.dto.SettingsSectionPrivacyAndSecurity
import dev.g000sha256.tdl.dto.SettingsSectionNotifications
import dev.g000sha256.tdl.dto.SettingsSectionAppearance
import dev.g000sha256.tdl.dto.SettingsSectionDevices
import dev.g000sha256.tdl.dto.SettingsSectionQrCode
import dev.g000sha256.tdl.dto.SettingsSectionChatFolders
import dev.g000sha256.tdl.dto.SettingsSectionAskQuestion
import dev.g000sha256.tdl.dto.SettingsSectionBusiness
import dev.g000sha256.tdl.dto.SettingsSectionDataAndStorage
import dev.g000sha256.tdl.dto.SettingsSectionFaq
import dev.g000sha256.tdl.dto.SettingsSectionFeatures
import dev.g000sha256.tdl.dto.SettingsSectionInAppBrowser
import dev.g000sha256.tdl.dto.SettingsSectionLanguage
import dev.g000sha256.tdl.dto.SettingsSectionMyGrams
import dev.g000sha256.tdl.dto.SettingsSectionMyStars
import dev.g000sha256.tdl.dto.SettingsSectionPowerSaving
import dev.g000sha256.tdl.dto.SettingsSectionPremium
import dev.g000sha256.tdl.dto.SettingsSectionPrivacyPolicy
import dev.g000sha256.tdl.dto.SettingsSectionSearch
import dev.g000sha256.tdl.dto.SettingsSectionSendGift
import dev.g000sha256.tdl.dto.FoundMessages
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.InternalLinkTypeBotStartInGroup
import dev.g000sha256.tdl.dto.SearchMessagesChatTypeFilterChannel
import dev.g000sha256.tdl.dto.SearchMessagesChatTypeFilterGroup
import dev.g000sha256.tdl.dto.SearchMessagesChatTypeFilterPrivate
import dev.g000sha256.tdl.dto.ChatJoinResultGuardBotApprovalRequired
import dev.g000sha256.tdl.dto.ChatJoinResultDeclined
import dev.g000sha256.tdl.dto.ChatJoinResultRequestSent
import dev.g000sha256.tdl.dto.ChatJoinResultSuccess
import dev.g000sha256.tdl.dto.InviteLinkChatTypeChannel
import dev.g000sha256.tdl.dto.InternalLinkTypeStickerSet
import dev.g000sha256.tdl.dto.InternalLinkTypeStory
import dev.g000sha256.tdl.dto.InternalLinkTypeBotStart
import dev.g000sha256.tdl.dto.InternalLinkTypeMainWebApp
import dev.g000sha256.tdl.dto.InternalLinkTypeWebApp
import dev.g000sha256.tdl.dto.InternalLinkTypeChatFolderInvite
import dev.g000sha256.tdl.dto.InternalLinkTypeChatInvite
import dev.g000sha256.tdl.dto.InternalLinkTypeMessage
import dev.g000sha256.tdl.dto.InternalLinkTypePublicChat
import dev.g000sha256.tdl.dto.InternalLinkTypeMessageDraft
import dev.g000sha256.tdl.dto.InternalLinkTypeSavedMessages
import dev.g000sha256.tdl.dto.InternalLinkTypeProxy
import dev.g000sha256.tdl.dto.InternalLinkTypeUserPhoneNumber
import dev.g000sha256.tdl.dto.InternalLinkTypeUserToken
import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.ArchiveChatListSettings
import dev.g000sha256.tdl.dto.ChatList
import dev.g000sha256.tdl.dto.ChatListArchive
import dev.g000sha256.tdl.dto.ChatListFolder
import dev.g000sha256.tdl.dto.ChatFolderInfo
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.ChatPosition
import dev.g000sha256.tdl.dto.ChatSource
import dev.g000sha256.tdl.dto.ChatSourceMtprotoProxy
import dev.g000sha256.tdl.dto.ChatSourcePublicServiceAnnouncement
import dev.g000sha256.tdl.dto.ChatType
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
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
import dev.g000sha256.tdl.dto.SecretChatStateReady
import dev.g000sha256.tdl.dto.SecretChatStatePending
import dev.g000sha256.tdl.dto.SecretChatStateClosed
import dev.g000sha256.tdl.dto.SecretChatState
import dev.g000sha256.tdl.dto.BlockListMain
import dev.g000sha256.tdl.dto.ChatActionBarReportAddBlock
import dev.g000sha256.tdl.dto.ChatActionBarAddContact
import dev.g000sha256.tdl.dto.ChatActionBarJoinRequest
import dev.g000sha256.tdl.dto.ChatActionBarSharePhoneNumber
import dev.g000sha256.tdl.dto.ChatActionBarInviteMembers
import dev.g000sha256.tdl.dto.ImportedContact
import dev.g000sha256.tdl.dto.ReportChatResultMessagesRequired
import dev.g000sha256.tdl.dto.ReportChatResultOptionRequired
import dev.g000sha256.tdl.dto.ReportChatResultTextRequired
import dev.g000sha256.tdl.dto.ChatActionBarReportSpam
import dev.g000sha256.tdl.dto.ReportChatResultOk
import dev.g000sha256.tdl.dto.DraftMessage
import dev.g000sha256.tdl.dto.DraftMessageContentText
import dev.g000sha256.tdl.dto.MessageSourceChatList
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.User
import dev.g000sha256.tdl.dto.UserTypeBot
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageTopicForum
import dev.g000sha256.tdl.dto.OptionValueBoolean
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.TopChatCategoryUsers
import dev.g000sha256.tdl.dto.UserStatus
import dev.g000sha256.tdl.dto.UserStatusLastWeek
import dev.g000sha256.tdl.dto.UserStatusOffline
import dev.g000sha256.tdl.dto.UserStatusOnline
import dev.g000sha256.tdl.dto.UserStatusRecently
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dev.g000sha256.tdl.dto.GroupCall
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.secret_chat_state_ready
import eu.tintera.plume.resources.secret_chat_state_pending
import eu.tintera.plume.resources.secret_chat_state_closed
import eu.tintera.plume.resources.chats_load_failed

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
 *
 * **Drží se VŠECHNY chaty, které TDLib ohlásí, i ty mimo složky.** Složka je jen pozice
 * ([positions]); archivace je změna pozice, ne jiný chat. Dokud se držely jen chaty hlavního
 * seznamu, zahazovaly se updaty všech ostatních — archivovaný chat neukázal v hlavičce
 * konverzace „píše…" a po vrácení z archivu přišel o dostupnost protějšku.
 */
class TdlChatRepository(
    private val holder: TelegramClientHolder,
    private val session: TelegramSession,
    private val scope: CoroutineScope,
) : ChatRepository {

    private val logger = Logger.withTag("Chats")

    private val client: TdlClient get() = holder.client

    /**
     * Zdroj pravdy: všechny známé chaty podle chatId. [ChatSummary.order] a [ChatSummary.isPinned]
     * tu nic neznamenají — pozice ve složkách je v [positions] a do položky se dosadí až při
     * sestavení seznamu složky.
     */
    private val known = mutableMapOf<Long, ChatSummary>()

    /**
     * Protějšek každého známého tajného chatu (chat → uživatel). Tajný chat má vlastní id, takže
     * updaty uživatele (dostupnost, ověření), které se na soukromý chat mapují přímo id, by ho
     * jinak minuly. Pod [mutex] jako [known].
     */
    private val secretPeers = mutableMapOf<Long, Long>()

    /** Pozice chatu ve složkách. Chat mimo všechny složky tu nemá záznam. */
    private val positions = mutableMapOf<Long, Map<ChatFolder, FolderPosition>>()
    private val mutex = Mutex()

    /**
     * Seznam každé složky, o kterou si někdo řekl. Hlavní seznam a archiv jsou tu od začátku,
     * složky Telegramu přibývají až s [chats] — publikovat seznam složky, kterou nikdo nečte,
     * by byla práce navíc při každém updatu. Mapa se jen rozšiřuje a čte bez zámku
     * (`chats` není `suspend`), proto `MutableStateFlow` s výměnou celé mapy.
     */
    private val lists = MutableStateFlow<Map<ChatFolder, MutableStateFlow<ChatListState>>>(
        listOf(ChatFolder.Main, ChatFolder.Archive).associateWith { MutableStateFlow(ChatListState.Loading) },
    )

    private fun listFor(folder: ChatFolder): MutableStateFlow<ChatListState> {
        lists.value[folder]?.let { return it }
        lists.update { current -> if (folder in current) current else current + (folder to MutableStateFlow(ChatListState.Loading)) }
        return lists.value.getValue(folder)
    }

    private val _chatFolders = MutableStateFlow(ChatFolders())

    override val chatFolders: StateFlow<ChatFolders> = _chatFolders.asStateFlow()

    private val _folderUnread = MutableStateFlow<Map<Int, ArchiveUnread>>(emptyMap())

    override val folderUnread: StateFlow<Map<Int, ArchiveUnread>> = _folderUnread.asStateFlow()

    /** Všechny známé chaty — pro [typing] a [drafts], kterým na složce nezáleží. */
    private val all = MutableStateFlow<Map<Long, ChatSummary>>(emptyMap())

    override val drafts: Flow<List<ChatSummary>> = all
        .map { chats ->
            chats.values
                .filter { it.draft != null }
                .sortedByDescending { it.draft?.timestamp ?: 0 }
        }
        .distinctUntilChanged()

    override val forumChats: Flow<List<ChatSummary>> = all
        .map { chats -> chats.values.filter { it.isForum } }
        .distinctUntilChanged()

    override suspend fun deleteDraft(chatId: Long): Result<DraftRestore?> {
        // Koncept se čte z TDLib, ne z [all]: tam je jen text a entity, a vrácení má obnovit
        // i odpověď a náhled odkazu. Datum se při vrácení přepíše na teď (ověřeno na emulátoru),
        // takže se vrácený koncept zařadí v přehledu nahoru.
        val draft = client.getChat(chatId = chatId).toResult().getOrElse { return Result.failure(it) }.draftMessage
            ?: return Result.success(null)
        return client.setChatDraftMessage(chatId = chatId, topicId = null, draftMessage = null)
            .toResult()
            .map {
                DraftRestore {
                    client.setChatDraftMessage(chatId = chatId, topicId = null, draftMessage = draft).toResult().map { }
                }
            }
    }

    /**
     * Kdo v chatu naposledy ohlásil činnost. `ChatSummary` nese jen DRUH činnosti (seznamu
     * chatů stačí „píše…"); konverzace ve skupině potřebuje i tvář. Zhasíná se spolu s činností
     * v [all], takže tu stará hodnota může zůstat — bez činnosti se nečte.
     */
    private val activitySenders = MutableStateFlow<Map<Long, ActivitySource>>(emptyMap())

    private val _archiveUnread = MutableStateFlow(ArchiveUnread())

    override val archiveUnread: StateFlow<ArchiveUnread> = _archiveUnread.asStateFlow()

    override fun chats(folder: ChatFolder): StateFlow<ChatListState> {
        val list = listFor(folder)
        // Seznam složky, kterou už repository zná z pozic, se publikuje hned — jinak by do
        // prvního updatu ukazoval načítání, i když chaty v paměti jsou.
        if (folder is ChatFolder.Custom) scope.launch { publish() }
        return list.asStateFlow()
    }

    override fun typing(chatId: Long): Flow<ChatTyping?> =
        combine(
            all.map { it[chatId]?.activity }.distinctUntilChanged(),
            activitySenders.map { it[chatId] }.distinctUntilChanged(),
        ) { activity, source ->
            if (activity != null && source != null) ChatTyping(activity, source.senderId, source.topicId) else null
        }
            .distinctUntilChanged()

    /** Složky, u kterých TDLib ohlásil, že další chaty už nejsou. */
    private val exhausted = mutableSetOf<ChatFolder>()

    /** Složky, které se aspoň jednou přečetly z lokální databáze — do té doby se načítají. */
    private val synced = mutableSetOf<ChatFolder>()
    private var started = false

    /** Id přihlášeného účtu; `null`, dokud přihlášení neproběhne. Je to i id Uložených zpráv. */
    private val me = MutableStateFlow<Long?>(null)
    private val myUserId: Long? get() = me.value

    override val savedMessagesChatId: StateFlow<Long?> = me.asStateFlow()

    private val dice = MutableStateFlow<List<String>>(emptyList())
    override val diceEmojis: StateFlow<List<String>> = dice.asStateFlow()

    private val effects = MutableStateFlow<List<Long>>(emptyList())
    override val availableEffectIds: StateFlow<List<Long>> = effects.asStateFlow()

    private val trial = MutableStateFlow<SpeechTrial?>(null)
    override val speechTrial: StateFlow<SpeechTrial?> = trial.asStateFlow()

    private val premium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = premium.asStateFlow()

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
                        me.value = session.me().getOrNull()?.id
                        premium.value = client.getMe().toResult().getOrNull()?.isPremium == true
                        // Uložené zprávy musí TDLib znát, jinak je nejde otevřít ani do nich
                        // přeposlat či sdílet — u účtu, který si nic neuložil, v databázi
                        // nejsou. `createPrivateChat` je lokální a nic neposílá.
                        me.value?.let { openSavedMessages() }
                        loadMore()
                    }

                    AuthStage.LoggingOut -> clear()
                    else -> Unit
                }
            }
        }
    }

    private fun collectUpdates() {
        scope.launch {
            holder.updates { diceEmojisUpdates }.collect { update -> dice.value = update.emojis.toList() }
        }
        scope.launch {
            holder.updates { speechRecognitionTrialUpdates }.collect { update ->
                logger.i {
                    "Zkušební přepisy: ${update.leftCount} z ${update.weeklyCount} za týden, " +
                        "do ${update.maxMediaDuration} s, obnova ${update.nextResetDate}"
                }
                trial.value = SpeechTrial(
                    maxMediaDurationSeconds = update.maxMediaDuration,
                    weeklyCount = update.weeklyCount,
                    leftCount = update.leftCount,
                    nextResetDate = update.nextResetDate.toLong(),
                )
            }
        }
        scope.launch {
            holder.updates { availableMessageEffectsUpdates }.collect { update ->
                effects.value = (update.reactionEffectIds.toList() + update.stickerEffectIds.toList()).distinct()
            }
        }

        // Nový chat. Pozice může být prázdná (chat je znám, ale v žádné složce) — pak se neukáže.
        scope.launch {
            holder.updates { newChatUpdates }.collect { update -> upsert(update.chat) }
        }

        // Změna pozice v konkrétní složce. Tohle je hlavní zdroj přeskládání seznamu.
        scope.launch {
            holder.updates { chatPositionUpdates }.collect { update ->
                applyPosition(update.chatId, update.position)
            }
        }

        // Nová poslední zpráva. Nese s sebou i nové pozice, takže se aktualizuje obojí najednou.
        scope.launch {
            holder.updates { chatLastMessageUpdates }.collect { update ->
                // Náhled se čte z prostředků (`suspend`), takže se připraví před `edit` —
                // ten bere obyčejnou funkci, protože běží pod zámkem nad stavem seznamu.
                val preview = update.lastMessage?.listPreview() ?: secretChatPreview(update.chatId)
                setPositions(update.chatId, update.positions)
                edit(update.chatId, fetchUnknown = true) { current ->
                    current.copy(
                        lastMessagePreview = preview,
                        lastMessagePreviewEntities = update.lastMessage?.previewEntities().orEmpty(),
                        lastMessageTimestamp = update.lastMessage?.date,
                    )
                }
            }
        }

        /*
         * Koncept. Stejně jako poslední zpráva nese nové pozice — chat s čerstvým konceptem
         * se v seznamu posune nahoru a jinudy se to nedozvíme.
         */
        scope.launch {
            holder.updates { chatDraftMessageUpdates }.collect { update ->
                setPositions(update.chatId, update.positions)
                edit(update.chatId) { current -> current.copy(draft = update.draftMessage?.toChatDraft()) }
            }
        }

        scope.launch {
            holder.updates { chatReadInboxUpdates }.collect { update ->
                edit(update.chatId) { it.copy(unreadCount = update.unreadCount) }
            }
        }

        // Zmínky a reakce: dva updaty nesou jen počet, dva i zprávu, o kterou jde.
        scope.launch {
            merge(
                holder.updates { chatUnreadMentionCountUpdates }.map { it.chatId to it.unreadMentionCount },
                holder.updates { messageMentionReadUpdates }.map { it.chatId to it.unreadMentionCount },
            ).collect { (chatId, count) -> edit(chatId) { it.copy(unreadMentionCount = count) } }
        }

        scope.launch {
            merge(
                holder.updates { chatUnreadReactionCountUpdates }.map { it.chatId to it.unreadReactionCount },
                holder.updates { messageUnreadReactionsUpdates }.map { it.chatId to it.unreadReactionCount },
            ).collect { (chatId, count) -> edit(chatId) { it.copy(unreadReactionCount = count) } }
        }

        scope.launch {
            merge(
                holder.updates { chatUnreadPollVoteCountUpdates }.map { it.chatId to it.unreadPollVoteCount },
                holder.updates { messageContainsUnreadPollVotesUpdates }.map { it.chatId to it.unreadPollVoteCount },
            ).collect { (chatId, count) -> edit(chatId) { it.copy(unreadPollVoteCount = count) } }
        }

        scope.launch {
            holder.updates { chatTitleUpdates }.collect { update ->
                edit(update.chatId) { it.copy(title = update.title) }
            }
        }

        // Nová (nebo smazaná) fotka skupiny i kontaktu. Bez odběru zůstala v seznamu stará fotka
        // až do restartu: `avatarFileId` se bral jen z objektu chatu při prvním `upsert`.
        scope.launch {
            holder.updates { chatPhotoUpdates }.collect { update ->
                edit(update.chatId) { it.copy(avatarFileId = update.photo?.small?.id) }
            }
        }

        // Ztlumení a ruční nepřečtení se mění z nabídky akcí, ale potvrzení chodí updatem —
        // stav se tedy nepřepisuje lokálně, čeká se na TDLib jako u všeho ostatního.
        scope.launch {
            holder.updates { chatNotificationSettingsUpdates }.collect { update ->
                edit(update.chatId) { it.copy(isMuted = update.notificationSettings.isMuted()) }
            }
        }

        scope.launch {
            holder.updates { chatIsMarkedAsUnreadUpdates }.collect { update ->
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
            holder.updates { userStatusUpdates }.collect { update ->
                chatsOfUser(update.userId).forEach { chatId ->
                    edit(chatId) { it.copy(presence = update.status.toPresence()) }
                }
            }
        }

        /*
         * Ověření a štítek podvodu. Nese je uživatel a supergroupa, ne chat, takže se mapují na
         * chat zvlášť: soukromý chat má id uživatele, supergroupa (skupina i kanál) id
         * odvozené z jejího. U cizích uživatelů `edit` chat nenajde a nic se nestane.
         */
        scope.launch {
            holder.updates { userUpdates }.collect { update ->
                val verification = update.user.verificationStatus.toVerification()
                chatsOfUser(update.user.id).forEach { chatId -> edit(chatId) { it.copy(verification = verification) } }
            }
        }
        scope.launch {
            holder.updates { supergroupUpdates }.collect { update ->
                edit(supergroupChatId(update.supergroup.id)) {
                    it.copy(
                        verification = update.supergroup.verificationStatus.toVerification(),
                        isForum = update.supergroup.isForum,
                    )
                }
            }
        }

        /*
         * Stav tajného chatu. Prázdný tajný chat nemá poslední zprávu, takže pod jménem stojí
         * jeho stav — a ten se mění, když se protějšek připojí nebo chat ukončí. Chat s historií
         * má náhled zprávy a stav nepotřebuje.
         */
        scope.launch {
            holder.updates { secretChatUpdates }.collect { update ->
                val chat = client.createSecretChat(secretChatId = update.secretChat.id).toResult().getOrNull()
                    ?: return@collect
                if (chat.lastMessage != null) return@collect
                val preview = update.secretChat.state.preview()
                edit(chat.id) { it.copy(lastMessagePreview = preview, lastMessagePreviewEntities = emptyList()) }
            }
        }

        // Emoji status za jménem. `Chat` ho nese sám, takže stačí jeden update na chat.
        scope.launch {
            holder.updates { chatEmojiStatusUpdates }.collect { update ->
                edit(update.chatId) { it.copy(emojiStatus = update.emojiStatus.statusEmojiId()) }
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
            holder.updates { chatActionUpdates }.collect { update ->
                val fromMe = (update.senderId as? MessageSenderUser)?.userId == myUserId
                val activity = if (fromMe) null else update.action.toActivity()

                if (activity != null) {
                    val sender = when (val id = update.senderId) {
                        is MessageSenderUser -> id.userId
                        is MessageSenderChat -> id.chatId
                    }
                    val topicId = (update.topicId as? MessageTopicForum)?.forumTopicId
                    activitySenders.update { it + (update.chatId to ActivitySource(sender, topicId)) }
                }
                edit(update.chatId) { it.copy(activity = activity) }

                if (activity != null) scheduleActivityExpiry(update.chatId, activity)
            }
        }

        // Zavřený klient během odhlašování ještě stihne poslat updaty, které by do seznamu vrátily
        // chaty odhlášeného účtu. Úklid až po výměně klienta je poslední slovo.
        scope.launch {
            holder.replaced.collect { clear() }
        }

        // Odebrání ze složky — chat sice dál existuje, ale v téhle složce nemá co dělat.
        scope.launch {
            holder.updates { chatRemovedFromListUpdates }.collect { update ->
                val folder = update.chatList.folder() ?: return@collect
                changePositions(update.chatId) { it - folder }
            }
        }

        /*
         * Počet nepřečtených chatů v archivu a ve složkách. Nepočítá se nad [chats] — archiv
         * i složka se načítají až při otevření, takže dokud je nikdo neotevřel, není z čeho
         * počítat; tohle chodí i bez nich. TDLib ho posílá jen se zapnutou databází zpráv
         * (`useMessageDatabase`).
         */
        scope.launch {
            holder.updates { unreadChatCountUpdates }.collect { update ->
                val unread = ArchiveUnread(chats = update.unreadCount, unmutedChats = update.unreadUnmutedCount)
                when (val list = update.chatList) {
                    is ChatListArchive -> _archiveUnread.value = unread
                    is ChatListFolder -> _folderUnread.update { it + (list.chatFolderId to unread) }
                    else -> Unit
                }
            }
        }

        /*
         * Složky Telegramu. Přijdou hned po startu klienta a po každé změně (i z jiného
         * zařízení) celé najednou, takže se seznam jen nahradí. Složka, která zmizela, nechá
         * po sobě pozice chatů — TDLib je ruší sám odebráním ze seznamu.
         */
        scope.launch {
            holder.updates { chatFoldersUpdates }.collect { update ->
                _chatFolders.value = ChatFolders(
                    folders = update.chatFolders.map { it.toTab() },
                    mainPosition = update.mainChatListPosition,
                )
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

        // Tajný chat: protějšek z mapy, id chatu není id uživatele.
        val userId = mutex.withLock { secretPeers[id] } ?: id
        val status = client.getUser(userId).toResult().getOrNull()?.status ?: return this
        return copy(presence = status.toPresence())
    }

    override suspend fun setPinned(chatId: Long, folder: ChatFolder, pinned: Boolean): Result<Unit> =
        client.toggleChatIsPinned(chatList = folder.toChatList(), chatId = chatId, isPinned = pinned)
            .toResult()
            .map { }

    override suspend fun setPinnedOrder(folder: ChatFolder, chatIds: List<Long>): Result<Unit> =
        client.setPinnedChats(chatList = folder.toChatList(), chatIds = chatIds.toLongArray()).toResult().map { }

    /**
     * `addChatToList` chat do cílové složky PŘESUNE: hlavní seznam a archiv se vylučují, takže
     * TDLib z té druhé pošle odebrání samo (`UpdateChatPosition` s nulovým `order`).
     */
    override suspend fun setArchived(chatId: Long, archived: Boolean): Result<Unit> =
        client.addChatToList(
            chatId = chatId,
            chatList = (if (archived) ChatFolder.Archive else ChatFolder.Main).toChatList(),
        )
            .toResult()
            .map { }

    /**
     * Nastavení archivu a k němu volba serveru, jestli se smí zapínat automatická archivace.
     * Nenastavená volba přijde jako `OptionValueEmpty`, tedy „nesmí".
     */
    override suspend fun archiveSettings(): Result<ArchiveSettings> {
        val settings = client.getArchiveChatListSettings().toResult()
            .onFailure { logger.w(it) { "Nastavení archivu se nepodařilo přečíst." } }
            .getOrElse { return Result.failure(it) }
        val canArchiveUnknown = client.getOption(name = ARCHIVE_UNKNOWN_OPTION).toResult().getOrNull()
        return Result.success(
            ArchiveSettings(
                archiveUnknownSenders = settings.archiveAndMuteNewChatsFromUnknownUsers,
                keepUnmutedArchived = settings.keepUnmutedChatsArchived,
                keepFromFoldersArchived = settings.keepChatsFromFoldersArchived,
                canArchiveUnknownSenders = (canArchiveUnknown as? OptionValueBoolean)?.value == true,
            ),
        )
    }

    /** Zapisuje se celým objektem — všechny tři volby jsou v [ArchiveSettings]. */
    override suspend fun setArchiveSettings(settings: ArchiveSettings): Result<Unit> =
        client.setArchiveChatListSettings(
            settings = ArchiveChatListSettings(
                archiveAndMuteNewChatsFromUnknownUsers = settings.archiveUnknownSenders,
                keepUnmutedChatsArchived = settings.keepUnmutedArchived,
                keepChatsFromFoldersArchived = settings.keepFromFoldersArchived,
            ),
        )
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Nastavení archivu se nepodařilo uložit." } }

    override fun muted(chatId: Long): Flow<Boolean?> =
        all.map { it[chatId]?.isMuted }.distinctUntilChanged()

    override fun appearance(chatId: Long): Flow<ChatAppearance?> =
        all.map { chats -> chats[chatId]?.let { ChatAppearance(it.title, it.avatarFileId) } }.distinctUntilChanged()

    override fun unreadCount(chatId: Long): Flow<Int> =
        all.map { it[chatId]?.unreadCount ?: 0 }.distinctUntilChanged()

    override suspend fun setMuted(chatId: Long, muted: Boolean): Result<Unit> =
        setMuteFor(chatId, if (muted) MUTE_FOREVER else 0)

    override suspend fun muteFor(chatId: Long, duration: Duration): Result<Unit> =
        setMuteFor(chatId, duration.inWholeSeconds.toInt())

    /**
     * Ztlumení se nastavuje **celým objektem nastavení**, ne jedním polem.
     *
     * Ostatní hodnoty se proto musí přenést z aktuálního stavu chatu — poslat nový objekt
     * s výchozími hodnotami by uživateli tiše přepsalo třeba vlastní zvuk nebo náhledy.
     *
     * `useDefaultMuteFor = false` je nutné: dokud je `true`, jede chat podle globálního
     * nastavení a `muteFor` se ignoruje.
     */
    private suspend fun setMuteFor(chatId: Long, seconds: Int): Result<Unit> {
        val current = when (val result = client.getChat(chatId)) {
            is TdlResult.Failure -> return Result.failure(IllegalStateException(result.message))
            is TdlResult.Success -> result.result.notificationSettings
        }

        val updated = current.withMuteFor(seconds)

        return client.setChatNotificationSettings(chatId = chatId, notificationSettings = updated)
            .toResult()
            .map { }
    }

    override suspend fun setMarkedAsUnread(chatId: Long, unread: Boolean): Result<Unit> =
        client.toggleChatIsMarkedAsUnread(chatId = chatId, isMarkedAsUnread = unread)
            .toResult()
            .map { }

    /**
     * Totéž co „označit jako přečtené" v oficiálních klientech: nejnovější zpráva se nahlásí jako
     * zhlédnutá (`forceRead`, zdroj seznam chatů) — server tím přečte i všechno před ní —
     * a zmínky, reakce a ruční příznak se vynulují zvlášť, protože na přečtení zpráv nevisí.
     * Volá se jen to, co je potřeba; `getChat` je lokální.
     */
    override suspend fun readChat(chatId: Long): Result<Unit> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        chat.lastMessage?.let { last ->
            if (chat.unreadCount > 0) {
                client.viewMessages(
                    chatId = chatId,
                    messageIds = longArrayOf(last.id),
                    source = MessageSourceChatList(),
                    forceRead = true,
                ).toResult().getOrThrow()
            }
        }
        if (chat.unreadMentionCount > 0) client.readAllChatMentions(chatId = chatId).toResult().getOrThrow()
        if (chat.unreadReactionCount > 0) client.readAllChatReactions(chatId = chatId).toResult().getOrThrow()
        if (chat.isMarkedAsUnread) {
            client.toggleChatIsMarkedAsUnread(chatId = chatId, isMarkedAsUnread = false).toResult().getOrThrow()
        }
    }

    override suspend fun resolveLink(url: String): Result<InternalLink?> {
        // Neinterní odkaz TDLib hlásí chybou — to není selhání, jen adresa pro prohlížeč.
        val type = client.getInternalLinkType(link = url).toResult().getOrNull()
            ?: return Result.success(null)
        return when (type) {
            is InternalLinkTypePublicChat ->
                client.searchPublicChat(username = type.chatUsername).toResult().map {
                    InternalLink.Chat(it.id, draftText = type.draftText.ifBlank { null }, openProfile = type.openProfile)
                }

            // Člověk podle čísla (`t.me/+420…`). Server ho vydá, jen když číslo neskrývá, nebo je
            // v kontaktech; jinak 400/404, a to má vlastní větu.
            is InternalLinkTypeUserPhoneNumber ->
                privateChatLink(
                    client.searchUserByPhoneNumber(phoneNumber = type.phoneNumber, onlyLocal = false),
                    draftText = type.draftText,
                    openProfile = type.openProfile,
                )

            // Kontaktní odkaz (`t.me/contact/…`), který si člověk vyrobil ve svém profilu.
            is InternalLinkTypeUserToken ->
                privateChatLink(
                    client.searchUserByToken(token = type.token),
                    draftText = "",
                    openProfile = false,
                    notFound = ::ContactLinkExpiredException,
                )

            // `t.me/share/url?url=…&text=…`. TDLib adresu i text složí do jednoho textu sám.
            is InternalLinkTypeMessageDraft ->
                Result.success(type.text.text.takeIf { it.isNotBlank() }?.let { InternalLink.ShareText(it) })

            // Bez proxy = druh, který tahle TDLib nezná; do prohlížeče by to nevedlo nikam.
            is InternalLinkTypeProxy -> type.proxy?.let { Result.success(InternalLink.Proxy(it.toProxyConfig())) }
                ?: Result.failure(UnsupportedProxyException())

            is InternalLinkTypeSavedMessages -> {
                val saved = savedMessagesChatId.value ?: client.getMe().toResult().getOrNull()?.id
                Result.success(saved?.let { InternalLink.Chat(it) })
            }

            is InternalLinkTypeMessage ->
                client.getMessageLinkInfo(url = type.url).toResult().mapCatching { info ->
                    // Nula = chat není k dispozici vůbec (soukromý, uživatel v něm není).
                    check(info.chatId != 0L) { "Chat z odkazu na zprávu není dostupný." }
                    InternalLink.Message(
                        chatId = info.chatId,
                        messageId = info.message?.id,
                        // Téma nese odkaz (`t.me/c/…/téma/zpráva`), jinak zpráva sama.
                        topicId = (info.topicId as? MessageTopicForum)?.forumTopicId
                            ?: (info.message?.topicId as? MessageTopicForum)?.forumTopicId,
                    )
                }

            is InternalLinkTypeChatInvite ->
                client.checkChatInviteLink(inviteLink = type.inviteLink).toResult().map { info ->
                    // Nenulové id = chat je dostupný už teď (uživatel v něm je, nebo je veřejný
                    // a nabídne „Přidat se" sám). Náhled pozvánky je jen pro chat za ní.
                    if (info.chatId != 0L) {
                        InternalLink.Chat(info.chatId)
                    } else {
                        InternalLink.Invite(
                            link = type.inviteLink,
                            preview = InvitePreview(
                                title = info.title,
                                description = info.description,
                                memberCount = info.memberCount,
                                isChannel = info.type is InviteLinkChatTypeChannel,
                                avatarFileId = info.photo?.small?.id,
                                createsJoinRequest = info.createsJoinRequest,
                            ),
                        )
                    }
                }

            is InternalLinkTypeBotStart ->
                client.searchPublicChat(username = type.botUsername).toResult().map { chat ->
                    InternalLink.BotStart(chat.id, type.startParameter, type.autostart)
                }

            is InternalLinkTypeBotStartInGroup ->
                client.searchPublicChat(username = type.botUsername).toResult().map { chat ->
                    InternalLink.BotStartInGroup(url, chat.id, asAdmin = type.administratorRights != null)
                }

            // Režim (kompaktní / přes celou obrazovku) se neřeší: Plume mini aplikaci kreslí vždy
            // přes celou obrazovku. Panel s podmínkami pro boty v nabídce příloh
            // (`getAttachmentMenuBot`) taky ne — nabídku příloh botů Plume nemá.
            is InternalLinkTypeMainWebApp ->
                client.searchPublicChat(username = type.botUsername).toResult().map { chat ->
                    val bot = client.getUser(userId = chat.id).toResult().getOrNull()?.type as? UserTypeBot
                    if (bot?.hasMainWebApp == true) InternalLink.MainWebApp(chat.id, type.startParameter) else InternalLink.Chat(chat.id)
                }

            // Odkaz na pojmenovanou mini aplikaci: tady se jen ověří, že je to bot. Jestli ji bot
            // má, řekne `searchWebApp` až obrazovka mini aplikace (tam je i souhlas a chyba).
            is InternalLinkTypeWebApp ->
                client.searchPublicChat(username = type.botUsername).toResult().map { chat ->
                    val isBot = client.getUser(userId = chat.id).toResult().getOrNull()?.type is UserTypeBot
                    if (isBot) InternalLink.WebApp(chat.id, type.webAppShortName, type.startParameter) else InternalLink.Chat(chat.id)
                }

            is InternalLinkTypeChatFolderInvite ->
                client.checkChatFolderInviteLink(inviteLink = type.inviteLink).toResult().map { info ->
                    val folder = info.chatFolderInfo
                    val title = folder.name.text.toBody()
                    InternalLink.FolderInvite(
                        link = type.inviteLink,
                        preview = FolderInvitePreview(
                            title = title.text,
                            titleEntities = title.entities,
                            iconName = folder.icon.name,
                            folderId = folder.id,
                            missingChats = summariesFor(info.missingChatIds),
                            alreadyJoined = info.addedChatIds.size,
                        ),
                    )
                }

            is InternalLinkTypeStickerSet ->
                client.searchStickerSet(name = type.stickerSetName, ignoreCache = false).toResult()
                    .map { InternalLink.StickerSet(it.id) }

            is InternalLinkTypeSettings -> Result.success(InternalLink.Screen(type.section.toAppScreen()))
            is InternalLinkTypeMyProfilePage -> Result.success(InternalLink.Screen(AppScreen.EditProfile))
            is InternalLinkTypeNewGroupChat -> Result.success(InternalLink.Screen(AppScreen.NewGroup))
            is InternalLinkTypeNewChannelChat -> Result.success(InternalLink.Screen(AppScreen.NewChannel))
            is InternalLinkTypeNewPrivateChat -> Result.success(InternalLink.Screen(AppScreen.NewChat))
            is InternalLinkTypeContactsPage -> Result.success(InternalLink.Screen(AppScreen.NewChat))
            is InternalLinkTypeSearch -> Result.success(InternalLink.Screen(AppScreen.Search))

            // Připojit se k videochatu Plume neumí; chat ukáže pruh s videochatem a ťuknutí na
            // něj vysvětlí proč. Je to víc, než kdyby odkaz skončil v prohlížeči.
            is InternalLinkTypeVideoChat ->
                client.searchPublicChat(username = type.chatUsername).toResult().map { InternalLink.Chat(it.id) }

            is InternalLinkTypeStory ->
                client.searchPublicChat(username = type.storyPosterUsername).toResult().map {
                    InternalLink.Story(posterChatId = it.id, storyId = type.storyId)
                }

            is InternalLinkTypeAuthenticationCode -> Result.success(InternalLink.LoginWarning(LoginLinkKind.Code))
            is InternalLinkTypeQrCodeAuthentication -> Result.success(InternalLink.LoginWarning(LoginLinkKind.QrCode))

            // Ostatní druhy (platby, alba příběhů, dárky, témata vzhledu…) zatím neumíme; prohlížeč
            // je aspoň otevře v oficiálním klientovi, když ho uživatel má.
            else -> Result.success(null)
        }
    }

    /**
     * Soukromý chat s uživatelem, kterého vydalo hledání podle čísla nebo kontaktního odkazu.
     * Chat se založí lokálně (`createPrivateChat`), jinak by ho TDLib neznal a nešel by otevřít.
     * [notFound] je chyba, když server nikoho nevydá — u čísla a u kontaktního odkazu to
     * znamená něco jiného (viz [ContactLinkExpiredException]).
     */
    private suspend fun privateChatLink(
        user: TdlResult<User>,
        draftText: String,
        openProfile: Boolean,
        notFound: () -> Throwable = ::LinkUserNotFoundException,
    ): Result<InternalLink?> =
        when (user) {
            is TdlResult.Failure ->
                if (user.code == BAD_REQUEST || user.code == NOT_FOUND) {
                    logger.i { "Uživatel z odkazu nenalezen: ${user.code} ${user.message}" }
                    Result.failure(notFound())
                } else {
                    user.toResult().map { null }
                }
            is TdlResult.Success ->
                client.createPrivateChat(userId = user.result.id, force = false).toResult().map { chat ->
                    InternalLink.Chat(chat.id, draftText = draftText.ifBlank { null }, openProfile = openProfile)
                }
        }

    override suspend fun joinByInvite(link: String): Result<InviteJoinResult> =
        client.joinChatByInviteLink(inviteLink = link).toResult().mapCatching { result ->
            when (result) {
                is ChatJoinResultSuccess -> InviteJoinResult.Joined(result.chatId)
                // Pozvánka se schválením: žádost odešla a čeká na správce.
                is ChatJoinResultRequestSent -> InviteJoinResult.Requested
                // Odmítnutí a schvalování botem („guard bot") neumíme dokončit — hlásí se jako
                // nepovedené přidání.
                is ChatJoinResultDeclined -> error("Přidání do chatu bylo odmítnuto.")
                is ChatJoinResultGuardBotApprovalRequired -> error("Přidání vyžaduje schválení botem.")
            }
        }

    override suspend fun addFolderByInvite(link: String, chatIds: List<Long>): Result<Unit> =
        client.addChatFolderByInviteLink(inviteLink = link, chatIds = chatIds.toLongArray()).toResult().map { }
            .onFailure { logger.w(it) { "Sdílenou složku se nepodařilo přidat." } }

    override suspend fun chatByUsername(username: String): Result<Long> =
        client.searchPublicChat(username = username).toResult().map { it.id }

    override suspend fun privateChat(userId: Long): Result<Long> =
        client.createPrivateChat(userId = userId, force = false).toResult().map { it.id }

    override suspend fun createSecretChat(userId: Long): Result<Long> =
        client.createNewSecretChat(userId = userId).toResult().map { it.id }

    override suspend fun closeSecretChat(chatId: Long): Result<Unit> = runCatching {
        val type = client.getChat(chatId = chatId).toResult().getOrThrow().type as? ChatTypeSecret
            ?: error("Chat $chatId není tajný.")
        client.closeSecretChat(secretChatId = type.secretChatId).toResult().getOrThrow()
        Unit
    }

    override suspend fun isForum(chatId: Long): Boolean =
        mutex.withLock { known[chatId]?.isForum }
            ?: client.getChat(chatId = chatId).toResult().getOrNull()?.let { client.isForum(it) }
            ?: false

    override suspend fun groupsForBot(asAdmin: Boolean): List<ChatSummary> {
        // Pořadí jako v seznamu chatů: hlavní seznam, pak archiv.
        val listed = listOf(ChatFolder.Main, ChatFolder.Archive)
            .flatMap { folder -> (listFor(folder).value as? ChatListState.Ready)?.chats.orEmpty() }
            .distinctBy { it.id }
            .filter { it.kind == ChatKind.Group }
        return listed.filter { summary ->
            val chat = client.getChat(chatId = summary.id).toResult().getOrNull() ?: return@filter false
            canAddBot(
                status = client.ownMemberStatus(chat),
                membersCanInvite = chat.permissions.canInviteUsers,
                asAdmin = asAdmin,
            )
        }
    }

    override suspend fun inlineQueryTargets(target: SwitchInlineTarget.Chosen): List<ChatSummary> {
        val saved = savedMessagesChatId.value
        val listed = listOf(ChatFolder.Main, ChatFolder.Archive)
            .flatMap { folder -> (listFor(folder).value as? ChatListState.Ready)?.chats.orEmpty() }
            .distinctBy { it.id }
            .filter { it.id != saved }
        /*
         * Druh chatu nese položka seznamu, takže TDLib se ptá jen tam, kde chat projít může.
         * Dotaz na každý chat v seznamu (stovky) trval na emulátoru přes minutu.
         */
        return listed.filter { summary ->
            when (summary.kind) {
                // Do soukromého chatu se píše vždycky; bota od člověka rozliší až uživatel za
                // chatem (lokální dotaz), a jen když bot dovolil jedno a druhé ne.
                ChatKind.Private -> when {
                    target.users == target.bots -> target.users
                    else -> {
                        val isBot = client.getUser(userId = summary.id).toResult().getOrNull()?.type is UserTypeBot
                        target.allows(ChatKind.Private, isBot)
                    }
                }
                ChatKind.Group, ChatKind.Channel -> target.allows(summary.kind, isBot = false) && run {
                    val chat = client.getChat(chatId = summary.id).toResult().getOrNull() ?: return@run false
                    writeAccess(
                        isChannel = (chat.type as? ChatTypeSupergroup)?.isChannel == true,
                        status = client.ownMemberStatus(chat),
                        membersCanSend = chat.permissions.canSendBasicMessages,
                    ) == WriteAccess.Allowed
                }
            }
        }
    }

    override suspend fun addBotToGroup(url: String, groupChatId: Long): Result<Unit> = runCatching {
        val link = client.getInternalLinkType(link = url).toResult().getOrThrow() as? InternalLinkTypeBotStartInGroup
            ?: error("Odkaz nepřidává bota do skupiny.")
        val bot = client.searchPublicChat(username = link.botUsername).toResult().getOrThrow()
        val botUserId = (bot.type as? ChatTypePrivate)?.userId ?: error("Odkaz nevede na bota.")
        val rights = link.administratorRights
        if (rights != null) {
            // V základní skupině jmenovat jde jen člena; supergroupa bota přidá rovnou se jmenováním.
            val basicGroup = client.getChat(chatId = groupChatId).toResult().getOrThrow().type is ChatTypeBasicGroup
            if (basicGroup) {
                client.addChatMember(chatId = groupChatId, userId = botUserId, forwardLimit = 0).toResult().getOrThrow()
            }
            // Práva přesně z odkazu: bot si o ně řekl a uživatel je v potvrzení viděl jako „správce".
            client.setChatMemberStatus(
                chatId = groupChatId,
                memberId = MessageSenderUser(userId = botUserId),
                status = ChatMemberStatusAdministrator(canBeEdited = true, rights = rights),
            ).toResult().getOrThrow()
        } else {
            client.addChatMember(chatId = groupChatId, userId = botUserId, forwardLimit = 0).toResult().getOrThrow()
        }
        client.sendBotStartMessage(botUserId = botUserId, chatId = groupChatId, parameter = link.startParameter)
            .toResult().getOrThrow()
        Unit
    }

    override suspend fun suitableDiscussionChats(): List<ChatSummary> =
        client.getSuitableDiscussionChats().toResult()
            .map { summariesFor(it.chatIds) }
            .getOrElse { failure ->
                logger.w(failure) { "Skupiny pro diskusi kanálu se nepodařilo načíst." }
                emptyList()
            }

    override suspend fun contacts(): Result<List<ChatSummary>> =
        client.getContacts().toResult().map { users ->
            val chatIds = users.userIds.toList().mapNotNull { userId ->
                when (val chat = client.createPrivateChat(userId = userId, force = false)) {
                    is TdlResult.Success -> chat.result.id
                    is TdlResult.Failure -> {
                        logger.w { "createPrivateChat($userId) selhalo: ${chat.code} ${chat.message}" }
                        null
                    }
                }
            }
            val summaries = summariesFor(chatIds.toLongArray()).map { it.withPresence() }
            summaries.sortedBy { it.title.lowercase() }
        }

    override suspend fun summaries(chatIds: List<Long>): List<ChatSummary> = summariesFor(chatIds.toLongArray())

    override suspend fun openSavedMessages(): Result<Long> {
        val userId = me.value ?: session.me().getOrNull()?.id
            ?: return Result.failure(IllegalStateException("Účet není přihlášený."))
        return holder.client.createPrivateChat(userId = userId, force = false).toResult().map { it.id }
    }

    override suspend fun deleteOrLeave(chatId: Long): Result<Unit> {
        val (summary, sponsor) = mutex.withLock { known[chatId] to positions[chatId]?.get(ChatFolder.Main)?.sponsor }
        summary ?: return Result.failure(IllegalStateException("Chat není známý."))

        // Oznámení od Telegramu se skrývá smazáním historie s odebráním ze seznamu; z kanálu se
        // neodchází, uživatel v něm není (TDLib `delete_dialog_history` u sponzorovaného chatu).
        val result = if (summary.isPrivate || sponsor is ChatSponsor.Announcement) {
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

    override suspend fun spamActions(chatId: Long): Result<SpamActions> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val userId = chat.blockableUserId()
        SpamActions(
            canBlock = userId != null,
            // Blokovaného pozná jen `UserFullInfo`; `Chat` to nenese.
            isBlocked = userId != null &&
                client.getUserFullInfo(userId = userId).toResult().getOrNull()?.blockList != null,
            canReport = chat.canBeReported || chat.reportsSpamDirectly(),
            reportsSpamDirectly = chat.reportsSpamDirectly(),
        )
    }

    override suspend fun setBlocked(chatId: Long, blocked: Boolean): Result<Unit> {
        val chat = client.getChat(chatId = chatId).toResult().getOrNull()
        val userId = chat?.blockableUserId()
            ?: return Result.failure(IllegalStateException("V chatu $chatId není koho blokovat."))
        return setSenderBlocked(userId, blocked)
    }

    // Blokuje se hlavní seznam (`BlockListMain`) — ten brání psát i volat; stories mají vlastní.
    override suspend fun setSenderBlocked(senderId: Long, blocked: Boolean): Result<Unit> =
        client.setMessageSenderBlockList(
            senderId = senderId.toMessageSender(),
            blockList = if (blocked) BlockListMain() else null,
        ).toResult().map { }

    /**
     * Server vrací jen id odesílatelů; jména a fotky se dočítají lokálně (`getUser` / `getChat`
     * TDLib zná, protože mu je server k seznamu poslal). Kdo se dočíst nedá, ze stránky vypadne —
     * [BlockedSendersPage.totalCount] ale zůstává od serveru, aby stránkování nepřeskočilo zbytek.
     */
    override suspend fun blockedSenders(offset: Int, limit: Int): Result<BlockedSendersPage> = runCatching {
        val page = client.getBlockedMessageSenders(blockList = BlockListMain(), offset = offset, limit = limit)
            .toResult().getOrThrow()
        BlockedSendersPage(
            senders = page.senders.filterNotNull().mapNotNull { blockedSender(it) },
            totalCount = page.totalCount,
        )
    }

    private suspend fun blockedSender(sender: MessageSender): BlockedSender? = when (sender) {
        is MessageSenderUser -> client.getUser(userId = sender.userId).toResult().getOrNull()?.let { user ->
            BlockedSender(
                id = user.id,
                title = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
                avatarFileId = user.profilePhoto?.small?.id,
                kind = ChatKind.Private,
                username = user.usernames?.activeUsernames?.firstOrNull(),
                isBot = user.type is UserTypeBot,
            )
        }
        is MessageSenderChat -> client.getChat(chatId = sender.chatId).toResult().getOrNull()?.let { chat ->
            BlockedSender(
                id = chat.id,
                title = chat.title,
                avatarFileId = chat.photo?.small?.id,
                kind = chat.type.chatKind(),
                username = null,
                isBot = false,
            )
        }
    }

    /**
     * `reportChat` je PRŮVODCE: server smí odpovědět, že chce volbu důvodu, text nebo konkrétní
     * zprávy. Jedním krokem projde jen chat se spamovým action barem (viz
     * [Chat.reportsSpamDirectly]); jinde vede odpověď do dalšího kroku průvodce.
     */
    override suspend fun report(chatId: Long, request: ReportRequest): Result<ReportStep> = client
        .reportChat(
            chatId = chatId,
            optionId = request.optionId?.bytes ?: ByteArray(0),
            messageIds = request.messageIds.toLongArray(),
            text = request.text,
        )
        .toResult()
        .map { result ->
            // Bez `else`: nový krok v novější TDLib musí rozbít překlad, ne tiše skončit.
            when (result) {
                is ReportChatResultOk -> ReportStep.Done
                is ReportChatResultOptionRequired -> ReportStep.ChooseOption(
                    title = result.title,
                    options = result.options.map { ReportOption(ReportOptionId(it.id), it.text) },
                )
                is ReportChatResultTextRequired -> ReportStep.EnterText(ReportOptionId(result.optionId), result.isOptional)
                is ReportChatResultMessagesRequired -> ReportStep.NeedsMessages
            }
        }

    /**
     * Odběr stojí před prvním čtením (`onStart`), aby se změna mezi nimi neztratila. Update nese
     * pruh sám, ale čte se znovu celý chat: kdo jde zablokovat, se pozná z typu chatu.
     */
    override fun chatBar(chatId: Long): Flow<ChatBar?> =
        holder.updates { chatActionBarUpdates }
            .filter { it.chatId == chatId }
            .map { }
            .onStart { emit(Unit) }
            .map { client.getChat(chatId = chatId).toResult().getOrNull()?.chatBar() }
            .distinctUntilChanged()

    /**
     * Chat říká, ŽE videochat je (`groupCallId`, nula = není), hovor, kolik v něm je lidí a jestli
     * teprve začne. Při změně hovoru se odběr přepne (`flatMapLatest`).
     *
     * **Pruh se ukáže hned, i když se hovor nenačte** (počet pak chybí): `getGroupCall` jde na
     * server a pruh, který naskočí až po něm, by poskočil. Skončený hovor (`isActive = false`
     * a nic naplánovaného) pruh schová, i kdyby update chatu ještě nedorazil.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun videoChat(chatId: Long): Flow<VideoChatInfo?> =
        holder.updates { chatVideoChatUpdates }
            .filter { it.chatId == chatId }
            .map { it.videoChat.groupCallId }
            .onStart { emit(client.getChat(chatId = chatId).toResult().getOrNull()?.videoChat?.groupCallId ?: 0) }
            .distinctUntilChanged()
            .flatMapLatest { callId -> if (callId == 0) flowOf(null) else groupCall(chatId, callId) }
            .distinctUntilChanged()

    private fun groupCall(chatId: Long, callId: Int): Flow<VideoChatInfo?> = flow {
        val isChannel = (client.getChat(chatId = chatId).toResult().getOrNull()?.type as? ChatTypeSupergroup)?.isChannel == true
        emit(VideoChatInfo(isLiveStream = isChannel, participantCount = null, scheduledStart = null, title = ""))
        client.getGroupCall(groupCallId = callId).toResult()
            .onSuccess { emit(it.toVideoChatInfo(isChannel)) }
            .onFailure { logger.w(it) { "Videochat $callId chatu $chatId se nepodařilo načíst." } }
        emitAll(
            holder.updates { groupCallUpdates }
                .filter { it.groupCall.id == callId }
                .map { it.groupCall.toVideoChatInfo(isChannel) },
        )
    }

    private fun GroupCall.toVideoChatInfo(isChannel: Boolean): VideoChatInfo? =
        if (!isActive && scheduledStartDate == 0) {
            null
        } else {
            VideoChatInfo(
                isLiveStream = isChannel || isRtmpStream,
                participantCount = participantCount,
                scheduledStart = scheduledStartDate.takeIf { it > 0 },
                title = title,
            )
        }

    override suspend fun dismissChatBar(chatId: Long): Result<Unit> =
        client.removeChatActionBar(chatId = chatId).toResult().map { }

    /** Pruh zmizí sám — TDLib ho po nasdílení odebere a pošle `UpdateChatActionBar`. */
    override suspend fun sharePhoneNumber(chatId: Long): Result<Unit> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val userId = chat.blockableUserId() ?: error("V chatu $chatId není komu číslo nasdílet.")
        client.sharePhoneNumber(userId = userId).toResult().getOrThrow()
    }

    /**
     * Obojí souběžně, jak to TDLib u `canUnarchive` předepisuje („simultaneously"). Selhání
     * kteréhokoli je selhání celku; co prošlo, se nevrací — přesunutý chat se ztlumením by
     * uživatel viděl a napravil, kdežto „vrácení" bez přesunu by vypadalo, že nic neudělalo.
     */
    override suspend fun unarchiveFromSpamBar(chatId: Long): Result<Unit> = coroutineScope {
        val move = async { setArchived(chatId, archived = false) }
        val unmute = async {
            client.setChatNotificationSettings(chatId = chatId, notificationSettings = defaultChatNotificationSettings())
                .toResult()
                .map { }
        }
        listOf(move, unmute).awaitAll().firstOrNull { it.isFailure } ?: Result.success(Unit)
    }

    /**
     * Jméno se bere z účtu protějšku, ne od uživatele: pruh nabízí „přidat", ne „vyplnit
     * formulář". Telefon se posílá jen známý — neznámý smí být prázdný. Svoje číslo protějšku
     * nesdílíme (`sharePhoneNumber = false`); to je samostatné rozhodnutí o soukromí.
     */
    override suspend fun addToContacts(chatId: Long): Result<Unit> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val userId = chat.blockableUserId() ?: error("V chatu $chatId není koho přidat do kontaktů.")
        val user = client.getUser(userId = userId).toResult().getOrThrow()
        client.addContact(
            userId = userId,
            contact = ImportedContact(
                phoneNumber = user.phoneNumber,
                // TDLib chce aspoň jeden znak křestního jména; bez něj vezmeme příjmení.
                firstName = user.firstName.ifBlank { user.lastName }.ifBlank { chat.title },
                lastName = if (user.firstName.isBlank()) "" else user.lastName,
                note = null,
            ),
            sharePhoneNumber = false,
        ).toResult().getOrThrow()
    }

    override suspend fun removeContact(userId: Long): Result<Unit> =
        client.removeContacts(userIds = longArrayOf(userId)).toResult().map { }

    override suspend fun searchChats(query: String, onServer: Boolean): Result<List<ChatSummary>> {
        val result = if (onServer) {
            client.searchChatsOnServer(query = query, limit = SEARCH_LIMIT)
        } else {
            client.searchChats(query = query, limit = SEARCH_LIMIT)
        }
        return result.toResult().map { summariesFor(it.chatIds) }
    }

    override suspend fun searchPublicChats(query: String): Result<List<ChatSummary>> =
        client.searchPublicChats(query = query).toResult().map { summariesFor(it.chatIds) }

    override suspend fun searchMessages(query: String, offset: String, filter: MessageSearchFilter): Result<FoundMessagesPage> {
        val (minDate, maxDate) = filter.period.dateRange(Clock.System.now().epochSeconds)
        return client.searchMessages(
            // `null` = hlavní seznam i archiv; tajné chaty `searchMessages` stejně neprochází.
            chatList = null,
            query = query,
            offset = offset,
            limit = MESSAGE_SEARCH_PAGE,
            filter = when (filter.content) {
                null -> null
                SearchContent.Photos -> SearchMessagesFilterPhoto()
                SearchContent.Videos -> SearchMessagesFilterVideo()
                SearchContent.Files -> SearchMessagesFilterDocument()
                SearchContent.Links -> SearchMessagesFilterUrl()
                SearchContent.Voice -> SearchMessagesFilterVoiceNote()
                SearchContent.Music -> SearchMessagesFilterAudio()
            },
            chatTypeFilter = when (filter.chatKind) {
                null -> null
                ChatKind.Private -> SearchMessagesChatTypeFilterPrivate()
                ChatKind.Group -> SearchMessagesChatTypeFilterGroup()
                ChatKind.Channel -> SearchMessagesChatTypeFilterChannel()
            },
            minDate = minDate,
            maxDate = maxDate,
        )
            .toResult()
            .map { it.toPage() }
    }

    override suspend fun searchPublicPosts(tag: String, offset: String): Result<FoundMessagesPage> =
        client.searchPublicMessagesByTag(tag = tag, offset = offset, limit = MESSAGE_SEARCH_PAGE)
            .toResult()
            .map { it.toPage() }

    /**
     * Stránka nalezených zpráv. Chaty se dočtou ([summariesFor]) — u veřejných příspěvků jde
     * o kanály, které uživatel nesleduje a TDLib je zná jen díky téhle odpovědi.
     */
    private suspend fun FoundMessages.toPage(): FoundMessagesPage {
        val found = messages.filterNotNull()
        val chats = summariesFor(found.map { it.chatId }.distinct().toLongArray()).associateBy { it.id }
        return FoundMessagesPage(
            messages = found.mapNotNull { message ->
                val chat = chats[message.chatId] ?: return@mapNotNull null
                FoundMessage(
                    chat = chat,
                    messageId = message.id,
                    text = message.listPreview(),
                    entities = message.previewEntities(),
                    date = message.date,
                    isOutgoing = message.isOutgoing,
                    senderName = if (chat.kind == ChatKind.Group && !message.isOutgoing) {
                        foundSenderName(message.senderId, message.chatId)
                    } else {
                        null
                    },
                    topicId = (message.topicId as? MessageTopicForum)?.forumTopicId,
                )
            },
            // Prázdný `nextOffset` je u TDLib konec výsledků.
            nextOffset = nextOffset.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Jméno odesílatele nalezené zprávy ve skupině. Lokální dotaz — TDLib odesílatele zná, když
     * zná zprávu. Anonymní správce (odesílatel je skupina sama) jméno nemá: název chatu už řádek
     * nese.
     */
    private suspend fun foundSenderName(sender: MessageSender, chatId: Long): String? = when (sender) {
        is MessageSenderUser -> client.getUser(userId = sender.userId).toResult().getOrNull()
            ?.let { user -> listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ") }
        is MessageSenderChat -> sender.chatId.takeIf { it != chatId }
            ?.let { client.getChat(chatId = it).toResult().getOrNull()?.title }
    }?.ifBlank { null }

    // `searchChats` s prázdným dotazem vrací právě historii hledání — `searchRecentlyFoundChats`
    // prázdný dotaz nebere.
    override suspend fun recentlyFoundChats(): Result<List<ChatSummary>> =
        client.searchChats(query = "", limit = SEARCH_LIMIT).toResult().map { summariesFor(it.chatIds) }

    override suspend fun addRecentlyFound(chatId: Long): Result<Unit> =
        client.addRecentlyFoundChat(chatId = chatId).toResult().map { }

    override suspend fun clearRecentlyFound(): Result<Unit> =
        client.clearRecentlyFoundChats().toResult().map { }

    override suspend fun topChats(): Result<List<ChatSummary>> =
        when (val result = client.getTopChats(category = TopChatCategoryUsers(), limit = TOP_CHATS_LIMIT)) {
            is TdlResult.Success -> Result.success(summariesFor(result.result.chatIds))
            // Uživatel si „nejčastější" v Telegramu vypnul — není co ukázat, chyba to není.
            is TdlResult.Failure -> {
                logger.d { "getTopChats: ${result.code} ${result.message}" }
                Result.success(emptyList())
            }
        }

    /**
     * Položky pro chaty podle id, v pořadí id. Skoro vždy je repository zná (drží všechny,
     * které TDLib ohlásil); chybějící se dočtou a uloží. Složka se doplní podle pozice, aby
     * výsledek hledání věděl, že je chat v archivu.
     */
    private suspend fun summariesFor(ids: LongArray): List<ChatSummary> = ids.toList().mapNotNull { id ->
        val cached = mutex.withLock { known[id]?.let { it to positions[id] } }
        if (cached != null) {
            val (chat, folders) = cached
            return@mapNotNull chat.inFolderOf(folders)
        }
        when (val chat = client.getChat(id)) {
            is TdlResult.Success -> {
                upsert(chat.result)
                val summary = mutex.withLock { known[id] } ?: chat.result.toSummary()
                summary.inFolderOf(chat.result.positions.toFolderPositions())
            }
            is TdlResult.Failure -> {
                logger.w { "getChat($id) selhalo: ${chat.code} ${chat.message}" }
                null
            }
        }
    }

    private fun ChatSummary.inFolderOf(folders: Map<ChatFolder, FolderPosition>?): ChatSummary {
        // Podtitul „V archivu" říká, ve kterém z dvojice hlavní seznam / archiv chat je; složka
        // Telegramu je jen výběr vedle nich, a do odpovědi nepatří.
        val folder = folders?.keys?.firstOrNull { it !is ChatFolder.Custom } ?: return this
        val position = folders.getValue(folder)
        return copy(folder = folder, order = position.order, isPinned = position.isPinned, isArchived = folder == ChatFolder.Archive)
    }

    override suspend fun loadMore(folder: ChatFolder) {
        if (folder in exhausted) return

        // `loadChats` vrací Ok, ne data — jen řekne TDLib, ať dotáhne další kus ze serveru.
        // Chyba 404 znamená „už není co načítat", což není selhání, ale konec seznamu.
        when (val result = client.loadChats(chatList = folder.toChatList(), limit = PAGE_SIZE)) {
            is TdlResult.Failure -> {
                if (result.code == NOT_FOUND) {
                    exhausted += folder
                    logger.d { "Složka $folder je celá načtená." }
                } else {
                    logger.e { "loadChats($folder) selhalo: ${result.code} ${result.message}" }
                    // Když už nějaké chaty máme, chybu nedeklarujeme jako prázdný stav —
                    // uživatel by přišel o seznam kvůli selhání dotažení dalších.
                    if (mutex.withLock { countIn(folder) } == 0) {
                        listFor(folder).value = ChatListState.Failed(UiText(Res.string.chats_load_failed))
                        return
                    }
                }
            }

            is TdlResult.Success -> Unit
        }

        syncFromLocalDatabase(folder)
    }

    /**
     * Přečte aktuální obsah hlavní složky z **lokální databáze** TDLib a doplní ho do mapy.
     *
     * Proč to nestačí nechat na updatech: updaty chodí jen na ZMĚNY. Když se repository vytvoří
     * (nebo se odběr zaregistruje) až po `AuthorizationStateReady`, první dávka `UpdateNewChat`
     * už proletěla do prázdna a seznam by zůstal navždy prázdný. Tohle je ta pojistka — updaty
     * pak drží seznam živý, tenhle průchod ho jen spolehlivě nastartuje.
     */
    private suspend fun syncFromLocalDatabase(folder: ChatFolder) {
        // Počítá se jen složka, ne všechny známé chaty — těch je (i mimo složky) víc.
        val limit = mutex.withLock { countIn(folder) } + PAGE_SIZE

        val chatIds = when (val result = client.getChats(chatList = folder.toChatList(), limit = limit)) {
            is TdlResult.Failure -> {
                logger.e { "getChats($folder) selhalo: ${result.code} ${result.message}" }
                return
            }

            is TdlResult.Success -> result.result.chatIds
        }

        // Načte se všechno, teprve pak se zapíše a publikuje JEDNOU. Publikovat po každém chatu
        // by znamenalo tolik emisí, kolik je chatů (naměřeno 77 při 30 chatech) a stejný počet
        // zbytečných přeřazení celého seznamu.
        // `toList()` je nutný: `chatIds` je LongArray a `mapNotNull` je definované jen pro
        // `Array<T>` a `Iterable`, ne pro primitivní pole.
        val chats = chatIds.toList().mapNotNull { chatId ->
            when (val chat = client.getChat(chatId)) {
                is TdlResult.Success -> chat.result
                is TdlResult.Failure -> {
                    logger.w { "getChat($chatId) selhalo: ${chat.code} ${chat.message}" }
                    null
                }
            }
        }
        val summaries = chats.map { it.fullSummary().withPresence() }

        mutex.withLock {
            summaries.forEach { known[it.id] = it.keepingLiveState(known[it.id]) }
            chats.forEach { positions[it.id] = it.positions.toFolderPositions() }
            synced += folder
        }

        // I prázdný výsledek je výsledek — bez tohohle by seznam bez chatů zůstal viset na spinneru.
        publish()
    }

    private suspend fun upsert(chat: Chat) {
        val summary = chat.fullSummary()
        mutex.withLock {
            val fresh = chat.positions.toFolderPositions()
            // Pozice NOVÉHO chatu, které už přišly vlastním updatem, mají přednost: updaty chodí
            // v různých korutinách a `fullSummary` se dočítá, takže `UpdateChatPosition`
            // poslaný až po `UpdateNewChat` bývá zpracovaný dřív — a přepsat ho pozicemi
            // z objektu chatu by chat ze seznamu vymazalo (tak mizela skupina hned po převodu).
            // U známého chatu je objekt čerstvě přečtený a platí celý.
            positions[chat.id] = if (summary.id in known) fresh else fresh + positions[chat.id].orEmpty()
            known[summary.id] = summary.keepingLiveState(known[summary.id])
        }
        publish()
    }

    private suspend fun applyPosition(chatId: Long, position: ChatPosition) {
        val folder = position.list.folder() ?: return
        changePositions(chatId) { current ->
            if (position.order == 0L) current - folder else current + (folder to position.toFolderPosition())
        }
    }

    /**
     * Pozice z updatu, který nese i něco jiného (poslední zpráva, koncept). Mění se jen složky,
     * které update jmenuje; nula znamená odchod ze složky jako u [applyPosition].
     */
    private suspend fun setPositions(chatId: Long, update: Array<ChatPosition>) {
        val changes = update.mapNotNull { position -> position.list.folder()?.let { it to position } }
        if (changes.isEmpty()) return
        changePositions(chatId) { current ->
            changes.fold(current) { acc, (folder, position) ->
                if (position.order == 0L) acc - folder else acc + (folder to position.toFolderPosition())
            }
        }
    }

    private suspend fun changePositions(
        chatId: Long,
        transform: (Map<ChatFolder, FolderPosition>) -> Map<ChatFolder, FolderPosition>,
    ) {
        mutex.withLock {
            val updated = transform(positions[chatId].orEmpty())
            if (updated.isEmpty()) positions.remove(chatId) else positions[chatId] = updated
        }
        publish()
    }

    /** Počet chatů ve složce. Volat pod [mutex]. */
    private fun countIn(folder: ChatFolder): Int = positions.values.count { folder in it }

    /**
     * Dostupnost a činnost přežijí přepsání chatu.
     *
     * `Chat` od TDLib je nenese — chodí samostatnými updaty. Kdyby se položka přepsala rovnou,
     * každá nová zpráva (`UpdateNewChat` při dotažení další stránky, seed z databáze) by zhasla
     * zelenou tečku i „píše…", protože obojí by se vrátilo na `null`.
     */
    /** Položka i s tím, co `Chat` nenese a dočítá se lokálně: ověření a to, jestli je to fórum. */
    /** Pod jménem prázdného tajného chatu jeho stav; jiný chat `null`. Lokální dotaz. */
    private suspend fun secretChatPreview(type: ChatType): UiText? {
        val secret = type as? ChatTypeSecret ?: return null
        return client.getSecretChat(secretChatId = secret.secretChatId).toResult().getOrNull()?.state?.preview()
    }

    private suspend fun secretChatPreview(chatId: Long): UiText? =
        client.getChat(chatId = chatId).toResult().getOrNull()?.type?.let { secretChatPreview(it) }

    private suspend fun Chat.fullSummary(): ChatSummary = toSummary().let { summary ->
        // Protějšek tajného chatu si zapamatovat dřív, než se bude dohledávat dostupnost.
        (type as? ChatTypeSecret)?.let { secret -> mutex.withLock { secretPeers[id] = secret.userId } }
        summary.copy(
            verification = client.verificationOf(this),
            isForum = client.isForum(this),
            lastMessagePreview = lastMessage?.let { message -> message.namedMemberPreview() }
                ?: summary.lastMessagePreview
                ?: secretChatPreview(type),
        )
    }

    /** Náhled poslední zprávy; zpráva o členech se jmény („Pixel 5 opustil(a) skupinu"). */
    private suspend fun Message.listPreview(): UiText = namedMemberPreview() ?: previewText()

    private suspend fun Message.namedMemberPreview(): UiText? =
        namedMemberServiceText(myUserId = { myUserId }) { id ->
            client.getUser(userId = id).toResult().getOrNull()?.fullName()?.ifBlank { null }
        }

    private fun ChatSummary.keepingLiveState(previous: ChatSummary?): ChatSummary =
        if (previous == null) this else copy(
            presence = presence ?: previous.presence,
            activity = activity ?: previous.activity,
            verification = verification ?: previous.verification,
        )

    /**
     * Upraví existující záznam. Když chat ještě neznáme, update se **zahodí** — bez `UpdateNewChat`
     * nemáme titulek ani zbytek, a vyrábět poloprázdný řádek by v seznamu problikávalo.
     *
     * [fetchUnknown]: neznámý chat se místo toho **dočte celý** (`getChat`, lokální). TDLib sice
     * `UpdateNewChat` pošle dřív než změny k chatu, jenže updaty se tu zpracovávají v různých
     * korutinách a [upsert] se před zápisem dočítá — poslední zpráva skupiny čerstvě převedené
     * na supergroupu tak doběhla dřív, zahodila se a řádek neměl náhled až do restartu. Jen pro
     * poslední zprávu: dostupnost chodí i k uživatelům bez chatu, a tam by se dotaz opakoval
     * naprázdno s každou změnou.
     */
    /** Soukromý chat s uživatelem (id uživatele) a všechny jeho známé tajné chaty. */
    private suspend fun chatsOfUser(userId: Long): List<Long> =
        listOf(userId) + mutex.withLock { secretPeers.filterValues { it == userId }.keys.toList() }

    private suspend fun edit(chatId: Long, fetchUnknown: Boolean = false, transform: (ChatSummary) -> ChatSummary) {
        val found = mutex.withLock {
            val current = known[chatId] ?: return@withLock false
            known[chatId] = transform(current)
            true
        }
        if (found) {
            publish()
        } else if (fetchUnknown) {
            client.getChat(chatId = chatId).toResult().getOrNull()?.let { upsert(it) }
        }
    }

    private suspend fun clear() {
        mutex.withLock {
            known.clear()
            positions.clear()
            secretPeers.clear()
            exhausted.clear()
            synced.clear()
            activityTimers.values.forEach { it.cancel() }
            activityTimers.clear()
        }
        me.value = null
        premium.value = false
        trial.value = null
        all.value = emptyMap()
        activitySenders.value = emptyMap()
        _archiveUnread.value = ArchiveUnread()
        _folderUnread.value = emptyMap()
        _chatFolders.value = ChatFolders()
        lists.value.values.forEach { it.value = ChatListState.Loading }
    }

    /**
     * Sestaví seznam každé složky. Složka je hotová, jakmile se jednou přečetla z databáze, nebo
     * když v ní už něco je — prázdný archiv, na který se ještě nikdo nezeptal, se načítá, není
     * „prázdný".
     */
    private suspend fun publish() {
        val folders = lists.value.keys
        val (snapshot, byFolder, ready) = mutex.withLock {
            val byFolder = folders.associateWith { folder ->
                known.values
                    .mapNotNull { chat ->
                        val chatPositions = positions[chat.id]
                        chatPositions?.get(folder)?.let { position ->
                            chat.copy(
                                order = position.order,
                                isPinned = position.isPinned,
                                folder = folder,
                                isArchived = ChatFolder.Archive in chatPositions,
                                sponsor = position.sponsor,
                            )
                        }
                    }
                    .sortedByDescending { it.order }
            }
            Triple(known.toMap(), byFolder, folders.filter { it in synced || byFolder.getValue(it).isNotEmpty() })
        }

        all.value = snapshot
        ready.forEach { folder ->
            listFor(folder).value = ChatListState.Ready(
                chats = byFolder.getValue(folder),
                canLoadMore = folder !in exhausted,
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 30

        /** Výsledků hledání chatů. Víc se na obrazovku stejně nevejde a nikdo je neprojde. */
        const val SEARCH_LIMIT = 50

        /** Stránka hledání zpráv. TDLib jich stejně vrací, kolik uzná za vhodné. */
        const val MESSAGE_SEARCH_PAGE = 30

        /** Řádek nejčastějších lidí nad historií hledání. */
        const val TOP_CHATS_LIMIT = 10

        /** TDLib hlásí „už není co načítat" jako 404. */
        const val NOT_FOUND = 404

        /** Hledání uživatele podle čísla nebo tokenu hlásí neexistující účet i jako 400. */
        const val BAD_REQUEST = 400

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

        /** Server rozhoduje, jestli účet smí zapnout automatickou archivaci chatů od neznámých. */
        const val ARCHIVE_UNKNOWN_OPTION = "can_archive_and_mute_new_chats_from_unknown_users"
    }
}

/**
 * Převod na doménový model. Pozice ve složce se nevyplňuje — tu drží repository zvlášť
 * a dosadí ji až do seznamu konkrétní složky.
 */
private suspend fun Chat.toSummary(): ChatSummary {
    return ChatSummary(
        id = id,
        title = title,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessage?.previewText(),
        lastMessagePreviewEntities = lastMessage?.previewEntities().orEmpty(),
        unreadMentionCount = unreadMentionCount,
        unreadReactionCount = unreadReactionCount,
        unreadPollVoteCount = unreadPollVoteCount,
        isPinned = false,
        order = 0L,
        avatarFileId = photo?.small?.id,
        isMuted = notificationSettings.isMuted(),
        isMarkedAsUnread = isMarkedAsUnread,
        kind = type.chatKind(),
        isSecret = type is ChatTypeSecret,
        avatarColorId = (type as? ChatTypeSecret)?.userId ?: id,
        lastMessageTimestamp = lastMessage?.date,
        // Dostupnost `Chat` nenese — dorazí updatem. Do té doby je `null`, což UI kreslí
        // prostě bez tečky.
        presence = null,
        activity = null,
        draft = draftMessage?.toChatDraft(),
        emojiStatus = emojiStatus.statusEmojiId(),
    )
}

/**
 * Koncept jako text, nebo `null`.
 *
 * Ukazuje se jen textový koncept. Rozpracovaná hlasovka nebo video kolečko jsou taky koncepty,
 * ale psát o nich „Koncept:" bez obsahu by nic neřeklo; zbude u nich náhled poslední zprávy.
 */
internal fun DraftMessage.toChatDraft(): ChatDraft? {
    val formatted = (content as? DraftMessageContentText)?.text ?: return null
    if (formatted.text.isBlank()) return null
    return ChatDraft(
        text = formatted.text,
        timestamp = date,
        entities = formatted.toBody().entities.filter {
            it.type is MessageEntityType.CustomEmoji || it.type == MessageEntityType.Spoiler
        },
    )
}

/** Pozice chatu v jedné složce. */
private data class FolderPosition(val order: Long, val isPinned: Boolean, val sponsor: ChatSponsor? = null)

/**
 * Koho v chatu jde zablokovat, nebo `null`, když nikoho.
 *
 * Jen soukromý a tajný chat s NĚKÝM JINÝM: ve skupině a kanálu se blokuje jinak (vyhození člena)
 * a v Uložených zprávách by šlo o blokování sebe — to TDLib odmítne.
 */
private fun Chat.blockableUserId(): Long? = when (val type = type) {
    is ChatTypePrivate -> type.userId
    is ChatTypeSecret -> type.userId
    else -> null
}

/**
 * Jde chat nahlásit jako spam JEDNÍM voláním?
 *
 * Rozhoduje action bar, ne `canBeReported`: ten říká jen „tohle jde nahlásit", zatímco
 * jednokrokové nahlášení s prázdnou volbou i seznamem zpráv slibuje dokumentace právě
 * u spamového action baru. Pravidlo projektu je nenabízet akci, která skončí chybou od serveru.
 */
private fun Chat.reportsSpamDirectly(): Boolean =
    actionBar is ChatActionBarReportSpam || actionBar is ChatActionBarReportAddBlock

/**
 * Soukromý chat s cizím, který má emoji status — pruh spamu pak MUSÍ upozornit, že status nic
 * nepotvrzuje (dokumentace `chatActionBarReportSpam` / `ReportAddBlock`: „a notice about emoji
 * status usage must be shown"). Status jde koupit s Premium a vypadá jako odznak u jména.
 */
private fun Chat.warnsEmojiStatus(): Boolean = blockableUserId() != null && emojiStatus.statusEmojiId() != null

/** Pruh nad konverzací; `null` u pruhů, které aplikace nekreslí. */
private fun Chat.chatBar(): ChatBar? = when (val bar = actionBar) {
    is ChatActionBarSharePhoneNumber -> SharePhoneBar
    is ChatActionBarInviteMembers -> InviteMembersBar
    is ChatActionBarJoinRequest -> JoinRequestBar(
        chatTitle = bar.title,
        isChannel = bar.isChannel,
        requestDate = bar.requestDate,
    )
    is ChatActionBarReportSpam -> SpamBar(
        canReportSpam = true,
        canBlock = false,
        canAddContact = false,
        canUnarchive = bar.canUnarchive,
        warnsEmojiStatus = warnsEmojiStatus(),
    )
    is ChatActionBarReportAddBlock -> SpamBar(
        canReportSpam = true,
        canBlock = blockableUserId() != null,
        canAddContact = blockableUserId() != null,
        canUnarchive = bar.canUnarchive,
        warnsEmojiStatus = warnsEmojiStatus(),
        account = bar.accountInfo?.let {
            CounterpartAccount.of(
                month = it.registrationMonth,
                year = it.registrationYear,
                phoneCountryCode = it.phoneNumberCountryCode,
                nameChangedAt = it.lastNameChangeDate,
                photoChangedAt = it.lastPhotoChangeDate,
            )
        },
    )
    is ChatActionBarAddContact -> SpamBar(canReportSpam = false, canBlock = false, canAddContact = true)
    else -> null
}

/** Kladné id je uživatel, záporné chat — konvence [BlockedSender.id] a [SenderInfo.id]. */
private fun Long.toMessageSender(): MessageSender =
    if (this > 0) MessageSenderUser(userId = this) else MessageSenderChat(chatId = this)

private fun ChatPosition.toFolderPosition() =
    FolderPosition(order = order, isPinned = isPinned, sponsor = source?.toSponsor())

/** `when` bez `else`: nový zdroj v novější TDLib rozbije překlad, ne tiše řádek. */
private fun ChatSource.toSponsor(): ChatSponsor = when (this) {
    is ChatSourceMtprotoProxy -> ChatSponsor.Proxy
    is ChatSourcePublicServiceAnnouncement -> ChatSponsor.Announcement(type = type, text = text)
}

private fun ChatList?.folder(): ChatFolder? = when (this) {
    is ChatListMain -> ChatFolder.Main
    is ChatListArchive -> ChatFolder.Archive
    is ChatListFolder -> ChatFolder.Custom(chatFolderId)
    else -> null
}

private fun ChatFolder.toChatList(): ChatList = when (this) {
    ChatFolder.Main -> ChatListMain()
    ChatFolder.Archive -> ChatListArchive()
    is ChatFolder.Custom -> ChatListFolder(chatFolderId = id)
}

private fun ChatFolderInfo.toTab(): ChatFolderTab {
    val body = name.text.toBody()
    return ChatFolderTab(
        id = id,
        title = body.text,
        titleEntities = body.entities,
        iconName = icon.name,
        isShareable = isShareable,
        hasMyInviteLinks = hasMyInviteLinks,
    )
}

private fun Array<ChatPosition>.toFolderPositions(): Map<ChatFolder, FolderPosition> =
    mapNotNull { position ->
        val folder = position.list.folder() ?: return@mapNotNull null
        if (position.order == 0L) null else folder to position.toFolderPosition()
    }.toMap()


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

/** Druh chatu: tajný chat a bot jsou soukromé, základní skupina i supergroup skupina. */
internal fun ChatType.chatKind(): ChatKind = when (this) {
    is ChatTypeSupergroup -> if (isChannel) ChatKind.Channel else ChatKind.Group
    is ChatTypeBasicGroup -> ChatKind.Group
    is ChatTypePrivate, is ChatTypeSecret -> ChatKind.Private
}

/** Kdo v chatu naposledy něco dělal, a v jakém tématu (skupina s tématy). */
private data class ActivitySource(val senderId: Long, val topicId: Int?)

/**
 * Sekce Nastavení z odkazu `tg://settings/…`. Co Plume nemá (jazyk, Premium, hvězdy…),
 * otevře Nastavení na kořeni — lepší než prohlížeč, který s `tg://` nic neudělá. `when` je bez
 * `else`: nová sekce v novější TDLib rozbije překlad, místo aby tiše skončila na kořeni.
 */
private fun SettingsSection?.toAppScreen(): AppScreen = when (this) {
    null -> AppScreen.Settings
    is SettingsSectionEditProfile -> AppScreen.EditProfile
    is SettingsSectionPrivacyAndSecurity -> AppScreen.General
    is SettingsSectionNotifications -> AppScreen.Notifications
    is SettingsSectionAppearance -> AppScreen.Appearance
    is SettingsSectionDevices, is SettingsSectionQrCode -> AppScreen.Devices
    is SettingsSectionChatFolders -> AppScreen.ChatFolders
    is SettingsSectionDataAndStorage -> AppScreen.Storage
    is SettingsSectionAskQuestion, is SettingsSectionBusiness,
    is SettingsSectionFaq, is SettingsSectionFeatures, is SettingsSectionInAppBrowser,
    is SettingsSectionLanguage, is SettingsSectionMyGrams, is SettingsSectionMyStars,
    is SettingsSectionPowerSaving, is SettingsSectionPremium, is SettingsSectionPrivacyPolicy,
    is SettingsSectionSearch, is SettingsSectionSendGift -> AppScreen.Settings
}

/** Stav tajného chatu slovy — pod jménem chatu, dokud v něm není žádná zpráva. */
private fun SecretChatState.preview(): UiText = UiText(
    when (this) {
        is SecretChatStatePending -> Res.string.secret_chat_state_pending
        is SecretChatStateReady -> Res.string.secret_chat_state_ready
        is SecretChatStateClosed -> Res.string.secret_chat_state_closed
    },
)
