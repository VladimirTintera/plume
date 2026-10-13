package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.NotificationSettingsScope
import dev.g000sha256.tdl.dto.NotificationSettingsScopeChannelChats
import dev.g000sha256.tdl.dto.NotificationSettingsScopeGroupChats
import dev.g000sha256.tdl.dto.NotificationSettingsScopePrivateChats
import dev.g000sha256.tdl.dto.OptionValueBoolean
import dev.g000sha256.tdl.dto.ScopeNotificationSettings
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlin.time.Clock
import kotlin.time.Duration

/** Nastavení notifikací nad TDLib. */
class TdlNotificationSettingsRepository(
    private val holder: TelegramClientHolder,
) : NotificationSettingsRepository {

    private val logger = Logger.withTag("NotificationSettings")

    private val client: TdlClient get() = holder.client

    /**
     * Update se nepřekládá, jen spouští nové čtení — a odběr stojí PŘED prvním čtením, aby se
     * změna mezi nimi neztratila. Změna z jiného zařízení i vlastní zápis přijdou touž cestou.
     */
    override fun scopeSettings(scope: NotificationScope): Flow<ScopeSettings?> =
        holder.updates { scopeNotificationSettingsUpdates }
            .filter { it.scope.toDomain() == scope }
            .map { }
            .onStart { emit(Unit) }
            .map {
                client.getScopeNotificationSettings(scope = scope.toTdl())
                    .toResult()
                    .onFailure { logger.w(it) { "Nastavení rozsahu $scope se nepodařilo přečíst." } }
                    .getOrNull()
                    ?.toDomain()
            }

    /**
     * Rozsah se zapisuje **celým objektem**, stejně jako ztlumení chatu. Co v [ScopeSettings]
     * není (zvuk, stories), se přenese z aktuálního stavu — jinak by se uživateli tiše přepsal
     * třeba zvuk nastavený v jiném klientovi.
     */
    override suspend fun setScopeSettings(scope: NotificationScope, settings: ScopeSettings): Result<Unit> {
        val current = client.getScopeNotificationSettings(scope = scope.toTdl()).toResult()
            .getOrElse { return Result.failure(it) }
        val updated = ScopeNotificationSettings(
            muteFor = if (settings.enabled) 0 else MUTE_FOREVER,
            soundId = current.soundId,
            showPreview = settings.showPreview,
            useDefaultMuteStories = current.useDefaultMuteStories,
            muteStories = current.muteStories,
            storySoundId = current.storySoundId,
            showStoryPoster = current.showStoryPoster,
            disablePinnedMessageNotifications = !settings.pinnedMessages,
            disableMentionNotifications = !settings.mentions,
        )
        return client.setScopeNotificationSettings(scope = scope.toTdl(), notificationSettings = updated)
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Nastavení rozsahu $scope se nepodařilo uložit." } }
    }

    override suspend fun exceptions(scope: NotificationScope): Result<List<NotificationException>> =
        client.getChatNotificationSettingsExceptions(scope = scope.toTdl(), compareSound = false)
            .toResult()
            .map { chats ->
                chats.chatIds.toList().mapNotNull { chatId ->
                    val chat = client.getChat(chatId).toResult().getOrNull() ?: return@mapNotNull null
                    val settings = chat.notificationSettings
                    NotificationException(
                        chatId = chat.id,
                        title = chat.title,
                        avatarFileId = chat.photo?.small?.id,
                        kind = chat.type.chatKind(),
                        state = when {
                            settings.useDefaultMuteFor -> ExceptionState.Custom
                            settings.muteFor > 0 -> ExceptionState.Muted
                            else -> ExceptionState.Enabled
                        },
                    )
                }
            }
            .onFailure { logger.w(it) { "Výjimky rozsahu $scope se nepodařilo načíst." } }

    /** Všechny `useDefault*` zapnuté: chat jede celý podle rozsahu, včetně zvuku. */
    override suspend fun resetToDefault(chatId: Long): Result<Unit> =
        client.setChatNotificationSettings(
            chatId = chatId,
            notificationSettings = defaultChatNotificationSettings(),
        )
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Nastavení chatu $chatId se nepodařilo vrátit." } }

    /** Nenastavená volba přijde jako `OptionValueEmpty` — tedy „nevypnuto". */
    override suspend fun contactJoinedEnabled(): Result<Boolean> =
        client.getOption(name = CONTACT_JOINED_OPTION)
            .toResult()
            .map { (it as? OptionValueBoolean)?.value != true }

    override suspend fun setContactJoinedEnabled(enabled: Boolean): Result<Unit> =
        client.setOption(name = CONTACT_JOINED_OPTION, value = OptionValueBoolean(value = !enabled))
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Volbu o nových kontaktech se nepodařilo uložit." } }

    /**
     * Čte se znovu na změnu chatu i na změnu rozsahu — chat, který jede podle rozsahu, se
     * s ním mění. Odběr stojí před prvním čtením, stejně jako u rozsahu samotného.
     */
    override fun chatSettings(chatId: Long): Flow<ChatNotificationPrefs?> =
        merge(
            holder.updates { chatNotificationSettingsUpdates }.filter { it.chatId == chatId }.map { },
            holder.updates { scopeNotificationSettingsUpdates }.map { },
        )
            .onStart { emit(Unit) }
            .map { readChatSettings(chatId) }

    private suspend fun readChatSettings(chatId: Long): ChatNotificationPrefs? {
        val chat = client.getChat(chatId).toResult()
            .onFailure { logger.w(it) { "Chat $chatId se nepodařilo přečíst." } }
            .getOrNull() ?: return null
        val scope = when (chat.type.chatKind()) {
            ChatKind.Private -> NotificationScope.Private
            ChatKind.Group -> NotificationScope.Groups
            ChatKind.Channel -> NotificationScope.Channels
        }
        val defaults = readScope(scope) ?: return null
        return notificationPrefs(
            own = chat.notificationSettings,
            inherited = null,
            scopeDefaults = defaults,
            scope = scope,
            nowEpochSeconds = Clock.System.now().epochSeconds,
        )
    }

    override suspend fun setChatOption(chatId: Long, option: ChatNotificationOption, value: Boolean): Result<Unit> {
        val current = client.getChat(chatId).toResult().getOrElse { return Result.failure(it) }.notificationSettings
        return client.setChatNotificationSettings(chatId = chatId, notificationSettings = current.withOption(option, value))
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Nastavení chatu $chatId se nepodařilo uložit." } }
    }

    /**
     * Téma se mění se sebou, se skupinou i s výchozím nastavením skupin — každá z těch vrstev
     * může rozhodnout o volbě, kterou téma nemá vlastní ([notificationPrefs]).
     */
    override fun topicSettings(chatId: Long, topicId: Int): Flow<ChatNotificationPrefs?> =
        merge(
            holder.updates { forumTopicUpdates }.filter { it.chatId == chatId && it.forumTopicId == topicId }.map { },
            holder.updates { chatNotificationSettingsUpdates }.filter { it.chatId == chatId }.map { },
            holder.updates { scopeNotificationSettingsUpdates }.map { },
        )
            .onStart { emit(Unit) }
            .map { readTopicSettings(chatId, topicId) }

    private suspend fun readTopicSettings(chatId: Long, topicId: Int): ChatNotificationPrefs? {
        val topic = client.getForumTopic(chatId = chatId, forumTopicId = topicId).toResult()
            .onFailure { logger.w(it) { "Téma $topicId ve skupině $chatId se nepodařilo přečíst." } }
            .getOrNull() ?: return null
        val chat = client.getChat(chatId).toResult().getOrNull() ?: return null
        // Témata má jen supergroupa, takže rozsah je vždycky skupiny.
        val defaults = readScope(NotificationScope.Groups) ?: return null
        return notificationPrefs(
            own = topic.notificationSettings,
            inherited = chat.notificationSettings,
            scopeDefaults = defaults,
            scope = NotificationScope.Groups,
            nowEpochSeconds = Clock.System.now().epochSeconds,
        )
    }

    override suspend fun setTopicMute(chatId: Long, topicId: Int, duration: Duration?): Result<Unit> =
        updateTopic(chatId, topicId) { it.withMuteFor(duration?.inWholeSeconds?.toInt() ?: MUTE_FOREVER) }

    override suspend fun setTopicOption(chatId: Long, topicId: Int, option: ChatNotificationOption, value: Boolean): Result<Unit> =
        updateTopic(chatId, topicId) { it.withOption(option, value) }

    /** Všechna `useDefault*` zapnutá: téma jede celé podle skupiny, včetně zvuku. */
    override suspend fun resetTopic(chatId: Long, topicId: Int): Result<Unit> =
        updateTopic(chatId, topicId) { defaultChatNotificationSettings() }

    /** Zápis celým objektem, přečteným z aktuálního tématu, jako u chatu. */
    private suspend fun updateTopic(
        chatId: Long,
        topicId: Int,
        change: (ChatNotificationSettings) -> ChatNotificationSettings,
    ): Result<Unit> {
        val current = client.getForumTopic(chatId = chatId, forumTopicId = topicId).toResult()
            .getOrElse { return Result.failure(it) }
            .notificationSettings
        return client.setForumTopicNotificationSettings(
            chatId = chatId,
            forumTopicId = topicId,
            notificationSettings = change(current),
        )
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Nastavení tématu $topicId ve skupině $chatId se nepodařilo uložit." } }
    }

    private suspend fun readScope(scope: NotificationScope): ScopeNotificationSettings? =
        client.getScopeNotificationSettings(scope = scope.toTdl()).toResult()
            .onFailure { logger.w(it) { "Nastavení rozsahu $scope se nepodařilo přečíst." } }
            .getOrNull()

    private fun NotificationScope.toTdl(): NotificationSettingsScope = when (this) {
        NotificationScope.Private -> NotificationSettingsScopePrivateChats()
        NotificationScope.Groups -> NotificationSettingsScopeGroupChats()
        NotificationScope.Channels -> NotificationSettingsScopeChannelChats()
    }

    private fun NotificationSettingsScope.toDomain(): NotificationScope = when (this) {
        is NotificationSettingsScopePrivateChats -> NotificationScope.Private
        is NotificationSettingsScopeGroupChats -> NotificationScope.Groups
        is NotificationSettingsScopeChannelChats -> NotificationScope.Channels
    }

    private fun ScopeNotificationSettings.toDomain() = ScopeSettings(
        enabled = muteFor == 0,
        showPreview = showPreview,
        pinnedMessages = !disablePinnedMessageNotifications,
        mentions = !disableMentionNotifications,
        soundId = soundId,
    )

    override suspend fun setScopeSound(scope: NotificationScope, soundId: Long): Result<Unit> {
        val current = client.getScopeNotificationSettings(scope = scope.toTdl()).toResult()
            .getOrElse { return Result.failure(it) }
        val updated = ScopeNotificationSettings(
            muteFor = current.muteFor,
            soundId = soundId,
            showPreview = current.showPreview,
            useDefaultMuteStories = current.useDefaultMuteStories,
            muteStories = current.muteStories,
            storySoundId = current.storySoundId,
            showStoryPoster = current.showStoryPoster,
            disablePinnedMessageNotifications = current.disablePinnedMessageNotifications,
            disableMentionNotifications = current.disableMentionNotifications,
        )
        return client.setScopeNotificationSettings(scope = scope.toTdl(), notificationSettings = updated)
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Zvuk rozsahu $scope se nepodařilo uložit." } }
    }

    private companion object {
        /** „Navždy" — stejná hodnota jako u ztlumení chatu. */
        const val MUTE_FOREVER = Int.MAX_VALUE

        /** Volba se jmenuje zápornou formou: `true` = NEupozorňovat. */
        const val CONTACT_JOINED_OPTION = "disable_contact_registered_notifications"
    }
}

/**
 * Upozornění chatu celá podle rozsahu (všechna `useDefault*`), včetně zvuku. Sdílí ho obnovení
 * výjimky v Nastavení i vrácení automaticky archivovaného chatu z pruhu nad konverzací.
 */
internal fun defaultChatNotificationSettings() = ChatNotificationSettings(
    useDefaultMuteFor = true,
    muteFor = 0,
    useDefaultSound = true,
    soundId = 0,
    useDefaultShowPreview = true,
    showPreview = false,
    useDefaultMuteStories = true,
    muteStories = false,
    useDefaultStorySound = true,
    storySoundId = 0,
    useDefaultShowStoryPoster = true,
    showStoryPoster = false,
    useDefaultDisablePinnedMessageNotifications = true,
    disablePinnedMessageNotifications = false,
    useDefaultDisableMentionNotifications = true,
    disableMentionNotifications = false,
)
