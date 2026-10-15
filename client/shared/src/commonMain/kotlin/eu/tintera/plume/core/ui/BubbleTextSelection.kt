package eu.tintera.plume.core.ui

/**
 * Vybírá se text zprávy přímo v bublině myší? Jen desktop.
 *
 * Na dotyku zahajuje výběr dlouhý stisk, a ten v konverzaci patří nabídce nad zprávou — tam se
 * vybírá v kopii zprávy v nabídce (viz `docs/poznatky/konverzace-akce.md`). S myší se vybírá
 * tažením, dvojklikem a trojklikem; za to tah myší přestane zakládat odpověď a odkrývat časy,
 * ťuknutí na zprávu nic nedělá a pravé tlačítko nad textem patří běžné textové nabídce. Odpověď,
 * čas, stav doručení i nabídku nad zprávou nese pilulka po najetí myší.
 */
expect val bubbleTextSelectable: Boolean
