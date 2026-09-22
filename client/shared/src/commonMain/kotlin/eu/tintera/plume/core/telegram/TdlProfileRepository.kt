package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.BlockListMain
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.ChatMemberStatusBanned
import dev.g000sha256.tdl.dto.ChatMemberStatusCreator
import dev.g000sha256.tdl.dto.ChatMemberStatusLeft
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.UserTypeBot
import dev.g000sha256.tdl.toResult

/**
 * [ProfileRepository] nad TDLib: `getChat` + podrobnosti podle druhu chatu (`getUserFullInfo`,
 * `getBasicGroupFullInfo`, `getSupergroupFullInfo`) a `searchChatMembers`.
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
                )
            }

            is ChatTypeSupergroup -> {
                val group = client.getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()
                val full = client.getSupergroupFullInfo(supergroupId = type.supergroupId).toResult().getOrNull()
                base.withAbout(describe(full?.description.orEmpty())).copy(
                    kind = if (type.isChannel) ProfileKind.Channel else ProfileKind.Group,
                    usernames = group?.usernames?.activeUsernames?.toList().orEmpty(),
                    memberCount = (full?.memberCount ?: group?.memberCount)?.takeIf { it > 0 },
                    // Kanál členy vydá jen správcům a skupina se skrytými členy vůbec.
                    canListMembers = !type.isChannel && full?.canGetMembers == true,
                    inviteLink = full?.inviteLink?.inviteLink?.takeIf { it.isNotBlank() },
                    isMember = client.ownMemberStatus(chat).isMember(),
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
        )
    }

    /** Blokuje se hlavní seznam (`BlockListMain`) — ten brání psát i volat; stories mají vlastní. */
    override suspend fun setBlocked(userId: Long, blocked: Boolean): Result<Unit> =
        client.setMessageSenderBlockList(
            senderId = MessageSenderUser(userId = userId),
            blockList = if (blocked) BlockListMain() else null,
        ).toResult().map { }

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

    override suspend fun members(chatId: Long): Result<List<ProfileMember>> =
        client.searchChatMembers(chatId = chatId, query = "", limit = MAX_PROFILE_MEMBERS, filter = null)
            .toResult()
            .map { found ->
                found.members.filterNotNull()
                    .mapNotNull { member ->
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
                    // Vlastník a správci napřed; uvnitř role pořadí od serveru.
                    .sortedBy { it.role.ordinal }
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
}
