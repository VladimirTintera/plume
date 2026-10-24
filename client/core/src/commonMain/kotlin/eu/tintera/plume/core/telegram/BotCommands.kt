package eu.tintera.plume.core.telegram

/**
 * Příkaz bota, jak ho nabízí napovídání a seznam příkazů: `/start` s popisem od bota.
 *
 * [text] je to, co se pošle. V soukromém chatu s botem holé `/start`; ve skupině
 * `/start@jmeno_bota` — botů tam může být víc a příkaz bez jména by dostali všichni, nebo žádný
 * (podle toho, jak má který bot nastavené soukromí). Tak to dělají i oficiální klienti.
 */
data class BotCommandItem(val command: String, val description: String, val text: String)

/**
 * Tlačítko nabídky bota s mini aplikací ([ConversationRepository.botMenuApp]). [text] píše
 * bot („Otevřít obchod"), [url] jde do `openWebApp` jako adresa z tlačítka pod zprávou —
 * kromě odkazu `t.me/bot/app` ([isLink]), ten se otevírá jako odkaz.
 */
data class BotMenuApp(val text: String, val url: String, val isLink: Boolean = false)

/** Co se pošle za příkaz: ve skupině s jménem bota, jinak holý. */
internal fun botCommandText(command: String, botUsername: String?, inGroup: Boolean): String =
    if (inGroup && !botUsername.isNullOrBlank()) "/$command@$botUsername" else "/$command"

/**
 * Napovídání podle rozepsaného textu: příkazy, které začínají tím, co je za lomítkem.
 *
 * `null` = rozepsaný text příkaz není a napovídání se nemá ukázat vůbec — ne prázdný seznam,
 * ten znamená „píšeš příkaz, ale žádný nesedí" a panel se taky schová. Příkaz je jen to, co
 * začíná lomítkem a nemá mezeru ani zalomení: po mezeře už uživatel píše argument, nebo větu,
 * která náhodou začíná lomítkem.
 */
fun commandSuggestions(draft: String, commands: List<BotCommandItem>): List<BotCommandItem>? {
    if (commands.isEmpty() || !draft.startsWith("/")) return null
    val typed = draft.substring(1)
    if (typed.any { it.isWhitespace() }) return null
    // Ve skupině jde napsat i `/start@bot`; napovídá se podle části před zavináčem.
    val prefix = typed.substringBefore('@')
    return commands.filter { it.command.startsWith(prefix, ignoreCase = true) }
}
