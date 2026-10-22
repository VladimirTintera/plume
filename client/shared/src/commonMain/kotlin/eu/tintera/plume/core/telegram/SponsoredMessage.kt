package eu.tintera.plume.core.telegram

/**
 * Sponzorovaná zpráva v kanálu nebo v chatu s botem.
 *
 * **Zobrazení je povinné.** [Telegram API ToS](https://core.telegram.org/api/terms) vyžaduje,
 * aby klient sponzorované zprávy zobrazoval a nezasahoval do nich — tedy ani neskrýval, ani
 * neoznačoval jako reklamu nad rámec toho, co dává Telegram.
 *
 * Zobrazení i kliknutí se musí **hlásit zpět** ([ConversationRepository.reportSponsoredView],
 * [ConversationRepository.reportSponsoredClick]); z toho se počítá odměna pro kanál.
 */
data class SponsoredMessage(
    val messageId: Long,
    /** Jméno inzerenta. */
    val title: String,
    /**
     * Text reklamy i s formátováním (`message` + `entities`) — u reklamy s médiem jeho popisek.
     * Prázdný, když reklama nese jen médium.
     */
    val body: MessageBody,
    /** Popisek tlačítka, jak ho dodá Telegram — nepřekládá se. */
    val buttonText: String,
    /** Cíl kliknutí. */
    val url: String,
    /** `true` u doporučených, ne placených — Telegram je odlišuje. */
    val isRecommended: Boolean,
    /** Doplňkový text od Telegramu (např. proč se zobrazuje); `null` když není. */
    val additionalInfo: String?,
    /** Smí se nahlásit moderátorům (`can_be_reported`); jinak ⋮ s nahlášením chybí. */
    val canBeReported: Boolean = true,
    /**
     * Médium reklamy v kanálu ([MessageMedia.Photo], [MessageMedia.Video], nebo
     * [MessageMedia.Animation]); `null` u textové. Ťuknutí na něj se hlásí jako kliknutí na
     * médium, viz [mediaOpensLink].
     */
    val media: MessageMedia? = null,
    /** Fotka inzerenta (`sponsor.photo`) — u reklamy u bota náhled vpravo v pruhu. */
    val photo: MessageMedia.Photo? = null,
    /** Barva reklamy (`accent_color_id`), viz `telegramAccentColor`. */
    val accentColorId: Int = 0,
    /**
     * Reklama v chatu s botem. Kreslí se jako pruh NAD konverzací (jako pruh proti spamu),
     * ne jako karta pod zprávami — tak to [specifikace](https://core.telegram.org/api/sponsored-messages)
     * u botů předepisuje.
     */
    val inBotChat: Boolean = false,
) {
    /** Text bez formátování. */
    val text: String get() = body.text

    /**
     * Ťuknutí na médium otevře odkaz reklamy (fotka, GIF = video bez zvuku), nebo médium na celé
     * obrazovce (video se zvukem) — tak to [specifikace](https://core.telegram.org/api/sponsored-messages)
     * rozlišuje. Odkaz se u videa otevře až tlačítkem reklamy v prohlížeči. `Video` z TDLib
     * příznak zvuku nenese, takže se video bere jako video se zvukem.
     */
    val mediaOpensLink: Boolean get() = media !is MessageMedia.Video
}

/**
 * Smí se odkaz reklamy otevřít bez potvrzení? Jen domény Telegramu ze
 * [specifikace](https://core.telegram.org/api/sponsored-messages) a jejich subdomény, přes
 * `http`, `https` nebo bez schématu. Jinde se adresa napřed ukáže v dialogu.
 *
 * **Přísnější než „končí na t.me"**: hostitel smí mít jen písmena, číslice, tečky a pomlčky,
 * takže `https://t.me@evil.example` (přihlašovací údaje) ani `t.me:8080` neprojdou — přesně
 * jako regulární výraz ve specifikaci, který za doménou připouští jen lomítko.
 */
fun isTrustedSponsorUrl(url: String): Boolean {
    val rest = url.trim().lowercase().let { lower ->
        when {
            lower.startsWith("https://") -> lower.removePrefix("https://")
            lower.startsWith("http://") -> lower.removePrefix("http://")
            "://" in lower -> return false
            else -> lower
        }
    }
    val host = rest.substringBefore('/')
    if (host.isEmpty() || !host.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }) return false
    return TRUSTED_SPONSOR_DOMAINS.any { host == it || host.endsWith(".$it") }
}

// `te.legra.ph` zvlášť: není subdoménou `telegra.ph`, jen se tak čte.
private val TRUSTED_SPONSOR_DOMAINS = listOf(
    "telegram.org", "telegram.me", "telegram.dog", "t.me", "telegra.ph", "te.legra.ph", "graph.org", "fragment.com",
    "telesco.pe",
)
