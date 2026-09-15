package eu.tintera.plume.core.telegram

/**
 * Sponzorovaná zpráva v kanálu.
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
    val text: String,
    /** Popisek tlačítka, jak ho dodá Telegram — nepřekládá se. */
    val buttonText: String,
    /** Cíl kliknutí. */
    val url: String,
    /** `true` u doporučených, ne placených — Telegram je odlišuje. */
    val isRecommended: Boolean,
    /** Doplňkový text od Telegramu (např. proč se zobrazuje); `null` když není. */
    val additionalInfo: String?,
)
