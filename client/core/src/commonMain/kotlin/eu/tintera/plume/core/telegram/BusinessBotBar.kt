package eu.tintera.plume.core.telegram

/**
 * Firemní bot v chatu (Telegram Business): odpovídá za uživatele, dokud ho nepozastaví nebo
 * z chatu neodebere. [manageUrl] otevře správu bota (odkaz od serveru); [canReply] `false` =
 * bot v chatu odpovídat nemůže (např. vypršelo okno pro odpověď).
 */
data class BusinessBotBar(
    val botUserId: Long,
    val botName: String,
    val manageUrl: String,
    val isPaused: Boolean,
    val canReply: Boolean,
)
