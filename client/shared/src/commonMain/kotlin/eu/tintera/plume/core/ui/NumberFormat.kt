package eu.tintera.plume.core.ui

/**
 * Číslo s tisíci oddělenými úzkou nezlomitelnou mezerou (U+202F): „10 704 668".
 *
 * Kotlin v commonMain `NumberFormat` nemá. Mezera místo čárky nebo tečky je čitelná v češtině
 * i angličtině (tak to doporučuje i SI) a nezlomitelná — číslo se nerozdělí na dva řádky.
 */
fun Int.grouped(): String = toLong().grouped()

/** Jako [Int.grouped], pro částky v `Long` (cena faktury). */
fun Long.grouped(): String {
    val digits = kotlin.math.abs(this).toString()
    val grouped = digits.reversed().chunked(3).joinToString(GROUP_SEPARATOR).reversed()
    return if (this < 0) "-$grouped" else grouped
}

private const val GROUP_SEPARATOR = " "
