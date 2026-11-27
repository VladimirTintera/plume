package eu.tintera.plume.tv

import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import java.net.URI

/**
 * První odkaz na Telegram v textu zprávy ([entities]): `t.me/…`, `telegram.me/…`, `tg://…`
 * nebo zmínka `@jméno` (jako `https://t.me/jméno`). `null`, když žádný není.
 *
 * Odkazy na web se vynechávají schválně: televize je otevřít neumí (prohlížeč na ní obvykle
 * není), a kdyby OK na zprávě s webovým odkazem jen ohlásilo „neotevřu", vzalo by zprávě
 * skok na citovanou zprávu, který OK dělalo dřív.
 */
fun telegramLinkOf(entities: List<MessageEntity>): String? = entities.firstNotNullOfOrNull { entity ->
    when (val type = entity.type) {
        is MessageEntityType.Link -> type.url.takeIf(::isTelegramUrl)
        is MessageEntityType.Mention -> "https://t.me/${type.username}"
        else -> null
    }
}

/** Vede adresa do Telegramu (a ne na web)? */
internal fun isTelegramUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    if (uri.scheme.equals("tg", ignoreCase = true)) return true
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
    // Jméno jako subdoména (`jmeno.t.me`) vede taky na chat.
    return TELEGRAM_HOSTS.any { host == it || host.endsWith(".$it") }
}

private val TELEGRAM_HOSTS = listOf("t.me", "telegram.me", "telegram.dog")
