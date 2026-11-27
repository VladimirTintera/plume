package eu.tintera.plume.features.places

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * Výřez přehledové mapy pro víc bodů najednou: střed a přiblížení, do kterého se všechny body
 * vejdou. Mapu kreslí Telegram jako obrázek kolem JEDNOHO bodu (`getMapThumbnailFile`) a špendlíky
 * se do něj promítnou samy — Web Mercator v jednotkách mapy (256 bodů na dlaždici při přiblížení
 * 0, jako u všech dlaždicových map). Interaktivní mapa by chtěla mapové SDK s klíčem.
 */
data class MapFit(val latitude: Double, val longitude: Double, val zoom: Int)

/** Bod na světě při přiblížení 0: x i y od 0 do 1 (y od severu). */
internal data class WorldPoint(val x: Double, val y: Double)

internal fun worldOf(latitude: Double, longitude: Double): WorldPoint {
    val lat = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180
    return WorldPoint(
        x = (longitude + 180) / 360,
        y = (1 - ln(tan(lat) + 1 / kotlin.math.cos(lat)) / PI) / 2,
    )
}

internal fun latitudeOf(y: Double): Double = atan(kotlin.math.sinh(PI * (1 - 2 * y))) * 180 / PI

/**
 * Nejvyšší přiblížení z [minZoom]..[maxZoom], při kterém se všechny [points] vejdou do mapy
 * [width] × [height] bodů s okrajem [margin]; `null`, když se nevejdou ani při [minZoom]
 * (body daleko od sebe — Telegram mapu vzdálenější než [minZoom] nevydá).
 */
fun fitMap(points: List<Pair<Double, Double>>, width: Int, height: Int, margin: Int, minZoom: Int = 13, maxZoom: Int = 17): MapFit? {
    if (points.isEmpty()) return null
    val world = points.map { (lat, lon) -> worldOf(lat, lon) }
    val minX = world.minOf { it.x }
    val maxX = world.maxOf { it.x }
    val minY = world.minOf { it.y }
    val maxY = world.maxOf { it.y }
    val usableWidth = (width - 2 * margin).coerceAtLeast(1)
    val usableHeight = (height - 2 * margin).coerceAtLeast(1)
    val zoom = (maxZoom downTo minZoom).firstOrNull { z ->
        val size = TILE * 2.0.pow(z)
        (maxX - minX) * size <= usableWidth && (maxY - minY) * size <= usableHeight
    } ?: return null
    val centerY = (minY + maxY) / 2
    return MapFit(latitude = latitudeOf(centerY), longitude = (minX + maxX) / 2 * 360 - 180, zoom = zoom)
}

/** Kde v mapě [width] × [height] bodů s výřezem [fit] leží bod (v bodech od levého horního rohu). */
fun pointOnMap(latitude: Double, longitude: Double, fit: MapFit, width: Int, height: Int): Pair<Double, Double> {
    val size = TILE * 2.0.pow(fit.zoom)
    val point = worldOf(latitude, longitude)
    val center = worldOf(fit.latitude, fit.longitude)
    return (width / 2.0 + (point.x - center.x) * size) to (height / 2.0 + (point.y - center.y) * size)
}

/** Body v mapě, které vypadnou ven (zaokrouhlení přiblížení), se přitáhnou k okraji. */
internal fun clampToMap(x: Double, y: Double, width: Int, height: Int): Pair<Double, Double> =
    min(max(x, 0.0), width.toDouble()) to min(max(y, 0.0), height.toDouble())

private const val TILE = 256.0

/** Mercator nad ±85,05° neexistuje (y by bylo nekonečné). */
private const val MAX_LATITUDE = 85.05112878

