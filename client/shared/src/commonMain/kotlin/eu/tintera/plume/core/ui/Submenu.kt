package eu.tintera.plume.core.ui

/**
 * Otevírá se podnabídka DO STRANY (kaskádově, po najetí myší)? Jen desktop.
 *
 * S myší je to zvyk ze systémových nabídek a vedle nabídky je místo. Na telefonu vedle nabídky
 * široké 220 dp místo není a podnabídka by se položila přes ni — tam se proto obsah nabídky
 * vymění na místě, se šipkou zpět (viz „Nabídka nad zprávou" v `docs/poznatky/konverzace-akce.md`).
 */
expect val submenusCascade: Boolean
