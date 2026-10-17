package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.CanTransferOwnershipResultOk
import dev.g000sha256.tdl.dto.CanTransferOwnershipResultPasswordNeeded
import dev.g000sha256.tdl.dto.CanTransferOwnershipResultPasswordTooFresh
import dev.g000sha256.tdl.dto.CanTransferOwnershipResultSessionTooFresh
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatAdministratorRights
import dev.g000sha256.tdl.dto.ChatMember
import dev.g000sha256.tdl.dto.ChatMemberStatus
import dev.g000sha256.tdl.dto.SupergroupFullInfo
import dev.g000sha256.tdl.dto.Supergroup
import dev.g000sha256.tdl.dto.ReactionTypeCustomEmoji
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.ChatAvailableReactionsSome
import dev.g000sha256.tdl.dto.ChatAvailableReactionsAll
import dev.g000sha256.tdl.dto.ChatAvailableReactions
import dev.g000sha256.tdl.dto.ChatMemberStatusMember
import dev.g000sha256.tdl.dto.ChatMemberStatusRestricted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.time.Clock
import kotlin.time.Duration
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.ChatPermissions
import dev.g000sha256.tdl.dto.ChatMemberStatusBanned
import dev.g000sha256.tdl.dto.ChatMemberStatusCreator
import dev.g000sha256.tdl.dto.ChatMemberStatusLeft
import dev.g000sha256.tdl.dto.ChatPhoto
import dev.g000sha256.tdl.dto.MessageChatChangePhoto
import dev.g000sha256.tdl.dto.SearchMessagesFilterChatPhoto
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.SecretChatStateClosed
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.InputChatPhotoPrevious
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.SupergroupMembersFilterAdministrators
import dev.g000sha256.tdl.dto.SupergroupMembersFilterBanned
import dev.g000sha256.tdl.dto.SupergroupMembersFilterRecent
import dev.g000sha256.tdl.dto.SupergroupMembersFilterRestricted
import dev.g000sha256.tdl.dto.SupergroupMembersFilterSearch
import dev.g000sha256.tdl.dto.UserTypeBot
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.BotVerification
import eu.tintera.plume.resources.content_audio
import eu.tintera.plume.resources.Res
import eu.tintera.plume.core.ui.readString
import dev.g000sha256.tdl.dto.InputFileId
import dev.g000sha256.tdl.dto.InputAudio

/**
 * [ProfileRepository] nad TDLib: `getChat` + podrobnosti podle druhu chatu (`getUserFullInfo`,
 * `getBasicGroupFullInfo`, `getSupergroupFullInfo`), členy `getSupergroupMembers`
 * (u základní skupiny `searchChatMembers`).
 */
class TdlProfileRepository(
    private val holder: TelegramClientHolder,
) : ProfileRepository {

    private val client: TdlClient get() = holder.client

    override suspend fun suggestBirthdate(userId: Long, birthdate: Birthdate): Result<Unit> =
        client.suggestUserBirthdate(userId = userId, birthdate = birthdate.toTdl()).toResult().map { }

    override suspend fun suggestPhoto(userId: Long, photo: ProfilePhotoFile): Result<Unit> =
        client.suggestUserProfilePhoto(userId = userId, photo = photo.toInputChatPhoto()).toResult().map { }

    override suspend fun setAutoDeleteTime(chatId: Long, seconds: Int): Result<Unit> =
        client.setChatMessageAutoDeleteTime(chatId = chatId, messageAutoDeleteTime = seconds)
            .toResult()
            .map { }

    override suspend fun profile(chatId: Long): Result<ChatProfile> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val base = ChatProfile(
            chatId = chatId,
            title = chat.title,
            kind = ProfileKind.Group,
            photoFileId = chat.photo?.big?.id,
            photoThumbnailFileId = chat.photo?.small?.id,
            emojiStatus = chat.emojiStatus.statusEmojiId(),
            autoDeleteTime = chat.messageAutoDeleteTime,
            usernames = emptyList(),
            about = "",
            aboutEntities = emptyList(),
            phone = null,
            memberCount = null,
            canListMembers = false,
        )
        // Bez `else`: nový druh chatu v novější TDLib má rozbít překlad, ne tiše skončit jako skupina.
        val profile = when (val type = chat.type) {
            is ChatTypePrivate -> userProfile(base, type.userId)
            is ChatTypeSecret -> {
                val secret = client.getSecretChat(secretChatId = type.secretChatId).toResult().getOrNull()
                userProfile(base, type.userId).copy(
                    isSecretChat = true,
                    secretChatOpen = secret?.state.let { it != null && it !is SecretChatStateClosed },
                    secretChatKey = secret?.keyHash?.let(SecretChatKey::of),
                )
            }

            is ChatTypeBasicGroup -> {
                val group = client.getBasicGroup(basicGroupId = type.basicGroupId).toResult().getOrNull()
                val full = client.getBasicGroupFullInfo(basicGroupId = type.basicGroupId).toResult().getOrNull()
                base.withAbout(describe(full?.description.orEmpty())).copy(
                    memberCount = group?.memberCount?.takeIf { it > 0 },
                    // Malá skupina členy vydá vždycky.
                    canListMembers = true,
                    inviteLink = full?.inviteLink?.inviteLink?.takeIf { it.isNotBlank() },
                    isMember = client.ownMemberStatus(chat).isMember(),
                    canAddMembers = canInviteMembers(
                        status = client.ownMemberStatus(chat),
                        isChannel = false,
                        membersCanInvite = chat.permissions.canInviteUsers,
                    ),
                    editRights = chatEditRights(
                        isChannel = false,
                        status = client.ownMemberStatus(chat),
                        membersCanChangeInfo = chat.permissions.canChangeInfo,
                    ),
                )
            }

            is ChatTypeSupergroup -> {
                val group = client.getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()
                val full = client.getSupergroupFullInfo(supergroupId = type.supergroupId).toResult().getOrNull()
                base.withAbout(describe(full?.description.orEmpty())).copy(
                    kind = if (type.isChannel) ProfileKind.Channel else ProfileKind.Group,
                    verification = group?.verificationStatus.toVerification(),
                    botVerification = botVerificationNote(full?.botVerification),
                    usernames = group?.usernames?.activeUsernames?.toList().orEmpty(),
                    linkedChat = full?.linkedChatId?.takeIf { it != 0L }?.let { linkedId ->
                        // Lokální dotaz; chat, který TDLib nezná (soukromý kanál bez členství), se vynechá.
                        client.getChat(chatId = linkedId).toResult().getOrNull()
                            ?.let { LinkedChat(chatId = it.id, title = it.title, photoFileId = it.photo?.small?.id) }
                    },
                    memberCount = (full?.memberCount ?: group?.memberCount)?.takeIf { it > 0 },
                    // Kanál členy vydá jen správcům a skupina se skrytými členy vůbec.
                    canListMembers = !type.isChannel && full?.canGetMembers == true,
                    // Skryté členy vidí jen správci; ostatním server vrací samé správce.
                    membersHidden = full?.hasHiddenMembers == true &&
                        client.ownMemberStatus(chat).let { it !is OwnMemberStatus.Creator && it !is OwnMemberStatus.Admin },
                    inviteLink = full?.inviteLink?.inviteLink?.takeIf { it.isNotBlank() },
                    isMember = client.ownMemberStatus(chat).isMember(),
                    canAddMembers = canInviteMembers(
                        status = client.ownMemberStatus(chat),
                        isChannel = type.isChannel,
                        membersCanInvite = chat.permissions.canInviteUsers,
                    ),
                    editRights = chatEditRights(
                        isChannel = type.isChannel,
                        status = client.ownMemberStatus(chat),
                        membersCanChangeInfo = chat.permissions.canChangeInfo,
                    ),
                )
            }
        }
        profile.copy(
            canSetAutoDelete = when (chat.type) {
                // Uložené zprávy nikdo jiný nečte a tajný chat má vlastní časovač zpráv.
                is ChatTypePrivate -> profile.kind != ProfileKind.SavedMessages
                // Tajný chat má časovač zpráv; nastavit jde, dokud chat běží.
            is ChatTypeSecret -> profile.secretChatOpen
                is ChatTypeBasicGroup, is ChatTypeSupergroup -> profile.editRights.canChangeInfo
            },
        )
    }

    private suspend fun userProfile(base: ChatProfile, userId: Long): ChatProfile {
        val user = client.getUser(userId = userId).toResult().getOrThrow()
        val full = client.getUserFullInfo(userId = userId).toResult().getOrNull()
        val me = client.getMe().toResult().getOrNull()?.id
        val isBot = user.type is UserTypeBot
        // Bot bio nemá; o sobě říká v krátkém popisu.
        val about = full?.bio?.takeIf { it.text.isNotBlank() }?.toBody()
            ?: full?.botInfo?.shortDescription?.let { describe(it) }
            ?: MessageBody.Empty
        return base.withAbout(about).copy(
            kind = when {
                userId == me -> ProfileKind.SavedMessages
                isBot -> ProfileKind.Bot
                else -> ProfileKind.User
            },
            usernames = user.usernames?.activeUsernames?.toList().orEmpty(),
            // TDLib posílá číslo bez „+"; prázdné = uživatel ho neukazuje.
            phone = user.phoneNumber.takeIf { it.isNotBlank() }?.let { "+$it" },
            birthdate = full?.birthdate?.toDomain(),
            personalChannel = full?.personalChatId?.takeIf { it != 0L }?.let { client.personalChannel(it) },
            userId = userId,
            isBlocked = full?.blockList != null,
            isContact = user.isContact,
            verification = user.verificationStatus.toVerification(),
            botVerification = botVerificationNote(full?.botVerification),
            hasMainWebApp = (user.type as? UserTypeBot)?.hasMainWebApp == true,
            firstProfileAudio = full?.firstProfileAudio?.toMedia(fallbackName = readString(Res.string.content_audio)),
        )
    }

    override suspend fun profileAudios(userId: Long): Result<List<MessageMedia.Audio>> {
        val fallback = readString(Res.string.content_audio)
        return client.getUserProfileAudios(userId = userId, offset = 0, limit = PROFILE_AUDIO_LIMIT)
            .toResult()
            .map { audios -> audios.audios.map { it.toMedia(fallback) } }
    }

    override suspend fun addProfileAudio(audio: MessageMedia.Audio): Result<Unit> =
        client.addProfileAudio(
            audio = InputAudio(
                // Soubor už na serveru je (skladba ze zprávy), TDLib ho podle id nenahrává znovu.
                audio = InputFileId(id = audio.fileId),
                albumCoverThumbnail = null,
                duration = audio.durationSeconds,
                title = audio.title,
                performer = audio.performer.orEmpty(),
            ),
        ).toResult().map { }

    override suspend fun removeProfileAudio(fileId: Int): Result<Unit> =
        client.removeProfileAudio(fileId = fileId).toResult().map { }

    override suspend fun isProfileAudio(fileId: Int): Boolean =
        client.isProfileAudio(fileId = fileId).toResult().isSuccess

    /** Ověření od bota pro profil; jméno bota je lokální dotaz (TDLib ho zná s plnými údaji). */
    private suspend fun botVerificationNote(verification: BotVerification?): BotVerificationNote? {
        verification ?: return null
        val bot = client.getUser(userId = verification.botUserId).toResult().getOrNull()
        val name = bot?.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" }
            ?: listOf(bot?.firstName, bot?.lastName).filterNot { it.isNullOrBlank() }.joinToString(" ")
        return BotVerificationNote(
            description = verification.customDescription.text.takeIf { it.isNotBlank() },
            botName = name,
        )
    }

    override suspend fun editableChat(chatId: Long): Result<EditableChat> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val isChannel = (chat.type as? ChatTypeSupergroup)?.isChannel == true
        val supergroup = (chat.type as? ChatTypeSupergroup)?.let {
            client.getSupergroup(supergroupId = it.supergroupId).toResult().getOrNull()
        }
        val supergroupInfo = (chat.type as? ChatTypeSupergroup)?.let {
            client.getSupergroupFullInfo(supergroupId = it.supergroupId).toResult().getOrNull()
        }
        val description = when (val type = chat.type) {
            is ChatTypeBasicGroup ->
                client.getBasicGroupFullInfo(basicGroupId = type.basicGroupId).toResult().getOrNull()?.description
            is ChatTypeSupergroup -> supergroupInfo?.description
            else -> null
        }
        val linkedChat = supergroupInfo?.linkedChatId?.takeIf { it != 0L }?.let { linkedId ->
            client.getChat(chatId = linkedId).toResult().getOrNull()
                ?.let { LinkedChat(chatId = it.id, title = it.title, photoFileId = it.photo?.small?.id) }
        }
        val stickerSet = supergroupInfo?.stickerSetId?.takeIf { it != 0L }?.let { setId ->
            client.getStickerSet(setId = setId).toResult().getOrNull()?.let { set ->
                StickerSetSummary(
                    id = set.id,
                    title = set.title,
                    cover = set.stickers.filterNotNull().firstOrNull()?.toMedia(),
                    size = set.stickers.size,
                )
            }
        }
        val isBasicGroup = chat.type is ChatTypeBasicGroup
        // Převedená základní skupina (`isActive == false`) se hlásí jako opuštěná, takže `owner`
        // u ní vyjde `false` — upravuje se už ta nová.
        val own = client.ownMemberStatus(chat)
        val owner = (supergroup != null || isBasicGroup) && (own as? OwnMemberStatus.Creator)?.isMember == true
        val rights = chatEditRights(
            isChannel = isChannel,
            status = own,
            membersCanChangeInfo = chat.permissions.canChangeInfo,
        )
        EditableChat(
            chatId = chatId,
            title = chat.title,
            description = description.orEmpty(),
            photoFileId = chat.photo?.big?.id,
            photoThumbnailFileId = chat.photo?.small?.id,
            isChannel = isChannel,
            rights = rights,
            permissions = chat.permissions.toGroupPermissions(),
            isForum = supergroup?.isForum == true,
            canToggleForum = owner && !isChannel && supergroup?.hasLinkedChat != true,
            isBasicGroup = isBasicGroup,
            username = supergroup?.usernames?.editableUsername.orEmpty(),
            canChangeUsername = owner,
            linkedChat = linkedChat,
            canSetDiscussion = isChannel && rights.canChangeInfo,
            stickerSet = stickerSet,
            canSetStickerSet = !isChannel && supergroupInfo?.canSetStickerSet == true,
            canDeleteForAll = chat.canBeDeletedForAllUsers,
            canViewEventLog = supergroup != null && (own is OwnMemberStatus.Admin || (own as? OwnMemberStatus.Creator)?.isMember == true),
            settings = chat.memberGroupKind()?.let { kind ->
                groupSettings(chat, kind, own, supergroup, supergroupInfo)
            } ?: GroupSettings(),
            moderation = (chat.type as? ChatTypeSupergroup)?.takeIf { own.canModerate() }?.let { type ->
                MemberModeration(
                    restrictedCount = if (isChannel) null else removedCount(type.supergroupId, MemberFilter.Restricted),
                    bannedCount = removedCount(type.supergroupId, MemberFilter.Banned),
                )
            },
        )
    }

    /**
     * Kolik je omezených / vyhozených — `totalCount` téhož dotazu, jakým se čte seznam.
     * **`SupergroupFullInfo.restrictedCount` / `bannedCount` NE**: TDLib ho drží v keši
     * a po omezení člena ukazoval „Nikdo" ještě minutu, zatímco seznam už člověka měl.
     */
    private suspend fun removedCount(supergroupId: Long, filter: MemberFilter): Int =
        client.getSupergroupMembers(
            supergroupId = supergroupId,
            filter = if (filter == MemberFilter.Banned) SupergroupMembersFilterBanned(query = "") else SupergroupMembersFilterRestricted(query = ""),
            offset = 0,
            limit = 1,
        ).toResult().getOrNull()?.totalCount ?: 0

    /** Současné nastavení, zúžené na to, co [groupSettingsOffer] nabídne. */
    private fun groupSettings(
        chat: Chat,
        kind: MemberGroupKind,
        own: OwnMemberStatus?,
        supergroup: Supergroup?,
        info: SupergroupFullInfo?,
    ): GroupSettings {
        val offer = groupSettingsOffer(
            kind = kind,
            own = own,
            isPublic = supergroup?.usernames?.activeUsernames?.any { it.isNotEmpty() } == true,
            hasLinkedChat = supergroup?.hasLinkedChat == true,
            joinToSendMessages = supergroup?.joinToSendMessages == true,
            canHideMembers = info?.canHideMembers == true,
            canToggleAntiSpam = info?.canToggleAggressiveAntiSpam == true,
        )
        fun value(toggle: GroupToggle): Boolean = when (toggle) {
            GroupToggle.AllHistory -> info?.isAllHistoryAvailable == true
            GroupToggle.JoinToSend -> supergroup?.joinToSendMessages == true
            GroupToggle.JoinByRequest -> supergroup?.joinByRequest == true
            GroupToggle.HiddenMembers -> info?.hasHiddenMembers == true
            GroupToggle.AggressiveAntiSpam -> info?.hasAggressiveAntiSpamEnabled == true
            GroupToggle.SignMessages -> supergroup?.signMessages == true
            GroupToggle.ShowMessageSender -> supergroup?.showMessageSender == true
            GroupToggle.ProtectedContent -> chat.hasProtectedContent
        }
        return GroupSettings(
            slowModeDelay = info?.slowModeDelay?.takeIf { offer.slowMode },
            toggles = offer.toggles.associateWith(::value),
            reactions = chat.availableReactions.toChatReactions().takeIf { offer.reactions },
        )
    }

    private fun ChatAvailableReactions.toChatReactions(): ChatReactions = when (this) {
        is ChatAvailableReactionsAll -> ChatReactions(all = true)
        is ChatAvailableReactionsSome -> ChatReactions(
            all = false,
            emojis = reactions.mapNotNull { (it as? ReactionTypeEmoji)?.emoji },
        )
    }

    override suspend fun eventLog(chatId: Long, fromEventId: Long, filter: ChatLogFilter): Result<ChatLogPage> = runCatching {
        val events = client.getChatEventLog(
            chatId = chatId,
            query = filter.query.trim(),
            fromEventId = fromEventId,
            limit = CHAT_LOG_PAGE,
            filters = filter.categories.toTdlFilters(),
            userIds = filter.userIds.toLongArray(),
        ).toResult().getOrThrow().events.filterNotNull()
        // Jména se čtou jednou za stránku: jeden správce bývá autorem desítek událostí.
        val senders = mutableMapOf<Long, SenderInfo>()
        suspend fun sender(id: MessageSender): SenderInfo = when (id) {
            is MessageSenderUser -> senders.getOrPut(id.userId) {
                member(id.userId)?.first ?: SenderInfo(id = id.userId, title = "", avatarFileId = null)
            }
            is MessageSenderChat -> senders.getOrPut(id.chatId) {
                client.getChat(chatId = id.chatId).toResult().getOrNull()
                    ?.let { SenderInfo(id = it.id, title = it.title, avatarFileId = it.photo?.small?.id) }
                    ?: SenderInfo(id = id.chatId, title = "", avatarFileId = null)
            }
        }
        ChatLogPage(
            events = events.map { event ->
                val text = event.action.toEventText { sender(it).title }
                ChatLogEvent(
                    id = event.id,
                    date = event.date,
                    actor = sender(event.memberId),
                    description = text.description,
                    detail = text.detail,
                )
            },
            // Plná stránka = možná je další; pokračuje se od nejstarší vrácené (TDLib ji vynechá).
            nextFromEventId = events.lastOrNull()?.id?.takeIf { events.size == CHAT_LOG_PAGE },
        )
    }

    override suspend fun administrators(chatId: Long): Result<List<SenderInfo>> = runCatching {
        val type = client.getChat(chatId = chatId).toResult().getOrThrow().type as? ChatTypeSupergroup
            ?: return@runCatching emptyList()
        client.getSupergroupMembers(
            supergroupId = type.supergroupId,
            filter = SupergroupMembersFilterAdministrators(),
            offset = 0,
            limit = MAX_MEMBERS_REQUEST,
        ).toResult().getOrThrow().members.filterNotNull()
            // Vlastník napřed, pak ostatní správci v pořadí od serveru.
            .sortedBy { if (it.status is ChatMemberStatusCreator) 0 else 1 }
            .mapNotNull { member -> (member.memberId as? MessageSenderUser)?.userId?.let { member(it)?.first } }
            .distinctBy { it.id }
    }

    override suspend fun deleteChatForAll(chatId: Long): Result<Unit> =
        client.deleteChat(chatId = chatId).toResult().map { }

    override suspend fun setSlowMode(chatId: Long, seconds: Int): Result<Unit> =
        client.setChatSlowModeDelay(chatId = chatId, slowModeDelay = seconds).toResult().map { }

    override suspend fun setGroupToggle(chatId: Long, toggle: GroupToggle, value: Boolean): Result<Unit> = runCatching {
        if (toggle == GroupToggle.ProtectedContent) {
            client.toggleChatHasProtectedContent(chatId = chatId, hasProtectedContent = value).toResult().getOrThrow()
            return@runCatching
        }
        val type = client.getChat(chatId = chatId).toResult().getOrThrow().type as? ChatTypeSupergroup
            ?: error("Chat $chatId není supergroup ani kanál.")
        val id = type.supergroupId
        val result = when (toggle) {
            GroupToggle.AllHistory -> client.toggleSupergroupIsAllHistoryAvailable(supergroupId = id, isAllHistoryAvailable = value)
            GroupToggle.JoinToSend -> client.toggleSupergroupJoinToSendMessages(supergroupId = id, joinToSendMessages = value)
            // Bez strážního bota a bez zásahu do existujících pozvánek — ty mají schvalování vlastní.
            GroupToggle.JoinByRequest -> client.toggleSupergroupJoinByRequest(
                supergroupId = id,
                joinByRequest = value,
                guardBotUserId = 0,
                applyToInviteLinks = false,
            )
            GroupToggle.HiddenMembers -> client.toggleSupergroupHasHiddenMembers(supergroupId = id, hasHiddenMembers = value)
            GroupToggle.AggressiveAntiSpam ->
                client.toggleSupergroupHasAggressiveAntiSpamEnabled(supergroupId = id, hasAggressiveAntiSpamEnabled = value)
            GroupToggle.SignMessages, GroupToggle.ShowMessageSender -> {
                val current = client.getSupergroup(supergroupId = id).toResult().getOrThrow()
                val sign = if (toggle == GroupToggle.SignMessages) value else current.signMessages
                // Profily autorů bez podpisů nedávají smysl; vypnuté podpisy je vypnou taky.
                val show = sign && (if (toggle == GroupToggle.ShowMessageSender) value else current.showMessageSender)
                client.toggleSupergroupSignMessages(supergroupId = id, signMessages = sign, showMessageSender = show)
            }
            GroupToggle.ProtectedContent -> error("vyřízeno výš")
        }
        result.toResult().getOrThrow()
        Unit
    }

    override suspend fun setReactions(chatId: Long, reactions: ChatReactions): Result<Unit> = runCatching {
        val current = client.getChat(chatId = chatId).toResult().getOrThrow().availableReactions
        val available = if (reactions.all) {
            ChatAvailableReactionsAll(maxReactionCount = current.maxReactionCount())
        } else {
            // Vlastní emoji reakce aplikace vybrat neumí, takže je nesmí ani smazat.
            val custom = (current as? ChatAvailableReactionsSome)?.reactions?.filterIsInstance<ReactionTypeCustomEmoji>().orEmpty()
            ChatAvailableReactionsSome(
                reactions = (reactions.emojis.map { ReactionTypeEmoji(emoji = it) } + custom).toTypedArray(),
                maxReactionCount = current.maxReactionCount(),
            )
        }
        client.setChatAvailableReactions(chatId = chatId, availableReactions = available).toResult().getOrThrow()
        Unit
    }

    private fun ChatAvailableReactions.maxReactionCount(): Int = when (this) {
        is ChatAvailableReactionsAll -> maxReactionCount
        is ChatAvailableReactionsSome -> maxReactionCount
    }

    /** Smí číst a rušit omezení a vyhození — stejné právo, jaké chce `banChatMember`. */
    private fun OwnMemberStatus?.canModerate(): Boolean = when (this) {
        is OwnMemberStatus.Creator -> isMember
        is OwnMemberStatus.Admin -> canRestrictMembers
        else -> false
    }

    override suspend fun setStickerSet(chatId: Long, setId: Long?): Result<Unit> = runCatching {
        val type = client.getChat(chatId = chatId).toResult().getOrThrow().type as? ChatTypeSupergroup
            ?: error("Sadu samolepek má jen supergroupa.")
        client.setSupergroupStickerSet(supergroupId = type.supergroupId, stickerSetId = setId ?: 0)
            .toResult().getOrThrow()
        Unit
    }

    override suspend fun setDiscussionGroup(channelId: Long, groupId: Long?): Result<Unit> =
        client.setChatDiscussionGroup(chatId = channelId, discussionChatId = groupId ?: 0).toResult().map { }

    override suspend fun setUsername(chatId: Long, username: String): Result<Unit> = runCatching {
        val type = client.getChat(chatId = chatId).toResult().getOrThrow().type as? ChatTypeSupergroup
            ?: error("Veřejné jméno má jen supergroupa nebo kanál.")
        client.setSupergroupUsername(supergroupId = type.supergroupId, username = username).toResult().getOrThrow()
        Unit
    }

    override suspend fun checkUsername(chatId: Long, username: String): Result<UsernameCheck> {
        // Základní skupinu TDLib nebere — ta veřejné jméno dostane až jako supergroupa, a pro
        // chat, který ještě není, se ptá nulou (stejně jako při zakládání).
        val basic = chatId != 0L && client.getChat(chatId = chatId).toResult().getOrNull()?.type is ChatTypeBasicGroup
        return client.checkChatUsername(chatId = if (basic) 0 else chatId, username = username)
            .toResult().map { it.toUsernameCheck() }
    }

    override suspend fun upgradeToSupergroup(chatId: Long): Result<Long> =
        client.upgradeBasicGroupChatToSupergroupChat(chatId = chatId).toResult().map { it.id }

    override suspend fun setTitle(chatId: Long, title: String): Result<Unit> =
        client.setChatTitle(chatId = chatId, title = title).toResult().map { }

    override suspend fun setDescription(chatId: Long, description: String): Result<Unit> =
        client.setChatDescription(chatId = chatId, description = description).toResult().map { }

    /** Fotka ze samolepky (`InputChatPhotoSticker`) je další druh a aplikace ji nenabízí. */
    override suspend fun setPhoto(chatId: Long, photo: ProfilePhotoFile): Result<Unit> =
        client.setChatPhoto(chatId = chatId, photo = photo.toInputChatPhoto()).toResult().map { }

    /** `photo = null` fotku odebere — jiné volání na to TDLib nemá. */
    override suspend fun deletePhoto(chatId: Long): Result<Unit> =
        client.setChatPhoto(chatId = chatId, photo = null).toResult().map { }

    override suspend fun setPermissions(chatId: Long, permissions: GroupPermissions): Result<Unit> = runCatching {
        // Celým objektem: co aplikace nenabízí, se přenese z aktuálního stavu.
        val current = client.getChat(chatId = chatId).toResult().getOrThrow().permissions
        client.setChatPermissions(chatId = chatId, permissions = current.merged(permissions))
            .toResult().getOrThrow()
        Unit
    }

    private fun OwnMemberStatus?.isMember(): Boolean = when (this) {
        is OwnMemberStatus.Creator -> isMember
        is OwnMemberStatus.Admin, OwnMemberStatus.Member -> true
        is OwnMemberStatus.Restricted -> isMember
        OwnMemberStatus.Left, OwnMemberStatus.Banned, null -> false
    }

    private fun ChatProfile.withAbout(body: MessageBody) = copy(about = body.text, aboutEntities = body.entities)

    /**
     * Popis skupiny chodí jako holý text. Odkazy a zmínky v něm dohledá TDLib (`getTextEntities`,
     * lokálně), aby byly klikatelné stejně jako v bio a ve zprávách.
     */
    private suspend fun describe(text: String): MessageBody {
        if (text.isBlank()) return MessageBody.Empty
        val entities = client.getTextEntities(text = text).toResult().getOrNull()?.entities ?: emptyArray()
        return FormattedText(text = text, entities = entities).toBody()
    }

    override suspend fun profilePhotos(chatId: Long): Result<List<ProfilePhoto>> = runCatching {
        // Kladné id bez chatu je uživatel, se kterým chat TDLib (zatím) nezná — třeba vlastní
        // účet, kdyby se Uložené zprávy nezaložily. Soukromý chat má id uživatele.
        val chat = client.getChat(chatId = chatId).toResult().getOrElse { failure ->
            if (chatId > 0) null else throw failure
        }
        // Fotky uživatele drží pořadí od serveru: první je aktuální. Podle data řadit nejde —
        // starší fotka nastavená znovu jako hlavní si nechává původní `addedDate`.
        val photos = when (val type = chat?.type) {
            null -> userPhotos(chatId)
            is ChatTypePrivate -> userPhotos(type.userId)
            is ChatTypeSecret -> userPhotos(type.userId)
            is ChatTypeBasicGroup -> groupPhotos(
                chatId = chatId,
                current = client.getBasicGroupFullInfo(basicGroupId = type.basicGroupId).toResult().getOrNull()?.photo,
            )
            is ChatTypeSupergroup -> groupPhotos(
                chatId = chatId,
                current = client.getSupergroupFullInfo(supergroupId = type.supergroupId).toResult().getOrNull()?.photo,
            )
        }
        // Aktuální fotka skupiny je zároveň v poslední zprávě o změně — jednou stačí.
        photos.distinctBy { it.id }.mapNotNull { it.toProfilePhoto() }
    }

    override suspend fun isOwnAccount(chatId: Long): Boolean =
        client.getMe().toResult().getOrNull()?.id == chatId

    override suspend fun setMainProfilePhoto(photoId: Long): Result<Unit> =
        client.setProfilePhoto(photo = InputChatPhotoPrevious(chatPhotoId = photoId), isPublic = false)
            .toResult().map { }

    override suspend fun deleteProfilePhoto(photoId: Long): Result<Unit> =
        client.deleteProfilePhoto(profilePhotoId = photoId).toResult().map { }

    /** Fotky uživatele po stránkách, dokud je server má (strop [MAX_PROFILE_PHOTOS]). */
    private suspend fun userPhotos(userId: Long): List<ChatPhoto> {
        val found = mutableListOf<ChatPhoto>()
        do {
            val page = client.getUserProfilePhotos(userId = userId, offset = found.size, limit = PROFILE_PHOTOS_PAGE)
                .toResult().getOrThrow()
            val photos = page.photos.filterNotNull()
            found += photos
        } while (photos.isNotEmpty() && found.size < page.totalCount && found.size < MAX_PROFILE_PHOTOS)
        return found
    }

    /**
     * Fotky skupiny nebo kanálu: aktuální ([current], z plných údajů) a ty ze zpráv o změně
     * fotky. Když hledání selže, zůstane aspoň aktuální — prohlížeč pak ukáže jen ji.
     */
    private suspend fun groupPhotos(chatId: Long, current: ChatPhoto?): List<ChatPhoto> {
        val found = listOfNotNull(current).toMutableList()
        var from = 0L
        do {
            val page = client.searchChatMessages(
                chatId = chatId,
                topicId = null,
                query = "",
                senderId = null,
                fromMessageId = from,
                offset = 0,
                limit = PROFILE_PHOTOS_PAGE,
                filter = SearchMessagesFilterChatPhoto(),
            ).toResult().getOrNull() ?: break
            val messages = page.messages.filterNotNull()
            found += messages.mapNotNull { (it.content as? MessageChatChangePhoto)?.photo }
            // Nula = dál už nic není. Prázdná stránka nebo stejné pokračování taky — jinak by se
            // smyčka dotazů točila donekonečna.
            val next = page.nextFromMessageId
            if (messages.isEmpty() || next == from) break
            from = next
        } while (from != 0L && found.size < MAX_PROFILE_PHOTOS)
        return found.sortedByDescending { it.addedDate }
    }

    /**
     * Profilovka jako médium prohlížeče. Animovaná je video s fotkou jako náhledem — rozměry
     * má čtvercové (`length`) a délku TDLib neposílá, přehrávač ji zjistí sám.
     */
    private fun ChatPhoto.toProfilePhoto(): ProfilePhoto? {
        val still = photoMedia(sizes = sizes.filterNotNull(), minithumbnail = minithumbnail) ?: return null
        val media = animation?.let { video ->
            MessageMedia.Video(
                fileId = video.file.id,
                thumbnailFileId = still.fileId,
                width = video.length,
                height = video.length,
                durationSeconds = 0,
                minithumbnail = still.minithumbnail,
            )
        } ?: still
        return ProfilePhoto(id = id, addedDate = addedDate, media = media)
    }

    override suspend fun commonGroups(userId: Long): Result<List<RelatedChat>> = runCatching {
        val found = client.getGroupsInCommon(userId = userId, offsetChatId = 0, limit = RELATED_CHATS_LIMIT)
            .toResult().getOrThrow()
        found.chatIds.toList().mapNotNull { relatedChat(it) }
    }

    override suspend fun similarChannels(chatId: Long): Result<List<RelatedChat>> = runCatching {
        val found = client.getChatSimilarChats(chatId = chatId).toResult().getOrThrow()
        found.chatIds.toList().take(RELATED_CHATS_LIMIT).mapNotNull { relatedChat(it) }
    }

    /** Chat do záložky: název, fotka a počet členů. Chat, který se načíst nedá, se vynechá. */
    private suspend fun relatedChat(chatId: Long): RelatedChat? {
        val chat = client.getChat(chatId = chatId).toResult().getOrNull() ?: return null
        val count = when (val type = chat.type) {
            is ChatTypeSupergroup -> client.getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()?.memberCount
            is ChatTypeBasicGroup -> client.getBasicGroup(basicGroupId = type.basicGroupId).toResult().getOrNull()?.memberCount
            else -> null
        }
        return RelatedChat(
            chatId = chat.id,
            title = chat.title,
            photoFileId = chat.photo?.small?.id,
            // Supergroup bez načtených údajů hlásí nulu; „0 členů" by lhalo.
            memberCount = count?.takeIf { it > 0 },
            isChannel = (chat.type as? ChatTypeSupergroup)?.isChannel == true,
        )
    }

    override suspend fun members(
        chatId: Long,
        query: String,
        offset: Int,
        limit: Int,
        filter: MemberFilter,
    ): Result<MemberPage> =
        runCatching {
            val search = query.trim()
            val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
            val type = chat.type
            if (filter != MemberFilter.Recent) {
                // Základní skupina vyhozené ani omezené nevede; prázdný seznam je pravda.
                if (type !is ChatTypeSupergroup) return@runCatching MemberPage(emptyList(), nextOffset = null)
                return@runCatching removedMembers(type.supergroupId, filter, search, offset, limit)
            }
            if (type !is ChatTypeSupergroup) {
                // Základní skupina vydá všechny naráz; stránkovat se nemá co.
                val found = client.searchChatMembers(
                    chatId = chatId,
                    query = search,
                    limit = MAX_MEMBERS_REQUEST,
                    filter = null,
                ).toResult().getOrThrow()
                val members = found.members.filterNotNull().toProfileMembers()
                return@runCatching MemberPage(
                    members = if (search.isEmpty()) members.sortedBy { it.role.ordinal } else members,
                    nextOffset = null,
                )
            }

            val page = client.getSupergroupMembers(
                supergroupId = type.supergroupId,
                filter = if (search.isEmpty()) SupergroupMembersFilterRecent() else SupergroupMembersFilterSearch(query = search),
                offset = offset,
                limit = limit.coerceIn(1, MAX_MEMBERS_REQUEST),
            ).toResult().getOrThrow()
            val raw = page.members.filterNotNull()
            // Konec: prázdná stránka, nebo server říká, že víc jich není. Posun z SUROVÉ odpovědi.
            val next = offset + raw.size
            val nextOffset = next.takeIf { raw.isNotEmpty() && it < page.totalCount }

            /*
             * Vlastník a správci napřed, jen na první stránce bez dotazu. Nedávní je vracejí
             * někde mezi ostatními; setřídit je nejde, protože další stránky přicházejí až potom.
             * Selhání tohohle dotazu stránku nezahodí — správci se pak objeví mezi nedávnými.
             */
            val admins = if (offset == 0 && search.isEmpty()) {
                client.getSupergroupMembers(
                    supergroupId = type.supergroupId,
                    filter = SupergroupMembersFilterAdministrators(),
                    offset = 0,
                    limit = MAX_MEMBERS_REQUEST,
                ).toResult().getOrNull()?.members?.filterNotNull().orEmpty()
                    .toProfileMembers()
                    .sortedBy { it.role.ordinal }
            } else {
                emptyList()
            }
            MemberPage(
                members = (admins + raw.toProfileMembers()).distinctBy { it.sender.id },
                nextOffset = nextOffset,
            )
        }

    /**
     * Stránka omezených nebo vyhozených. Filtr `Banned` / `Restricted` bere dotaz rovnou (hledá
     * se jen mezi nimi), takže samostatné hledání jako u běžných členů potřeba není.
     */
    private suspend fun removedMembers(
        supergroupId: Long,
        filter: MemberFilter,
        query: String,
        offset: Int,
        limit: Int,
    ): MemberPage {
        val page = client.getSupergroupMembers(
            supergroupId = supergroupId,
            filter = if (filter == MemberFilter.Banned) {
                SupergroupMembersFilterBanned(query = query)
            } else {
                SupergroupMembersFilterRestricted(query = query)
            },
            offset = offset,
            limit = limit.coerceIn(1, MAX_MEMBERS_REQUEST),
        ).toResult().getOrThrow()
        val raw = page.members.filterNotNull()
        val next = offset + raw.size
        val members = raw.mapNotNull { member ->
            val userId = (member.memberId as? MessageSenderUser)?.userId ?: return@mapNotNull null
            val until = when (val status = member.status) {
                is ChatMemberStatusBanned -> status.bannedUntilDate
                is ChatMemberStatusRestricted -> status.restrictedUntilDate
                // Mezitím ho někdo vpustil zpátky nebo omezení zrušil.
                else -> return@mapNotNull null
            }
            member(userId)?.let { (sender, isBot) ->
                ProfileMember(
                    sender = sender,
                    role = MemberRole.Member,
                    isBot = isBot,
                    until = until.takeIf { it != FOREVER },
                )
            }
        }
        return MemberPage(
            members = members.distinctBy { it.sender.id },
            nextOffset = next.takeIf { raw.isNotEmpty() && it < page.totalCount },
        )
    }

    private suspend fun List<ChatMember>.toProfileMembers(): List<ProfileMember> = mapNotNull { member ->
        val userId = (member.memberId as? MessageSenderUser)?.userId ?: return@mapNotNull null
        val role = when (member.status) {
            is ChatMemberStatusCreator -> MemberRole.Owner
            is ChatMemberStatusAdministrator -> MemberRole.Admin
            // Kdo odešel nebo byl vyhozen, člen není.
            is ChatMemberStatusLeft, is ChatMemberStatusBanned -> return@mapNotNull null
            else -> MemberRole.Member
        }
        member(userId)?.let { (sender, isBot) ->
            ProfileMember(
                sender = sender,
                role = role,
                tag = member.tag.trim().ifEmpty { null },
                isBot = isBot,
            )
        }
    }

    /** Člen a jestli je to bot — obojí z jednoho `getUser`. */
    private suspend fun member(userId: Long): Pair<SenderInfo, Boolean>? {
        val user = client.getUser(userId = userId).toResult().getOrNull() ?: return null
        val name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
        // Smazaný účet jméno nemá; v seznamu by byl prázdný řádek.
        if (name.isBlank()) return null
        return SenderInfo(id = userId, title = name, avatarFileId = user.profilePhoto?.small?.id) to
            (user.type is UserTypeBot)
    }

    // --- Správa členů ---

    override suspend fun member(chatId: Long, userId: Long): Result<MemberDetail> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val kind = chat.memberGroupKind()
            ?: error("Chat $chatId nemá členy, které by šlo spravovat.")
        val chatMember = client.getChatMember(chatId = chatId, memberId = MessageSenderUser(userId = userId))
            .toResult().getOrThrow()
        val (sender, isBot) = member(userId) ?: error("Uživatele $userId se nepodařilo načíst.")
        val status = chatMember.status
        val restricted = status as? ChatMemberStatusRestricted
        val isForum = (chat.type as? ChatTypeSupergroup)?.let { type ->
            client.getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()?.isForum
        } == true

        MemberDetail(
            chatId = chatId,
            userId = userId,
            name = sender.title,
            avatarFileId = sender.avatarFileId,
            isBot = isBot,
            role = status.role(),
            kind = kind,
            isForum = isForum,
            tag = chatMember.tag.trim().ifEmpty { null },
            // Kdo správce není, dostane běžnou moderátorskou sadu jako výchozí nabídku; ve skupině
            // s tématy k ní patří i jejich správa, jinde to právo nedává smysl.
            adminRights = (status as? ChatMemberStatusAdministrator)?.rights?.toAdminRights()
                ?: AdminRights.Default.copy(manageTopics = isForum),
            // Omezený člen má vlastní práva, ostatní výchozí práva skupiny.
            permissions = (restricted?.permissions ?: chat.permissions).toGroupPermissions(),
            isRestricted = restricted != null,
            restrictedUntil = restricted?.restrictedUntilDate?.takeIf { it != FOREVER },
            actions = memberActions(
                own = client.ownMemberStatus(chat),
                target = TargetMember(
                    role = status.role(),
                    canBeEdited = (status as? ChatMemberStatusAdministrator)?.canBeEdited == true,
                    isSelf = userId == client.getMe().toResult().getOrNull()?.id,
                    isBot = isBot,
                ),
                kind = kind,
            ),
        )
    }

    /**
     * Změna člena a změna plných údajů skupiny. Počet členů nese `SupergroupFullInfo`, a ten má
     * TDLib v keši až minutu — přečíst ho hned po vyhození vrátí starý počet. Když se změní,
     * TDLib pošle `UpdateSupergroupFullInfo`, a teprve na ten profil počet opraví.
     */
    /**
     * Odkud se profil skládá, se zjistí z druhu chatu: u člověka (i v tajném chatu) jeho `User`
     * a `UserFullInfo`, u skupiny její `BasicGroup` / `Supergroup`. Plné info skupin hlídá už
     * [memberUpdates], tady by bylo podruhé.
     */
    override fun profileUpdates(chatId: Long): Flow<Unit> = flow {
        val chat = client.getChat(chatId = chatId).toResult().getOrNull() ?: return@flow
        val type = chat.type
        val userId = when (type) {
            is ChatTypePrivate -> type.userId
            is ChatTypeSecret -> type.userId
            else -> null
        }
        val sources = buildList {
            add(holder.updates { chatTitleUpdates }.filter { it.chatId == chatId }.map { })
            add(holder.updates { chatPhotoUpdates }.filter { it.chatId == chatId }.map { })
            add(holder.updates { chatEmojiStatusUpdates }.filter { it.chatId == chatId }.map { })
            add(holder.updates { chatMessageAutoDeleteTimeUpdates }.filter { it.chatId == chatId }.map { })
            if (userId != null) {
                add(holder.updates { userUpdates }.filter { it.user.id == userId }.map { })
                add(holder.updates { userFullInfoUpdates }.filter { it.userId == userId }.map { })
            }
            if (type is ChatTypeSecret) {
                add(holder.updates { secretChatUpdates }.filter { it.secretChat.id == type.secretChatId }.map { })
            }
            if (type is ChatTypeBasicGroup) {
                add(holder.updates { basicGroupUpdates }.filter { it.basicGroup.id == type.basicGroupId }.map { })
            }
            if (type is ChatTypeSupergroup) {
                add(holder.updates { supergroupUpdates }.filter { it.supergroup.id == type.supergroupId }.map { })
            }
        }
        emitAll(sources.merge())
    }

    override fun memberUpdates(chatId: Long): Flow<Unit> = merge(
        holder.updates { chatMemberUpdates }.filter { it.chatId == chatId }.map { },
        holder.updates { supergroupFullInfoUpdates }.filter { supergroupChatId(it.supergroupId) == chatId }.map { },
        holder.updates { basicGroupFullInfoUpdates }.filter { -it.basicGroupId == chatId }.map { },
    )

    override suspend fun setAdminRights(chatId: Long, userId: Long, rights: AdminRights): Result<Unit> = runCatching {
        val memberId = MessageSenderUser(userId = userId)
        val status = if (rights.any) {
            // Celým objektem: co aplikace nenabízí, se přenese ze současných práv správce.
            val current = client.getChatMember(chatId = chatId, memberId = memberId).toResult()
                .getOrNull()?.status as? ChatMemberStatusAdministrator
            ChatMemberStatusAdministrator(
                canBeEdited = current?.canBeEdited ?: true,
                rights = (current?.rights ?: emptyAdministratorRights()).merged(rights),
            )
        } else {
            // Bez jediného práva je to sesazení na běžného člena; vlastní volání na to není.
            ChatMemberStatusMember(memberUntilDate = FOREVER)
        }
        client.setChatMemberStatus(chatId = chatId, memberId = memberId, status = status).toResult().getOrThrow()
        Unit
    }

    override suspend fun restrictMember(
        chatId: Long,
        userId: Long,
        permissions: GroupPermissions,
        duration: Duration?,
        keepUntil: Int?,
    ): Result<Unit> = runCatching {
        val memberId = MessageSenderUser(userId = userId)
        val now = Clock.System.now().epochSeconds
        // Základ jsou jeho současná práva (u neomezeného výchozí práva skupiny), aby se
        // nepřepsalo to, co aplikace nenabízí — stejně jako u `setPermissions`.
        val current = (client.getChatMember(chatId = chatId, memberId = memberId).toResult()
            .getOrNull()?.status as? ChatMemberStatusRestricted)?.permissions
            ?: client.getChat(chatId = chatId).toResult().getOrThrow().permissions
        client.setChatMemberStatus(
            chatId = chatId,
            memberId = memberId,
            status = ChatMemberStatusRestricted(
                // Omezený člen ve skupině zůstává; vyhození je `banMember`.
                isMember = true,
                restrictedUntilDate = keepUntil?.let { keptUntilDate(it, now) } ?: untilDate(duration, now),
                permissions = current.merged(permissions),
            ),
        ).toResult().getOrThrow()
        Unit
    }

    /** Návrat mezi běžné členy: od té chvíle platí výchozí práva skupiny. */
    override suspend fun unrestrictMember(chatId: Long, userId: Long): Result<Unit> =
        client.setChatMemberStatus(
            chatId = chatId,
            memberId = MessageSenderUser(userId = userId),
            status = ChatMemberStatusMember(memberUntilDate = FOREVER),
        ).toResult().map { }

    /**
     * `banChatMember`, ne `setChatMemberStatus` — jen ono umí dobu a rozhodnutí o zprávách.
     * `revokeMessages = false`: co po něm zůstalo, zůstane. Mazání celé historie je jiná věc
     * a v nabídce hned vedle vyhození by se na něj dalo ťuknout omylem.
     */
    override suspend fun banMember(chatId: Long, userId: Long, duration: Duration?): Result<Unit> =
        client.banChatMember(
            chatId = chatId,
            memberId = MessageSenderUser(userId = userId),
            bannedUntilDate = untilDate(duration, Clock.System.now().epochSeconds),
            revokeMessages = false,
        ).toResult().map { }

    /** Zpět na „opustil": vyhození přestane platit, ale do skupiny se tím nikdo nepřidá. */
    override suspend fun unbanMember(chatId: Long, userId: Long): Result<Unit> =
        client.setChatMemberStatus(
            chatId = chatId,
            memberId = MessageSenderUser(userId = userId),
            status = ChatMemberStatusLeft(),
        ).toResult().map { }

    override suspend fun setMemberTag(chatId: Long, userId: Long, tag: String): Result<Unit> =
        client.setChatMemberTag(chatId = chatId, userId = userId, tag = tag).toResult().map { }

    override suspend fun canTransferOwnership(): Result<OwnershipTransferCheck> =
        client.canTransferOwnership().toResult().map { result ->
            // Bez `else`: nový důvod v novější TDLib má rozbít překlad, ne tiše projít jako „jde to".
            when (result) {
                is CanTransferOwnershipResultOk -> OwnershipTransferCheck.Ready
                is CanTransferOwnershipResultPasswordNeeded -> OwnershipTransferCheck.PasswordNeeded
                is CanTransferOwnershipResultPasswordTooFresh ->
                    OwnershipTransferCheck.TooFresh(result.retryAfter)
                is CanTransferOwnershipResultSessionTooFresh ->
                    OwnershipTransferCheck.TooFresh(result.retryAfter)
            }
        }

    /** Heslo jde rovnou do TDLib a nikde se nedrží — ani v logu, ani ve stavu obrazovky. */
    override suspend fun transferOwnership(chatId: Long, userId: Long, password: String): Result<Unit> =
        client.transferChatOwnership(chatId = chatId, userId = userId, password = password).toResult().map { }

    override suspend fun addMembers(chatId: Long, userIds: List<Long>): Result<List<Long>> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val failed = if (chat.type is ChatTypeBasicGroup) {
            // Základní skupina bere jen po jednom; první chyba (ne soukromí) zastaví zbytek a ohlásí se.
            userIds.flatMap { userId ->
                client.addChatMember(chatId = chatId, userId = userId, forwardLimit = BASIC_GROUP_HISTORY)
                    .toResult().getOrThrow().failedToAddMembers.toList()
            }
        } else {
            client.addChatMembers(chatId = chatId, userIds = userIds.toLongArray())
                .toResult().getOrThrow().failedToAddMembers.toList()
        }
        failed.filterNotNull().map { it.userId }
    }

    override suspend fun botsAmong(userIds: List<Long>): List<Long> = userIds.filter { userId ->
        client.getUser(userId = userId).toResult().getOrNull()?.type is UserTypeBot
    }

    override suspend fun sendInviteLink(userIds: List<Long>, link: String): Result<Unit> = runCatching {
        // Server odkaz v textu sám neoznačí; bez entity by u příjemce nešel ťuknout.
        val entities = client.getTextEntities(text = link).toResult().getOrNull()?.entities ?: emptyArray()
        userIds.forEach { userId ->
            val chat = client.createPrivateChat(userId = userId, force = false).toResult().getOrThrow()
            client.sendMessage(
                chatId = chat.id,
                topicId = null,
                replyTo = null,
                options = null,
                replyMarkup = null,
                inputMessageContent = InputMessageText(
                    text = FormattedText(text = link, entities = entities),
                    linkPreviewOptions = null,
                    clearDraft = false,
                ),
            ).toResult().getOrThrow()
        }
    }

    /** Druh chatu z pohledu správy členů; `null` = soukromý chat, tam se nespravuje nic. */
    private fun Chat.memberGroupKind(): MemberGroupKind? = when (val type = type) {
        is ChatTypeBasicGroup -> MemberGroupKind.BasicGroup
        is ChatTypeSupergroup -> if (type.isChannel) MemberGroupKind.Channel else MemberGroupKind.Supergroup
        is ChatTypePrivate, is ChatTypeSecret -> null
    }
}

/** Role z postavení člena; `when` bez `else`, takže nový stav v TDLib rozbije překlad. */
internal fun ChatMemberStatus.role(): MemberRole = when (this) {
    is ChatMemberStatusCreator -> MemberRole.Owner
    is ChatMemberStatusAdministrator -> MemberRole.Admin
    is ChatMemberStatusMember, is ChatMemberStatusRestricted -> MemberRole.Member
    // Kdo odešel nebo byl vyhozen, členem není; role „Member" je tu jen proto, aby se
    // nabídka dala postavit — co s ním jde dělat, rozhoduje `memberActions`.
    is ChatMemberStatusLeft, is ChatMemberStatusBanned -> MemberRole.Member
}

private fun ChatAdministratorRights.toAdminRights() = AdminRights(
    changeInfo = canChangeInfo,
    postMessages = canPostMessages,
    editMessages = canEditMessages,
    deleteMessages = canDeleteMessages,
    restrictMembers = canRestrictMembers,
    inviteUsers = canInviteUsers,
    pinMessages = canPinMessages,
    promoteMembers = canPromoteMembers,
    anonymous = isAnonymous,
    manageTopics = canManageTopics,
)

/** Nový správce: všechno vypnuté, a teprve na to se položí vybraná práva. */
private fun emptyAdministratorRights() = ChatAdministratorRights(
    canManageChat = false,
    canChangeInfo = false,
    canPostMessages = false,
    canEditMessages = false,
    canDeleteMessages = false,
    canInviteUsers = false,
    canRestrictMembers = false,
    canPinMessages = false,
    canManageTopics = false,
    canPromoteMembers = false,
    canManageVideoChats = false,
    canPostStories = false,
    canEditStories = false,
    canDeleteStories = false,
    canManageDirectMessages = false,
    canManageTags = false,
    canSendWelcomeMessages = false,
    isAnonymous = false,
)

/**
 * Vybraná práva nad těmi současnými. Pojmenované argumenty, jako u všeho generovaného.
 *
 * `canManageChat` je v Telegramu implicitní právo každého správce (přístup ke statistikám
 * a k seznamu členů), takže se drží podle toho, jestli správcem vůbec zůstává.
 */
private fun ChatAdministratorRights.merged(next: AdminRights) = ChatAdministratorRights(
    canManageChat = next.any,
    canChangeInfo = next.changeInfo,
    canPostMessages = next.postMessages,
    canEditMessages = next.editMessages,
    canDeleteMessages = next.deleteMessages,
    canInviteUsers = next.inviteUsers,
    canRestrictMembers = next.restrictMembers,
    canPinMessages = next.pinMessages,
    canManageTopics = next.manageTopics,
    canPromoteMembers = next.promoteMembers,
    canManageVideoChats = canManageVideoChats,
    canPostStories = canPostStories,
    canEditStories = canEditStories,
    canDeleteStories = canDeleteStories,
    canManageDirectMessages = canManageDirectMessages,
    canManageTags = canManageTags,
    canSendWelcomeMessages = canSendWelcomeMessages,
    isAnonymous = next.anonymous,
)

private fun ChatPermissions.toGroupPermissions() = GroupPermissions(
    sendMessages = canSendBasicMessages,
    sendPhotos = canSendPhotos,
    sendVideos = canSendVideos,
    sendDocuments = canSendDocuments,
    sendVoiceNotes = canSendVoiceNotes,
    sendVideoNotes = canSendVideoNotes,
    sendStickers = canSendOtherMessages,
    sendPolls = canSendPolls,
    react = canReactToMessages,
    inviteUsers = canInviteUsers,
    pinMessages = canPinMessages,
    changeInfo = canChangeInfo,
)

/**
 * Nová práva nad těmi současnými. Pojmenované argumenty, jako u všeho generovaného: upstream
 * do `ChatPermissions` přidává pole a poziční volání by se tiše rozsypalo.
 */
private fun ChatPermissions.merged(next: GroupPermissions) = ChatPermissions(
    canSendBasicMessages = next.sendMessages,
    canSendAudios = canSendAudios,
    canSendDocuments = next.sendDocuments,
    canSendPhotos = next.sendPhotos,
    canSendVideos = next.sendVideos,
    canSendVideoNotes = next.sendVideoNotes,
    canSendVoiceNotes = next.sendVoiceNotes,
    canSendPolls = next.sendPolls,
    canSendOtherMessages = next.sendStickers,
    canAddLinkPreviews = canAddLinkPreviews,
    canReactToMessages = next.react,
    canEditTag = canEditTag,
    canChangeInfo = next.changeInfo,
    canInviteUsers = next.inviteUsers,
    canPinMessages = next.pinMessages,
    canCreateTopics = canCreateTopics,
)

/** Kolik profilových fotek se čte najednou (`getUserProfilePhotos` bere nejvýš 100). */
private const val PROFILE_PHOTOS_PAGE = 100

/** Strop historie profilovek; víc nikdo neprolistuje a každá stránka je dotaz na server. */
private const val MAX_PROFILE_PHOTOS = 300
