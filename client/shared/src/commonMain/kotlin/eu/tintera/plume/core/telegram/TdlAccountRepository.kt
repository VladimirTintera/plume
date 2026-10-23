package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.ResendCodeReasonUserRequest
import dev.g000sha256.tdl.dto.PhoneNumberCodeTypeChange
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.CheckChatUsernameResult
import dev.g000sha256.tdl.dto.CheckChatUsernameResultOk
import dev.g000sha256.tdl.dto.CheckChatUsernameResultPublicChatsTooMany
import dev.g000sha256.tdl.dto.CheckChatUsernameResultPublicGroupsUnavailable
import dev.g000sha256.tdl.dto.CheckChatUsernameResultUsernameInvalid
import dev.g000sha256.tdl.dto.CheckChatUsernameResultUsernameOccupied
import dev.g000sha256.tdl.dto.CheckChatUsernameResultUsernamePurchasable
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
        val full = client.getUserFullInfo(userId = me.id).toResult()
            .onFailure { logger.w(it) { "Bio se nepodařilo přečíst." } }
            .getOrNull()
        val bio = full?.bio?.text.orEmpty()
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
                birthdate = full?.birthdate?.toDomain(),
                personalChannel = full?.personalChatId?.takeIf { it != 0L }?.let { client.personalChannel(it) },
                usernames = me.usernames?.let {
                    AccountUsernames(active = it.activeUsernames.toList(), disabled = it.disabledUsernames.toList(), editable = it.editableUsername)
                } ?: AccountUsernames(),
                mainTab = full?.mainProfileTab?.toDomain(),
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
            .map { it.toUsernameCheck() }
    }

    override suspend fun setBio(bio: String): Result<Unit> =
        client.setBio(bio = bio).toResult().map { }
            .onFailure { logger.w(it) { "Bio se nepodařilo uložit." } }

    override suspend fun setBirthdate(birthdate: Birthdate?): Result<Unit> =
        client.setBirthdate(birthdate = birthdate?.toTdl()).toResult().map { }
            .onFailure { logger.w(it) { "Datum narození se nepodařilo uložit." } }

    override suspend fun setUsernameActive(username: String, active: Boolean): Result<Unit> =
        client.toggleUsernameIsActive(username = username, isActive = active).toResult().map { }
            .onFailure { logger.w(it) { "Uživatelské jméno se nepodařilo zapnout / vypnout." } }

    override suspend fun reorderUsernames(active: List<String>): Result<Unit> =
        client.reorderActiveUsernames(usernames = active.toTypedArray()).toResult().map { }
            .onFailure { logger.w(it) { "Pořadí uživatelských jmen se nepodařilo uložit." } }

    override suspend fun suitablePersonalChannels(): Result<List<PersonalChannel>> =
        client.getSuitablePersonalChats().toResult()
            .map { chats -> chats.chatIds.toList().mapNotNull { client.personalChannel(it) } }
            .onFailure { logger.w(it) { "Kanály pro profil se nepodařilo načíst." } }

    override suspend fun setPersonalChannel(chatId: Long?): Result<Unit> =
        client.setPersonalChat(chatId = chatId ?: 0L).toResult().map { }
            .onFailure { logger.w(it) { "Osobní kanál se nepodařilo uložit." } }

    /** `settings` výchozí (null) — stejná úvaha jako u přihlášení, viz `docs/poznatky/prihlaseni.md`. */
    override suspend fun sendPhoneChangeCode(phoneNumber: String): Result<PhoneChangeCode> =
        client.sendPhoneNumberCode(phoneNumber = phoneNumber, settings = null, type = PhoneNumberCodeTypeChange())
            .toResult().map { it.toPhoneChangeCode() }
            .onFailure { logger.w(it) { "Kód na nové číslo se nepodařilo poslat." } }

    override suspend fun resendPhoneChangeCode(): Result<PhoneChangeCode> =
        client.resendPhoneNumberCode(reason = ResendCodeReasonUserRequest())
            .toResult().map { it.toPhoneChangeCode() }
            .onFailure { logger.w(it) { "Kód na nové číslo se nepodařilo poslat znovu." } }

    override suspend fun checkPhoneChangeCode(code: String): Result<Unit> =
        client.checkPhoneNumberCode(code = code).toResult().map { }
            .onFailure { logger.w(it) { "Kód z nového čísla neprošel." } }

    override suspend fun deleteAccount(reason: String, password: String): Result<Unit> =
        client.deleteAccount(reason = reason, password = password)
            .toResult()
            .map { }
            // Heslo do logu nejde, jen hláška TDLib (konstanta jako PASSWORD_HASH_INVALID).
            .onFailure { logger.w(it) { "Účet se nepodařilo smazat." } }

    override suspend fun loginPasskeys(): Result<List<LoginPasskey>> =
        client.getLoginPasskeys()
            .toResult()
            .map { result ->
                result.passkeys.map { key ->
                    LoginPasskey(
                        id = key.id,
                        name = key.name,
                        addedAt = key.additionDate,
                        lastUsedAt = key.lastUsageDate.takeIf { it > 0 },
                        iconCustomEmojiId = key.softwareIconCustomEmojiId.takeIf { it != 0L },
                    )
                }
            }
            .onFailure { logger.w(it) { "Přístupové klíče se nepodařilo načíst." } }

    override suspend fun removeLoginPasskey(id: String): Result<Unit> =
        client.removeLoginPasskey(passkeyId = id)
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Přístupový klíč se nepodařilo odebrat." } }

    override suspend fun contactLink(): Result<ContactLink> =
        client.getUserLink().toResult().map { ContactLink(url = it.url, expiresInSeconds = it.expiresIn) }
            .onFailure { logger.w(it) { "Kontaktní odkaz se nepodařilo vyrobit." } }

    /** Veřejná fotka (`isPublic`) je jen pro ty, kdo vidí skutečnou fotku zakázanou nastavením — tady ne. */
    override suspend fun setPhoto(photo: ProfilePhotoFile): Result<Unit> =
        client.setProfilePhoto(photo = photo.toInputChatPhoto(), isPublic = false)
            .toResult().map { }
            .onFailure { logger.w(it) { "Fotku se nepodařilo nastavit." } }

    override suspend fun setMainProfileTab(tab: ProfileMainTab): Result<Unit> =
        client.setMainProfileTab(mainProfileTab = tab.toTdl())
            .toResult().map { }
            .onFailure { logger.w(it) { "Hlavní záložku profilu se nepodařilo nastavit." } }

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

/** Sdílí ho vlastní účet i veřejný odkaz nové skupiny nebo kanálu (`TdlChatCreation`). */
internal fun CheckChatUsernameResult.toUsernameCheck(): UsernameCheck = when (this) {
    is CheckChatUsernameResultOk -> UsernameCheck.Available
    is CheckChatUsernameResultUsernameInvalid -> UsernameCheck.Invalid
    is CheckChatUsernameResultUsernameOccupied -> UsernameCheck.Occupied
    is CheckChatUsernameResultUsernamePurchasable -> UsernameCheck.Purchasable
    is CheckChatUsernameResultPublicChatsTooMany,
    is CheckChatUsernameResultPublicGroupsUnavailable -> UsernameCheck.TooManyPublic
}
