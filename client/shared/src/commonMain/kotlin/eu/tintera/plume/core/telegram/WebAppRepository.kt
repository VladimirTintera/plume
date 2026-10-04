package eu.tintera.plume.core.telegram

/**
 * Mini aplikace botů (Telegram Web Apps). Adresu i podpis dat uživatele skládá TDLib; aplikace
 * stránku jen zobrazí ve WebView a zprostředkuje události (viz `features/webapp`).
 */
interface WebAppRepository {

    /**
     * Mini aplikace z tlačítka pod zprávou (`inlineKeyboardButtonTypeWebApp`) nebo z nabídky
     * bota. **Musí se k ní zavolat [close]**, jinak TDLib považuje spuštění za otevřené.
     */
    suspend fun open(chatId: Long, botUserId: Long, url: String, theme: WebAppTheme): Result<WebAppLaunch>

    /**
     * Hlavní mini aplikace bota (`getMainWebApp`): tlačítko v profilu bota a odkaz
     * `t.me/bot?startapp=…`. Spuštění nemá, takže se nezavírá. [chatId] je chat, ve kterém se
     * otevírá (konverzace s odkazem, profil bota); [startParameter] z odkazu, jinak prázdný.
     */
    suspend fun mainUrl(chatId: Long, botUserId: Long, startParameter: String, theme: WebAppTheme): Result<String>

    /**
     * Pojmenovaná mini aplikace bota z odkazu `t.me/bot/app` (`searchWebApp`). Chyba = bot ji
     * nemá (TDLib vrátí 404).
     */
    suspend fun findWebApp(botUserId: Long, shortName: String): Result<NamedWebApp>

    /**
     * Adresa pojmenované mini aplikace (`getWebAppLinkUrl`). Spuštění nemá, takže se nezavírá.
     * [allowWriteAccess] = uživatel v souhlasu dovolil botovi psát (jen s
     * [NamedWebApp.requestWriteAccess]).
     */
    suspend fun linkUrl(
        chatId: Long,
        botUserId: Long,
        shortName: String,
        startParameter: String,
        allowWriteAccess: Boolean,
        theme: WebAppTheme,
    ): Result<String>

    /** Mini aplikace z tlačítka vlastní klávesnice bota (`keyboardButtonTypeWebApp`); bez spuštění. */
    suspend fun keyboardUrl(botUserId: Long, url: String, theme: WebAppTheme): Result<String>

    suspend fun close(launchId: Long)

    /** Data z mini aplikace otevřené tlačítkem vlastní klávesnice (`web_app_data_send`). */
    suspend fun sendData(botUserId: Long, buttonText: String, data: String): Result<Unit>

    /** Vlastní metoda bota (`web_app_invoke_custom_method`); výsledek je JSON od bota. */
    suspend fun customRequest(botUserId: Long, method: String, parameters: String): Result<String>

    /** Jméno bota do hlavičky (`@jméno`, jinak jméno). */
    suspend fun botName(botUserId: Long): String?
}

/**
 * Pojmenovaná mini aplikace bota (`foundWebApp`). [skipConfirmation] = server dovolí otevřít ji
 * bez potvrzení; [requestWriteAccess] = chce, aby jí uživatel dovolil psát mu.
 */
data class NamedWebApp(val title: String, val requestWriteAccess: Boolean, val skipConfirmation: Boolean)

/** Spuštěná mini aplikace: [launchId] pro [WebAppRepository.close] a adresa do WebView. */
data class WebAppLaunch(val launchId: Long?, val url: String)

/**
 * Barvy aplikace pro mini aplikaci, RGB (bez průhlednosti). Dostane je TDLib do adresy
 * i stránka událostí `theme_changed` — mini aplikace pak vypadá jako zbytek Plume.
 */
data class WebAppTheme(
    val background: Int,
    val secondaryBackground: Int,
    val headerBackground: Int,
    val bottomBarBackground: Int,
    val sectionBackground: Int,
    val sectionSeparator: Int,
    val text: Int,
    val accentText: Int,
    val sectionHeaderText: Int,
    val subtitleText: Int,
    val destructiveText: Int,
    val hint: Int,
    val link: Int,
    val button: Int,
    val buttonText: Int,
)
