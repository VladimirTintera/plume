package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage

/**
 * Zpráva s médiem, na které míří čas v textu („1:23") zprávy [message].
 *
 * TDLib: médium je v téže zprávě, nebo ve zprávě, na kterou odpovídá — rozlišuje to
 * `hasTimestampedMedia`. Citovaná zpráva nemusí být v načtené historii, proto i [quotes]
 * (dotažené citace). `null`, když ji nemáme.
 */
internal fun timestampTarget(
    message: ChatMessage,
    messages: List<ChatMessage>,
    quotes: Map<Long, ChatMessage>,
): ChatMessage? = when {
    message.hasTimestampedMedia -> message
    else -> message.replyToMessageId?.let { id -> messages.firstOrNull { it.id == id } ?: quotes[id] }
}

/**
 * Adresa videa z náhledu odkazu, která ho otevře od [seconds]. Video v náhledu v aplikaci
 * nepřehráváme, takže čas „1:23" vede do prohlížeče nebo aplikace webu — a umí to jen weby, které
 * čas v adrese berou: YouTube parametrem `t`, Vimeo kotvou `#t`. Jinde zůstane adresa, jak je;
 * otevřít video od začátku je lepší než ťuknutí, které nedělá nic.
 */
internal fun videoLinkAt(url: String, seconds: Int): String {
    val host = url.substringAfter("://", url)
        .substringBefore('/').substringBefore('?').substringBefore('#')
        .lowercase().removePrefix("www.").removePrefix("m.")
    val time = "${seconds}s"
    return when {
        host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ->
            url.withQueryParameter("t", time)
        host == "vimeo.com" -> url.substringBefore('#') + "#t=$time"
        else -> url
    }
}

/** Přidá parametr do adresy, nebo přepíše stejnojmenný (odkaz už čas mít může). */
private fun String.withQueryParameter(name: String, value: String): String {
    val fragment = substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
    val withoutFragment = substringBefore('#')
    val base = withoutFragment.substringBefore('?')
    val kept = withoutFragment.substringAfter('?', "")
        .split('&')
        .filter { it.isNotEmpty() && it.substringBefore('=') != name }
    return base + "?" + (kept + "$name=$value").joinToString("&") + fragment
}
