package eu.tintera.plume.core.telegram

/**
 * Hledání emoji podle slov („srdce", „love").
 *
 * Samotný katalog emoji z TDLib nejde — ten je vygenerovaný z Unicode dat přímo v aplikaci.
 * TDLib dodává jen slovník klíčových slov v jazycích uživatele, který si stahuje sám.
 */
interface EmojiRepository {

    /** Emoji k zadanému textu (hledá se podle začátků slov). Prázdné, když nic nesedí. */
    suspend fun searchEmojis(text: String, languageCodes: List<String>): List<String>

    /**
     * Emoji, jejichž klíčové slovo je PŘESNĚ [word] („srdce" → ❤️). Na rozdíl od [searchEmojis]
     * nehledá podle začátků — návrhy při psaní by jinak vyskakovaly u každého druhého slova.
     */
    suspend fun keywordEmojis(word: String, languageCodes: List<String>): List<String>
}
