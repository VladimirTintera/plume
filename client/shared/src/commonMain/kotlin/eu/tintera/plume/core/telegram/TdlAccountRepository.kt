package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.CheckChatUsernameResultOk
import dev.g000sha256.tdl.dto.CheckChatUsernameResultPublicChatsTooMany
import dev.g000sha256.tdl.dto.CheckChatUsernameResultPublicGroupsUnavailable
import dev.g000sha256.tdl.dto.CheckChatUsernameResultUsernameInvalid
import dev.g000sha256.tdl.dto.CheckChatUsernameResultUsernameOccupied
import dev.g000sha256.tdl.dto.CheckChatUsernameResultUsernamePurchasable
import dev.g000sha256.tdl.dto.InputChatPhotoStatic
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.OptionValueInteger
import dev.g000sha256.tdl.dto.User
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Vlastní účet nad TDLib. */
class TdlAccountRepository(
    private val holder: TelegramClientHolder,
) : AccountRepository {

    private val logger = Logger.withTag("Account")

    private val client: TdlClient get() = holder.client

    /**
     * `UpdateUser` chodí pro každého uživatele, kterého TDLib zná, a nese ho celého — z něj se
     * bere jen vlastní účet. Odběr stojí PŘED prvním čtením (`UNDISPATCHED`), aby se změna mezi
     * nimi neztratila.
     */
    override val account: Flow<AccountInfo?> = channelFlow {
        var myId: Long? = null
        launch(start = CoroutineStart.UNDISPATCHED) {
            holder.updates { userUpdates }.collect { update ->
                if (update.user.id == myId) send(update.user.toAccountInfo())
            }
        }
        val me = client.getMe().toResult()
            .onFailure { logger.w(it) { "Účet se nepodařilo přečíst." } }
            .getOrNull()
        myId = me?.id
        send(me?.toAccountInfo())
    }

    override suspend fun editableProfile(): Result<EditableProfile> {
        val me = client.getMe().toResult().getOrElse { return Result.failure(it) }
        val bio = client.getUserFullInfo(userId = me.id).toResult()
            .onFailure { logger.w(it) { "Bio se nepodařilo přečíst." } }
            .getOrNull()?.bio?.text.orEmpty()
        val bioMax = (client.getOption(name = "bio_length_max").toResult().getOrNull() as? OptionValueInteger)
            ?.value?.toInt() ?: DEFAULT_BIO_MAX
        return Result.success(
            EditableProfile(
                id = me.id,
                firstName = me.firstName,
                lastName = me.lastName,
                username = me.usernames?.editableUsername.orEmpty(),
                bio = bio,
                bioMaxLength = bioMax,
                photoFileId = me.profilePhoto?.big?.id,
                photoThumbnailFileId = me.profilePhoto?.small?.id,
            ),
        )
    }

    override suspend fun setName(firstName: String, lastName: String): Result<Unit> =
        client.setName(firstName = firstName, lastName = lastName).toResult().map { }
            .onFailure { logger.w(it) { "Jméno se nepodařilo uložit." } }

    override suspend fun setUsername(username: String): Result<Unit> =
        client.setUsername(username = username).toResult().map { }
            .onFailure { logger.w(it) { "Uživatelské jméno se nepodařilo uložit." } }

    /** Pro vlastní účet se ptá přes soukromý chat se sebou (Uložené zprávy) — tak to TDLib chce. */
    override suspend fun checkUsername(username: String): Result<UsernameCheck> {
        val me = client.getMe().toResult().getOrElse { return Result.failure(it) }
        return client.checkChatUsername(chatId = me.id, username = username).toResult()
            .map { result ->
                when (result) {
                    is CheckChatUsernameResultOk -> UsernameCheck.Available
                    is CheckChatUsernameResultUsernameInvalid -> UsernameCheck.Invalid
                    is CheckChatUsernameResultUsernameOccupied -> UsernameCheck.Occupied
                    is CheckChatUsernameResultUsernamePurchasable -> UsernameCheck.Purchasable
                    is CheckChatUsernameResultPublicChatsTooMany,
                    is CheckChatUsernameResultPublicGroupsUnavailable -> UsernameCheck.TooManyPublic
                }
            }
    }

    override suspend fun setBio(bio: String): Result<Unit> =
        client.setBio(bio = bio).toResult().map { }
            .onFailure { logger.w(it) { "Bio se nepodařilo uložit." } }

    /** Veřejná fotka (`isPublic`) je jen pro ty, kdo vidí skutečnou fotku zakázanou nastavením — tady ne. */
    override suspend fun setPhoto(path: String): Result<Unit> =
        client.setProfilePhoto(photo = InputChatPhotoStatic(photo = InputFileLocal(path = path)), isPublic = false)
            .toResult().map { }
            .onFailure { logger.w(it) { "Fotku se nepodařilo nastavit." } }

    override suspend fun deletePhoto(): Result<Unit> {
        val photoId = client.getMe().toResult().getOrElse { return Result.failure(it) }.profilePhoto?.id
            ?: return Result.success(Unit)
        return client.deleteProfilePhoto(profilePhotoId = photoId).toResult().map { }
            .onFailure { logger.w(it) { "Fotku se nepodařilo odebrat." } }
    }

    private fun User.toAccountInfo() = AccountInfo(
        id = id,
        firstName = firstName,
        lastName = lastName,
        phoneNumber = phoneNumber,
        username = usernames?.editableUsername?.takeIf { it.isNotBlank() },
        photoFileId = profilePhoto?.small?.id,
    )

    private companion object {
        /** Kdyby server volbu neposlal. Tolik má účet bez Premium. */
        const val DEFAULT_BIO_MAX = 70
    }
}
