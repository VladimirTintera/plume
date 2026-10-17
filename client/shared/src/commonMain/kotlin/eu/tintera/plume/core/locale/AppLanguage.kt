package eu.tintera.plume.core.locale

/**
 * Jazyk aplikace nezávisle na jazyku systému (per-app language).
 *
 * **Nastavení si drží systém, ne aplikace.** Android (od 13) ho ukládá u balíčku a aplikuje ještě
 * před startem aplikace, iOS ho má v Nastavení u aplikace. Kopie v `AppSettings` by se s ním
 * rozešla — změna v systémovém nastavení by o ní nevěděla. Výjimka je Android do 12, kde systém
 * jazyk pro jednu aplikaci nezná a drží ho aplikace sama (`LegacyAppLanguage`).
 */
expect class AppLanguage {

    /** Dá se jazyk vybrat přímo v aplikaci? Jinak zbývá jen [openSystemSettings]. */
    val canChooseInApp: Boolean

    /** Jde jazyk aplikace vůbec zvolit? (Všude, kde je Plume; do Androidu 12 ho drží aplikace sama.) */
    val isSupported: Boolean

    /** Zvolený jazyk (kód z [APP_LANGUAGES]), `null` = podle systému. */
    fun current(): String?

    /** Nastaví jazyk; `null` vrátí jazyk systému. Obrazovka se přeloží sama (změna konfigurace). */
    fun set(language: String?)

    /** Otevře nastavení jazyka aplikace v systému. */
    fun openSystemSettings()
}

/**
 * Jazyky, do kterých je aplikace přeložená — **vygenerované ze složek `values-*`** v composeResources
 * (úloha `generateAppLanguages`): angličtina, jazyky, které píšeme sami, a komunitní jazyky přeložené
 * aspoň z 80 %. Ručně se sem nic nepíše; Android `locales_config.xml` vzniká ze stejného seznamu.
 */
val APP_LANGUAGES: List<String> = GENERATED_APP_LANGUAGES

/**
 * Název jazyka v něm samém („Čeština" i v anglické aplikaci), aby se dalo vrátit z jazyka, kterému
 * člověk nerozumí. Bere ho systém — nový jazyk tak nepotřebuje vlastní řetězec v každém `strings.xml`.
 */
expect fun nativeLanguageName(code: String): String
