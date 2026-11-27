package eu.tintera.plume.core.settings.sync

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.chatcolor.StoredChatColor
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.settings.DataStoreAppSettings
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.video.MAX_VIDEO_POSITIONS
import eu.tintera.plume.core.video.VideoPosition
import eu.tintera.plume.core.video.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dvě zařízení (dva soubory nastavení) nad jedním úložištěm. Na skutečném čase jako
 * `DataStoreAppSettingsTest`: DataStore zapisuje na vlastních vláknech.
 */
class CloudSyncTest {

    private class FakeCloudStore : CloudStore {
        val values = mutableMapOf<String, String>()

        override suspend fun get(keys: List<String>) = Result.success(keys.mapNotNull { key -> values[key]?.let { key to it } }.toMap())

        override suspend fun set(key: String, value: String): Result<Unit> {
            check(value.length <= CLOUD_VALUE_LIMIT) { "Hodnota $key má ${value.length} znaků." }
            values[key] = value
            return Result.success(Unit)
        }

        override suspend fun delete(keys: List<String>): Result<Unit> {
            keys.forEach(values::remove)
            return Result.success(Unit)
        }
    }

    private inner class Device(store: CloudStore, scope: CoroutineScope) {
        val settings = DataStoreAppSettings(createSettingsDataStore(tempPath()), scope)
        val sync = CloudSync(
            settings = settings,
            account = settings.forAccount(AccountSlot.Primary),
            store = store,
            accountId = MutableStateFlow(ACCOUNT),
            authStage = MutableStateFlow(AuthStage.Connecting),
            scope = scope,
        )
    }

    private fun tempPath(): String =
        (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-sync-${Random.nextLong()}.preferences_pb").toString()

    private fun twoDevices(block: suspend (Device, Device, FakeCloudStore) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FakeCloudStore()
            block(Device(store, scope), Device(store, scope), store)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = withTimeout(WAIT_MS) { first(predicate) }

    @Test
    fun preferenceChangeReachesTheOtherDevice() = twoDevices { phone, desktop, _ ->
        phone.settings.applySyncedPreferences(mapOf("video_speed" to JsonPrimitive("Fast"), "message_text_size" to JsonPrimitive(18)))
        phone.settings.syncedPreferences.await { it?.get("video_speed") != null }
        assertTrue(phone.sync.sync(ACCOUNT))
        assertTrue(desktop.sync.sync(ACCOUNT))
        val synced = desktop.settings.syncedPreferences.await { it?.get("video_speed") != null }!!
        assertEquals("Fast", synced.getValue("video_speed").content)
        assertEquals(18, synced.getValue("message_text_size").content.toInt())

        // Návrat k výchozí hodnotě (klíč smazaný) se propíše taky.
        desktop.settings.applySyncedPreferences(mapOf("video_speed" to null))
        desktop.settings.syncedPreferences.await { it?.get("video_speed") == null }
        desktop.sync.sync(ACCOUNT)
        phone.sync.sync(ACCOUNT)
        phone.settings.syncedPreferences.await { it?.get("video_speed") == null }
    }

    @Test
    fun appThemeStaysOnEachDevice() = twoDevices { phone, desktop, store ->
        phone.settings.setThemeColor(ThemeColor.GREEN)
        phone.settings.themeColor.await { it == ThemeColor.GREEN }
        phone.sync.sync(ACCOUNT)
        desktop.sync.sync(ACCOUNT)
        assertTrue(store.values.values.none { "GREEN" in it })
        assertEquals(null, desktop.settings.syncedPreferences.await { it != null }!!["theme_color"])
    }

    @Test
    fun chatColorAndItsRemovalReachTheOtherDevice() = twoDevices { phone, desktop, _ ->
        phone.settings.setChatColors(listOf(StoredChatColor(ACCOUNT, 42, "PURPLE"), StoredChatColor(OTHER_ACCOUNT, 7, "RED")))
        phone.settings.chatColors.await { it.size == 2 }
        phone.sync.sync(ACCOUNT)
        desktop.sync.sync(ACCOUNT)
        // Jen barva tohoto účtu; barva chatu jiného účtu zůstala v telefonu.
        assertEquals(listOf(StoredChatColor(ACCOUNT, 42, "PURPLE")), desktop.settings.chatColors.await { it.isNotEmpty() })

        desktop.settings.setChatColors(emptyList())
        desktop.settings.chatColors.await { it.isEmpty() }
        desktop.sync.sync(ACCOUNT)
        phone.sync.sync(ACCOUNT)
        assertEquals(listOf(StoredChatColor(OTHER_ACCOUNT, 7, "RED")), phone.settings.chatColors.await { it.size == 1 })
    }

    @Test
    fun watchedVideoAndItsRemovalReachTheOtherDevice() = twoDevices { phone, desktop, _ ->
        val position = VideoPosition("file", 60_000, HOUR, VideoSource(ACCOUNT, 5, 6), at = 1)
        phone.settings.updateVideoPositions { listOf(position) }
        phone.settings.videoPositions.await { it.isNotEmpty() }
        phone.sync.sync(ACCOUNT)
        desktop.sync.sync(ACCOUNT)
        assertEquals(60_000, desktop.settings.videoPositions.await { it.isNotEmpty() }.single().positionMs)

        // Odebráno na desktopu → zmizí i z telefonu, nevrátí se z úložiště.
        desktop.settings.updateVideoPositions { emptyList() }
        desktop.settings.videoPositions.await { it.isEmpty() }
        desktop.sync.sync(ACCOUNT)
        phone.sync.sync(ACCOUNT)
        phone.settings.videoPositions.await { it.isEmpty() }
    }

    @Test
    fun otherAccountsDataStayOnTheDevice() = twoDevices { phone, desktop, store ->
        val foreign = VideoPosition("foreign", 60_000, HOUR, VideoSource(OTHER_ACCOUNT, 5, 6), at = 1)
        phone.settings.updateVideoPositions { listOf(foreign) }
        phone.settings.setMessageBookmarks(listOf(MessageBookmark(OTHER_ACCOUNT, 1, 2, "tajné", null, 0, 1)))
        phone.settings.messageBookmarks.await { it.isNotEmpty() }
        phone.settings.videoPositions.await { it.isNotEmpty() }
        phone.sync.sync(ACCOUNT)
        assertTrue(store.values.values.none { "foreign" in it || "tajné" in it })
        desktop.sync.sync(ACCOUNT)
        assertTrue(desktop.settings.videoPositions.value.isEmpty())
    }

    @Test
    fun manyPositionsAreSplitIntoParts() = twoDevices { phone, desktop, store ->
        val many = (0 until MAX_VIDEO_POSITIONS).map { VideoPosition("file-$it", 60_000L + it, HOUR, VideoSource(ACCOUNT, 5, it.toLong()), at = it.toLong()) }
        phone.settings.updateVideoPositions { many }
        phone.settings.videoPositions.await { it.size == MAX_VIDEO_POSITIONS }
        phone.sync.sync(ACCOUNT)
        assertTrue(store.values.keys.count { it.startsWith("plume_positions_") } > 1)
        desktop.sync.sync(ACCOUNT)
        val synced = desktop.settings.videoPositions.await { it.size == MAX_VIDEO_POSITIONS }
        assertEquals("file-${MAX_VIDEO_POSITIONS - 1}", synced.first().key)
    }

    @Test
    fun bookmarkReachesTheOtherDevice() = twoDevices { phone, desktop, _ ->
        phone.settings.setMessageBookmarks(listOf(MessageBookmark(ACCOUNT, 1, 2, "Ahoj 😀", "Jana", 100, 5)))
        phone.settings.messageBookmarks.await { it.isNotEmpty() }
        phone.sync.sync(ACCOUNT)
        desktop.sync.sync(ACCOUNT)
        assertEquals("Ahoj 😀", desktop.settings.messageBookmarks.await { it.isNotEmpty() }.single().text)
    }

    @Test
    fun everyGroupedPreferenceIsStoredBySettings() = twoDevices { phone, _, _ ->
        val values = mapOf(
            "chat_row_gaps" to JsonPrimitive(true),
            "message_text_size" to JsonPrimitive(18),
            "chat_preview_lines" to JsonPrimitive(1),
            "sticker_size" to JsonPrimitive(140),
            "voice_speed" to JsonPrimitive("Fast"),
            "video_speed" to JsonPrimitive("Fast"),
            "skip_silence" to JsonPrimitive(true),
            "double_tap_reaction" to JsonPrimitive(false),
            "on_this_day" to JsonPrimitive(false),
            "open_links_in_app" to JsonPrimitive(false),
            "strip_tracking_parameters" to JsonPrimitive(false),
            "chat_swipe_right" to JsonPrimitive("Archive"),
            "chat_swipe_left" to JsonPrimitive("Pin"),
            "seen_gesture_hints" to JsonPrimitive("a,b"),
        )
        // Seznam skupin a seznam klíčů v `AppSettings` jsou dva: tenhle test hlídá, že se nerozešly.
        assertEquals(SYNCED_PREFERENCE_GROUPS.values.flatten().toSet(), values.keys)
        phone.settings.applySyncedPreferences(values)
        val stored = phone.settings.syncedPreferences.await { it?.size == values.size }!!
        assertEquals(values.mapValues { it.value.toString() }, stored.mapValues { it.value.toString() })
    }

    private companion object {
        const val ACCOUNT = 1L
        const val OTHER_ACCOUNT = 2L
        const val HOUR = 60 * 60 * 1000L
        const val WAIT_MS = 10_000L
    }
}
