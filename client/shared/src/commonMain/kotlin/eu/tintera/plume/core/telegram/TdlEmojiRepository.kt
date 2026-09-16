package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.toResult

/** [EmojiRepository] nad TDLib `searchEmojis` (potřebuje `useFileDatabase = true`). */
class TdlEmojiRepository(
    private val holder: TelegramClientHolder,
) : EmojiRepository {

    private val logger = Logger.withTag("Emoji")

    override suspend fun searchEmojis(text: String, languageCodes: List<String>): List<String> =
        holder.client.searchEmojis(text = text, inputLanguageCodes = languageCodes.toTypedArray())
            .toResult()
            .map { result -> result.emojiKeywords.filterNotNull().map { it.emoji }.distinct() }
            .getOrElse { failure ->
                logger.w(failure) { "Emoji se nepodařilo vyhledat." }
                emptyList()
            }
}
