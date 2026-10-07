package eu.tintera.plume.core.settings

/**
 * Gesto, které se v konverzaci napoprvé vysvětlí nápovědou. Ukládá se NÁZVEM, jako ostatní
 * výčty v nastavení: nová hodnota uprostřed nesmí změnit význam uložených dat.
 */
enum class GestureHint {
    /** Tah doleva po konverzaci odkryje časy zpráv. */
    RevealTimes,

    /** Podržení mikrofonu (kamery) nahrává, tah doleva ruší, nahoru zamyká. */
    HoldToRecord,

    /** Podržení odesílacího tlačítka nabídne efekt, odeslání bez zvuku a naplánování. */
    SendOptions,
}
