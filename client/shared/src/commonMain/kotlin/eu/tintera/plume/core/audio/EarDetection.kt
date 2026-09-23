package eu.tintera.plume.core.audio

import kotlin.math.abs

/**
 * Stav senzorů, ze kterých se pozná telefon u ucha.
 *
 * [gravityZ] je složka tíže kolmá k displeji, v násobcích g: +1 displejem nahoru, −1 displejem
 * dolů, kolem nuly telefon nastojato. Výchozí nula znamená „nevím" a čte se jako nastojato —
 * zařízení bez akcelerometru tím spadne na samotné přiblížení, místo aby funkci ztratilo.
 */
data class EarSensors(
    val near: Boolean = false,
    val gravityZ: Float = 0f,
)

/**
 * Má se zvuk přepnout do sluchátka u ucha?
 *
 * Samotné přiblížení NESTAČÍ. Senzor hlásí „zakryto" i telefonu položenému displejem dolů na
 * stole — a tam by přepnutí do sluchátka znamenalo, že hlasovka ztichne a nikdo neví proč.
 * Přidává se proto sklon: u ucha je telefon zhruba nastojato, na stole leží naplocho.
 *
 * **Se sluchátky se nepřepíná vůbec.** Kdo poslouchá přes drát nebo Bluetooth, drží telefon u
 * ucha leda omylem, a přesměrovat mu zvuk do sluchátka telefonu je to poslední, co chce.
 *
 * Pravidlo je čistá funkce, ne podmínka uvnitř senzoru: hraniční případy (naplocho displejem
 * nahoru i dolů, těsně pod prahem) se jinak neověří a projeví se až na zařízení tím, že hlasovka
 * ztichne v kapse.
 */
fun raisedToEar(sensors: EarSensors, headphonesConnected: Boolean): Boolean =
    sensors.near && !headphonesConnected && abs(sensors.gravityZ) < MAX_FLAT_GRAVITY

/**
 * Odkdy se telefon počítá za ležící naplocho — 0,7 g je náklon zhruba 45° od vodorovné roviny.
 *
 * U ucha bývá `gravityZ` do 0,3; na stole přes 0,95. Práh uprostřed proto nikam neškrtá a nechává
 * rezervu na telefon opřený o rameno.
 */
private const val MAX_FLAT_GRAVITY = 0.7f
