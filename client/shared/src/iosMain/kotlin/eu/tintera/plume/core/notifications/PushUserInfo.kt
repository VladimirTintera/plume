package eu.tintera.plume.core.notifications

import platform.Foundation.NSNumber

/**
 * Co notifikace nese o chatu, ze `userInfo`.
 *
 * Dvě cesty: klíče [NotificationCategories] vyplní rozšíření notifikací, když běží (push
 * s `mutable-content`). Když neběží, zůstane v `userInfo` jen to, co poslal Telegram —
 * `aps.alert.loc-key` a `custom` — a čte se přímo ([TelegramPush]). Aplikace musí zvládnout
 * obojí, protože jestli Telegram `mutable-content` posílá, není nikde popsané.
 */
internal data class PushInfo(val chatId: Long, val messageId: Long?, val isGroup: Boolean)

internal fun pushInfo(userInfo: Map<Any?, *>): PushInfo? {
    userInfo[NotificationCategories.KEY_CHAT_ID].asLong()?.let { chatId ->
        return PushInfo(
            chatId = chatId,
            // 0 = id chybí, ne zpráva s id 0.
            messageId = userInfo[NotificationCategories.KEY_MESSAGE_ID].asLong()?.takeIf { it > 0 },
            // Z JSONu pushe přijde `NSNumber` (1), z lokální notifikace i Kotlin `Boolean`.
            isGroup = userInfo[NotificationCategories.KEY_IS_GROUP].let { it == true || it.asLong() == 1L },
        )
    }
    val push = telegramPush(userInfo) ?: return null
    return PushInfo(chatId = push.chatId ?: return null, messageId = push.messageId, isGroup = push.isGroup)
}

internal fun pushChatId(userInfo: Map<Any?, *>): Long? = pushInfo(userInfo)?.chatId

/** Push od Telegramu tak, jak přišel: `loc-key` v `aps.alert`, zbytek v `custom`. */
internal fun telegramPush(userInfo: Map<Any?, *>): TelegramPush? {
    val alert = (userInfo["aps"] as? Map<*, *>)?.get("alert") as? Map<*, *>
    val locKey = alert?.get("loc-key") as? String ?: userInfo["loc_key"] as? String
    val custom = (userInfo["custom"] as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value }.orEmpty()
    return TelegramPush.parse(locKey, custom.mapValues { (_, value) -> if (value is NSNumber) value.longLongValue else value })
}

/** `userInfo` z JSONu pushe nese čísla jako `NSNumber`; z lokální notifikace i jako Kotlin číslo. */
internal fun Any?.asLong(): Long? = when (this) {
    is Long -> this
    is Number -> toLong()
    is NSNumber -> longLongValue
    is String -> toLongOrNull()
    else -> null
}
