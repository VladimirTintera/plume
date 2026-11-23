package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient

/**
 * Platformní nastavení logu TDLib, volané s každým nově vytvořeným klientem (účinné jednou za
 * proces). Desktop ho přesměruje do souboru se záznamy o chodu aplikace; Android a iOS nechávají
 * výchozí stav TDLib (pády hlásí Crashlytics).
 */
internal expect fun configureTdlibLog(client: TdlClient)
