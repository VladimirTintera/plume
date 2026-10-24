package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * InviteLinkRepository pro testy: každé volání, které test nepřepsal, vyhodí chybu se jménem metody — test tak
 * hned řekne, na co ještě sahá. Vygenerované z rozhraní; po změně rozhraní stačí doplnit chybějící
 * člen (překladač ho vyjmenuje).
 */
open class UnimplementedInviteLinkRepository : InviteLinkRepository {
    override suspend fun links(chatId: Long, creatorUserId: Long?) : Result<InviteLinks> = error("links")
    override suspend fun otherAdminsLinks(chatId: Long) : Result<List<AdminInviteLinks>> = error("otherAdminsLinks")
    override suspend fun linkMembers(chatId: Long, url: String) : Result<List<InviteLinkMember>> = error("linkMembers")
    override suspend fun create(chatId: Long, draft: InviteLinkDraft) : Result<InviteLink> = error("create")
    override suspend fun edit(chatId: Long, url: String, draft: InviteLinkDraft) : Result<InviteLink> = error("edit")
    override suspend fun revoke(chatId: Long, url: String) : Result<Unit> = error("revoke")
    override suspend fun deleteRevoked(chatId: Long, url: String) : Result<Unit> = error("deleteRevoked")
    override suspend fun deleteAllRevoked(chatId: Long, creatorUserId: Long?) : Result<Unit> = error("deleteAllRevoked")
    override fun pendingJoinRequests(chatId: Long) : Flow<PendingJoinRequests> = error("pendingJoinRequests")
    override suspend fun joinRequests(chatId: Long) : Result<List<JoinRequest>> = error("joinRequests")
    override suspend fun processJoinRequest(chatId: Long, userId: Long, approve: Boolean) : Result<Unit> = error("processJoinRequest")
    override suspend fun processAllJoinRequests(chatId: Long, approve: Boolean) : Result<Unit> = error("processAllJoinRequests")
}
