package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.CanTransferOwnershipResultOk
import dev.g000sha256.tdl.dto.CanTransferOwnershipResultPasswordNeeded
import dev.g000sha256.tdl.dto.CanTransferOwnershipResultPasswordTooFresh
import dev.g000sha256.tdl.dto.CanTransferOwnershipResultSessionTooFresh
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatAdministratorRights
import dev.g000sha256.tdl.dto.ChatMember
import dev.g000sha256.tdl.dto.ChatMemberStatus
import dev.g000sha256.tdl.dto.ChatMemberStatusMember
import dev.g000sha256.tdl.dto.ChatMemberStatusRestricted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.ChatPermissions
import dev.g000sha256.tdl.dto.ChatMemberStatusBanned
import dev.g000sha256.tdl.dto.ChatMemberStatusCreator
import dev.g000sha256.tdl.dto.ChatMemberStatusLeft
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputChatPhotoStatic
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.SupergroupMembersFilterAdministrators
import dev.g000sha256.tdl.dto.SupergroupMembersFilterRecent
import dev.g000sha256.tdl.dto.SupergroupMembersFilterSearch
import dev.g000sha256.tdl.dto.UserTypeBot
import dev.g000sha256.tdl.toResult

/**
 * [ProfileRepository] nad TDLib: `getChat` + podrobnosti podle druhu chatu (`getUserFullInfo`,
 * `getBasicGroupFullInfo`, `getSupergroupFullInfo`), členy `getSupergroupMembers`
 * (u základní skupiny `searchChatMembers`).
 */
class TdlProfileRepository(
    private val holder: TelegramClientHolder,
) : ProfileRepository {

    private val client: TdlClient get() = holder.client

    override suspend fun profile(chatId: Long): Result<ChatProfile> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val base = ChatProfile(
            chatId = chatId,
            title = chat.title,
            kind = ProfileKind.Group,
            photoFileId = chat.photo?.big?.id,
            photoThumbnailFileId = chat.photo?.small?.id,
            usernames = emptyList(),
            about = "",
            aboutEntities = emptyList(),
            phone = null,
            memberCount = null,
            canListMembers = false,
        )
        // Bez `else`: nový druh chatu v novější TDLib má rozbít překlad, ne tiše skončit jako skupina.
        when (val type = chat.type) {
            is ChatTypePrivate -> userProfile(base, type.userId)
            is ChatTypeSecret -> userProfile(base, type.userId)

            is ChatTypeBasicGroup -> {
                val group = client.getBasicGroup(basicGroupId = type.basicGroupId).toResult().getOrNull()
                val full = client.getBasicGroupFullInfo(basicGroupId = type.basicGroupId).toResult().getOrNull()
                base.withAbout(describe(full?.description.orEmpty())).copy(
                    memberCount = group?.memberCount?.takeIf { it > 0 },
                    // Malá skupina členy vydá vždycky.
                    canListMembers = true,
                    inviteLink = full?.inviteLink?.inviteLink?.takeIf { it.isNotBlank() },
                    isMember = client.ownMemberStatus(chat).isMember(),
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
                    usernames = group?.usernames?.activeUsernames?.toList().orEmpty(),
                    memberCount = (full?.memberCount ?: group?.memberCount)?.takeIf { it > 0 },
                    // Kanál členy vydá jen správcům a skupina se skrytými členy vůbec.
                    canListMembers = !type.isChannel && full?.canGetMembers == true,
                    // Skryté členy vidí jen správci; ostatním server vrací samé správce.
                    membersHidden = full?.hasHiddenMembers == true &&
                        client.ownMemberStatus(chat).let { it !is OwnMemberStatus.Creator && it !is OwnMemberStatus.Admin },
                    inviteLink = full?.inviteLink?.inviteLink?.takeIf { it.isNotBlank() },
                    isMember = client.ownMemberStatus(chat).isMember(),
                    editRights = chatEditRights(
                        isChannel = type.isChannel,
                        status = client.ownMemberStatus(chat),
                        membersCanChangeInfo = chat.permissions.canChangeInfo,
                    ),
                )
            }
        }
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
            userId = userId,
            isBlocked = full?.blockList != null,
            verification = user.verificationStatus.toVerification(),
        )
    }

    override suspend fun editableChat(chatId: Long): Result<EditableChat> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val isChannel = (chat.type as? ChatTypeSupergroup)?.isChannel == true
        val description = when (val type = chat.type) {
            is ChatTypeBasicGroup ->
                client.getBasicGroupFullInfo(basicGroupId = type.basicGroupId).toResult().getOrNull()?.description
            is ChatTypeSupergroup ->
                client.getSupergroupFullInfo(supergroupId = type.supergroupId).toResult().getOrNull()?.description
            else -> null
        }
        EditableChat(
            chatId = chatId,
            title = chat.title,
            description = description.orEmpty(),
            photoFileId = chat.photo?.big?.id,
            photoThumbnailFileId = chat.photo?.small?.id,
            isChannel = isChannel,
            rights = chatEditRights(
                isChannel = isChannel,
                status = client.ownMemberStatus(chat),
                membersCanChangeInfo = chat.permissions.canChangeInfo,
            ),
            permissions = chat.permissions.toGroupPermissions(),
        )
    }

    override suspend fun setTitle(chatId: Long, title: String): Result<Unit> =
        client.setChatTitle(chatId = chatId, title = title).toResult().map { }

    override suspend fun setDescription(chatId: Long, description: String): Result<Unit> =
        client.setChatDescription(chatId = chatId, description = description).toResult().map { }

    /**
     * Fotka jde přes CESTU (`InputFileLocal`), ne přes systémové URI — TDLib jiné nepřijme,
     * stejně jako u příloh. `InputChatPhotoStatic` je obyčejný snímek; animovaná fotka
     * a fotka ze samolepky jsou jiné druhy a aplikace je nenabízí.
     */
    override suspend fun setPhoto(chatId: Long, path: String): Result<Unit> =
        client.setChatPhoto(
            chatId = chatId,
            photo = InputChatPhotoStatic(photo = InputFileLocal(path = path)),
        ).toResult().map { }

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

    override suspend fun deleteOrLeave(profile: ChatProfile): Result<Unit> =
        if (profile.isPrivate) {
            client.deleteChatHistory(chatId = profile.chatId, removeFromChatList = true, revoke = false)
        } else {
            client.leaveChat(chatId = profile.chatId)
        }.toResult().map { }

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

    override suspend fun members(chatId: Long, query: String, offset: Int, limit: Int): Result<MemberPage> =
        runCatching {
            val search = query.trim()
            val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
            val type = chat.type
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

        MemberDetail(
            chatId = chatId,
            userId = userId,
            name = sender.title,
            avatarFileId = sender.avatarFileId,
            isBot = isBot,
            role = status.role(),
            kind = kind,
            tag = chatMember.tag.trim().ifEmpty { null },
            // Kdo správce není, dostane běžnou moderátorskou sadu jako výchozí nabídku.
            adminRights = (status as? ChatMemberStatusAdministrator)?.rights?.toAdminRights()
                ?: AdminRights.Default,
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

    override fun memberUpdates(chatId: Long): Flow<Unit> =
        holder.updates { chatMemberUpdates }.filter { it.chatId == chatId }.map { }

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
    ): Result<Unit> = runCatching {
        val memberId = MessageSenderUser(userId = userId)
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
                restrictedUntilDate = untilDate(duration, Clock.System.now().epochSeconds),
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

    /** Druh chatu z pohledu správy členů; `null` = soukromý chat, tam se nespravuje nic. */
    private fun Chat.memberGroupKind(): MemberGroupKind? = when (val type = type) {
        is ChatTypeBasicGroup -> MemberGroupKind.BasicGroup
        is ChatTypeSupergroup -> if (type.isChannel) MemberGroupKind.Channel else MemberGroupKind.Supergroup
        is ChatTypePrivate, is ChatTypeSecret -> null
    }
}

/** Role z postavení člena; `when` bez `else`, takže nový stav v TDLib rozbije překlad. */
private fun ChatMemberStatus.role(): MemberRole = when (this) {
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
    canManageTopics = canManageTopics,
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
    sendStickers = canSendOtherMessages,
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
    canSendVideoNotes = canSendVideoNotes,
    canSendVoiceNotes = next.sendVoiceNotes,
    canSendPolls = canSendPolls,
    canSendOtherMessages = next.sendStickers,
    canAddLinkPreviews = canAddLinkPreviews,
    canReactToMessages = next.react,
    canEditTag = canEditTag,
    canChangeInfo = next.changeInfo,
    canInviteUsers = next.inviteUsers,
    canPinMessages = next.pinMessages,
    canCreateTopics = canCreateTopics,
)
