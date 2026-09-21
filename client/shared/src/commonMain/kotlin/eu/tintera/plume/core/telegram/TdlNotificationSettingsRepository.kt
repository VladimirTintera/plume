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
import kotlinx.coroutines.flow.onStart

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
            notificationSettings = ChatNotificationSettings(
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
            ),
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
    )

    private companion object {
        /** „Navždy" — stejná hodnota jako u ztlumení chatu. */
        const val MUTE_FOREVER = Int.MAX_VALUE

        /** Volba se jmenuje zápornou formou: `true` = NEupozorňovat. */
        const val CONTACT_JOINED_OPTION = "disable_contact_registered_notifications"
    }
}
