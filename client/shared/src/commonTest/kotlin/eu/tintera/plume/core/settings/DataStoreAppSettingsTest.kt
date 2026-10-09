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
    fun vycleneniNeprectenychJeVychoziVypnute() = settingsTest { settings, _ ->
        assertEquals(false, settings.separateUnreadChats.value)
    }

    @Test
    fun vycleneniNeprectenychSeUloziAVrati() = settingsTest { settings, _ ->
        settings.setSeparateUnreadChats(true)
        assertEquals(
            true,
            settings.separateUnreadChats.awaitValue("zapnuté vyčlenění") { it },
        )

        settings.setSeparateUnreadChats(false)
        assertEquals(
            false,
            settings.separateUnreadChats.awaitValue("vypnuté vyčlenění") { !it },
        )
    }

    @Test
    fun akceTahuJsouVychoziArchivace() = settingsTest { settings, _ ->
        assertEquals(ChatSwipeActions(ChatSwipeAction.Archive, ChatSwipeAction.Archive), settings.chatSwipeActions.value)
    }

    @Test
    fun akceTahuSeUloziProKazdouStranuZvlast() = settingsTest { settings, _ ->
        settings.setChatSwipeActions(ChatSwipeActions(right = ChatSwipeAction.Read, left = ChatSwipeAction.None))
        assertEquals(
            ChatSwipeActions(right = ChatSwipeAction.Read, left = ChatSwipeAction.None),
            settings.chatSwipeActions.awaitValue("zvolené akce") { it.right == ChatSwipeAction.Read },
        )

        // Návrat k výchozí na jedné straně nesmí shodit volbu té druhé.
        settings.setChatSwipeActions(ChatSwipeActions(right = ChatSwipeAction.Archive, left = ChatSwipeAction.None))
        assertEquals(
            ChatSwipeActions(right = ChatSwipeAction.Archive, left = ChatSwipeAction.None),
            settings.chatSwipeActions.awaitValue("vrácená pravá strana") { it.right == ChatSwipeAction.Archive },
        )
    }

    @Test
    fun automatickeStahovaniMaVychoziUrovne() = settingsTest { settings, _ ->
        assertEquals(AutoDownloadSettings(), settings.autoDownload.value)
    }

    @Test
    fun automatickeStahovaniSeUloziProKazdouSit() = settingsTest { settings, _ ->
        val chosen = AutoDownloadSettings(
            wifi = AutoDownloadLevel.Medium,
            mobile = AutoDownloadLevel.Off,
            roaming = AutoDownloadLevel.Off,
        )
        settings.setAutoDownload(chosen)
        assertEquals(chosen, settings.autoDownload.awaitValue("zvolené úrovně") { it.mobile == AutoDownloadLevel.Off })

        // Návrat k výchozí na jedné síti nesmí shodit volbu ostatních.
        settings.setAutoDownload(chosen.copy(mobile = AutoDownloadSettings().mobile))
        assertEquals(
            chosen.copy(mobile = AutoDownloadSettings().mobile),
            settings.autoDownload.awaitValue("vrácená mobilní data") { it.mobile != AutoDownloadLevel.Off },
        )
    }

    @Test
    fun zavrenaAktualizaceSePamatujePodleVerze() = settingsTest { settings, _ ->
        assertEquals(null, settings.dismissedUpdateVersionCode.value)

        settings.dismissUpdate(41)
        assertEquals(41, settings.dismissedUpdateVersionCode.awaitValue("zavřená verze 41") { it == 41 })

        // Další vydaná verze přepíše tu předchozí — pamatuje se jen poslední zavřená.
        settings.dismissUpdate(42)
        assertEquals(42, settings.dismissedUpdateVersionCode.awaitValue("zavřená verze 42") { it == 42 })
    }

    /** `null` znamená „uživatel si nevybral" — výchozí motiv se podle platformy dopočítá až v UI. */
    @Test
    fun motivJeVychoziNevybrany() = settingsTest { settings, _ ->
        assertEquals(null, settings.themeMode.value)
    }

    @Test
    fun motivSeUloziAZrusi() = settingsTest { settings, _ ->
        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(
            ThemeMode.DARK,
            settings.themeMode.awaitValue("uložený tmavý motiv") { it == ThemeMode.DARK },
        )

        settings.setThemeMode(null)
        assertEquals(
            null,
            settings.themeMode.awaitValue("motiv zpátky na výchozí") { it == null },
        )
    }

    /** `null` znamená „uživatel si nevybral" — výchozí podle platformy se dopočítá až v UI. */
    @Test
    fun dynamickeBarvyJsouVychoziNevybrane() = settingsTest { settings, _ ->
        assertEquals(null, settings.dynamicColor.value)
    }

    @Test
    fun dynamickeBarvySeUlozi() = settingsTest { settings, _ ->
        settings.setDynamicColor(false)
        assertEquals(false, settings.dynamicColor.awaitValue("vypnuté barvy z tapety") { it == false })

        settings.setDynamicColor(true)
        assertEquals(true, settings.dynamicColor.awaitValue("zapnuté barvy z tapety") { it == true })
    }

    /**
     * Neznámý název motivu (hodnota z novější verze aplikace) se chová jako nevybraný.
     *
     * Testuje se PŘECHODEM z platné hodnoty, ne čtením na začátku: `first()` nad `StateFlow`
     * vrátí počáteční hodnotu (`null`) dřív, než se ze souboru cokoli načte, takže by test
     * prošel i tehdy, kdyby se neznámá hodnota do stavu propsala.
     */
    @Test
    fun neznamyMotivSpadneNaNevybrany() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dataStore = createSettingsDataStore(tempPath())
            val settings = DataStoreAppSettings(dataStore, scope)

            settings.setThemeMode(ThemeMode.LIGHT)
            settings.themeMode.awaitValue("uložený světlý motiv") { it == ThemeMode.LIGHT }

            withTimeout(WAIT_MS) { dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" } }

            assertEquals(
                null,
                settings.themeMode.awaitValue("pád na nevybraný motiv") { it == null },
            )
        } finally {
            scope.cancel()
        }
    }

    /** `null` znamená modrou — výchozí barva se neukládá, aby šla změnit bez migrace. */
    @Test
    fun napovedyGestSeSkladajiANezapomenou() = settingsTest { settings, _ ->
        assertEquals(emptySet(), settings.seenGestureHints.value)
        settings.markGestureHintSeen(GestureHint.HoldToRecord)
        settings.seenGestureHints.awaitValue("viděné nahrávání") { GestureHint.HoldToRecord in it }
        // Další nápověda tu první nepřepíše — zápis čte uložený stav uvnitř `edit`.
        settings.markGestureHintSeen(GestureHint.RevealTimes)
        assertEquals(
            setOf(GestureHint.HoldToRecord, GestureHint.RevealTimes),
            settings.seenGestureHints.awaitValue("obě nápovědy") { it.size == 2 },
        )
    }

    /** Nápověda, kterou tahle verze nezná (z novější), se zahodí — ostatní zůstanou. */
    @Test
    fun neznamaNapovedaSeZahodi() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dataStore = createSettingsDataStore(tempPath())
            val settings = DataStoreAppSettings(dataStore, scope)

            settings.markGestureHintSeen(GestureHint.SendOptions)
            settings.seenGestureHints.awaitValue("viděné volby odeslání") { it.isNotEmpty() }

            withTimeout(WAIT_MS) {
                dataStore.edit { it[stringPreferencesKey("seen_gesture_hints")] = "SendOptions SwipeToFly RevealTimes" }
            }

            assertEquals(
                setOf(GestureHint.SendOptions, GestureHint.RevealTimes),
                settings.seenGestureHints.awaitValue("bez neznámé") { it.size == 2 },
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun barvaMotivuJeVychoziNevybrana() = settingsTest { settings, _ ->
        assertEquals(null, settings.themeColor.value)
    }

    @Test
    fun barvaMotivuSeUlozi() = settingsTest { settings, _ ->
        settings.setThemeColor(ThemeColor.GREEN)
        assertEquals(
            ThemeColor.GREEN,
            settings.themeColor.awaitValue("uložená zelená") { it == ThemeColor.GREEN },
        )
    }

    @Test
    fun uchovaniMediiJeVychoziNavzdy() = settingsTest { settings, _ ->
        assertEquals(KeepMedia.Forever, settings.keepMedia.value)
        assertEquals(null, settings.lastStorageCleanup.value)
    }

    @Test
    fun uchovaniMediiACasUklidSeUlozi() = settingsTest { settings, _ ->
        settings.setKeepMedia(KeepMedia.Week)
        settings.setLastStorageCleanup(1_700_000_000)
        assertEquals(KeepMedia.Week, settings.keepMedia.awaitValue("uložený týden") { it == KeepMedia.Week })
        assertEquals(
            1_700_000_000L,
            settings.lastStorageCleanup.awaitValue("čas úklidu") { it == 1_700_000_000L },
        )
    }

    /** Doba, kterou tahle verze nezná, nesmí znamenat mazání — spadne na „navždy". */
    @Test
    fun neznameUchovaniSpadneNaNavzdy() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dataStore = createSettingsDataStore(tempPath())
            val settings = DataStoreAppSettings(dataStore, scope)

            settings.setKeepMedia(KeepMedia.ThreeDays)
            settings.keepMedia.awaitValue("uložené tři dny") { it == KeepMedia.ThreeDays }

            withTimeout(WAIT_MS) { dataStore.edit { it[stringPreferencesKey("keep_media")] = "Hour" } }

            assertEquals(
                KeepMedia.Forever,
                settings.keepMedia.awaitValue("pád na navždy") { it == KeepMedia.Forever },
            )
        } finally {
            scope.cancel()
        }
    }

    /** Barva, kterou tahle verze nezná (z novější), spadne na výchozí — přechodem, viz výš. */
    @Test
    fun neznamaBarvaMotivuSpadneNaVychozi() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dataStore = createSettingsDataStore(tempPath())
            val settings = DataStoreAppSettings(dataStore, scope)

            settings.setThemeColor(ThemeColor.RED)
            settings.themeColor.awaitValue("uložená červená") { it == ThemeColor.RED }

            withTimeout(WAIT_MS) { dataStore.edit { it[stringPreferencesKey("theme_color")] = "MAGENTA" } }

            assertEquals(
                null,
                settings.themeColor.awaitValue("pád na výchozí barvu") { it == null },
            )
        } finally {
            scope.cancel()
        }
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

    /**
     * Zápis hned po vzniku nastavení se musí projevit — i když se trefí do úvodního čtení souboru.
     *
     * Hlídá obchvat chyby DataStore 1.2.x (viz [DataStoreAppSettings]): odběr `data` spuštěný
     * souběžně se zápisem dostal stará data s novou verzí a zápis pak zahodil. Selhávalo to
     * náhodně a jen pod zátěží (dva emulátory, link Kotlin/Native), pokaždé u testu, který zapisuje
     * „odpal a zapomeň" hned po vzniku. Jedno kolo okno mine skoro vždycky, proto kol stovky:
     * s původním kódem a všemi jádry vytíženými (`yes > /dev/null` dvakrát na jádro) se zaseklo
     * 25 z 2 000, s obchvatem 0 z 6 000. Bez zátěže tenhle test chybu nepozná — je to pojistka,
     * ne důkaz.
     */
    @Test
    fun zapisHnedPoVznikuSeProjevi() = runBlocking {
        repeat(ROUNDS) { round ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val settings = DataStoreAppSettings(createSettingsDataStore(tempPath()), scope)
                settings.setSeparateUnreadChats(true)
                settings.separateUnreadChats.awaitValue("zápis v kole $round") { it }
            } finally {
                scope.cancel()
            }
        }
    }

    private companion object {
        /** Kol v [zapisHnedPoVznikuSeProjevi]. Kolo trvá milisekundy, dokud se nic nezasekne. */
        const val ROUNDS = 300

        /**
         * Limit jednoho čekání.
         *
         * Není to měřítko rychlosti, jen pojistka proti zaseknutí — proto raději velkoryse.
         * Selhání pod zátěží, která se dřív přičítala pomalému stroji, byla ztracená data
         * (chyba DataStore, viz [zapisHnedPoVznikuSeProjevi]); delší limit by na ně nepomohl.
         */
        const val WAIT_MS = 15_000L
    }
}
