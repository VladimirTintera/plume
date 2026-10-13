package eu.tintera.plume.core.notifications

import java.io.File
import kotlin.time.Instant

/**
 * XML toastu Windows pro jednu notifikaci (schéma `ToastGeneric`).
 *
 * - **Tři řádky jako na macOS**: název chatu, odesílatel (jen ve skupině), text.
 * - **Profilovka chatu jako kulatá ikona** (`appLogoOverride`), fotka ze zprávy jako obrázek
 *   pod textem.
 * - **Hlavička chatu** (`header`) seskupí toasty jednoho chatu v Centru oznámení, jako
 *   `threadIdentifier` na macOS. Klik na ni otevře chat stejně jako klik na toast.
 * - **`displayTimestamp`** je čas zprávy, ne čas zobrazení — dávka po návratu spojení by jinak
 *   měla všechno „teď".
 * - **Tlačítka jako na Androidu** ([DesktopNotification.buttons]): pole „Odpovědět" s Odeslat,
 *   „Označit jako přečtené" a „Ztlumit na hodinu". Všechna `activationType="background"` —
 *   jdou na aktivátor jako klik, ale okno se kvůli nim nevytahuje.
 */
internal fun toastXml(notification: DesktopNotification): String {
    val launch = escape(activationArguments(notification.chatId, notification.topicId))
    return buildString {
        append("<toast launch=\"").append(launch).append('"')
        notification.timestamp?.let { append(" displayTimestamp=\"").append(Instant.fromEpochSeconds(it)).append('"') }
        append('>')
        append("<header id=\"").append(escape(notification.thread))
            .append("\" title=\"").append(escape(notification.title))
            .append("\" arguments=\"").append(launch).append("\"/>")
        append("<visual><binding template=\"ToastGeneric\">")
        append("<text hint-maxLines=\"1\">").append(escape(notification.title)).append("</text>")
        notification.subtitle?.takeIf { it.isNotBlank() }?.let {
            append("<text hint-maxLines=\"1\">").append(escape(it)).append("</text>")
        }
        append("<text>").append(escape(notification.body)).append("</text>")
        notification.avatarPath?.let(::fileUri)?.let {
            append("<image placement=\"appLogoOverride\" hint-crop=\"circle\" src=\"").append(escape(it)).append("\"/>")
        }
        notification.imagePath?.let(::fileUri)?.let {
            append("<image src=\"").append(escape(it)).append("\"/>")
        }
        append("</binding></visual>")
        notification.buttons?.let { appendActions(notification, it) }
        if (notification.silent) append("<audio silent=\"true\"/>")
        append("</toast>")
    }
}

private fun StringBuilder.appendActions(notification: DesktopNotification, buttons: NotificationButtons) {
    val chat = "chat=${notification.chatId}"
    append("<actions>")
    if (buttons.canReply) {
        append("<input id=\"").append(REPLY_INPUT).append("\" type=\"text\" placeHolderContent=\"")
            .append(escape(buttons.labels.reply)).append("\"/>")
        val reply = "action=$ACTION_REPLY&$chat&topic=${notification.topicId ?: 0}&quote=${buttons.quoteMessageId ?: 0}"
        // `hint-inputId` posadí tlačítko vedle pole.
        appendAction(buttons.labels.send, reply, inputId = REPLY_INPUT)
    }
    appendAction(buttons.labels.markRead, "action=$ACTION_READ&$chat&msg=${buttons.messageId ?: 0}")
    if (buttons.canMute) appendAction(buttons.labels.mute, "action=$ACTION_MUTE&$chat")
    append("</actions>")
}

private fun StringBuilder.appendAction(label: String, arguments: String, inputId: String? = null) {
    append("<action content=\"").append(escape(label))
        .append("\" arguments=\"").append(escape(arguments))
        .append("\" activationType=\"background\"")
    inputId?.let { append(" hint-inputId=\"").append(it).append('"') }
    append("/>")
}

/** Co toast vrátí po kliknutí na sebe nebo hlavičku; čte [parseActivation]. */
internal fun activationArguments(chatId: Long, topicId: Int?): String = "chat=$chatId&topic=${topicId ?: 0}"

/**
 * Co uživatel s toastem udělal, z argumentů kliknutí a textu pole odpovědi. `null`, když
 * v argumentech chat není — nebo u odpovědi bez textu (Odeslat jde stisknout i s prázdným polem).
 */
internal fun parseActivation(arguments: String, reply: String? = null): ToastActivation? {
    val values = arguments.split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val chatId = values["chat"]?.toLongOrNull() ?: return null
    fun long(key: String) = values[key]?.toLongOrNull()?.takeIf { it != 0L }
    val topicId = values["topic"]?.toIntOrNull()?.takeIf { it != 0 }
    return when (values["action"]) {
        null -> ToastActivation.Open(chatId, topicId)
        ACTION_REPLY -> reply?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { ToastActivation.Reply(chatId, topicId, quoteMessageId = long("quote"), text = it) }
        ACTION_READ -> ToastActivation.MarkRead(chatId, messageId = long("msg"))
        ACTION_MUTE -> ToastActivation.Mute(chatId)
        else -> null
    }
}

/** Co se s toastem stalo. [Open] otevírá chat v okně, ostatní běží na pozadí. */
internal sealed interface ToastActivation {
    val chatId: Long

    data class Open(override val chatId: Long, val topicId: Int?) : ToastActivation

    data class Reply(override val chatId: Long, val topicId: Int?, val quoteMessageId: Long?, val text: String) : ToastActivation

    /** [messageId] = nejnovější zpráva oznámení; `null` = po nejnovější v chatu. */
    data class MarkRead(override val chatId: Long, val messageId: Long?) : ToastActivation

    data class Mute(override val chatId: Long) : ToastActivation
}

/** Klíč pole odpovědi; tentýž hledá aktivátor v `PlumeToasts.cpp`. */
private const val REPLY_INPUT = "reply"
private const val ACTION_REPLY = "reply"
private const val ACTION_READ = "read"
private const val ACTION_MUTE = "mute"

/** `file:///C:/…` — Windows chce tři lomítka; `File.toURI` dává jen jedno. */
private fun fileUri(path: String): String? = File(path).takeIf { it.isFile }?.toPath()?.toUri()?.toString()

private fun escape(text: String): String = buildString(text.length) {
    for (char in text) {
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            // Řídicí znaky XML 1.0 nepřipouští a LoadXml by toast odmítl celý.
            in '\u0000'..'\u0008', '\u000B', '\u000C', in '\u000E'..'\u001F' -> Unit
            else -> append(char)
        }
    }
}
