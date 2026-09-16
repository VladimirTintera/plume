package eu.tintera.plume.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
}

/** Část výběru: emoji se vkládají do textu, samolepky se posílají. */
enum class ExpressionMode { Emoji, Stickers }

/**
 * [AppSettings] nad DataStore Preferences.
 *
 * Hodnoty se ukládají jako **název** výčtu, ne pořadí — přidání nové hodnoty doprostřed by
 * jinak tiše změnilo význam uložených dat. Neznámý název (hodnota z novější verze aplikace
 * po návratu ke starší) spadne na výchozí.
 */
class DataStoreAppSettings(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : AppSettings {

    override val expressionMode: StateFlow<ExpressionMode> = dataStore.data
        .map { preferences ->
            ExpressionMode.entries.firstOrNull { it.name == preferences[EXPRESSION_MODE] } ?: ExpressionMode.Emoji
        }
        // Eagerly: čte se hned při startu aplikace, ne až při první obrazovce, která se zeptá.
        .stateIn(scope, SharingStarted.Eagerly, ExpressionMode.Emoji)

    override suspend fun setExpressionMode(mode: ExpressionMode) {
        dataStore.edit { it[EXPRESSION_MODE] = mode.name }
    }

    private companion object {
        val EXPRESSION_MODE = stringPreferencesKey("expression_mode")
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
