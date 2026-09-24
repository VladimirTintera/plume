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
        val defaults = client.getScopeNotificationSettings(scope = scope.toTdl()).toResult()
            .onFailure { logger.w(it) { "Nastavení rozsahu $scope se nepodařilo přečíst." } }
            .getOrNull() ?: return null
        val own = chat.notificationSettings
        return ChatNotificationPrefs(
            mute = muteState(
                muteForSeconds = if (own.useDefaultMuteFor) defaults.muteFor else own.muteFor,
                nowEpochSeconds = Clock.System.now().epochSeconds,
            ),
            muteIsDefault = own.useDefaultMuteFor,
            showPreview = if (own.useDefaultShowPreview) defaults.showPreview else own.showPreview,
            pinnedMessages = !(
                if (own.useDefaultDisablePinnedMessageNotifications) defaults.disablePinnedMessageNotifications
                else own.disablePinnedMessageNotifications
                ),
            mentions = !(
                if (own.useDefaultDisableMentionNotifications) defaults.disableMentionNotifications
                else own.disableMentionNotifications
                ),
            // Zvuk a stories se nepočítají: nastavit je tu nejde, takže by „Obnovit" slibovalo
            // změnu, kterou uživatel nevidí. Obnovení je vrátí taky, to ano.
            isDefault = own.useDefaultMuteFor && own.useDefaultShowPreview &&
                own.useDefaultDisablePinnedMessageNotifications && own.useDefaultDisableMentionNotifications,
            scope = scope,
        )
    }

    /** Celým objektem, jako ztlumení: měněná volba přestane jet podle rozsahu, zbytek se přenese. */
    override suspend fun setChatOption(chatId: Long, option: ChatNotificationOption, value: Boolean): Result<Unit> {
        val current = client.getChat(chatId).toResult().getOrElse { return Result.failure(it) }.notificationSettings
        val preview = option == ChatNotificationOption.ShowPreview
        val pinned = option == ChatNotificationOption.PinnedMessages
        val mentions = option == ChatNotificationOption.Mentions
        val updated = ChatNotificationSettings(
            useDefaultMuteFor = current.useDefaultMuteFor,
            muteFor = current.muteFor,
            useDefaultSound = current.useDefaultSound,
            soundId = current.soundId,
            useDefaultShowPreview = if (preview) false else current.useDefaultShowPreview,
            showPreview = if (preview) value else current.showPreview,
            useDefaultMuteStories = current.useDefaultMuteStories,
            muteStories = current.muteStories,
            useDefaultStorySound = current.useDefaultStorySound,
            storySoundId = current.storySoundId,
            useDefaultShowStoryPoster = current.useDefaultShowStoryPoster,
            showStoryPoster = current.showStoryPoster,
            useDefaultDisablePinnedMessageNotifications = if (pinned) false else current.useDefaultDisablePinnedMessageNotifications,
            disablePinnedMessageNotifications = if (pinned) !value else current.disablePinnedMessageNotifications,
            useDefaultDisableMentionNotifications = if (mentions) false else current.useDefaultDisableMentionNotifications,
            disableMentionNotifications = if (mentions) !value else current.disableMentionNotifications,
        )
        return client.setChatNotificationSettings(chatId = chatId, notificationSettings = updated)
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Nastavení chatu $chatId se nepodařilo uložit." } }
    }

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
