package eu.tintera.plume.core.chatcolor

import eu.tintera.plume.core.settings.DataStoreAppSettings
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.features.chats.FakeChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Barvy konverzací nad skutečným úložištěm nastavení (viz `DataStoreAppSettingsTest`). */
class ChatColorsTest {

    private fun colorTest(block: suspend (ChatColors, DataStoreAppSettings) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-chat-color-${Random.nextLong()}.preferences_pb").toString()
            val settings = DataStoreAppSettings(createSettingsDataStore(path), scope)
            block(ChatColors(settings, FakeChatRepository()), settings)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun ChatColors.await(predicate: (Map<Long, ThemeColor>) -> Boolean): Map<Long, ThemeColor> =
        withTimeout(WAIT_MS) { colors.first(predicate) }

    @Test
    fun barvaSeUloziAZrusenimZmizi() = colorTest { colors, settings ->
        colors.set(chatId = 5, color = ThemeColor.GREEN)
        colors.await { it[5L] == ThemeColor.GREEN }
        assertEquals(ThemeColor.GREEN, colors.current()[5L])

        // Druhá barva téhož chatu tu první nahradí, nepřidá se vedle.
        colors.set(chatId = 5, color = ThemeColor.RED)
        colors.await { it[5L] == ThemeColor.RED }
        assertEquals(1, settings.chatColors.value.size)

        colors.set(chatId = 5, color = null)
        colors.await { 5L !in it }
        assertEquals(emptyList(), withTimeout(WAIT_MS) { settings.chatColors.first { it.isEmpty() } })
    }

    @Test
    fun barvyJinehoUctuSeNeukazuji() {
        val stored = listOf(
            StoredChatColor(accountId = 1, chatId = 5, color = "GREEN"),
            StoredChatColor(accountId = 2, chatId = 6, color = "PINK"),
        )
        assertEquals(mapOf(5L to ThemeColor.GREEN), chatColorMap(stored, account = 1))
        assertEquals(emptyMap(), chatColorMap(stored, account = null))
    }

    @Test
    fun neznamaBarvaZNovejsiVerzeSeZahodi() {
        val stored = listOf(StoredChatColor(accountId = 1, chatId = 5, color = "MAGENTA"))
        assertNull(chatColorMap(stored, account = 1)[5L])
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
