package eu.tintera.plume.core.settings

import eu.tintera.plume.core.video.VideoPosition
import eu.tintera.plume.features.chats.StoredTriageVerdict
import eu.tintera.plume.features.conversation.StoredTranscript
import kotlinx.serialization.json.Json
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.snooze.ChatSnooze
import eu.tintera.plume.core.chatcolor.StoredChatColor
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.GemmaModel
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.adaptive.ListPaneAnchor
import eu.tintera.plume.core.adaptive.ProfilePaneAnchor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

/**
 * AppSettings pro testy: každé volání, které test nepřepsal, vyhodí chybu se jménem metody — test tak
 * hned řekne, na co ještě sahá. Vygenerované z rozhraní; po změně rozhraní stačí doplnit chybějící
 * člen (překladač ho vyjmenuje).
 */
open class UnimplementedAppSettings : AppSettings {
    override val expressionMode: StateFlow<ExpressionMode> get() = error("expressionMode")
    override suspend fun setExpressionMode(mode: ExpressionMode): Unit = error("setExpressionMode")
    override val recordMode: StateFlow<RecordMode> get() = error("recordMode")
    override suspend fun setRecordMode(mode: RecordMode): Unit = error("setRecordMode")
    override val lastStickerTab: StateFlow<String?> get() = error("lastStickerTab")
    override fun setLastStickerTab(key: String): Unit = error("setLastStickerTab")
    override val recentEmojis: StateFlow<List<String>> get() = error("recentEmojis")
    override fun recordRecentEmoji(emoji: String): Unit = error("recordRecentEmoji")
    override val emojiSkinTones: StateFlow<Map<String, String>> get() = error("emojiSkinTones")
    override fun setEmojiSkinTone(base: String, variant: String?): Unit = error("setEmojiSkinTone")
    override val themeMode: StateFlow<ThemeMode?> get() = error("themeMode")
    override fun setThemeMode(mode: ThemeMode?): Unit = error("setThemeMode")
    override val dynamicColor: StateFlow<Boolean?> get() = error("dynamicColor")
    override fun setDynamicColor(enabled: Boolean): Unit = error("setDynamicColor")
    override val themeColor: StateFlow<ThemeColor?> get() = error("themeColor")
    override fun setThemeColor(color: ThemeColor): Unit = error("setThemeColor")
    override val listPaneAnchor: StateFlow<ListPaneAnchor?> get() = error("listPaneAnchor")
    override fun setListPaneAnchor(anchor: ListPaneAnchor): Unit = error("setListPaneAnchor")
    override val profilePaneAnchor: StateFlow<ProfilePaneAnchor?> get() = error("profilePaneAnchor")
    override fun setProfilePaneAnchor(anchor: ProfilePaneAnchor): Unit = error("setProfilePaneAnchor")
    override val separateUnreadChats: StateFlow<Boolean> get() = error("separateUnreadChats")
    override fun setSeparateUnreadChats(separate: Boolean): Unit = error("setSeparateUnreadChats")
    override val savedMessagesAsChats: StateFlow<Boolean> get() = error("savedMessagesAsChats")
    override fun setSavedMessagesAsChats(asChats: Boolean): Unit = error("setSavedMessagesAsChats")
    override val chatSwipeActions: StateFlow<ChatSwipeActions> get() = error("chatSwipeActions")
    override fun setChatSwipeActions(actions: ChatSwipeActions): Unit = error("setChatSwipeActions")
    override val autoDownload: StateFlow<AutoDownloadSettings> get() = error("autoDownload")
    override fun setAutoDownload(settings: AutoDownloadSettings): Unit = error("setAutoDownload")
    override suspend fun loadedAutoDownload() : AutoDownloadSettings = error("loadedAutoDownload")
    override suspend fun autoDownloadSync() : SettingsSync? = error("autoDownloadSync")
    override suspend fun setAutoDownloadSync(state: SettingsSync): Unit = error("setAutoDownloadSync")
    override val messageBookmarks: StateFlow<List<MessageBookmark>> get() = error("messageBookmarks")
    override fun setMessageBookmarks(bookmarks: List<MessageBookmark>): Unit = error("setMessageBookmarks")
    override val chatSnoozes: StateFlow<List<ChatSnooze>> get() = error("chatSnoozes")
    override val videoPositions: StateFlow<List<VideoPosition>> get() = error("videoPositions")
    override fun updateVideoPositions(transform: (List<VideoPosition>) -> List<VideoPosition>): Unit = error("updateVideoPositions")
    override fun setChatSnoozes(snoozes: List<ChatSnooze>): Unit = error("setChatSnoozes")
    override val chatColors: StateFlow<List<StoredChatColor>> get() = error("chatColors")
    override fun setChatColors(colors: List<StoredChatColor>): Unit = error("setChatColors")
    override val storedTranscripts: StateFlow<List<StoredTranscript>> get() = error("storedTranscripts")
    override val storedTriageVerdicts: StateFlow<List<StoredTriageVerdict>> get() = error("storedTriageVerdicts")
    override fun setStoredTriageVerdicts(verdicts: List<StoredTriageVerdict>): Unit = error("setStoredTriageVerdicts")
    override fun setStoredTranscripts(transcripts: List<StoredTranscript>): Unit = error("setStoredTranscripts")
    override val storedPhotoDescriptions: StateFlow<List<eu.tintera.plume.features.media.StoredPhotoDescription>>
        get() = error("storedPhotoDescriptions")
    override fun setStoredPhotoDescriptions(descriptions: List<eu.tintera.plume.features.media.StoredPhotoDescription>): Unit =
        error("setStoredPhotoDescriptions")
    override val quietHours: StateFlow<QuietHours> get() = error("quietHours")
    override fun setQuietHours(quietHours: QuietHours): Unit = error("setQuietHours")
    override val onDeviceAiEnabled: StateFlow<Boolean> get() = error("onDeviceAiEnabled")
    override fun setOnDeviceAiEnabled(enabled: Boolean): Unit = error("setOnDeviceAiEnabled")
    override val replySuggestionsEnabled: StateFlow<Boolean> get() = error("replySuggestionsEnabled")
    override fun setReplySuggestionsEnabled(enabled: Boolean): Unit = error("setReplySuggestionsEnabled")
    override val scamWarningsEnabled: StateFlow<Boolean> get() = error("scamWarningsEnabled")
    override val backgroundPhotoDescriptions: StateFlow<Boolean> get() = error("backgroundPhotoDescriptions")
    override fun setBackgroundPhotoDescriptions(enabled: Boolean): Unit = error("setBackgroundPhotoDescriptions")
    override fun setScamWarningsEnabled(enabled: Boolean): Unit = error("setScamWarningsEnabled")
    override val chatTriageEnabled: StateFlow<Boolean> get() = error("chatTriageEnabled")
    override val contactSyncEnabled: StateFlow<Boolean> get() = error("contactSyncEnabled")
    override fun setContactSyncEnabled(enabled: Boolean): Unit = error("setContactSyncEnabled")
    override fun setChatTriageEnabled(enabled: Boolean): Unit = error("setChatTriageEnabled")
    override val messageContextEnabled: StateFlow<Boolean> get() = error("messageContextEnabled")
    override fun setMessageContextEnabled(enabled: Boolean): Unit = error("setMessageContextEnabled")
    override val onDeviceAiVariant: StateFlow<ModelVariant> get() = error("onDeviceAiVariant")
    override fun setOnDeviceAiVariant(variant: ModelVariant): Unit = error("setOnDeviceAiVariant")
    override val voiceSpeed: StateFlow<VoiceSpeed> get() = error("voiceSpeed")
    override fun setVoiceSpeed(speed: VoiceSpeed): Unit = error("setVoiceSpeed")
    override val videoSpeed: StateFlow<VoiceSpeed> get() = error("videoSpeed")
    override fun setVideoSpeed(speed: VoiceSpeed): Unit = error("setVideoSpeed")
    override val skipSilence: StateFlow<Boolean> get() = error("skipSilence")
    override fun setSkipSilence(enabled: Boolean): Unit = error("setSkipSilence")
    override val dismissedUpdateVersionCode: StateFlow<Int?> get() = error("dismissedUpdateVersionCode")
    override fun dismissUpdate(versionCode: Int): Unit = error("dismissUpdate")
    override val confirmedWebAppBots: StateFlow<Set<Long>> get() = error("confirmedWebAppBots")
    override fun confirmWebAppBot(botUserId: Long): Unit = error("confirmWebAppBot")
    override val seenGestureHints: StateFlow<Set<GestureHint>> get() = error("seenGestureHints")
    override fun markGestureHintSeen(hint: GestureHint): Unit = error("markGestureHintSeen")
    override val usedOneTimeKeyboards: StateFlow<Map<Long, Long>> get() = error("usedOneTimeKeyboards")
    override fun markOneTimeKeyboardUsed(chatId: Long, messageId: Long): Unit = error("markOneTimeKeyboardUsed")
    override val translationLanguage: StateFlow<String?> get() = error("translationLanguage")
    override fun setTranslationLanguage(code: String?): Unit = error("setTranslationLanguage")
    override val translateOnDevice: StateFlow<Boolean> get() = error("translateOnDevice")
    override fun setTranslateOnDevice(onDevice: Boolean): Unit = error("setTranslateOnDevice")
    override val stripTrackingParameters: StateFlow<Boolean> get() = error("stripTrackingParameters")
    override val discreetReading: StateFlow<Boolean> get() = error("discreetReading")
    override fun setDiscreetReading(enabled: Boolean): Unit = error("setDiscreetReading")
    override fun setStripTrackingParameters(enabled: Boolean): Unit = error("setStripTrackingParameters")
    override val keepMedia: StateFlow<KeepMedia> get() = error("keepMedia")
    override fun setKeepMedia(keep: KeepMedia): Unit = error("setKeepMedia")
    override val cacheLimit: StateFlow<CacheLimit> get() = error("cacheLimit")
    override fun setCacheLimit(limit: CacheLimit): Unit = error("setCacheLimit")
    override val lastStorageCleanup: StateFlow<Long?> get() = error("lastStorageCleanup")
    override fun setLastStorageCleanup(epochSeconds: Long): Unit = error("setLastStorageCleanup")
    override val messageTextSize: StateFlow<Int> get() = error("messageTextSize")
    override fun setMessageTextSize(size: Int): Unit = error("setMessageTextSize")
    override val openLinksInApp: StateFlow<Boolean> get() = error("openLinksInApp")
    override fun setOpenLinksInApp(enabled: Boolean): Unit = error("setOpenLinksInApp")
    override val doubleTapReaction: StateFlow<Boolean> get() = error("doubleTapReaction")
    override fun setDoubleTapReaction(enabled: Boolean): Unit = error("setDoubleTapReaction")
}
