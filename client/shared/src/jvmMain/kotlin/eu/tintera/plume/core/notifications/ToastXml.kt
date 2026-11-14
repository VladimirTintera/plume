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
 * - **Příchozí hovor** ([DesktopNotification.call]) má `scenario="incomingCall"`: toast zůstane
 *   na obrazovce, dokud se nerozhodne, a tlačítka Přijmout (`foreground` — Windows tím aplikaci
 *   dovolí vytáhnout okno) a Odmítnout (na pozadí). Zvoní aplikace sama, toast je tichý.
 */
internal fun toastXml(notification: DesktopNotification): String {
    val launch = escape(activationArguments(notification.chatId, notification.topicId, notification.account))
    return buildString {
        append("<toast launch=\"").append(launch).append('"')
        if (notification.call != null) append(" scenario=\"incomingCall\"")
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
        notification.call?.let { appendCallActions(notification, it) }
        if (notification.silent) append("<audio silent=\"true\"/>")
        append("</toast>")
    }
}

private fun StringBuilder.appendActions(notification: DesktopNotification, buttons: NotificationButtons) {
    val chat = "chat=${notification.chatId}" + accountArgument(notification.account)
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

private fun StringBuilder.appendCallActions(notification: DesktopNotification, call: CallButtons) {
    val arguments = "chat=${notification.chatId}&call=${call.callId}" + accountArgument(notification.account)
    append("<actions>")
    appendAction(call.answer, "action=$ACTION_ANSWER&$arguments", activation = "foreground")
    appendAction(call.decline, "action=$ACTION_DECLINE&$arguments")
    append("</actions>")
}

private fun StringBuilder.appendAction(label: String, arguments: String, inputId: String? = null, activation: String = "background") {
    append("<action content=\"").append(escape(label))
        .append("\" arguments=\"").append(escape(arguments))
        .append("\" activationType=\"").append(activation).append('"')
    inputId?.let { append(" hint-inputId=\"").append(it).append('"') }
    append("/>")
}

/**
 * Co toast vrátí po kliknutí na sebe nebo hlavičku; čte [parseActivation]. [account] je slot
 * účtu, kterému oznámení patří (primární účet ho nenese): kliknutí, odpověď i Přečteno musí
 * jít do účtu, ze kterého oznámení přišlo, ne do toho, který zrovna poslouchá.
 */
internal fun activationArguments(chatId: Long, topicId: Int?, account: String? = null): String =
    "chat=$chatId&topic=${topicId ?: 0}" + accountArgument(account)

private fun accountArgument(account: String?): String = account?.let { "&account=$it" }.orEmpty()

/**
 * Argumenty oznámení na macOS: všechno, co jeho tlačítka potřebují, v jednom řetězci. Toast nese
 * argumenty u každého tlačítka zvlášť, kdežto `UNNotificationCategory` jen identifikátor
 * tlačítka — ten se přidá až při odpovědi ([parseNotificationResponse]).
 */
internal fun notificationArguments(notification: DesktopNotification): String = buildString {
    append(activationArguments(notification.chatId, notification.topicId, notification.account))
    notification.buttons?.let { append("&msg=").append(it.messageId ?: 0).append("&quote=").append(it.quoteMessageId ?: 0) }
    notification.call?.let { append("&call=").append(it.callId) }
}

/**
 * Odpověď na oznámení macOS: [arguments] z [notificationArguments], [action] = identifikátor
 * tlačítka (`null` = klik na oznámení), [reply] = text napsaný do pole odpovědi.
 */
internal fun parseNotificationResponse(arguments: String, action: String?, reply: String?): ToastActivation? =
    parseActivation(if (action == null) arguments else "action=$action&$arguments", reply)

/**
 * Co uživatel s toastem udělal, z argumentů kliknutí a textu pole odpovědi. `null`, když
 * v argumentech chat není — nebo u odpovědi bez textu (Odeslat jde stisknout i s prázdným polem).
 */
internal fun parseActivation(arguments: String, reply: String? = null): ToastActivation? {
    val values = arguments.split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val chatId = values["chat"]?.toLongOrNull() ?: return null
    fun long(key: String) = values[key]?.toLongOrNull()?.takeIf { it != 0L }
    val topicId = values["topic"]?.toIntOrNull()?.takeIf { it != 0 }
    val account = values["account"]?.takeIf { it.isNotEmpty() }
    return when (values["action"]) {
        null -> ToastActivation.Open(chatId, topicId, account)
        ACTION_REPLY -> reply?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { ToastActivation.Reply(chatId, topicId, quoteMessageId = long("quote"), text = it, account = account) }
        ACTION_READ -> ToastActivation.MarkRead(chatId, messageId = long("msg"), account = account)
        ACTION_MUTE -> ToastActivation.Mute(chatId, account)
        ACTION_ANSWER -> values["call"]?.toIntOrNull()?.let { ToastActivation.AnswerCall(chatId, it, account) }
        ACTION_DECLINE -> values["call"]?.toIntOrNull()?.let { ToastActivation.DeclineCall(chatId, it, account) }
        else -> null
    }
}

/** Co se s toastem stalo. [Open] otevírá chat v okně, ostatní běží na pozadí. */
internal sealed interface ToastActivation {
    val chatId: Long

    /** Slot účtu oznámení; `null` = primární účet (i oznámení z doby před víc účty). */
    val account: String?

    data class Open(override val chatId: Long, val topicId: Int?, override val account: String? = null) : ToastActivation

    data class Reply(
        override val chatId: Long,
        val topicId: Int?,
        val quoteMessageId: Long?,
        val text: String,
        override val account: String? = null,
    ) : ToastActivation

    /** [messageId] = nejnovější zpráva oznámení; `null` = po nejnovější v chatu. */
    data class MarkRead(override val chatId: Long, val messageId: Long?, override val account: String? = null) : ToastActivation

    data class Mute(override val chatId: Long, override val account: String? = null) : ToastActivation

    /** Přijmout příchozí hovor: přijímá obrazovka hovoru (po dotazu na mikrofon), okno jde dopředu. */
    data class AnswerCall(override val chatId: Long, val callId: Int, override val account: String? = null) : ToastActivation

    data class DeclineCall(override val chatId: Long, val callId: Int, override val account: String? = null) : ToastActivation
}

/** Klíč pole odpovědi; tentýž hledá aktivátor v `PlumeToasts.cpp`. */
private const val REPLY_INPUT = "reply"
private const val ACTION_REPLY = "reply"
private const val ACTION_READ = "read"
private const val ACTION_MUTE = "mute"
private const val ACTION_ANSWER = "answer"
private const val ACTION_DECLINE = "decline"

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
