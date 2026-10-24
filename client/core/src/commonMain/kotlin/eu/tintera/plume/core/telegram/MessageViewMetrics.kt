package eu.tintera.plume.core.telegram

/**
 * Jedno zobrazení zprávy na obrazovce, jak ho chce TDLib v `sendMessageViewMetrics`.
 *
 * Nejde o přečtení (to je `viewMessages`), ale o statistiku: jak dlouho zpráva byla vidět, kolik
 * z toho uživatel na obrazovce něco dělal a jak velkou část zprávy viděl.
 */
data class MessageViewMetrics(
    val messageId: Long,
    /** Jak dlouho byl vidět aspoň jeden pixel zprávy, v milisekundách. */
    val timeInViewMs: Int,
    /** Z toho do 15 s od poslední akce uživatele (posun seznamu), v milisekundách. */
    val activeTimeInViewMs: Int,
    /** Výška zprávy k výšce viewportu, v tisícinách (vysoká zpráva přes 1000). */
    val heightToViewportRatioPerMille: Int,
    /** Kolik z výšky zprávy bylo za celé zobrazení vidět, v tisícinách (0–1000). */
    val seenRangeRatioPerMille: Int,
)
