package eu.tintera.plume.core.locale

/**
 * Jazyk aplikace nezávisle na jazyku systému (per-app language).
 *
 * **Nastavení si drží systém, ne aplikace.** Android (od 13) ho ukládá u balíčku a aplikuje ještě
 * před startem aplikace, iOS ho má v Nastavení u aplikace. Kopie v `AppSettings` by se s ním
 * rozešla — změna v systémovém nastavení by o ní nevěděla.
 */
expect class AppLanguage {

    /** Dá se jazyk vybrat přímo v aplikaci? Jinak zbývá jen [openSystemSettings]. */
    val canChooseInApp: Boolean

    /** Nabízí systém jazyk pro jednu aplikaci vůbec? Kde ne (Android do 12), volba nemá smysl. */
    val isSupported: Boolean

    /** Zvolený jazyk (kód z [APP_LANGUAGES]), `null` = podle systému. */
    fun current(): String?

    /** Nastaví jazyk; `null` vrátí jazyk systému. Obrazovka se přeloží sama (změna konfigurace). */
    fun set(language: String?)

    /** Otevře nastavení jazyka aplikace v systému. */
    fun openSystemSettings()
}

/** Jazyky, do kterých je aplikace přeložená (složky `values*` v composeResources). */
val APP_LANGUAGES = listOf("en", "cs")
