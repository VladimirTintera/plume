package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.OptionValueBoolean
import dev.g000sha256.tdl.toResult

/**
 * Volba „Zobrazovat citlivý obsah" (`ignore_sensitive_content_restrictions`).
 *
 * **Je to nastavení ÚČTU, ne aplikace.** TDLib ho zapíše na server (`account.setContentSettings`),
 * takže platí na všech zařízeních — a naopak volbu nastavenou v jiném klientovi aplikace
 * respektuje odjakživa (viz [hidesSensitive]).
 *
 * **Změnit ji smí jen účet, kterému to server dovolí** (`can_ignore_sensitive_content_restrictions`).
 * Kde ne, TDLib zápis odmítne, a přepínač se proto vůbec nenabízí — stejné pravidlo jako
 * u archivace chatů od neznámých.
 */
interface SensitiveContentSettings {

    /** Jestli se citlivý obsah ukazuje bez rozostření; `null` = změnit ho nejde, nenabízet. */
    suspend fun showsSensitiveContent(): Result<Boolean?>

    suspend fun setShowsSensitiveContent(show: Boolean): Result<Unit>
}

class TdlSensitiveContentSettings(private val holder: TelegramClientHolder) : SensitiveContentSettings {

    private val client: TdlClient get() = holder.client

    private val logger = Logger.withTag("SensitiveContent")

    override suspend fun showsSensitiveContent(): Result<Boolean?> = runCatching {
        if (!booleanOption(CAN_IGNORE_OPTION)) return@runCatching null
        booleanOption(SensitiveContent.IGNORE_OPTION)
    }

    override suspend fun setShowsSensitiveContent(show: Boolean): Result<Unit> =
        client.setOption(name = SensitiveContent.IGNORE_OPTION, value = OptionValueBoolean(value = show))
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Volbu citlivého obsahu se nepodařilo změnit" } }

    /** Nenastavená volba přijde jako `OptionValueEmpty` — to je `false`. */
    private suspend fun booleanOption(name: String): Boolean =
        (client.getOption(name = name).toResult().getOrThrow() as? OptionValueBoolean)?.value == true

    private companion object {
        const val CAN_IGNORE_OPTION = "can_ignore_sensitive_content_restrictions"
    }
}
