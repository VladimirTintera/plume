package eu.tintera.plume.core.settings

import eu.tintera.plume.features.conversation.StoredTranscript
import kotlinx.serialization.json.Json
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.snooze.ChatSnooze
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.GemmaModel
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.audio.VoiceSpeed
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
 * Nastavení aplikace, která přežijí restart.
 *
 * Jen to, co si aplikace pamatuje o uživateli — ne data z Telegramu (ta drží TDLib). Hodnoty jsou
 * [StateFlow], načtené hned při startu: obrazovka je má k dispozici v prvním snímku a nic
 * neproblikne z výchozí hodnoty na uloženou.
 */
interface AppSettings {

    /** Poslední otevřená část výběru ve vstupním poli — podle ní ikona i to, čím se výběr otevře. */
    val expressionMode: StateFlow<ExpressionMode>

    suspend fun setExpressionMode(mode: ExpressionMode)

    /**
     * Co nahraje podržení tlačítka v prázdném poli: hlasovku, nebo video kolečko. Přepíná se
     * ťuknutím na totéž tlačítko a pamatuje se, jako v Telegramu — kdo posílá kolečka, nechce
     * před každým přepínat.
     */
    val recordMode: StateFlow<RecordMode>

    suspend fun setRecordMode(mode: RecordMode)

    /** Emoji naposledy vybraná ve výběru, od nejnovějšího. Bez duplicit, nejvýš [MAX_RECENT_EMOJIS]. */
    val recentEmojis: StateFlow<List<String>>

    /**
     * Zařadí emoji na začátek nedávných (už použité se přesune). Neblokuje — zápis běží v rozsahu
     * aplikace, aby ho nezrušil odchod z obrazovky hned po ťuknutí.
     */
    fun recordRecentEmoji(emoji: String)

    /**
     * Zvolený odstín pleti pro jednotlivá emoji: základ z katalogu → varianta. Emoji, které tu není,
     * se kreslí ve výchozí (žluté) podobě. Pamatuje se po emoji, ne jeden odstín pro všechna —
     * tak to dělá Telegram i systémové klávesnice.
     */
    val emojiSkinTones: StateFlow<Map<String, String>>

    /** Uloží odstín pro [base]; `null` vrátí emoji k výchozí podobě. Neblokuje, jako [recordRecentEmoji]. */
    fun setEmojiSkinTone(base: String, variant: String?)

    /**
     * Zvolený motiv, nebo `null`, dokud si uživatel nevybral.
     *
     * Kdo kreslí, spadne na [ThemeMode.SYSTEM].
     */
    val themeMode: StateFlow<ThemeMode?>

    /** Uloží motiv; `null` vrátí volbu na výchozí podle platformy. Neblokuje, jako [recordRecentEmoji]. */
    fun setThemeMode(mode: ThemeMode?)

    /**
     * Barvy z tapety (Material You), nebo `null`, dokud si uživatel nevybral.
     *
     * Výchozí hodnota se schválně NEUKLÁDÁ: liší se podle platformy (Android 12+ zapnuto, jinde
     * nejsou) a zapsaná hodnota by ten rozdíl zakonzervovala i na zařízení, kde neplatí. Kdo
     * kreslí, spadne na `supportsDynamicColor`.
     */
    val dynamicColor: StateFlow<Boolean?>

    /** Zapne/vypne barvy z tapety. Neblokuje, jako [recordRecentEmoji]. */
    fun setDynamicColor(enabled: Boolean)

    /**
     * Barva, ze které se dopočítá paleta, když neplatí barvy z tapety; `null` = výchozí
     * ([ThemeColor.BLUE]). Výchozí se neukládá, jako u motivu — kdyby se značková barva někdy
     * změnila, má ji dostat i ten, kdo si nikdy nic nevybral.
     */
    val themeColor: StateFlow<ThemeColor?>

    /** Uloží barvu palety. Neblokuje, jako [recordRecentEmoji]. */
    fun setThemeColor(color: ThemeColor)

    /**
     * Mají nepřečtené chaty v seznamu vlastní blok?
     *
     * Výchozí je `false` — nepřečtený chat zůstane tam, kde ho uživatel zná z pořadí od TDLib.
     * Vyčlenění je užitečné, jen když je chatů hodně; jinak seznam jen rozbíjí.
     */
    val separateUnreadChats: StateFlow<Boolean>

    /** Zapne/vypne vlastní blok nepřečtených. Neblokuje, jako [recordRecentEmoji]. */
    fun setSeparateUnreadChats(separate: Boolean)

    /**
     * Otevírají se Uložené zprávy jako seznam chatů, ze kterých se ukládalo (témata), místo
     * jedné konverzace? Výchozí `false` — tak se aplikace chovala dřív; přepíná se v hlavičce
     * Uložených zpráv a pamatuje se, jako u oficiálních klientů.
     */
    val savedMessagesAsChats: StateFlow<Boolean>

    fun setSavedMessagesAsChats(asChats: Boolean)

    /**
     * Co dělá tah chatem doprava a doleva v seznamu chatů (Nastavení → Obecné → Chaty).
     * Výchozí je archivace na obou stranách; výchozí hodnota strany se neukládá, jako u motivu.
     */
    val chatSwipeActions: StateFlow<ChatSwipeActions>

    /** Uloží akce tahu. Neblokuje, jako [recordRecentEmoji]. */
    fun setChatSwipeActions(actions: ChatSwipeActions)

    /**
     * Co se smí stáhnout samo na Wi-Fi, mobilních datech a v roamingu (Nastavení → Úložiště).
     * Výchozí hodnota sítě se neukládá, jako u motivu — kdyby se výchozí změnila, má ji dostat
     * i ten, kdo si nic nevybral.
     */
    val autoDownload: StateFlow<AutoDownloadSettings>

    /** Uloží úrovně automatického stahování. Neblokuje. */
    fun setAutoDownload(settings: AutoDownloadSettings)

    /**
     * [autoDownload] až po načtení souboru. StateFlow do té doby drží výchozí hodnotu, a podle
     * ní by synchronizace ([AutoDownloadSync]) usoudila, že si uživatel nic nevybral.
     */
    suspend fun loadedAutoDownload(): AutoDownloadSettings

    /**
     * Kde stojí automatické stahování vůči serveru Telegramu. `null` = ještě nikdy: čerstvá
     * instalace, nebo aktualizace z verze, která nesynchronizovala. Čeká na načtení souboru.
     */
    suspend fun autoDownloadSync(): SettingsSync?

    /** Zapíše [autoDownloadSync]. Na rozdíl od ostatních zápisů čeká, až je zapsáno. */
    suspend fun setAutoDownloadSync(state: SettingsSync)

    /** Záložky zpráv všech účtů; filtruje a spravuje je [eu.tintera.plume.core.bookmarks.MessageBookmarks]. */
    val messageBookmarks: StateFlow<List<MessageBookmark>>

    /** Uloží záložky. Neblokuje. */
    fun setMessageBookmarks(bookmarks: List<MessageBookmark>)

    /** Odložené chaty všech účtů; filtruje a spravuje je [eu.tintera.plume.core.snooze.ChatSnoozes]. */
    val chatSnoozes: StateFlow<List<ChatSnooze>>

    /** Uloží odložené chaty. Neblokuje. */
    fun setChatSnoozes(snoozes: List<ChatSnooze>)

    /**
     * Hotové přepisy hlasovek a koleček z modelu v telefonu, všech účtů; filtruje a spravuje je
     * [eu.tintera.plume.features.conversation.LocalTranscripts].
     */
    val storedTranscripts: StateFlow<List<StoredTranscript>>

    /** Uloží přepisy. Neblokuje. */
    fun setStoredTranscripts(transcripts: List<StoredTranscript>)

    /** Tiché hodiny notifikací (Nastavení → Notifikace). Jen v zařízení, nesynchronizují se. */
    val quietHours: StateFlow<QuietHours>

    /** Uloží tiché hodiny; výchozí hodnoty se neukládají. Neblokuje. */
    fun setQuietHours(quietHours: QuietHours)

    /**
     * Experimentální funkce s jazykovým modelem v zařízení (shrnutí konverzace, viz `core/ai`).
     *
     * Výchozí je `false`: model má gigabajty a funkce je jen pro toho, kdo o ni stojí.
     */
    val onDeviceAiEnabled: StateFlow<Boolean>

    /** Zapne/vypne funkce s modelem v zařízení. Neblokuje, jako [recordRecentEmoji]. */
    fun setOnDeviceAiEnabled(enabled: Boolean)

    /**
     * Nabízet návrhy odpovědi od modelu v zařízení.
     *
     * Vlastní volba vedle [onDeviceAiEnabled]: shrnutí se otevírá vědomě, kdežto návrhy vyskakují
     * samy pod rozhovorem, takže o ně nemusí stát ani ten, kdo shrnutí používá rád.
     * Výchozí `false`.
     */
    val replySuggestionsEnabled: StateFlow<Boolean>

    /** Zapne/vypne návrhy odpovědi. Neblokuje, jako [recordRecentEmoji]. */
    fun setReplySuggestionsEnabled(enabled: Boolean)

    /**
     * Kdo model počítá (Gemini Nano / Gemma) a jeho varianta. Platí pro CELOU aplikaci — každá
     * funkce s modelem bere tuhle hodnotu. Výchozí Gemini Nano, stabilní plná.
     */
    val onDeviceAiVariant: StateFlow<ModelVariant>

    /** Uloží variantu modelu. Neblokuje, jako [recordRecentEmoji]. */
    fun setOnDeviceAiVariant(variant: ModelVariant)

    /**
     * Rychlost přehrávání hlasovek. Platí pro celou aplikaci, ne pro jednu hlasovku: kdo poslouchá
     * na 1,5×, chce tak poslouchat i tu další. Výchozí [VoiceSpeed.Normal].
     */
    val voiceSpeed: StateFlow<VoiceSpeed>

    /** Uloží rychlost přehrávání hlasovek. Neblokuje, jako [recordRecentEmoji]. */
    fun setVoiceSpeed(speed: VoiceSpeed)

    /**
     * Rychlost přehrávání videa v prohlížeči. Zvlášť od [voiceSpeed]: kdo poslouchá hlasovky
     * na 2×, nemusí tak chtít dívat i na video. Stupně jsou tytéž. Výchozí [VoiceSpeed.Normal].
     */
    val videoSpeed: StateFlow<VoiceSpeed>

    /** Uloží rychlost přehrávání videa. Neblokuje. */
    fun setVideoSpeed(speed: VoiceSpeed)

    /**
     * `versionCode` aktualizace, jejíž kartu uživatel v seznamu chatů zavřel. Tatáž verze se
     * znovu nenabízí, další vydaná ano — `versionCode` se zvedá při každém releasu.
     */
    val dismissedUpdateVersionCode: StateFlow<Int?>

    /** Zapamatuje zavřenou kartu aktualizace. Neblokuje, jako [recordRecentEmoji]. */
    fun dismissUpdate(versionCode: Int)

    /**
     * Boti, u jejichž mini aplikace už uživatel potvrdil, že jí posílá data. TDLib chce to
     * upozornění ukázat pro každého bota jednou (dokumentace `openWebApp`).
     */
    val confirmedWebAppBots: StateFlow<Set<Long>>

    fun confirmWebAppBot(botUserId: Long)

    /**
     * Nápovědy gest, které uživatel už viděl (nebo gesto použil sám). Každá se ukáže jednou
     * za instalaci — kdo gesto zná, nemá ho mít vysvětlované dokola.
     */
    val seenGestureHints: StateFlow<Set<GestureHint>>

    /** Zapamatuje nápovědu jako viděnou. Neblokuje. */
    fun markGestureHintSeen(hint: GestureHint)

    /**
     * Použité jednorázové klávesnice botů: chat → zpráva, ze které klávesnice pochází. Klávesnice
     * po použití v chatu ZŮSTÁVÁ (Bot API ji jen schová) a TDLib si nepamatuje, že byla použitá —
     * bez tohohle by se při každém otevření chatu ukázala zase rozbalená. Tak to drží i oficiální
     * klient (`hidekeyboard_<chat>` v nastavení).
     */
    val usedOneTimeKeyboards: StateFlow<Map<Long, Long>>

    fun markOneTimeKeyboardUsed(chatId: Long, messageId: Long)

    /**
     * Do jakého jazyka se překládají zprávy; `null` = jazyk aplikace (výchozí). Platí pro celou
     * aplikaci, ne pro jeden chat: kdo si jednou vybere angličtinu, čte v ní i další překlady.
     */
    val translationLanguage: StateFlow<String?>

    /** Uloží jazyk překladu, `null` ho vrátí na jazyk aplikace. Neblokuje. */
    fun setTranslationLanguage(code: String?)

    /**
     * Překládat zprávy v telefonu (`core/translation`) místo na serveru — text neodchází
     * z telefonu. Výchozí `false`: první překlad v telefonu chce stáhnout jazykové balíčky.
     */
    val translateOnDevice: StateFlow<Boolean>

    fun setTranslateOnDevice(onDevice: Boolean)

    /**
     * Automaticky odstraňovat sledovací parametry (UTM, click ID, YouTube si atd.) z odkazů
     * při odesílání, otevírání a kopírování. Výchozí `true`.
     */
    val stripTrackingParameters: StateFlow<Boolean>

    /** Zapne nebo vypne odstraňování sledovacích parametrů. Neblokuje. */
    fun setStripTrackingParameters(enabled: Boolean)

    /**
     * Jak dlouho držet stažená média, na která se nesáhlo (Nastavení → Úložiště). Výchozí
     * [KeepMedia.Forever] — aplikace bez výslovného přání nic nemaže.
     */
    val keepMedia: StateFlow<KeepMedia>

    /** Uloží dobu uchování médií. Neblokuje. */
    fun setKeepMedia(keep: KeepMedia)

    /**
     * Kdy naposledy proběhl automatický úklid médií (sekundy od epochy), `null` = nikdy.
     * Úklid podle [keepMedia] stačí jednou denně, ne při každém startu.
     */
    val lastStorageCleanup: StateFlow<Long?>

    /** Zapíše čas posledního úklidu. Neblokuje. */
    fun setLastStorageCleanup(epochSeconds: Long)

    /**
     * Velikost písma textu zpráv v sp (Nastavení → Vzhled), v rozsahu [MESSAGE_TEXT_SIZES].
     * Výchozí [DEFAULT_MESSAGE_TEXT_SIZE] — tatáž velikost jako `bodyLarge`.
     */
    val messageTextSize: StateFlow<Int>

    /** Uloží velikost písma zpráv; výchozí se neukládá. Neblokuje. */
    fun setMessageTextSize(size: Int)

    /**
     * Webové odkazy otevírat uvnitř aplikace (Custom Tabs / `SFSafariViewController`), ne
     * v prohlížeči. Výchozí `true`, jako v oficiálním klientovi; odkaz, který si vezme jiná
     * aplikace (YouTube), jde do ní v obou režimech — viz `UrlOpener`.
     */
    val openLinksInApp: StateFlow<Boolean>

    /** Zapne nebo vypne otevírání odkazů v aplikaci. Neblokuje. */
    fun setOpenLinksInApp(enabled: Boolean)

    /**
     * Dvojité ťuknutí na zprávu přidá (a znovu odebere) rychlou reakci. Výchozí `true`, jako
     * v oficiálním klientovi. Kterou reakci, je nastavení účtu na serveru (`QuickReaction`).
     */
    val doubleTapReaction: StateFlow<Boolean>

    /** Zapne nebo vypne rychlou reakci dvojitým ťuknutím. Neblokuje. */
    fun setDoubleTapReaction(enabled: Boolean)
}

/** Výchozí velikost písma zpráv (sp) — `bodyLarge`, jak ho zprávy měly odjakživa. */
const val DEFAULT_MESSAGE_TEXT_SIZE = 16

/** Nabízené velikosti písma zpráv (sp); výchozí stojí uprostřed. */
val MESSAGE_TEXT_SIZES = 12..20

/** Kolik nedávných emoji se drží. Zhruba pět řádků mřížky na telefonu. */
const val MAX_RECENT_EMOJIS = 40

/** Část výběru: emoji se vkládají do textu, samolepky se posílají. */
enum class ExpressionMode { Emoji, Stickers, Gifs }

/** Co nahrává tlačítko v prázdném poli. */
enum class RecordMode { Voice, VideoNote }

/**
 * [AppSettings] nad DataStore Preferences.
 *
 * Hodnoty se ukládají jako **název** výčtu, ne pořadí — přidání nové hodnoty doprostřed by
 * jinak tiše změnilo význam uložených dat. Neznámý název (hodnota z novější verze aplikace
 * po návratu ke starší) spadne na výchozí.
 *
 * **`dataStore.data` se odebírá JEDNOU a každý zápis počká, až ten odběr načte soubor**
 * ([preferences], [edit]). Obchází se tím chyba DataStore 1.2.x: nový odběr `data` čte úvodní
 * stav bez zámku, a když zrovna běží zápis, dostane STARÁ data s už zvýšenou verzí (zápis zvedá
 * verzi před zápisem souboru). Nová data se pak v odběru zahodí jako „už viděná"
 * (`dropWhile { version <= startState.version }`) a hodnota zůstane stará až do dalšího zápisu.
 * Opraveno v DataStore 1.3.0 (čtení bez zámku bere verzi z keše); do té doby se zápis do okna
 * úvodního čtení nesmí trefit — a s pěti odběry spuštěnými při vzniku a zápisem hned po něm se
 * pod zátěží trefoval.
 */
class DataStoreAppSettings(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : AppSettings {

    /**
     * Jediný odběr souboru; `null`, dokud se nenačte. Všechny hodnoty se z něj odvozují v paměti.
     *
     * Eagerly: čte se hned při startu aplikace, ne až při první obrazovce, která se zeptá.
     */
    private val preferences: StateFlow<Preferences?> = dataStore.data
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun <T> value(default: T, read: (Preferences) -> T): StateFlow<T> = preferences
        .map { it?.let(read) ?: default }
        .stateIn(scope, SharingStarted.Eagerly, default)

    /**
     * Zápis, až je soubor načtený (viz dokumentace třídy). Po prvním načtení odběr sleduje keš
     * DataStore, a ta změnu nepropásne.
     */
    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        preferences.first { it != null }
        dataStore.edit(transform)
    }

    override val expressionMode: StateFlow<ExpressionMode> = value(ExpressionMode.Emoji) { preferences ->
        ExpressionMode.entries.firstOrNull { it.name == preferences[EXPRESSION_MODE] } ?: ExpressionMode.Emoji
    }

    override suspend fun setExpressionMode(mode: ExpressionMode) {
        edit { it[EXPRESSION_MODE] = mode.name }
    }

    override val recordMode: StateFlow<RecordMode> = value(RecordMode.Voice) { preferences ->
        RecordMode.entries.firstOrNull { it.name == preferences[RECORD_MODE] } ?: RecordMode.Voice
    }

    override suspend fun setRecordMode(mode: RecordMode) {
        edit { it[RECORD_MODE] = mode.name }
    }

    override val recentEmojis: StateFlow<List<String>> = value(emptyList()) { preferences ->
        preferences[RECENT_EMOJIS]?.decodeEmojis().orEmpty()
    }

    override fun recordRecentEmoji(emoji: String) {
        scope.launch {
            // Čte a zapisuje se uvnitř `edit`: dvě rychlá ťuknutí za sebou se nepřepíšou navzájem.
            edit { preferences ->
                val current = preferences[RECENT_EMOJIS]?.decodeEmojis().orEmpty()
                val updated = (listOf(emoji) + current.filterNot { it == emoji }).take(MAX_RECENT_EMOJIS)
                preferences[RECENT_EMOJIS] = updated.joinToString(EMOJI_SEPARATOR)
            }
        }
    }

    override val emojiSkinTones: StateFlow<Map<String, String>> = value(emptyMap()) { preferences ->
        preferences[EMOJI_SKIN_TONES]?.decodeTones().orEmpty()
    }

    override fun setEmojiSkinTone(base: String, variant: String?) {
        scope.launch {
            edit { preferences ->
                val current = preferences[EMOJI_SKIN_TONES]?.decodeTones().orEmpty()
                val updated = if (variant == null || variant == base) current - base else current + (base to variant)
                preferences[EMOJI_SKIN_TONES] = updated.entries.joinToString(TONE_ENTRY_SEPARATOR) { (k, v) ->
                    k + TONE_PAIR_SEPARATOR + v
                }
            }
        }
    }

    override val themeMode: StateFlow<ThemeMode?> = value(null) { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[THEME_MODE] }
    }

    override fun setThemeMode(mode: ThemeMode?) {
        scope.launch {
            edit { preferences ->
                if (mode == null) preferences.remove(THEME_MODE) else preferences[THEME_MODE] = mode.name
            }
        }
    }

    override val dynamicColor: StateFlow<Boolean?> = value(null) { preferences ->
        preferences[DYNAMIC_COLOR]
    }

    override fun setDynamicColor(enabled: Boolean) {
        scope.launch {
            edit { preferences -> preferences[DYNAMIC_COLOR] = enabled }
        }
    }

    override val themeColor: StateFlow<ThemeColor?> = value(null) { preferences ->
        ThemeColor.entries.firstOrNull { it.name == preferences[THEME_COLOR] }
    }

    override fun setThemeColor(color: ThemeColor) {
        scope.launch {
            edit { preferences -> preferences[THEME_COLOR] = color.name }
        }
    }

    override val separateUnreadChats: StateFlow<Boolean> = value(false) { preferences ->
        preferences[SEPARATE_UNREAD_CHATS] ?: false
    }

    override fun setSeparateUnreadChats(separate: Boolean) {
        scope.launch {
            edit { preferences -> preferences[SEPARATE_UNREAD_CHATS] = separate }
        }
    }

    override val savedMessagesAsChats: StateFlow<Boolean> = value(false) { preferences ->
        preferences[SAVED_MESSAGES_AS_CHATS] ?: false
    }

    override fun setSavedMessagesAsChats(asChats: Boolean) {
        scope.launch {
            edit { preferences -> preferences[SAVED_MESSAGES_AS_CHATS] = asChats }
        }
    }

    override val chatSwipeActions: StateFlow<ChatSwipeActions> = value(ChatSwipeActions()) { preferences ->
        val default = ChatSwipeActions()
        ChatSwipeActions(
            right = ChatSwipeAction.entries.firstOrNull { it.name == preferences[CHAT_SWIPE_RIGHT] } ?: default.right,
            left = ChatSwipeAction.entries.firstOrNull { it.name == preferences[CHAT_SWIPE_LEFT] } ?: default.left,
        )
    }

    override fun setChatSwipeActions(actions: ChatSwipeActions) {
        val default = ChatSwipeActions()
        scope.launch {
            edit { preferences ->
                if (actions.right == default.right) preferences.remove(CHAT_SWIPE_RIGHT) else preferences[CHAT_SWIPE_RIGHT] = actions.right.name
                if (actions.left == default.left) preferences.remove(CHAT_SWIPE_LEFT) else preferences[CHAT_SWIPE_LEFT] = actions.left.name
            }
        }
    }

    override val autoDownload: StateFlow<AutoDownloadSettings> = value(AutoDownloadSettings(), ::readAutoDownload)

    private fun readAutoDownload(preferences: Preferences): AutoDownloadSettings {
        val default = AutoDownloadSettings()
        fun level(key: Preferences.Key<String>, fallback: AutoDownloadLevel) =
            AutoDownloadLevel.entries.firstOrNull { it.name == preferences[key] } ?: fallback
        return AutoDownloadSettings(
            wifi = level(AUTO_DOWNLOAD_WIFI, default.wifi),
            mobile = level(AUTO_DOWNLOAD_MOBILE, default.mobile),
            roaming = level(AUTO_DOWNLOAD_ROAMING, default.roaming),
        )
    }

    override suspend fun loadedAutoDownload(): AutoDownloadSettings =
        readAutoDownload(preferences.filterNotNull().first())

    override suspend fun autoDownloadSync(): SettingsSync? {
        val stored = preferences.filterNotNull().first()[AUTO_DOWNLOAD_SYNC] ?: return null
        // Neznámý název z novější verze: stáhnout ze serveru je bezpečné, nic se neztratí.
        return SettingsSync.entries.firstOrNull { it.name == stored } ?: SettingsSync.Pull
    }

    override suspend fun setAutoDownloadSync(state: SettingsSync) {
        edit { preferences -> preferences[AUTO_DOWNLOAD_SYNC] = state.name }
    }

    override fun setAutoDownload(settings: AutoDownloadSettings) {
        val default = AutoDownloadSettings()
        scope.launch {
            edit { preferences ->
                fun put(key: Preferences.Key<String>, level: AutoDownloadLevel, fallback: AutoDownloadLevel) {
                    if (level == fallback) preferences.remove(key) else preferences[key] = level.name
                }
                put(AUTO_DOWNLOAD_WIFI, settings.wifi, default.wifi)
                put(AUTO_DOWNLOAD_MOBILE, settings.mobile, default.mobile)
                put(AUTO_DOWNLOAD_ROAMING, settings.roaming, default.roaming)
            }
        }
    }

    override val messageBookmarks: StateFlow<List<MessageBookmark>> = value(emptyList()) { preferences ->
        // Poškozený nebo cizí zápis nesmí shodit aplikaci; záložky se pak prostě nenačtou.
        preferences[MESSAGE_BOOKMARKS]?.let { stored ->
            runCatching { storedListJson.decodeFromString<List<MessageBookmark>>(stored) }.getOrNull()
        }.orEmpty()
    }

    override fun setMessageBookmarks(bookmarks: List<MessageBookmark>) {
        scope.launch {
            edit { preferences ->
                if (bookmarks.isEmpty()) preferences.remove(MESSAGE_BOOKMARKS) else preferences[MESSAGE_BOOKMARKS] = storedListJson.encodeToString(bookmarks)
            }
        }
    }

    override val storedTranscripts: StateFlow<List<StoredTranscript>> = value(emptyList()) { preferences ->
        preferences[STORED_TRANSCRIPTS]?.let { stored ->
            runCatching { storedListJson.decodeFromString<List<StoredTranscript>>(stored) }.getOrNull()
        }.orEmpty()
    }

    override fun setStoredTranscripts(transcripts: List<StoredTranscript>) {
        scope.launch {
            edit { preferences ->
                if (transcripts.isEmpty()) {
                    preferences.remove(STORED_TRANSCRIPTS)
                } else {
                    preferences[STORED_TRANSCRIPTS] = storedListJson.encodeToString(transcripts)
                }
            }
        }
    }

    override val chatSnoozes: StateFlow<List<ChatSnooze>> = value(emptyList()) { preferences ->
        preferences[CHAT_SNOOZES]?.let { stored ->
            runCatching { storedListJson.decodeFromString<List<ChatSnooze>>(stored) }.getOrNull()
        }.orEmpty()
    }

    override fun setChatSnoozes(snoozes: List<ChatSnooze>) {
        scope.launch {
            edit { preferences ->
                if (snoozes.isEmpty()) preferences.remove(CHAT_SNOOZES) else preferences[CHAT_SNOOZES] = storedListJson.encodeToString(snoozes)
            }
        }
    }

    override val quietHours: StateFlow<QuietHours> = value(QuietHours()) { preferences ->
        val default = QuietHours()
        // Čas mimo den (poškozený nebo cizí zápis) spadne na výchozí, jinak by úsek nedával smysl.
        fun minute(key: Preferences.Key<Int>, fallback: Int) =
            preferences[key]?.takeIf { it in 0 until MINUTES_PER_DAY } ?: fallback
        QuietHours(
            enabled = preferences[QUIET_HOURS_ENABLED] ?: default.enabled,
            start = minute(QUIET_HOURS_START, default.start),
            end = minute(QUIET_HOURS_END, default.end),
        )
    }

    override fun setQuietHours(quietHours: QuietHours) {
        val default = QuietHours()
        scope.launch {
            edit { preferences ->
                if (quietHours.enabled == default.enabled) preferences.remove(QUIET_HOURS_ENABLED) else preferences[QUIET_HOURS_ENABLED] = quietHours.enabled
                if (quietHours.start == default.start) preferences.remove(QUIET_HOURS_START) else preferences[QUIET_HOURS_START] = quietHours.start
                if (quietHours.end == default.end) preferences.remove(QUIET_HOURS_END) else preferences[QUIET_HOURS_END] = quietHours.end
            }
        }
    }

    override val onDeviceAiEnabled: StateFlow<Boolean> = value(false) { preferences ->
        preferences[ON_DEVICE_AI_ENABLED] ?: false
    }

    override fun setOnDeviceAiEnabled(enabled: Boolean) {
        scope.launch {
            edit { preferences -> preferences[ON_DEVICE_AI_ENABLED] = enabled }
        }
    }

    override val replySuggestionsEnabled: StateFlow<Boolean> = value(false) { preferences ->
        preferences[REPLY_SUGGESTIONS_ENABLED] ?: false
    }

    override fun setReplySuggestionsEnabled(enabled: Boolean) {
        scope.launch {
            edit { preferences -> preferences[REPLY_SUGGESTIONS_ENABLED] = enabled }
        }
    }

    /*
     * Poskytovatel a model Gemmy se ukládají NÁZVEM (výčty, viz „Nastavení aplikace").
     * Chybějící poskytovatel je Gemini Nano: tak se aplikace chovala před tím, než šlo vybírat,
     * a kdo si Nano zapnul, nemá po aktualizaci najednou stahovat gigabajty.
     */
    override val onDeviceAiVariant: StateFlow<ModelVariant> = value(ModelVariant()) { preferences ->
        ModelVariant(
            provider = AiProvider.entries.firstOrNull { it.name == preferences[ON_DEVICE_AI_PROVIDER] }
                ?: AiProvider.GeminiNano,
            preview = preferences[ON_DEVICE_AI_PREVIEW] ?: false,
            fast = preferences[ON_DEVICE_AI_FAST] ?: false,
            gemma = GemmaModel.entries.firstOrNull { it.name == preferences[ON_DEVICE_AI_GEMMA] }
                ?: GemmaModel.E2B,
        )
    }

    override fun setOnDeviceAiVariant(variant: ModelVariant) {
        scope.launch {
            edit { preferences ->
                preferences[ON_DEVICE_AI_PROVIDER] = variant.provider.name
                preferences[ON_DEVICE_AI_PREVIEW] = variant.preview
                preferences[ON_DEVICE_AI_FAST] = variant.fast
                preferences[ON_DEVICE_AI_GEMMA] = variant.gemma.name
            }
        }
    }

    override val voiceSpeed: StateFlow<VoiceSpeed> = value(VoiceSpeed.Normal) { preferences ->
        VoiceSpeed.entries.firstOrNull { it.name == preferences[VOICE_SPEED] } ?: VoiceSpeed.Normal
    }

    override fun setVoiceSpeed(speed: VoiceSpeed) {
        scope.launch {
            edit { preferences -> preferences[VOICE_SPEED] = speed.name }
        }
    }

    override val videoSpeed: StateFlow<VoiceSpeed> = value(VoiceSpeed.Normal) { preferences ->
        VoiceSpeed.entries.firstOrNull { it.name == preferences[VIDEO_SPEED] } ?: VoiceSpeed.Normal
    }

    override fun setVideoSpeed(speed: VoiceSpeed) {
        scope.launch {
            edit { preferences -> preferences[VIDEO_SPEED] = speed.name }
        }
    }

    override val dismissedUpdateVersionCode: StateFlow<Int?> = value(null) { preferences ->
        preferences[DISMISSED_UPDATE_VERSION_CODE]
    }

    override val confirmedWebAppBots: StateFlow<Set<Long>> = value(emptySet()) { preferences ->
        preferences[CONFIRMED_WEB_APP_BOTS]?.split(' ')?.mapNotNullTo(HashSet()) { it.toLongOrNull() }.orEmpty()
    }

    override fun confirmWebAppBot(botUserId: Long) {
        scope.launch {
            edit { preferences ->
                val current = preferences[CONFIRMED_WEB_APP_BOTS]?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
                preferences[CONFIRMED_WEB_APP_BOTS] = (current + botUserId.toString()).distinct().joinToString(" ")
            }
        }
    }

    override val seenGestureHints: StateFlow<Set<GestureHint>> = value(emptySet()) { preferences ->
        preferences[SEEN_GESTURE_HINTS].decodeHints()
    }

    override fun markGestureHintSeen(hint: GestureHint) {
        if (hint in seenGestureHints.value) return
        scope.launch {
            edit { preferences ->
                preferences[SEEN_GESTURE_HINTS] = (preferences[SEEN_GESTURE_HINTS].decodeHints() + hint)
                    .joinToString(" ") { it.name }
            }
        }
    }

    /** Názvy oddělené mezerou; neznámý název (novější verze aplikace) se zahodí. */
    private fun String?.decodeHints(): Set<GestureHint> =
        orEmpty().split(' ').mapNotNullTo(LinkedHashSet()) { name -> GestureHint.entries.firstOrNull { it.name == name } }

    override val usedOneTimeKeyboards: StateFlow<Map<Long, Long>> = value(emptyMap()) { preferences ->
        preferences[USED_ONE_TIME_KEYBOARDS]?.decodeKeyboards().orEmpty()
    }

    override fun markOneTimeKeyboardUsed(chatId: Long, messageId: Long) {
        scope.launch {
            edit { preferences ->
                // Nejnovější na konec; nejstarší chaty vypadnou, aby záznam nerostl donekonečna.
                val current = preferences[USED_ONE_TIME_KEYBOARDS]?.decodeKeyboards().orEmpty() - chatId
                preferences[USED_ONE_TIME_KEYBOARDS] = (current + (chatId to messageId)).entries
                    .toList()
                    .takeLast(MAX_USED_KEYBOARDS)
                    .joinToString(KEYBOARD_ENTRY_SEPARATOR) { "${it.key}$TONE_PAIR_SEPARATOR${it.value}" }
            }
        }
    }

    /** `chat:zpráva` oddělené mezerou, v pořadí od nejstaršího. */
    private fun String.decodeKeyboards(): Map<Long, Long> =
        split(KEYBOARD_ENTRY_SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(TONE_PAIR_SEPARATOR, limit = 2)
            val chat = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val message = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            chat to message
        }.toMap(LinkedHashMap())

    override fun dismissUpdate(versionCode: Int) {
        scope.launch {
            edit { preferences -> preferences[DISMISSED_UPDATE_VERSION_CODE] = versionCode }
        }
    }

    override val translationLanguage: StateFlow<String?> = value(null) { preferences ->
        preferences[TRANSLATION_LANGUAGE]
    }

    override fun setTranslationLanguage(code: String?) {
        scope.launch {
            edit { preferences ->
                if (code == null) preferences.remove(TRANSLATION_LANGUAGE) else preferences[TRANSLATION_LANGUAGE] = code
            }
        }
    }

    override val translateOnDevice: StateFlow<Boolean> = value(false) { preferences ->
        preferences[TRANSLATE_ON_DEVICE] ?: false
    }

    override fun setTranslateOnDevice(onDevice: Boolean) {
        scope.launch {
            edit { preferences -> preferences[TRANSLATE_ON_DEVICE] = onDevice }
        }
    }

    override val stripTrackingParameters: StateFlow<Boolean> = value(true) { preferences ->
        preferences[STRIP_TRACKING_PARAMETERS] ?: true
    }

    override fun setStripTrackingParameters(enabled: Boolean) {
        scope.launch {
            edit { preferences -> preferences[STRIP_TRACKING_PARAMETERS] = enabled }
        }
    }

    override val keepMedia: StateFlow<KeepMedia> = value(KeepMedia.Forever) { preferences ->
        KeepMedia.entries.firstOrNull { it.name == preferences[KEEP_MEDIA] } ?: KeepMedia.Forever
    }

    override fun setKeepMedia(keep: KeepMedia) {
        scope.launch {
            edit { preferences -> preferences[KEEP_MEDIA] = keep.name }
        }
    }

    override val lastStorageCleanup: StateFlow<Long?> = value(null) { preferences ->
        preferences[LAST_STORAGE_CLEANUP]
    }

    override fun setLastStorageCleanup(epochSeconds: Long) {
        scope.launch {
            edit { preferences -> preferences[LAST_STORAGE_CLEANUP] = epochSeconds }
        }
    }

    override val messageTextSize: StateFlow<Int> = value(DEFAULT_MESSAGE_TEXT_SIZE) { preferences ->
        // Hodnota mimo rozsah (z novější verze s jiným rozsahem) se přitáhne k nejbližší nabízené.
        preferences[MESSAGE_TEXT_SIZE]?.coerceIn(MESSAGE_TEXT_SIZES) ?: DEFAULT_MESSAGE_TEXT_SIZE
    }

    override fun setMessageTextSize(size: Int) {
        val clamped = size.coerceIn(MESSAGE_TEXT_SIZES)
        scope.launch {
            edit { preferences ->
                // Výchozí se neukládá, jako u motivu: výchozí velikost tak jde změnit bez migrace.
                if (clamped == DEFAULT_MESSAGE_TEXT_SIZE) {
                    preferences.remove(MESSAGE_TEXT_SIZE)
                } else {
                    preferences[MESSAGE_TEXT_SIZE] = clamped
                }
            }
        }
    }

    override val openLinksInApp: StateFlow<Boolean> = value(true) { preferences ->
        preferences[OPEN_LINKS_IN_APP] ?: true
    }

    override fun setOpenLinksInApp(enabled: Boolean) {
        scope.launch {
            edit { preferences -> preferences[OPEN_LINKS_IN_APP] = enabled }
        }
    }

    override val doubleTapReaction: StateFlow<Boolean> = value(true) { preferences ->
        preferences[DOUBLE_TAP_REACTION] ?: true
    }

    override fun setDoubleTapReaction(enabled: Boolean) {
        scope.launch {
            edit { preferences -> preferences[DOUBLE_TAP_REACTION] = enabled }
        }
    }

    /** `základ:varianta` oddělené středníkem. Ani dvojtečka, ani středník v žádném emoji nejsou. */
    private fun String.decodeTones(): Map<String, String> =
        split(TONE_ENTRY_SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(TONE_PAIR_SEPARATOR, limit = 2)
                if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
            }
            .toMap()

    /*
     * Seznam jako jeden řetězec oddělený mezerou. `stringSetPreferencesKey` by ztratil pořadí,
     * a na tom tady záleží. Mezera v žádném emoji není (ani ve složeninách přes ZWJ).
     */
    private fun String.decodeEmojis(): List<String> = split(EMOJI_SEPARATOR).filter { it.isNotEmpty() }

    private companion object {
        val EXPRESSION_MODE = stringPreferencesKey("expression_mode")
        val RECORD_MODE = stringPreferencesKey("record_mode")
        val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")
        val EMOJI_SKIN_TONES = stringPreferencesKey("emoji_skin_tones")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val SEPARATE_UNREAD_CHATS = booleanPreferencesKey("separate_unread_chats")
        val SAVED_MESSAGES_AS_CHATS = booleanPreferencesKey("saved_messages_as_chats")
        val TRANSLATE_ON_DEVICE = booleanPreferencesKey("translate_on_device")
        val CHAT_SWIPE_RIGHT = stringPreferencesKey("chat_swipe_right")
        val CHAT_SWIPE_LEFT = stringPreferencesKey("chat_swipe_left")
        val AUTO_DOWNLOAD_WIFI = stringPreferencesKey("auto_download_wifi")
        val AUTO_DOWNLOAD_MOBILE = stringPreferencesKey("auto_download_mobile")
        val AUTO_DOWNLOAD_ROAMING = stringPreferencesKey("auto_download_roaming")
        val AUTO_DOWNLOAD_SYNC = stringPreferencesKey("auto_download_sync")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val MESSAGE_BOOKMARKS = stringPreferencesKey("message_bookmarks")
        val CHAT_SNOOZES = stringPreferencesKey("chat_snoozes")
        val STORED_TRANSCRIPTS = stringPreferencesKey("stored_transcripts")
        val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
        val ON_DEVICE_AI_ENABLED = booleanPreferencesKey("on_device_ai_enabled")
        val REPLY_SUGGESTIONS_ENABLED = booleanPreferencesKey("reply_suggestions_enabled")
        val ON_DEVICE_AI_PREVIEW = booleanPreferencesKey("on_device_ai_preview")
        val ON_DEVICE_AI_FAST = booleanPreferencesKey("on_device_ai_fast")
        val ON_DEVICE_AI_PROVIDER = stringPreferencesKey("on_device_ai_provider")
        val ON_DEVICE_AI_GEMMA = stringPreferencesKey("on_device_ai_gemma")
        val VOICE_SPEED = stringPreferencesKey("voice_speed")
        val VIDEO_SPEED = stringPreferencesKey("video_speed")
        val DISMISSED_UPDATE_VERSION_CODE = intPreferencesKey("dismissed_update_version_code")
        val CONFIRMED_WEB_APP_BOTS = stringPreferencesKey("confirmed_web_app_bots")
        val TRANSLATION_LANGUAGE = stringPreferencesKey("translation_language")
        val USED_ONE_TIME_KEYBOARDS = stringPreferencesKey("used_one_time_keyboards")
        val SEEN_GESTURE_HINTS = stringPreferencesKey("seen_gesture_hints")
        val STRIP_TRACKING_PARAMETERS = booleanPreferencesKey("strip_tracking_parameters")
        val KEEP_MEDIA = stringPreferencesKey("keep_media")
        val LAST_STORAGE_CLEANUP = longPreferencesKey("last_storage_cleanup")
        val MESSAGE_TEXT_SIZE = intPreferencesKey("message_text_size")
        val OPEN_LINKS_IN_APP = booleanPreferencesKey("open_links_in_app")
        val DOUBLE_TAP_REACTION = booleanPreferencesKey("double_tap_reaction")
        const val KEYBOARD_ENTRY_SEPARATOR = " "
        const val MAX_USED_KEYBOARDS = 200
        const val EMOJI_SEPARATOR = " "
        const val TONE_ENTRY_SEPARATOR = ";"
        const val TONE_PAIR_SEPARATOR = ":"
    }
}

/**
 * DataStore nad souborem [path].
 *
 * Musí vzniknout **jednou za proces** (v Koinu jako `single`) — dvě instance nad týmž souborem
 * DataStore odmítne. Přípona `.preferences_pb` je povinná.
 */
fun createSettingsDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

/** Název souboru nastavení; adresář dodává platforma (`settingsFilePath`). */
const val SETTINGS_FILE_NAME = "app_settings.preferences_pb"

/** Seznamy uložené jako JSON (záložky, odložené chaty); neznámá pole z novější verze se přeskočí. */
private val storedListJson = Json { ignoreUnknownKeys = true }
