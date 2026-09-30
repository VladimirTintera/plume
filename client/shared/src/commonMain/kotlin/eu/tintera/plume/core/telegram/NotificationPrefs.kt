package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.ScopeNotificationSettings

/**
 * Účinná upozornění z vrstev nastavení: napřed [own] (chat, nebo téma), pak [inherited] (u tématu
 * skupina), nakonec výchozí nastavení rozsahu [scopeDefaults].
 *
 * Každá volba se rozhoduje ZVLÁŠŤ: platí první vrstva, která ji má vlastní (`useDefault… = false`).
 * Téma tedy může mít vlastní ztlumení a náhled brát ze skupiny, která ho bere z rozsahu.
 *
 * [ChatNotificationPrefs.muteIsDefault] a [ChatNotificationPrefs.isDefault] se ptají jen na [own]:
 * říkají, jestli má smysl „Obnovit" a jestli ztlumení přichází odjinud.
 */
internal fun notificationPrefs(
    own: ChatNotificationSettings,
    inherited: ChatNotificationSettings?,
    scopeDefaults: ScopeNotificationSettings,
    scope: NotificationScope,
    nowEpochSeconds: Long,
): ChatNotificationPrefs {
    val layers = listOfNotNull(own, inherited)
    fun <T : Any> pick(useDefault: (ChatNotificationSettings) -> Boolean, value: (ChatNotificationSettings) -> T, fallback: T): T =
        layers.firstOrNull { !useDefault(it) }?.let(value) ?: fallback

    return ChatNotificationPrefs(
        mute = muteState(
            muteForSeconds = pick({ it.useDefaultMuteFor }, { it.muteFor }, scopeDefaults.muteFor),
            nowEpochSeconds = nowEpochSeconds,
        ),
        muteIsDefault = own.useDefaultMuteFor,
        showPreview = pick({ it.useDefaultShowPreview }, { it.showPreview }, scopeDefaults.showPreview),
        pinnedMessages = !pick(
            { it.useDefaultDisablePinnedMessageNotifications },
            { it.disablePinnedMessageNotifications },
            scopeDefaults.disablePinnedMessageNotifications,
        ),
        mentions = !pick(
            { it.useDefaultDisableMentionNotifications },
            { it.disableMentionNotifications },
            scopeDefaults.disableMentionNotifications,
        ),
        // Zvuk a stories se nepočítají: nastavit je tu nejde, takže by „Obnovit" slibovalo
        // změnu, kterou uživatel nevidí. Obnovení je vrátí taky, to ano.
        isDefault = own.useDefaultMuteFor && own.useDefaultShowPreview &&
            own.useDefaultDisablePinnedMessageNotifications && own.useDefaultDisableMentionNotifications,
        scope = scope,
        defaults = if (inherited == null) NotificationDefaults.Scope else NotificationDefaults.Chat,
    )
}

/**
 * Nastavení s jednou změněnou volbou: ta přestane jet podle vrstvy níž, zbytek se přenese —
 * zapisuje se celým objektem, jinak by se tiše přepsal třeba zvuk nastavený jinde.
 */
internal fun ChatNotificationSettings.withOption(option: ChatNotificationOption, value: Boolean): ChatNotificationSettings {
    val preview = option == ChatNotificationOption.ShowPreview
    val pinned = option == ChatNotificationOption.PinnedMessages
    val mentions = option == ChatNotificationOption.Mentions
    return ChatNotificationSettings(
        useDefaultMuteFor = useDefaultMuteFor,
        muteFor = muteFor,
        useDefaultSound = useDefaultSound,
        soundId = soundId,
        useDefaultShowPreview = if (preview) false else useDefaultShowPreview,
        showPreview = if (preview) value else showPreview,
        useDefaultMuteStories = useDefaultMuteStories,
        muteStories = muteStories,
        useDefaultStorySound = useDefaultStorySound,
        storySoundId = storySoundId,
        useDefaultShowStoryPoster = useDefaultShowStoryPoster,
        showStoryPoster = showStoryPoster,
        useDefaultDisablePinnedMessageNotifications = if (pinned) false else useDefaultDisablePinnedMessageNotifications,
        disablePinnedMessageNotifications = if (pinned) !value else disablePinnedMessageNotifications,
        useDefaultDisableMentionNotifications = if (mentions) false else useDefaultDisableMentionNotifications,
        disableMentionNotifications = if (mentions) !value else disableMentionNotifications,
    )
}

/**
 * Nastavení s vlastním ztlumením na [seconds] (0 = zapnuto). `useDefaultMuteFor = false` je nutné:
 * dokud je `true`, jede ztlumení podle vrstvy níž a `muteFor` se ignoruje.
 */
internal fun ChatNotificationSettings.withMuteFor(seconds: Int) = ChatNotificationSettings(
    useDefaultMuteFor = false,
    muteFor = seconds,
    useDefaultSound = useDefaultSound,
    soundId = soundId,
    useDefaultShowPreview = useDefaultShowPreview,
    showPreview = showPreview,
    useDefaultMuteStories = useDefaultMuteStories,
    muteStories = muteStories,
    useDefaultStorySound = useDefaultStorySound,
    storySoundId = storySoundId,
    useDefaultShowStoryPoster = useDefaultShowStoryPoster,
    showStoryPoster = showStoryPoster,
    useDefaultDisablePinnedMessageNotifications = useDefaultDisablePinnedMessageNotifications,
    disablePinnedMessageNotifications = disablePinnedMessageNotifications,
    useDefaultDisableMentionNotifications = useDefaultDisableMentionNotifications,
    disableMentionNotifications = disableMentionNotifications,
)
