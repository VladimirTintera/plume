package eu.tintera.plume.core.settings

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
     * Výchozí motiv se schválně NEUKLÁDÁ jako konkrétní hodnota: liší se podle platformy
     * (Android → Material You, jinde systémový) a zapsaná hodnota by ten rozdíl zakonzervovala
     * i na zařízení, kde neplatí. Kdo kreslí, spadne na `defaultThemeMode()`.
     */
    val themeMode: StateFlow<ThemeMode?>

    /** Uloží motiv; `null` vrátí volbu na výchozí podle platformy. Neblokuje, jako [recordRecentEmoji]. */
    fun setThemeMode(mode: ThemeMode?)

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
     * `versionCode` aktualizace, jejíž kartu uživatel v seznamu chatů zavřel. Tatáž verze se
     * znovu nenabízí, další vydaná ano — `versionCode` se zvedá při každém releasu.
     */
    val dismissedUpdateVersionCode: StateFlow<Int?>

    /** Zapamatuje zavřenou kartu aktualizace. Neblokuje, jako [recordRecentEmoji]. */
    fun dismissUpdate(versionCode: Int)
}

/** Kolik nedávných emoji se drží. Zhruba pět řádků mřížky na telefonu. */
const val MAX_RECENT_EMOJIS = 40

/** Část výběru: emoji se vkládají do textu, samolepky se posílají. */
enum class ExpressionMode { Emoji, Stickers }

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

    override val separateUnreadChats: StateFlow<Boolean> = value(false) { preferences ->
        preferences[SEPARATE_UNREAD_CHATS] ?: false
    }

    override fun setSeparateUnreadChats(separate: Boolean) {
        scope.launch {
            edit { preferences -> preferences[SEPARATE_UNREAD_CHATS] = separate }
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
        val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")
        val EMOJI_SKIN_TONES = stringPreferencesKey("emoji_skin_tones")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SEPARATE_UNREAD_CHATS = booleanPreferencesKey("separate_unread_chats")
        val DISMISSED_UPDATE_VERSION_CODE = intPreferencesKey("dismissed_update_version_code")
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
