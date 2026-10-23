package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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

    /**
     * Mini aplikace strážního bota, který schvaluje vstup do chatu ([JoinResult.GuardBot]).
     * Spuštění nemá; zavřít se musí, až přijde rozhodnutí ([ChatRepository.awaitGuardDecision]).
     */
    suspend fun guardUrl(queryId: Long, theme: WebAppTheme): Result<String>

    suspend fun close(launchId: Long)

    /**
     * Spuštění ([WebAppLaunch.launchId]), ze kterých mini aplikace odeslala zprávu
     * (`updateWebAppMessageSent`, bot odpověděl přes `answerWebAppQuery`). TDLib pak přikazuje
     * mini aplikaci zavřít.
     */
    val messagesSent: Flow<Long> get() = emptyFlow()

    /** Data z mini aplikace otevřené tlačítkem vlastní klávesnice (`web_app_data_send`). */
    suspend fun sendData(botUserId: Long, buttonText: String, data: String): Result<Unit>

    /** Vlastní metoda bota (`web_app_invoke_custom_method`); výsledek je JSON od bota. */
    suspend fun customRequest(botUserId: Long, method: String, parameters: String): Result<String>

    /** Jméno bota do hlavičky (`@jméno`, jinak jméno). */
    suspend fun botName(botUserId: Long): String?

    /** Uživatelské jméno bota bez `@` (pro odkaz `t.me/<bot>?startapp`); `null` = nemá. */
    suspend fun botUsername(botUserId: Long): String? = null

    /**
     * Smí bot uživateli už psát (`canBotSendMessages`)? `false` i tehdy, když se to nepodařilo
     * zjistit — pak se uživatel zeptá, a dovolení navíc nic nerozbije.
     */
    suspend fun canBotWrite(botUserId: Long): Boolean = false

    /** Dovolí botovi psát uživateli (`allowBotToSendMessages`) — mini aplikace o to požádala. */
    suspend fun allowBotToWrite(botUserId: Long): Result<Unit> = Result.failure(UnsupportedOperationException())

    /**
     * Zpráva, kterou bot připravil ke sdílení (`getPreparedInlineMessage`, `shareMessage`).
     * Chyba = zpráva vypršela nebo neexistuje.
     */
    suspend fun preparedMessage(botUserId: Long, preparedMessageId: String): Result<PreparedMessage> =
        Result.failure(UnsupportedOperationException())

    /** Tlačítko „vyber chat / lidi", které bot připravil (`getPreparedKeyboardButton`, `requestChat`). */
    suspend fun preparedButton(botUserId: Long, preparedButtonId: String): Result<PreparedButton> =
        Result.failure(UnsupportedOperationException())

    /**
     * Smí mini aplikace stáhnout soubor [url] jako [fileName] (`checkWebAppFileDownload`)?
     * Vrací jméno očištěné od nebezpečných znaků (`cleanFileName`), pod kterým se uloží.
     */
    suspend fun checkDownload(botUserId: Long, fileName: String, url: String): Result<String> =
        Result.failure(UnsupportedOperationException())
}

/**
 * Zpráva, kterou bot připravil a mini aplikace ji nabízí ke sdílení. Pošle se jako výsledek
 * inline dotazu [queryId] / [result] („přes @bot"), jen do chatů z [targets].
 */
data class PreparedMessage(val queryId: Long, val result: InlineResult, val targets: SwitchInlineTarget.Chosen)

/** Tlačítko připravené botem pro `requestChat`: koho vybrat, jako u tlačítek ve zprávě. */
sealed interface PreparedButton {
    data class Users(val request: BotUsersRequest) : PreparedButton
    data class Chat(val request: BotChatRequest) : PreparedButton

    /** Založení spravovaného bota — Plume ho neumí ani u tlačítka ve zprávě. */
    data object Unsupported : PreparedButton
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
