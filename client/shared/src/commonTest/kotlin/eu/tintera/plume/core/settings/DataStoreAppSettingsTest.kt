package eu.tintera.plume.core.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Testy nad skutečným souborem.
 *
 * Běží na SKUTEČNÉM čase (`runBlocking` + `Dispatchers.Default`), ne na virtuálním `runTest`:
 * DataStore čte a zapisuje na vlastních vláknech a s testovacím plánovačem se to občas minulo —
 * test visel do minutového limitu. Každé čekání má proto limit, aby ze zaseknutí bylo selhání.
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

    /**
     * Počká, až hodnota vyhoví [predicate].
     *
     * Při vypršení limitu selže vlastní hláškou: řekne, NA CO se čekalo a co bylo naposledy vidět.
     * Holý `withTimeout` hlásí jen „Timed out waiting for N ms", a z toho se u zápisu, který se
     * neprojevil, nedá poznat vůbec nic — přesně kvůli tomu se jedno selhání tohohle testu
     * nepodařilo vysvětlit a muselo se hádat.
     */
    private suspend fun <T> StateFlow<T>.awaitValue(what: String, predicate: (T) -> Boolean): T =
        try {
            withTimeout(WAIT_MS) { first(predicate) }
        } catch (timeout: TimeoutCancellationException) {
            fail("Čekání na „$what\" nedoběhlo za $WAIT_MS ms. Poslední hodnota: $value", timeout)
        }

    @Test
    fun vychoziJeEmoji() = settingsTest { settings, _ ->
        assertEquals(ExpressionMode.Emoji, settings.expressionMode.value)
    }

    @Test
    fun ulozenaHodnotaSeNacte() = settingsTest { settings, _ ->
        withTimeout(WAIT_MS) { settings.setExpressionMode(ExpressionMode.Stickers) }

        assertEquals(
            ExpressionMode.Stickers,
            settings.expressionMode.awaitValue("uložené Stickers") { it == ExpressionMode.Stickers },
        )
    }

    @Test
    fun nedavnaEmojiNejnovejsiPrvniBezDuplicit() = settingsTest { settings, _ ->
        /*
         * Čeká se na CELÝ očekávaný seznam, ne jen na první položku.
         *
         * Nedoběhlý zápis pak selže hlášením, které říká, co se čekalo a co je v úložišti —
         * ne až závěrečným `assertEquals` o krok dál, kde už není poznat, který ze zápisů
         * se neprojevil.
         */
        val steps = listOf(
            "😀" to listOf("😀"),
            "👍" to listOf("👍", "😀"),
            // Znovu použité emoji se přesune na začátek, nepřidá se podruhé.
            "😀" to listOf("😀", "👍"),
            "👨‍👩‍👧" to listOf("👨‍👩‍👧", "😀", "👍"),
        )

        steps.forEach { (emoji, expected) ->
            // Zápis běží v rozsahu nastavení (neblokuje); počká se, až se projeví.
            settings.recordRecentEmoji(emoji)
            settings.recentEmojis.awaitValue("nedávná po $emoji: $expected") { it == expected }
        }

        assertEquals(steps.last().second, settings.recentEmojis.value)
    }

    @Test
    fun nedavnychJeNejvysStrop() = settingsTest { settings, _ ->
        val emojis = (0..MAX_RECENT_EMOJIS).map { "e$it" }

        emojis.forEach { emoji ->
            settings.recordRecentEmoji(emoji)
            settings.recentEmojis.awaitValue("nedávná začínají $emoji") { it.firstOrNull() == emoji }
        }

        assertEquals(MAX_RECENT_EMOJIS, settings.recentEmojis.value.size)
        assertEquals(emojis.last(), settings.recentEmojis.value.first())
    }

    @Test
    fun odstinPletiSeUloziAZrusi() = settingsTest { settings, _ ->
        settings.setEmojiSkinTone("👍", "👍🏽")
        assertEquals(
            mapOf("👍" to "👍🏽"),
            settings.emojiSkinTones.awaitValue("uložený odstín") { it.isNotEmpty() },
        )

        settings.setEmojiSkinTone("👍", null)
        assertEquals(
            emptyMap(),
            settings.emojiSkinTones.awaitValue("zrušený odstín") { it.isEmpty() },
        )
    }

    @Test
    fun neznamaHodnotaSpadneNaVychozi() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dataStore = createSettingsDataStore(tempPath())
            val settings = DataStoreAppSettings(dataStore, scope)

            /*
             * Nejdřív platná hodnota, teprve pak ta neznámá.
             *
             * Bez toho test NEOVĚŘOVAL NIC: `first()` nad `StateFlow` vrátí počáteční hodnotu
             * (`Emoji`) dřív, než se ze souboru cokoli načte, takže výsledek vyšel správně
             * i s platným „Stickers" v úložišti. Přechod Stickers → Emoji je naopak vidět.
             */
            withTimeout(WAIT_MS) { settings.setExpressionMode(ExpressionMode.Stickers) }
            settings.expressionMode.awaitValue("uložené Stickers") { it == ExpressionMode.Stickers }

            // Hodnota z budoucí verze aplikace, kterou tahle verze nezná.
            withTimeout(WAIT_MS) { dataStore.edit { it[stringPreferencesKey("expression_mode")] = "Gify" } }

            assertEquals(
                ExpressionMode.Emoji,
                settings.expressionMode.awaitValue("pád na výchozí Emoji") { it == ExpressionMode.Emoji },
            )
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        /**
         * Limit jednoho čekání.
         *
         * Není to měřítko rychlosti, jen pojistka proti zaseknutí — proto raději velkoryse.
         * Jediné pozorované selhání přišlo z plného `check` na stroji, kde se současně točil
         * emulátor, simulátor iOS a link Kotlin/Native; zápis do souboru se tam na pět vteřin
         * vejít nemusí a z „flaky" testu není užitek žádný.
         */
        const val WAIT_MS = 15_000L
    }
}
