package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ThemeParameters
import dev.g000sha256.tdl.dto.WebAppOpenModeFullSize
import dev.g000sha256.tdl.dto.WebAppOpenParameters
import dev.g000sha256.tdl.toResult

/**
 * Mini aplikace nad TDLib.
 *
 * `applicationName` je platforma (`android` / `ios`): TDLib ji dá stránce jako
 * `tgWebAppPlatform` a mini aplikace se podle ní rozhodují (třeba o haptice nebo o tom, jak
 * kreslit tlačítka). Vlastní jméno by většina z nich nepoznala a spadla by do „unknown".
 */
class TdlWebAppRepository(private val holder: TelegramClientHolder) : WebAppRepository {

    private val logger = Logger.withTag("WebApp")

    private val client: TdlClient get() = holder.client

    override suspend fun open(chatId: Long, botUserId: Long, url: String, theme: WebAppTheme): Result<WebAppLaunch> =
        client.openWebApp(
            chatId = chatId,
            botUserId = botUserId,
            url = url,
            topicId = null,
            replyTo = null,
            parameters = parameters(theme),
        )
            .toResult()
            .map { WebAppLaunch(launchId = it.launchId, url = it.url.url) }
            .onFailure { logger.w(it) { "Mini aplikaci bota $botUserId nejde otevřít." } }

    override suspend fun keyboardUrl(botUserId: Long, url: String, theme: WebAppTheme): Result<String> =
        client.getWebAppUrl(botUserId = botUserId, url = url, parameters = parameters(theme))
            .toResult()
            .map { it.url }
            .onFailure { logger.w(it) { "Adresu mini aplikace bota $botUserId nejde získat." } }

    override suspend fun mainUrl(chatId: Long, botUserId: Long, startParameter: String, theme: WebAppTheme): Result<String> =
        client.getMainWebApp(chatId = chatId, botUserId = botUserId, startParameter = startParameter, parameters = parameters(theme))
            .toResult()
            .map { it.url.url }
            .onFailure { logger.w(it) { "Hlavní mini aplikaci bota $botUserId nejde otevřít." } }

    override suspend fun findWebApp(botUserId: Long, shortName: String): Result<NamedWebApp> =
        client.searchWebApp(botUserId = botUserId, webAppShortName = shortName)
            .toResult()
            .map { NamedWebApp(it.webApp.title, it.requestWriteAccess, it.skipConfirmation) }
            .onFailure { logger.w(it) { "Mini aplikaci $shortName bota $botUserId nejde najít." } }

    override suspend fun linkUrl(
        chatId: Long,
        botUserId: Long,
        shortName: String,
        startParameter: String,
        allowWriteAccess: Boolean,
        theme: WebAppTheme,
    ): Result<String> =
        client.getWebAppLinkUrl(
            chatId = chatId,
            botUserId = botUserId,
            webAppShortName = shortName,
            startParameter = startParameter,
            allowWriteAccess = allowWriteAccess,
            parameters = parameters(theme),
        )
            .toResult()
            .map { it.url }
            .onFailure { logger.w(it) { "Adresu mini aplikace $shortName bota $botUserId nejde získat." } }

    override suspend fun close(launchId: Long) {
        client.closeWebApp(webAppLaunchId = launchId).toResult()
            .onFailure { logger.w(it) { "Zavření mini aplikace $launchId TDLib nepřijal." } }
    }

    override suspend fun sendData(botUserId: Long, buttonText: String, data: String): Result<Unit> =
        client.sendWebAppData(botUserId = botUserId, buttonText = buttonText, data = data)
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Data z mini aplikace bota $botUserId nejdou odeslat." } }

    override suspend fun customRequest(botUserId: Long, method: String, parameters: String): Result<String> =
        client.sendWebAppCustomRequest(botUserId = botUserId, method = method, parameters = parameters)
            .toResult()
            .map { it.result }

    override suspend fun botName(botUserId: Long): String? =
        client.getUser(userId = botUserId).toResult().getOrNull()?.let { user ->
            listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        }

    private fun parameters(theme: WebAppTheme) = WebAppOpenParameters(
        theme = ThemeParameters(
            backgroundColor = theme.background,
            secondaryBackgroundColor = theme.secondaryBackground,
            headerBackgroundColor = theme.headerBackground,
            bottomBarBackgroundColor = theme.bottomBarBackground,
            sectionBackgroundColor = theme.sectionBackground,
            sectionSeparatorColor = theme.sectionSeparator,
            textColor = theme.text,
            accentTextColor = theme.accentText,
            sectionHeaderTextColor = theme.sectionHeaderText,
            subtitleTextColor = theme.subtitleText,
            destructiveTextColor = theme.destructiveText,
            hintColor = theme.hint,
            linkColor = theme.link,
            buttonColor = theme.button,
            buttonTextColor = theme.buttonText,
        ),
        applicationName = webAppPlatform,
        mode = WebAppOpenModeFullSize(),
    )
}

/** `tgWebAppPlatform` mini aplikace: `android` / `ios`. */
internal expect val webAppPlatform: String
