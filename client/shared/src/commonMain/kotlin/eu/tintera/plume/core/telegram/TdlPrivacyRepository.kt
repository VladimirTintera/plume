package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.AccountTtl
import dev.g000sha256.tdl.dto.ReadDatePrivacySettings
import dev.g000sha256.tdl.dto.UserPrivacySetting
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowCalls
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowChatInvites
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowFindingByPhoneNumber
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowPeerToPeerCalls
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowUnpaidMessages
import dev.g000sha256.tdl.dto.UserPrivacySettingAutosaveGifts
import dev.g000sha256.tdl.dto.UserPrivacySettingRule
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowBots
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowChatMembers
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowContacts
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowPremiumUsers
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowUsers
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictBots
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictChatMembers
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictContacts
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictUsers
import dev.g000sha256.tdl.dto.UserPrivacySettingRules
import dev.g000sha256.tdl.dto.UserPrivacySettingShowBio
import dev.g000sha256.tdl.dto.UserPrivacySettingShowBirthdate
import dev.g000sha256.tdl.dto.UserPrivacySettingShowLinkInForwardedMessages
import dev.g000sha256.tdl.dto.UserPrivacySettingShowPhoneNumber
import dev.g000sha256.tdl.dto.UserPrivacySettingShowProfileAudio
import dev.g000sha256.tdl.dto.UserPrivacySettingShowProfilePhoto
import dev.g000sha256.tdl.dto.UserPrivacySettingShowStatus
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class TdlPrivacyRepository(private val holder: TelegramClientHolder) : PrivacyRepository {

    private val client: TdlClient get() = holder.client

    private val logger = Logger.withTag("Privacy")

    override fun rules(setting: PrivacySetting): Flow<Result<List<PrivacyRule>>> = channelFlow {
        // Odběr napřed, teprve pak čtení: změna mezi nimi by se jinak ztratila.
        launch(start = CoroutineStart.UNDISPATCHED) {
            holder.updates { userPrivacySettingRulesUpdates }
                .filter { it.setting.toDomain() == setting }
                .collect { send(Result.success(it.rules.toDomain())) }
        }
        val read = client.getUserPrivacySettingRules(setting = setting.toTdl())
            .toResult()
            .map { it.toDomain() }
            .onFailure { logger.w(it) { "Soukromí $setting se nepodařilo přečíst." } }
        send(read)
    }

    override suspend fun setRules(setting: PrivacySetting, rules: List<PrivacyRule>): Result<Unit> =
        client.setUserPrivacySettingRules(
            setting = setting.toTdl(),
            rules = UserPrivacySettingRules(rules = rules.map { it.toTdl() }.toTypedArray()),
        )
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Soukromí $setting se nepodařilo zapsat." } }

    override suspend fun showsReadDate(): Result<Boolean> =
        client.getReadDatePrivacySettings().toResult().map { it.showReadDate }

    override suspend fun setShowsReadDate(show: Boolean): Result<Unit> =
        client.setReadDatePrivacySettings(settings = ReadDatePrivacySettings(showReadDate = show))
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Čas přečtení se nepodařilo zapsat." } }

    override suspend fun accountTtlDays(): Result<Int> =
        client.getAccountTtl()
            .toResult()
            .map { it.days }
            .onFailure { logger.w(it) { "Samosmazání účtu se nepodařilo přečíst." } }

    override suspend fun setAccountTtlDays(days: Int): Result<Unit> =
        client.setAccountTtl(ttl = AccountTtl(days = days))
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Samosmazání účtu ($days dní) se nepodařilo zapsat." } }

    override suspend fun members(userIds: List<Long>, chatIds: List<Long>): List<PrivacyMember> =
        userIds.map { user(it) } + chatIds.map { group(it) }

    override suspend fun memberOf(chatId: Long): Result<PrivacyMember> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        when (val type = chat.type) {
            is ChatTypePrivate -> user(type.userId)
            is ChatTypeSecret -> user(type.userId)
            is ChatTypeBasicGroup -> group(chat.id)
            is ChatTypeSupergroup ->
                if (type.isChannel) error("Kanál nemůže být výjimkou soukromí.") else group(chat.id)
        }
    }

    private suspend fun user(userId: Long): PrivacyMember {
        val user = client.getUser(userId = userId).toResult().getOrNull()
        return PrivacyMember(
            id = userId,
            isGroup = false,
            title = user?.let { listOf(it.firstName, it.lastName).filter(String::isNotBlank).joinToString(" ") }.orEmpty(),
            avatarFileId = user?.profilePhoto?.small?.id,
            kind = ChatKind.Private,
        )
    }

    private suspend fun group(chatId: Long): PrivacyMember {
        val chat = client.getChat(chatId = chatId).toResult().getOrNull()
        return PrivacyMember(
            id = chatId,
            isGroup = true,
            title = chat?.title.orEmpty(),
            avatarFileId = chat?.photo?.small?.id,
            kind = ChatKind.Group,
        )
    }
}

/** `when` bez `else`: nové nastavení v novější TDLib rozbije překlad, místo aby se tiše ztratilo. */
private fun UserPrivacySetting.toDomain(): PrivacySetting? = when (this) {
    is UserPrivacySettingShowStatus -> PrivacySetting.LastSeen
    is UserPrivacySettingShowPhoneNumber -> PrivacySetting.PhoneNumber
    is UserPrivacySettingAllowFindingByPhoneNumber -> PrivacySetting.FindByPhone
    is UserPrivacySettingShowProfilePhoto -> PrivacySetting.ProfilePhoto
    is UserPrivacySettingShowBio -> PrivacySetting.Bio
    is UserPrivacySettingShowBirthdate -> PrivacySetting.Birthdate
    is UserPrivacySettingShowLinkInForwardedMessages -> PrivacySetting.ForwardLink
    is UserPrivacySettingAllowCalls -> PrivacySetting.Calls
    is UserPrivacySettingAllowPeerToPeerCalls -> PrivacySetting.PeerToPeer
    is UserPrivacySettingAllowChatInvites -> PrivacySetting.ChatInvites
    is UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages -> PrivacySetting.VoiceMessages
    // Aplikace je zatím nenabízí (hudba v profilu, placené zprávy, ukládání dárků).
    is UserPrivacySettingShowProfileAudio, is UserPrivacySettingAllowUnpaidMessages, is UserPrivacySettingAutosaveGifts -> null
}

private fun PrivacySetting.toTdl(): UserPrivacySetting = when (this) {
    PrivacySetting.LastSeen -> UserPrivacySettingShowStatus()
    PrivacySetting.PhoneNumber -> UserPrivacySettingShowPhoneNumber()
    PrivacySetting.FindByPhone -> UserPrivacySettingAllowFindingByPhoneNumber()
    PrivacySetting.ProfilePhoto -> UserPrivacySettingShowProfilePhoto()
    PrivacySetting.Bio -> UserPrivacySettingShowBio()
    PrivacySetting.Birthdate -> UserPrivacySettingShowBirthdate()
    PrivacySetting.ForwardLink -> UserPrivacySettingShowLinkInForwardedMessages()
    PrivacySetting.Calls -> UserPrivacySettingAllowCalls()
    PrivacySetting.PeerToPeer -> UserPrivacySettingAllowPeerToPeerCalls()
    PrivacySetting.ChatInvites -> UserPrivacySettingAllowChatInvites()
    PrivacySetting.VoiceMessages -> UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages()
}

private fun UserPrivacySettingRules.toDomain(): List<PrivacyRule> = rules.filterNotNull().map { it.toDomain() }

private fun UserPrivacySettingRule.toDomain(): PrivacyRule = when (this) {
    is UserPrivacySettingRuleAllowAll -> PrivacyRule.AllowAll
    is UserPrivacySettingRuleAllowContacts -> PrivacyRule.AllowContacts
    is UserPrivacySettingRuleAllowBots -> PrivacyRule.AllowBots
    is UserPrivacySettingRuleAllowPremiumUsers -> PrivacyRule.AllowPremium
    is UserPrivacySettingRuleAllowUsers -> PrivacyRule.AllowUsers(userIds.toList())
    is UserPrivacySettingRuleAllowChatMembers -> PrivacyRule.AllowChatMembers(chatIds.toList())
    is UserPrivacySettingRuleRestrictAll -> PrivacyRule.RestrictAll
    is UserPrivacySettingRuleRestrictContacts -> PrivacyRule.RestrictContacts
    is UserPrivacySettingRuleRestrictBots -> PrivacyRule.RestrictBots
    is UserPrivacySettingRuleRestrictUsers -> PrivacyRule.RestrictUsers(userIds.toList())
    is UserPrivacySettingRuleRestrictChatMembers -> PrivacyRule.RestrictChatMembers(chatIds.toList())
}

private fun PrivacyRule.toTdl(): UserPrivacySettingRule = when (this) {
    PrivacyRule.AllowAll -> UserPrivacySettingRuleAllowAll()
    PrivacyRule.AllowContacts -> UserPrivacySettingRuleAllowContacts()
    PrivacyRule.AllowBots -> UserPrivacySettingRuleAllowBots()
    PrivacyRule.AllowPremium -> UserPrivacySettingRuleAllowPremiumUsers()
    is PrivacyRule.AllowUsers -> UserPrivacySettingRuleAllowUsers(userIds = userIds.toLongArray())
    is PrivacyRule.AllowChatMembers -> UserPrivacySettingRuleAllowChatMembers(chatIds = chatIds.toLongArray())
    PrivacyRule.RestrictAll -> UserPrivacySettingRuleRestrictAll()
    PrivacyRule.RestrictContacts -> UserPrivacySettingRuleRestrictContacts()
    PrivacyRule.RestrictBots -> UserPrivacySettingRuleRestrictBots()
    is PrivacyRule.RestrictUsers -> UserPrivacySettingRuleRestrictUsers(userIds = userIds.toLongArray())
    is PrivacyRule.RestrictChatMembers -> UserPrivacySettingRuleRestrictChatMembers(chatIds = chatIds.toLongArray())
}
