package eu.tintera.plume.core.platform

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/** Uložená poloha okna proti dnešním monitorům: zůstane, posune se, nebo ji vybere systém. */
class DesktopWindowBoundsTest {

    private val main = Rectangle(0, 0, 1920, 1040)
    private val right = Rectangle(1920, 0, 2560, 1400)

    @Test
    fun oknoNaMonitoruZustaneKdeBylo() {
        val saved = SavedWindowBounds(x = 2100, y = 100, width = 1600, height = 1000, maximized = true)
        assertEquals(saved, DesktopWindowBounds.fitted(saved, listOf(main, right)))
    }

    @Test
    fun odpojenyMonitorPolohuZahodiAVelikostZmensi() {
        val saved = SavedWindowBounds(x = 2100, y = 100, width = 2400, height = 1300, maximized = false)
        assertEquals(
            SavedWindowBounds(x = null, y = null, width = 1920, height = 1040, maximized = false),
            DesktopWindowBounds.fitted(saved, listOf(main)),
        )
    }

    @Test
    fun oknoPresOkrajSePosuneDovnitr() {
        val saved = SavedWindowBounds(x = 1500, y = -20, width = 1200, height = 800, maximized = false)
        assertEquals(
            SavedWindowBounds(x = 720, y = 0, width = 1200, height = 800, maximized = false),
            DesktopWindowBounds.fitted(saved, listOf(main)),
        )
    }

    @Test
    fun skoroCeleMimoMonitorJdeKamChceSystem() {
        // Z okna je na monitoru vidět jen pruh 50 bodů — za to se chytit nedá.
        val saved = SavedWindowBounds(x = 1870, y = 100, width = 800, height = 600, maximized = false)
        assertEquals(null, DesktopWindowBounds.fitted(saved, listOf(main)).x)
    }

    @Test
    fun malyMonitorNespadne() {
        val tiny = Rectangle(0, 0, 400, 300)
        val saved = SavedWindowBounds(x = 10, y = 10, width = 1200, height = 800, maximized = false)
        assertEquals(
            SavedWindowBounds(x = 0, y = 0, width = DesktopWindowBounds.MIN_WIDTH, height = DesktopWindowBounds.MIN_HEIGHT, maximized = false),
            DesktopWindowBounds.fitted(saved, listOf(tiny)),
        )
    }

    @Test
    fun bezMonitoruZustaneUlozene() {
        val saved = SavedWindowBounds(x = 5, y = 5, width = 900, height = 700, maximized = false)
        assertEquals(saved, DesktopWindowBounds.fitted(saved, emptyList()))
    }

    @Test
    fun ulozeniAPrecteniVratiTotez() {
        val saved = SavedWindowBounds(x = -1200, y = 30, width = 1100, height = 760, maximized = true)
        assertEquals(saved, DesktopWindowBounds.parse(DesktopWindowBounds.serialize(saved)))
        val placeless = saved.copy(x = null, y = null)
        assertEquals(placeless, DesktopWindowBounds.parse(DesktopWindowBounds.serialize(placeless)))
    }
}
