package eu.tintera.plume.core.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testy nad skutečným souborem.
 *
 * Běží na SKUTEČNÉM čase (`runBlocking` + `Dispatchers.Default`), ne na virtuálním `runTest`:
 * DataStore čte a zapisuje na vlastních vláknech a s testovacím plánovačem se to občas minulo —
 * test visel do minutového limitu. Každé čekání má krátký limit, aby zaseknutí selhalo hned.
 */
class DataStoreAppSettingsTest {

    /** Každý test vlastní soubor — DataStore nad jedním souborem smí existovat jen jednou. */
    private fun tempPath(): String =
        (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-test-${Random.nextLong()}.preferences_pb").toString()

    private fun settingsTest(block: suspend (DataStoreAppSettings, CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block(DataStoreAppSettings(createSettingsDataStore(tempPath()), scope), scope)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun <T> StateFlow<T>.awaitValue(predicate: (T) -> Boolean): T =
        withTimeout(WAIT_MS) { first(predicate) }

    @Test
    fun vychoziJeEmoji() = settingsTest { settings, _ ->
        assertEquals(ExpressionMode.Emoji, settings.expressionMode.value)
    }

    @Test
    fun ulozenaHodnotaSeNacte() = settingsTest { settings, _ ->
        withTimeout(WAIT_MS) { settings.setExpressionMode(ExpressionMode.Stickers) }
        assertEquals(ExpressionMode.Stickers, settings.expressionMode.awaitValue { it == ExpressionMode.Stickers })
    }

    @Test
    fun nedavnaEmojiNejnovejsiPrvniBezDuplicit() = settingsTest { settings, _ ->
        listOf("😀", "👍", "😀", "👨‍👩‍👧").forEach { emoji ->
            settings.recordRecentEmoji(emoji)
            // Zápis běží v rozsahu nastavení; počká se, až se projeví.
            settings.recentEmojis.awaitValue { it.firstOrNull() == emoji }
        }

        assertEquals(listOf("👨‍👩‍👧", "😀", "👍"), settings.recentEmojis.value)
    }

    @Test
    fun nedavnychJeNejvysStrop() = settingsTest { settings, _ ->
        val emojis = (0..MAX_RECENT_EMOJIS).map { "e$it" }

        emojis.forEach { emoji ->
            settings.recordRecentEmoji(emoji)
            settings.recentEmojis.awaitValue { it.firstOrNull() == emoji }
        }

        assertEquals(MAX_RECENT_EMOJIS, settings.recentEmojis.value.size)
        assertEquals(emojis.last(), settings.recentEmojis.value.first())
    }

    @Test
    fun odstinPletiSeUloziAZrusi() = settingsTest { settings, _ ->
        settings.setEmojiSkinTone("👍", "👍🏽")
        assertEquals(mapOf("👍" to "👍🏽"), settings.emojiSkinTones.awaitValue { it.isNotEmpty() })

        settings.setEmojiSkinTone("👍", null)
        assertEquals(emptyMap(), settings.emojiSkinTones.awaitValue { it.isEmpty() })
    }

    @Test
    fun neznamaHodnotaSpadneNaVychozi() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dataStore = createSettingsDataStore(tempPath())
            // Hodnota z budoucí verze aplikace, kterou tahle verze nezná.
            withTimeout(WAIT_MS) { dataStore.edit { it[stringPreferencesKey("expression_mode")] = "Gify" } }

            val settings = DataStoreAppSettings(dataStore, scope)

            assertEquals(ExpressionMode.Emoji, withTimeout(WAIT_MS) { settings.expressionMode.first() })
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
