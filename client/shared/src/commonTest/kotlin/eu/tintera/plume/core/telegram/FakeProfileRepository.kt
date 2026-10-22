package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * [ProfileRepository] pro testy ViewModelů: co test nepřepíše, spadne na „nečekané volání".
 * Repository má přes čtyřicet metod a ViewModel jich volá pár — každý test si přepíše jen ty.
 */
open class FakeProfileRepository : ProfileRepository {

    override suspend fun profile(chatId: Long): Result<ChatProfile> = unsupported()
    override suspend fun profilePhotos(chatId: Long): Result<List<ProfilePhoto>> = unsupported()
    override suspend fun isOwnAccount(chatId: Long): Boolean = unsupported()
    override suspend fun profileAudios(userId: Long): Result<List<MessageMedia.Audio>> = unsupported()
    override suspend fun addProfileAudio(audio: MessageMedia.Audio): Result<Unit> = unsupported()
    override suspend fun addProfileAudioFile(file: OutgoingAttachment.Audio): Result<Unit> = unsupported()
    override suspend fun removeProfileAudio(fileId: Int): Result<Unit> = unsupported()
    override suspend fun moveProfileAudio(fileId: Int, afterFileId: Int): Result<Unit> = unsupported()
    override suspend fun isProfileAudio(fileId: Int): Boolean = unsupported()
    override suspend fun setMainProfilePhoto(photoId: Long): Result<Unit> = unsupported()
    override suspend fun deleteProfilePhoto(photoId: Long): Result<Unit> = unsupported()
    override suspend fun setAutoDeleteTime(chatId: Long, seconds: Int): Result<Unit> = unsupported()
    override suspend fun commonGroups(userId: Long): Result<List<RelatedChat>> = unsupported()
    override suspend fun similarChannels(chatId: Long): Result<List<RelatedChat>> = unsupported()
    override suspend fun members(chatId: Long, query: String, offset: Int, limit: Int, filter: MemberFilter): Result<MemberPage> = unsupported()
    override suspend fun editableChat(chatId: Long): Result<EditableChat> = unsupported()
    override suspend fun setTitle(chatId: Long, title: String): Result<Unit> = unsupported()
    override suspend fun setDescription(chatId: Long, description: String): Result<Unit> = unsupported()
    override suspend fun setDiscussionGroup(channelId: Long, groupId: Long?): Result<Unit> = unsupported()
    override suspend fun setStickerSet(chatId: Long, setId: Long?): Result<Unit> = unsupported()
    override suspend fun setUsername(chatId: Long, username: String): Result<Unit> = unsupported()
    override suspend fun checkUsername(chatId: Long, username: String): Result<UsernameCheck> = unsupported()
    override suspend fun upgradeToSupergroup(chatId: Long): Result<Long> = unsupported()
    override suspend fun setPhoto(chatId: Long, photo: ProfilePhotoFile): Result<Unit> = unsupported()
    override suspend fun deletePhoto(chatId: Long): Result<Unit> = unsupported()
    override suspend fun setPermissions(chatId: Long, permissions: GroupPermissions): Result<Unit> = unsupported()
    override suspend fun deleteChatForAll(chatId: Long): Result<Unit> = unsupported()
    override suspend fun eventLog(chatId: Long, fromEventId: Long, filter: ChatLogFilter): Result<ChatLogPage> = unsupported()
    override suspend fun administrators(chatId: Long): Result<List<SenderInfo>> = unsupported()
    override suspend fun setSlowMode(chatId: Long, seconds: Int): Result<Unit> = unsupported()
    override suspend fun setGroupToggle(chatId: Long, toggle: GroupToggle, value: Boolean): Result<Unit> = unsupported()
    override suspend fun setReactions(chatId: Long, reactions: ChatReactions): Result<Unit> = unsupported()
    override suspend fun member(chatId: Long, userId: Long): Result<MemberDetail> = unsupported()
    override fun memberUpdates(chatId: Long): Flow<Unit> = unsupported()
    override fun profileUpdates(chatId: Long): Flow<Unit> = unsupported()
    override suspend fun setAdminRights(chatId: Long, userId: Long, rights: AdminRights): Result<Unit> = unsupported()
    override suspend fun restrictMember(chatId: Long, userId: Long, permissions: GroupPermissions, duration: Duration?, keepUntil: Int?): Result<Unit> = unsupported()
    override suspend fun unrestrictMember(chatId: Long, userId: Long): Result<Unit> = unsupported()
    override suspend fun banMember(chatId: Long, userId: Long, duration: Duration?): Result<Unit> = unsupported()
    override suspend fun unbanMember(chatId: Long, userId: Long): Result<Unit> = unsupported()
    override suspend fun setMemberTag(chatId: Long, userId: Long, tag: String): Result<Unit> = unsupported()
    override suspend fun canTransferOwnership(): Result<OwnershipTransferCheck> = unsupported()
    override suspend fun transferOwnership(chatId: Long, userId: Long, password: String): Result<Unit> = unsupported()
    override suspend fun addMembers(chatId: Long, userIds: List<Long>): Result<List<Long>> = unsupported()
    override suspend fun botsAmong(userIds: List<Long>): List<Long> = unsupported()
    override suspend fun sendInviteLink(userIds: List<Long>, link: String): Result<Unit> = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("V testu nečekané volání ProfileRepository.")
}
