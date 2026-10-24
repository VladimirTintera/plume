package eu.tintera.plume.core.ui

import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.distance_kilometers
import eu.tintera.plume.resources.distance_meters

/**
 * Vzdálenost rozložená na čísla: metry pod kilometr, nad ním kilometry s jedním desetinným
 * místem do deseti kilometrů („1,2 km") a celé nad nimi („12 km"). Nulová desetina se vynechá
 * („1 km", ne „1,0 km"). Oddělovač i jednotku dodá až jazyk ([distanceUiText]).
 */
data class Distance(val whole: Int, val tenths: Int?, val kilometers: Boolean)

fun distanceOf(meters: Int): Distance {
    val m = meters.coerceAtLeast(0)
    if (m < 1_000) return Distance(whole = m, tenths = null, kilometers = false)
    if (m >= 10_000) return Distance(whole = (m + 500) / 1_000, tenths = null, kilometers = true)
    val tenthsTotal = (m + 50) / 100
    val tenths = tenthsTotal % 10
    return Distance(whole = tenthsTotal / 10, tenths = tenths.takeIf { it != 0 }, kilometers = true)
}

/**
 * Vzdálenost jako text k přeložení: „120 m", „1,2 km". [UiText], ne řetězec — skládá ji i servisní
 * zpráva v `core/telegram` a desetinný oddělovač patří jazyku (`decimal_number`).
 */
fun distanceUiText(meters: Int): UiText {
    val distance = distanceOf(meters)
    val number: Any = distance.tenths
        ?.let { UiText(Res.string.decimal_number, distance.whole.toString(), it.toString()) }
        ?: distance.whole.toString()
    return UiText(if (distance.kilometers) Res.string.distance_kilometers else Res.string.distance_meters, number)
}
