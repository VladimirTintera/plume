package eu.tintera.plume.features.places

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapFitTest {

    // Václavské náměstí, Staroměstské náměstí, Pražský hrad — vše v Praze, pár kilometrů od sebe.
    private val wenceslas = 50.0810 to 14.4280
    private val oldTown = 50.0875 to 14.4213
    private val castle = 50.0900 to 14.4000

    @Test
    fun jedenBodMaNejvyssiPriblizeniAStredVNem() {
        val fit = assertNotNull(fitMap(listOf(wenceslas), width = 400, height = 240, margin = 32))
        assertEquals(17, fit.zoom)
        assertTrue(abs(fit.latitude - wenceslas.first) < 1e-9 && abs(fit.longitude - wenceslas.second) < 1e-9)
    }

    @Test
    fun bodyVeMesteSeVejdouAOdKrajeNepretecou() {
        val points = listOf(wenceslas, oldTown, castle)
        val fit = assertNotNull(fitMap(points, width = 400, height = 240, margin = 32))
        assertTrue(fit.zoom in 13..17, "zoom ${fit.zoom}")
        points.forEach { (lat, lon) ->
            val (x, y) = pointOnMap(lat, lon, fit, 400, 240)
            assertTrue(x in 31.9..368.1 && y in 31.9..208.1, "bod mimo okraj: $x, $y")
        }
    }

    @Test
    fun stredMapyJeUprostredObrazku() {
        val fit = MapFit(latitude = 50.0, longitude = 14.0, zoom = 15)
        val (x, y) = pointOnMap(50.0, 14.0, fit, 400, 240)
        assertTrue(abs(x - 200) < 1e-6 && abs(y - 120) < 1e-6)
    }

    @Test
    fun vychodJeVpravoASeverNahore() {
        val fit = MapFit(latitude = 50.0, longitude = 14.0, zoom = 15)
        val (eastX, _) = pointOnMap(50.0, 14.001, fit, 400, 240)
        val (_, northY) = pointOnMap(50.001, 14.0, fit, 400, 240)
        assertTrue(eastX > 200)
        assertTrue(northY < 120)
    }

    @Test
    fun bodyDalekoOdSebeNaJednuMapuNejdou() {
        // Praha a Brno: ani přiblížení 13 je do 400 bodů nevezme.
        assertNull(fitMap(listOf(wenceslas, 49.1951 to 16.6068), width = 400, height = 240, margin = 32))
        assertNull(fitMap(emptyList(), width = 400, height = 240, margin = 32))
    }
}
