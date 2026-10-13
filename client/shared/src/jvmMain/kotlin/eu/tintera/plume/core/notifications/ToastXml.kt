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
        if (notification.silent) append("<audio silent=\"true\"/>")
        append("</toast>")
    }
}

/** Co toast vrátí po kliknutí; čte [parseActivation]. */
internal fun activationArguments(chatId: Long, topicId: Int?): String = "chat=$chatId&topic=${topicId ?: 0}"

/** Chat a téma z argumentů kliknutí, nebo `null`, když v nich chat není. */
internal fun parseActivation(arguments: String): Pair<Long, Int?>? {
    val values = arguments.split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val chatId = values["chat"]?.toLongOrNull() ?: return null
    return chatId to values["topic"]?.toIntOrNull()?.takeIf { it != 0 }
}

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
