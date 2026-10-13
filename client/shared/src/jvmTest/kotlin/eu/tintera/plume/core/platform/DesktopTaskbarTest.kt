package eu.tintera.plume.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/** Overlay ve Windows je 16 × 16 bodů; kreslí se v pixelech podle měřítka, jinak by se rozmazal. */
class DesktopTaskbarTest {

    private val colors = DesktopTaskbar.BadgeColors(container = 0xFFB3261E.toInt(), content = 0xFFFFFFFF.toInt())

    @Test
    fun sizeFollowsDisplayScale() {
        assertEquals(16, DesktopTaskbar.badgeImage(3, colors, scale = 1.0).width)
        assertEquals(24, DesktopTaskbar.badgeImage(3, colors, scale = 1.5).width)
        assertEquals(32, DesktopTaskbar.badgeImage(3, colors, scale = 2.0).height)
    }

    @Test
    fun circleHasThemeColorAndTransparentCorners() {
        val image = DesktopTaskbar.badgeImage(120, colors, scale = 2.0)
        assertEquals(0, image.getRGB(0, 0) ushr 24, "roh mimo kolečko je průhledný")
        assertEquals(colors.container, image.getRGB(image.width / 2, 1))
    }
}
