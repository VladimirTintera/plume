package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.ChatInviteLink
import dev.g000sha256.tdl.dto.ChatInviteLinkMember
import dev.g000sha256.tdl.dto.UserTypeBot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Pozvánky a žádosti o vstup nad TDLib.
 *
 * - **Seznam odkazů se po každé změně čte znovu** (dělá to ViewModel), nic se nepřepisuje
 *   lokálně — co zrušení hlavního odkazu vyrobilo za nový, ví jen server. Stejný vzor jako
 *   relace v Nastavení.
 * - **Stránkuje se po [PAGE_SIZE] a nejvýš [MAX_PAGES]**: odkazů bývá pár, ale zrušené se
 *   hromadí, dokud je někdo nesmaže.
 */
class TdlInviteLinkRepository(
    private val holder: TelegramClientHolder,
) : InviteLinkRepository {

    private val client: TdlClient get() = holder.client

    override suspend fun links(chatId: Long, creatorUserId: Long?): Result<InviteLinks> = runCatching {
        val me = creatorUserId ?: client.getMe().toResult().getOrThrow().id
        InviteLinks(
            active = page(chatId, me, revoked = false)
                .sortedWith(compareByDescending<InviteLink> { it.isPrimary }.thenByDescending { it.createdAt }),
            revoked = page(chatId, me, revoked = true).sortedByDescending { it.createdAt },
        )
    }

    private suspend fun page(chatId: Long, creator: Long, revoked: Boolean): List<InviteLink> {
        val all = mutableListOf<ChatInviteLink>()
        repeat(MAX_PAGES) {
            val last = all.lastOrNull()
            val links = client.getChatInviteLinks(
                chatId = chatId,
                creatorUserId = creator,
                isRevoked = revoked,
                offsetDate = last?.date ?: 0,
                offsetInviteLink = last?.inviteLink.orEmpty(),
                limit = PAGE_SIZE,
            ).toResult().getOrThrow().inviteLinks
            all += links.filterNot { link -> all.any { it.inviteLink == link.inviteLink } }
            if (links.size < PAGE_SIZE) return all.map { it.toInviteLink() }
        }
        return all.map { it.toInviteLink() }
    }

    override suspend fun otherAdminsLinks(chatId: Long): Result<List<AdminInviteLinks>> = runCatching {
        val me = client.getMe().toResult().getOrThrow().id
        // Správce, který vlastníkem není, dostane chybu — pro něj žádní další správci nejsou.
        val counts = client.getChatInviteLinkCounts(chatId = chatId).toResult().getOrNull()
            ?.inviteLinkCounts.orEmpty()
        counts.filter { it.userId != me && it.inviteLinkCount + it.revokedInviteLinkCount > 0 }
            .mapNotNull { count ->
                // Jméno a fotka jsou lokální dotaz: správce TDLib zná, když zná jeho odkazy.
                val user = client.getUser(userId = count.userId).toResult().getOrNull() ?: return@mapNotNull null
                AdminInviteLinks(
                    userId = count.userId,
                    name = user.fullName(),
                    avatarFileId = user.profilePhoto?.small?.id,
                    active = count.inviteLinkCount,
                    revoked = count.revokedInviteLinkCount,
                    isBot = user.type is UserTypeBot,
                )
            }
    }

    override suspend fun linkMembers(chatId: Long, url: String): Result<List<InviteLinkMember>> = runCatching {
        val all = mutableListOf<ChatInviteLinkMember>()
        repeat(MAX_PAGES) {
            val members = client.getChatInviteLinkMembers(
                chatId = chatId,
                inviteLink = url,
                onlyWithExpiredSubscription = false,
                offsetMember = all.lastOrNull(),
                limit = PAGE_SIZE,
            ).toResult().getOrThrow().members
            all += members.filterNot { member -> all.any { it.userId == member.userId } }
            if (members.size < PAGE_SIZE) return@runCatching all.mapNotNull { it.toMember() }
        }
        all.mapNotNull { it.toMember() }
    }

    private suspend fun ChatInviteLinkMember.toMember(): InviteLinkMember? {
        val user = client.getUser(userId = userId).toResult().getOrNull() ?: return null
        return InviteLinkMember(
            userId = userId,
            name = user.fullName(),
            avatarFileId = user.profilePhoto?.small?.id,
            joinedAt = joinedChatDate,
            viaFolder = viaChatFolderInviteLink,
        )
    }

    override suspend fun create(chatId: Long, draft: InviteLinkDraft): Result<InviteLink> {
        val link = draft.normalized()
        return client.createChatInviteLink(
            chatId = chatId,
            name = link.name,
            expirationDate = link.expiresAt,
            memberLimit = link.memberLimit,
            createsJoinRequest = link.createsJoinRequest,
        ).toResult().map { it.toInviteLink() }
    }

    override suspend fun edit(chatId: Long, url: String, draft: InviteLinkDraft): Result<InviteLink> {
        val link = draft.normalized()
        return client.editChatInviteLink(
            chatId = chatId,
            inviteLink = url,
            name = link.name,
            expirationDate = link.expiresAt,
            memberLimit = link.memberLimit,
            createsJoinRequest = link.createsJoinRequest,
        ).toResult().map { it.toInviteLink() }
    }

    override suspend fun revoke(chatId: Long, url: String): Result<Unit> =
        client.revokeChatInviteLink(chatId = chatId, inviteLink = url).toResult().map { }

    override suspend fun deleteRevoked(chatId: Long, url: String): Result<Unit> =
        client.deleteRevokedChatInviteLink(chatId = chatId, inviteLink = url).toResult().map { }

    override suspend fun deleteAllRevoked(chatId: Long, creatorUserId: Long?): Result<Unit> = runCatching {
        val me = creatorUserId ?: client.getMe().toResult().getOrThrow().id
        client.deleteAllRevokedChatInviteLinks(chatId = chatId, creatorUserId = me).toResult().getOrThrow()
    }

    override fun pendingJoinRequests(chatId: Long): Flow<PendingJoinRequests> =
        holder.updates { chatPendingJoinRequestsUpdates }
            .filter { it.chatId == chatId }
            .map { it.pendingJoinRequests }
            // Odběr stojí před prvním čtením, aby se změna mezi nimi neztratila.
            .onStart { emit(client.getChat(chatId = chatId).toResult().getOrNull()?.pendingJoinRequests) }
            .distinctUntilChanged()
            .map { info ->
                PendingJoinRequests(
                    count = info?.totalCount ?: 0,
                    // Tváře jsou lokální dotaz: TDLib žadatele zná, když zná žádost.
                    recent = info?.userIds?.toList().orEmpty().take(MAX_RECENT_REQUESTERS).mapNotNull { userId ->
                        val user = client.getUser(userId = userId).toResult().getOrNull() ?: return@mapNotNull null
                        SenderInfo(id = userId, title = user.fullName(), avatarFileId = user.profilePhoto?.small?.id)
                    },
                )
            }

    override suspend fun joinRequests(chatId: Long): Result<List<JoinRequest>> = runCatching {
        client.getChatJoinRequests(
            chatId = chatId,
            inviteLink = "",
            query = "",
            offsetRequest = null,
            limit = JOIN_REQUESTS_LIMIT,
        ).toResult().getOrThrow().requests.mapNotNull { request ->
            // Jméno a fotka jsou lokální dotaz: TDLib uživatele zná, když zná žádost.
            val user = client.getUser(userId = request.userId).toResult().getOrNull() ?: return@mapNotNull null
            JoinRequest(
                userId = request.userId,
                name = user.fullName(),
                avatarFileId = user.profilePhoto?.small?.id,
                date = request.date,
                bio = request.bio,
            )
        }
    }

    override suspend fun processJoinRequest(chatId: Long, userId: Long, approve: Boolean): Result<Unit> =
        client.processChatJoinRequest(chatId = chatId, userId = userId, approve = approve).toResult().map { }

    override suspend fun processAllJoinRequests(chatId: Long, approve: Boolean): Result<Unit> =
        // Prázdný odkaz = žádosti ze všech odkazů.
        client.processChatJoinRequests(chatId = chatId, inviteLink = "", approve = approve).toResult().map { }
}

private fun ChatInviteLink.toInviteLink() = InviteLink(
    url = inviteLink,
    name = name,
    isPrimary = isPrimary,
    isRevoked = isRevoked,
    createdAt = date,
    expiresAt = expirationDate,
    memberLimit = memberLimit,
    memberCount = memberCount,
    pendingRequests = pendingJoinRequestCount,
    createsJoinRequest = createsJoinRequest,
    isSubscription = subscriptionPricing != null,
)


/** Tváří posledních žadatelů v pruhu nad konverzací — víc se jich přes sebe nevejde. */
private const val MAX_RECENT_REQUESTERS = 3

private const val PAGE_SIZE = 100

private const val MAX_PAGES = 5

/** Víc žádostí naráz se procházet nebude; zbytek schválí „Přijmout vše". */
private const val JOIN_REQUESTS_LIMIT = 100
