package eu.tintera.plume.core.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreAppSettingsTest {

    /** Každý test vlastní soubor — DataStore nad jedním souborem smí existovat jen jednou. */
    private fun tempPath(): String =
        (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-test-${Random.nextLong()}.preferences_pb").toString()

    @Test
    fun vychoziJeEmoji() = runTest {
        val settings = DataStoreAppSettings(createSettingsDataStore(tempPath()), backgroundScope)
        assertEquals(ExpressionMode.Emoji, settings.expressionMode.value)
    }

    @Test
    fun ulozenaHodnotaSeNacte() = runTest {
        val path = tempPath()
        val settings = DataStoreAppSettings(createSettingsDataStore(path), backgroundScope)

        settings.setExpressionMode(ExpressionMode.Stickers)

        assertEquals(ExpressionMode.Stickers, settings.expressionMode.first { it == ExpressionMode.Stickers })
    }

    @Test
    fun nedavnaEmojiNejnovejsiPrvniBezDuplicit() = runTest {
        val settings = DataStoreAppSettings(createSettingsDataStore(tempPath()), backgroundScope)

        listOf("😀", "👍", "😀", "👨‍👩‍👧").forEach { emoji ->
            settings.recordRecentEmoji(emoji)
            // Zápis běží v rozsahu nastavení; počká se, až se projeví.
            settings.recentEmojis.first { it.firstOrNull() == emoji }
        }

        assertEquals(listOf("👨‍👩‍👧", "😀", "👍"), settings.recentEmojis.value)
    }

    @Test
    fun nedavnychJeNejvysStrop() = runTest {
        val settings = DataStoreAppSettings(createSettingsDataStore(tempPath()), backgroundScope)
        val emojis = (0..MAX_RECENT_EMOJIS).map { "e$it" }

        emojis.forEach { emoji ->
            settings.recordRecentEmoji(emoji)
            settings.recentEmojis.first { it.firstOrNull() == emoji }
        }

        assertEquals(MAX_RECENT_EMOJIS, settings.recentEmojis.value.size)
        assertEquals(emojis.last(), settings.recentEmojis.value.first())
    }

    @Test
    fun neznamaHodnotaSpadneNaVychozi() = runTest {
        val dataStore = createSettingsDataStore(tempPath())
        // Hodnota z budoucí verze aplikace, kterou tahle verze nezná.
        dataStore.edit { it[stringPreferencesKey("expression_mode")] = "Gify" }

        val settings = DataStoreAppSettings(dataStore, backgroundScope)

        assertEquals(ExpressionMode.Emoji, settings.expressionMode.first())
    }
}
