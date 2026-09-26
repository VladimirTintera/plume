package eu.tintera.plume.core.telegram

/**
 * Odkaz na Telegram, který aplikace otevře sama, místo aby ho poslala do prohlížeče
 * ([ChatRepository.resolveLink]). Co odkaz znamená, rozhoduje TDLib (`getInternalLinkType`) —
 * zná všechny podoby (`t.me/…`, `telegram.me/…`, `tg://…`), my jen vybíráme, co umíme ukázat.
 */
sealed interface InternalLink {
    /** Veřejný chat, uživatel nebo kanál (`t.me/jméno`), případně chat z pozvánky, kde už uživatel je. */
    data class Chat(val chatId: Long) : InternalLink

    /**
     * Odkaz na zprávu. [messageId] je `null`, když chat otevřít jde, ale zprávu samotnou server
     * nevydal (smazaná, soukromý chat bez přístupu ke starší historii) — otevře se aspoň chat.
     */
    data class Message(val chatId: Long, val messageId: Long?) : InternalLink

    /** Pozvánka do chatu, ve kterém uživatel není. Nejdřív náhled, přidává se až na potvrzení. */
    data class Invite(val link: String, val preview: InvitePreview) : InternalLink

    /** Sada samolepek (`t.me/addstickers/…`). */
    data class StickerSet(val setId: Long) : InternalLink

    /**
     * Spuštění bota s parametrem (`t.me/bot?start=…`). [autostart] = pustit hned, bez tlačítka
     * Start (`InternalLinkTypeBotStart.autostart`); jinak chat nabídne Start a parametr pošle
     * až ono.
     */
    data class BotStart(val chatId: Long, val parameter: String, val autostart: Boolean) : InternalLink
}

/** Co o chatu řekne pozvánka, než se do něj uživatel přidá (`checkChatInviteLink`). */
data class InvitePreview(
    val title: String,
    val description: String,
    val memberCount: Int,
    val isChannel: Boolean,
    val avatarFileId: Int?,
    /** Přidat se nejde hned — správce musí žádost schválit. */
    val createsJoinRequest: Boolean,
)

/** Výsledek přidání se přes pozvánku. */
sealed interface InviteJoinResult {
    data class Joined(val chatId: Long) : InviteJoinResult

    /** Pozvánka vyžaduje schválení; žádost odešla a čeká na správce. */
    data object Requested : InviteJoinResult
}
