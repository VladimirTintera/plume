package eu.tintera.plume.core.settings

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
    override suspend fun setExpressionMode(mode: ExpressionMode) = error("setExpressionMode")
    override val recordMode: StateFlow<RecordMode> get() = error("recordMode")
    override suspend fun setRecordMode(mode: RecordMode) = error("setRecordMode")
    override val lastStickerTab: StateFlow<String?> get() = error("lastStickerTab")
    override fun setLastStickerTab(key: String) = error("setLastStickerTab")
    override val recentEmojis: StateFlow<List<String>> get() = error("recentEmojis")
    override fun recordRecentEmoji(emoji: String) = error("recordRecentEmoji")
    override val emojiSkinTones: StateFlow<Map<String, String>> get() = error("emojiSkinTones")
    override fun setEmojiSkinTone(base: String, variant: String?) = error("setEmojiSkinTone")
    override val themeMode: StateFlow<ThemeMode?> get() = error("themeMode")
    override fun setThemeMode(mode: ThemeMode?) = error("setThemeMode")
    override val dynamicColor: StateFlow<Boolean?> get() = error("dynamicColor")
    override fun setDynamicColor(enabled: Boolean) = error("setDynamicColor")
    override val themeColor: StateFlow<ThemeColor?> get() = error("themeColor")
    override fun setThemeColor(color: ThemeColor) = error("setThemeColor")
    override val listPaneAnchor: StateFlow<ListPaneAnchor?> get() = error("listPaneAnchor")
    override fun setListPaneAnchor(anchor: ListPaneAnchor) = error("setListPaneAnchor")
    override val separateUnreadChats: StateFlow<Boolean> get() = error("separateUnreadChats")
    override fun setSeparateUnreadChats(separate: Boolean) = error("setSeparateUnreadChats")
    override val savedMessagesAsChats: StateFlow<Boolean> get() = error("savedMessagesAsChats")
    override fun setSavedMessagesAsChats(asChats: Boolean) = error("setSavedMessagesAsChats")
    override val chatSwipeActions: StateFlow<ChatSwipeActions> get() = error("chatSwipeActions")
    override fun setChatSwipeActions(actions: ChatSwipeActions) = error("setChatSwipeActions")
    override val autoDownload: StateFlow<AutoDownloadSettings> get() = error("autoDownload")
    override fun setAutoDownload(settings: AutoDownloadSettings) = error("setAutoDownload")
    override suspend fun loadedAutoDownload() : AutoDownloadSettings = error("loadedAutoDownload")
    override suspend fun autoDownloadSync() : SettingsSync? = error("autoDownloadSync")
    override suspend fun setAutoDownloadSync(state: SettingsSync) = error("setAutoDownloadSync")
    override val messageBookmarks: StateFlow<List<MessageBookmark>> get() = error("messageBookmarks")
    override fun setMessageBookmarks(bookmarks: List<MessageBookmark>) = error("setMessageBookmarks")
    override val chatSnoozes: StateFlow<List<ChatSnooze>> get() = error("chatSnoozes")
    override fun setChatSnoozes(snoozes: List<ChatSnooze>) = error("setChatSnoozes")
    override val chatColors: StateFlow<List<StoredChatColor>> get() = error("chatColors")
    override fun setChatColors(colors: List<StoredChatColor>) = error("setChatColors")
    override val storedTranscripts: StateFlow<List<StoredTranscript>> get() = error("storedTranscripts")
    override fun setStoredTranscripts(transcripts: List<StoredTranscript>) = error("setStoredTranscripts")
    override val quietHours: StateFlow<QuietHours> get() = error("quietHours")
    override fun setQuietHours(quietHours: QuietHours) = error("setQuietHours")
    override val onDeviceAiEnabled: StateFlow<Boolean> get() = error("onDeviceAiEnabled")
    override fun setOnDeviceAiEnabled(enabled: Boolean) = error("setOnDeviceAiEnabled")
    override val replySuggestionsEnabled: StateFlow<Boolean> get() = error("replySuggestionsEnabled")
    override fun setReplySuggestionsEnabled(enabled: Boolean) = error("setReplySuggestionsEnabled")
    override val scamWarningsEnabled: StateFlow<Boolean> get() = error("scamWarningsEnabled")
    override fun setScamWarningsEnabled(enabled: Boolean) = error("setScamWarningsEnabled")
    override val messageContextEnabled: StateFlow<Boolean> get() = error("messageContextEnabled")
    override fun setMessageContextEnabled(enabled: Boolean) = error("setMessageContextEnabled")
    override val onDeviceAiVariant: StateFlow<ModelVariant> get() = error("onDeviceAiVariant")
    override fun setOnDeviceAiVariant(variant: ModelVariant) = error("setOnDeviceAiVariant")
    override val voiceSpeed: StateFlow<VoiceSpeed> get() = error("voiceSpeed")
    override fun setVoiceSpeed(speed: VoiceSpeed) = error("setVoiceSpeed")
    override val videoSpeed: StateFlow<VoiceSpeed> get() = error("videoSpeed")
    override fun setVideoSpeed(speed: VoiceSpeed) = error("setVideoSpeed")
    override val dismissedUpdateVersionCode: StateFlow<Int?> get() = error("dismissedUpdateVersionCode")
    override fun dismissUpdate(versionCode: Int) = error("dismissUpdate")
    override val confirmedWebAppBots: StateFlow<Set<Long>> get() = error("confirmedWebAppBots")
    override fun confirmWebAppBot(botUserId: Long) = error("confirmWebAppBot")
    override val seenGestureHints: StateFlow<Set<GestureHint>> get() = error("seenGestureHints")
    override fun markGestureHintSeen(hint: GestureHint) = error("markGestureHintSeen")
    override val usedOneTimeKeyboards: StateFlow<Map<Long, Long>> get() = error("usedOneTimeKeyboards")
    override fun markOneTimeKeyboardUsed(chatId: Long, messageId: Long) = error("markOneTimeKeyboardUsed")
    override val translationLanguage: StateFlow<String?> get() = error("translationLanguage")
    override fun setTranslationLanguage(code: String?) = error("setTranslationLanguage")
    override val translateOnDevice: StateFlow<Boolean> get() = error("translateOnDevice")
    override fun setTranslateOnDevice(onDevice: Boolean) = error("setTranslateOnDevice")
    override val stripTrackingParameters: StateFlow<Boolean> get() = error("stripTrackingParameters")
    override val discreetReading: StateFlow<Boolean> get() = error("discreetReading")
    override fun setDiscreetReading(enabled: Boolean) = error("setDiscreetReading")
    override fun setStripTrackingParameters(enabled: Boolean) = error("setStripTrackingParameters")
    override val keepMedia: StateFlow<KeepMedia> get() = error("keepMedia")
    override fun setKeepMedia(keep: KeepMedia) = error("setKeepMedia")
    override val lastStorageCleanup: StateFlow<Long?> get() = error("lastStorageCleanup")
    override fun setLastStorageCleanup(epochSeconds: Long) = error("setLastStorageCleanup")
    override val messageTextSize: StateFlow<Int> get() = error("messageTextSize")
    override fun setMessageTextSize(size: Int) = error("setMessageTextSize")
    override val openLinksInApp: StateFlow<Boolean> get() = error("openLinksInApp")
    override fun setOpenLinksInApp(enabled: Boolean) = error("setOpenLinksInApp")
    override val doubleTapReaction: StateFlow<Boolean> get() = error("doubleTapReaction")
    override fun setDoubleTapReaction(enabled: Boolean) = error("setDoubleTapReaction")
}
