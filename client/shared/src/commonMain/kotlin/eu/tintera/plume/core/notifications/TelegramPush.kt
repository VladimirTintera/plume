package eu.tintera.plume.core.notifications

/**
 * Push, který na iOS posílá Telegram přímo přes APNs (zařízení registrované `registerDevice`
 * s `deviceTokenApplePush` — ten šifrování nemá, takže obsah je čitelný bez TDLib).
 *
 * Text notifikace skládá iOS sám z `aps.alert.loc-key` a `loc-args` podle `Localizable.strings`
 * aplikace; tady jde jen o to, KAM notifikace patří: `custom` nese id odesílatele / skupiny /
 * kanálu a id zprávy v serverovém číslování, které TDLib převádí po svém.
 *
 * **Totéž pravidlo má ve Swiftu rozšíření notifikací** (`NotificationService.swift`) — tam
 * Kotlin framework není. Změna jedné strany musí jít i do druhé.
 */
data class TelegramPush(
    val locKey: String,
    /** Id chatu v číslování TDLib, `null` u pushů bez chatu (přihlášení, kontakt se připojil…). */
    val chatId: Long?,
    /** Id zprávy v číslování TDLib, `null` když ho push nenese. */
    val messageId: Long?,
    /** Skupina (i supergroup) — tam odpověď cituje, viz [NotificationCategories.KEY_IS_GROUP]. */
    val isGroup: Boolean,
    /** Příspěvek kanálu: psát se tam nedá, odpověď se nenabízí. */
    val isChannel: Boolean,
    /** Zmínka nebo odpověď na mě — jde i ze ztlumeného chatu, takže se nenabízí ztlumení. */
    val isMention: Boolean,
) {
    /** Kategorie s akcemi; `null` = bez akcí (push, který není zprávou v chatu). */
    val category: String?
        get() = if (chatId == null || !isMessage) null
        else NotificationCategories.chatCategory(canReply = !isChannel, canMute = !isMention)

    /** Zpráva v chatu (text, média, reakce, připnutí) — ne přihlášení, hovor nebo služba. */
    private val isMessage: Boolean
        get() = MESSAGE_PREFIXES.any { locKey.startsWith(it) } || locKey == "MESSAGES"

    companion object {
        private val MESSAGE_PREFIXES = listOf("MESSAGE_", "CHAT_MESSAGE", "CHANNEL_MESSAGE", "REACT_", "CHAT_REACT_", "PINNED_")

        /**
         * Klíče, které se nemají zobrazit — Telegram jimi hlásí přečtení nebo smazání jinde
         * a obnovu odznaku. Aplikace je stejně uklidí při příštím otevření (přečtené chaty).
         */
        val SERVICE_KEYS = setOf("READ_HISTORY", "MESSAGE_DELETED", "READ_REACTION", "READ_STORIES", "STORY_DELETED", "MESSAGE_MUTED", "DC_UPDATE", "SESSION_REVOKE", "GEO_LIVE_PENDING")

        /**
         * Z `loc-key` a slovníku `custom`. Čísla v `custom` chodí jako JSON čísla i jako řetězce
         * (příklady v dokumentaci TDLib mají řetězce), proto se berou obojí.
         */
        fun parse(locKey: String?, custom: Map<String, Any?>): TelegramPush? {
            if (locKey.isNullOrEmpty()) return null
            val chatId = telegramChatId(
                userId = custom["from_id"].asLong(),
                basicGroupId = custom["chat_id"].asLong(),
                channelId = custom["channel_id"].asLong(),
            )
            return TelegramPush(
                locKey = locKey,
                chatId = chatId,
                messageId = custom["msg_id"].asLong()?.let(::tdlibMessageId),
                isGroup = locKey.startsWith("CHAT_"),
                isChannel = locKey.startsWith("CHANNEL_"),
                isMention = custom["mention"].asLong() == 1L || custom["mention"] == true,
            )
        }

        /**
         * Chat v číslování TDLib: uživatel kladně, základní skupina záporně, supergroup a kanál
         * `-100…` (−10¹² − id). Skupina a kanál mají přednost před `from_id` — ve skupině ho
         * push nenese, ale kdyby ano, byl by to autor, ne chat.
         */
        fun telegramChatId(userId: Long?, basicGroupId: Long?, channelId: Long?): Long? = when {
            channelId != null && channelId > 0 -> -CHANNEL_ID_OFFSET - channelId
            basicGroupId != null && basicGroupId > 0 -> -basicGroupId
            userId != null && userId > 0 -> userId
            else -> null
        }

        /** TDLib čísluje zprávy serverovým id posunutým o 20 bitů. */
        fun tdlibMessageId(serverMessageId: Long): Long? = serverMessageId.takeIf { it > 0 }?.shl(20)

        private const val CHANNEL_ID_OFFSET = 1_000_000_000_000L

        private fun Any?.asLong(): Long? = when (this) {
            is Number -> toLong()
            is String -> toLongOrNull()
            else -> null
        }
    }
}
