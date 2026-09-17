package eu.tintera.plume.core.telegram

/**
 * Text chyby pro uživatele.
 *
 * **Hláška z TDLib se uživateli NIKDY nevypisuje.** Chodí ve dvou podobách a ani jedna není věta
 * pro člověka: buď konstanta od serveru (`PHONE_CODE_INVALID`), nebo anglická věta pro vývojáře
 * od samotné TDLib („Have no write access to the chat"). Navíc by tím text od TDLib prosakoval
 * přes hranici `core/telegram` až na obrazovku, takže by změna znění v novější verzi měnila,
 * co čte uživatel.
 *
 * Dřív to tak bylo: na dvaceti místech stálo `failure.message ?: "Česká věta"`. To vypadá jako
 * pojistka, ale byl to **mrtvý kód** — výjimka z TDLib má `message` vždycky vyplněné, takže elvis
 * nikdy nesepnul a uživatel viděl vždycky tu anglickou hlášku. Proto se pořadí obrací: základ je
 * věta od nás ([fallback]) a hláška z TDLib se použije jen tam, kde z ní umíme vyrobit něco
 * konkrétnějšího.
 *
 * Surová hláška patří do logu, ne na obrazovku — volající ji tam loguje i s výjimkou, takže
 * skončí i v kontextu hlášení pádů. **Tabulka níž se tím pádem dá rozšiřovat podle toho, co se
 * v logu opravdu objeví**; hádat dopředu nemá cenu, protože o tom, jestli chybu vyrobí server
 * nebo TDLib sama, rozhoduje pokaždé konkrétní volání.
 *
 * Porovnává se text, ne typ výjimky: tutéž hlášku někde zabalíme do vlastní výjimky (repository
 * vrací `Result`, ne typ z TDLib) a naše vlastní chyby se sem trefit nemají — ty spadnou na
 * [fallback].
 */
fun Throwable.userMessage(fallback: String): String {
    val raw = message ?: return fallback

    floodWaitSeconds(raw)?.let { return "Moc rychle po sobě. Zkus to ${waitLabel(it)}." }

    return when (raw) {
        // Věty od samotné TDLib (ověřené v `libtdjsonjava.so`). Vznikají z její vlastní kontroly
        // ještě před dotazem na server, takže na ně dojde dřív než na konstantu od serveru.
        "The maximum number of pinned chats exceeded" ->
            "Víc chatů připnout nejde. Vyšší limit má Telegram Premium."
        "Message can't be pinned" -> "Tuhle zprávu připnout nejde."
        "Message can't be edited" -> "Tuhle zprávu už upravit nejde."
        "Message can't be deleted" -> "Tuhle zprávu smazat nejde."
        "Message can't be deleted for everyone" -> "Smazat ji i ostatním už nejde."
        "Have no write access to the chat" -> "Do tohohle chatu psát nemůžeš."
        "Have not enough rights" -> "Na tohle nemáš v chatu právo."

        // Konstanty od serveru. TDLib je nepřekládá, posílá je dál, jak přišly.
        "PINNED_DIALOGS_TOO_MUCH" -> "Víc chatů připnout nejde. Vyšší limit má Telegram Premium."
        "CHAT_ADMIN_REQUIRED" -> "Na tohle potřebuješ být správce."
        "PHONE_NUMBER_INVALID" -> "Tohle číslo Telegram nezná. Zkontroluj předvolbu."
        "PHONE_CODE_INVALID" -> "Kód nesedí."
        "PHONE_CODE_EXPIRED" -> "Kódu vypršela platnost. Nech si poslat nový."
        "PASSWORD_HASH_INVALID" -> "Heslo nesedí."

        else -> fallback
    }
}

/**
 * Počet sekund z hlášky o překročení limitu. Server posílá `FLOOD_WAIT_23`, vlastní omezovač
 * TDLib `Too Many Requests: retry after 23` (obojí ověřeno v knihovně) — pro uživatele je to totéž.
 */
private fun floodWaitSeconds(raw: String): Int? = when {
    raw.startsWith(FLOOD_WAIT_PREFIX) -> raw.removePrefix(FLOOD_WAIT_PREFIX).toIntOrNull()
    raw.startsWith(TOO_MANY_REQUESTS_PREFIX) -> raw.removePrefix(TOO_MANY_REQUESTS_PREFIX).trim().toIntOrNull()
    else -> null
}

/** „za 23 s" u krátkého čekání, „za 4 min" u dlouhého — vteřiny po hodině nikdo neodpočítává. */
private fun waitLabel(seconds: Int): String =
    if (seconds < 60) "za $seconds s" else "za ${(seconds + 59) / 60} min"

private const val FLOOD_WAIT_PREFIX = "FLOOD_WAIT_"
private const val TOO_MANY_REQUESTS_PREFIX = "Too Many Requests: retry after "
