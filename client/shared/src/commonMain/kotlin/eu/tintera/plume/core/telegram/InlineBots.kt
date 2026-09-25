package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.OptionValueString
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Id inline bota, kterého jmenuje volba serveru ([option], třeba `animation_search_bot_username`
 * pro GIFy nebo `venue_search_bot_username` pro místa), s [fallback], kdyby ji server neposlal.
 *
 * Zjišťuje se jednou za klienta. Klíčem je instance klienta: po odhlášení je jiný a nový účet si
 * bota najde znovu (`searchPublicChat` ho teprve zavede do databáze).
 */
internal class InlineBot(
    private val holder: TelegramClientHolder,
    private val option: String,
    private val fallback: String,
) {
    private var bot: Pair<TdlClient, Long>? = null
    private val lock = Mutex()

    suspend fun userId(): Result<Long> = lock.withLock {
        val current = holder.client
        bot?.takeIf { it.first === current }?.let { return@withLock Result.success(it.second) }

        val username = current.getOption(name = option)
            .toResult()
            .getOrNull()
            .let { it as? OptionValueString }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: fallback
        current.searchPublicChat(username = username)
            .toResult()
            .mapCatching { chat ->
                (chat.type as? ChatTypePrivate)?.userId ?: error("@$username není bot.")
            }
            .onSuccess { bot = current to it }
    }
}
