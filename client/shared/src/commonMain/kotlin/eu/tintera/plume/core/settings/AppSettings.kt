package eu.tintera.plume.core.settings

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
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
     * Mají nepřečtené chaty v seznamu vlastní blok?
     *
     * Výchozí je `false` — nepřečtený chat zůstane tam, kde ho uživatel zná z pořadí od TDLib.
     * Vyčlenění je užitečné, jen když je chatů hodně; jinak seznam jen rozbíjí.
     */
    val separateUnreadChats: StateFlow<Boolean>

    /** Zapne/vypne vlastní blok nepřečtených. Neblokuje, jako [recordRecentEmoji]. */
    fun setSeparateUnreadChats(separate: Boolean)

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
     * Do jakého jazyka se překládají zprávy; `null` = jazyk aplikace (výchozí). Platí pro celou
     * aplikaci, ne pro jeden chat: kdo si jednou vybere angličtinu, čte v ní i další překlady.
     */
    val translationLanguage: StateFlow<String?>

    /** Uloží jazyk překladu, `null` ho vrátí na jazyk aplikace. Neblokuje. */
    fun setTranslationLanguage(code: String?)
}

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

    override val separateUnreadChats: StateFlow<Boolean> = value(false) { preferences ->
        preferences[SEPARATE_UNREAD_CHATS] ?: false
    }

    override fun setSeparateUnreadChats(separate: Boolean) {
        scope.launch {
            edit { preferences -> preferences[SEPARATE_UNREAD_CHATS] = separate }
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
        val SEPARATE_UNREAD_CHATS = booleanPreferencesKey("separate_unread_chats")
        val ON_DEVICE_AI_ENABLED = booleanPreferencesKey("on_device_ai_enabled")
        val REPLY_SUGGESTIONS_ENABLED = booleanPreferencesKey("reply_suggestions_enabled")
        val ON_DEVICE_AI_PREVIEW = booleanPreferencesKey("on_device_ai_preview")
        val ON_DEVICE_AI_FAST = booleanPreferencesKey("on_device_ai_fast")
        val ON_DEVICE_AI_PROVIDER = stringPreferencesKey("on_device_ai_provider")
        val ON_DEVICE_AI_GEMMA = stringPreferencesKey("on_device_ai_gemma")
        val VOICE_SPEED = stringPreferencesKey("voice_speed")
        val VIDEO_SPEED = stringPreferencesKey("video_speed")
        val DISMISSED_UPDATE_VERSION_CODE = intPreferencesKey("dismissed_update_version_code")
        val TRANSLATION_LANGUAGE = stringPreferencesKey("translation_language")
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
