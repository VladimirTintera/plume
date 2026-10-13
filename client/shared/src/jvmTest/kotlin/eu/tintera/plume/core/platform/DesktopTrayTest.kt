package eu.tintera.plume.core.platform

import java.awt.image.MultiResolutionImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ikona u hodin: odznak vpravo dole, vyříznutý lem a varianty pro měřítka obrazovky. */
class DesktopTrayTest {

    private val colors = DesktopTaskbar.BadgeColors(container = 0xFFB3261E.toInt(), content = 0xFFFFFFFF.toInt())

    @Test
    fun badgeSitsBottomRightWithThemeColor() {
        val image = DesktopTray.trayVariant(size = 32, count = 3, colors = colors)
        // Střed kolečka (průměr 0,68 ikony) leží vpravo dole; horní okraj kolečka má barvu odznaku.
        val badge = (32 * 0.68).toInt()
        assertEquals(colors.container, image.getRGB(32 - badge / 2, 32 - badge + 1))
    }

    @Test
    fun noBadgeWithoutUnreadOrColors() {
        val plain = DesktopTray.trayVariant(size = 32, count = 0, colors = colors)
        val noColors = DesktopTray.trayVariant(size = 32, count = 5, colors = null)
        for (x in 0 until 32) for (y in 0 until 32) assertEquals(plain.getRGB(x, y), noColors.getRGB(x, y))
    }

    @Test
    fun variantsForDisplayScales() {
        val image = DesktopTray.trayImage(count = 12, colors = colors) as MultiResolutionImage
        val sizes = image.resolutionVariants.map { it.getWidth(null) }
        assertTrue(sizes.size >= 4, "varianty: $sizes")
        assertEquals(sizes.sorted(), sizes)
    }

    @Test
    fun transparentMarginIsTrimmed() {
        val image = java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (x in 2..6) for (y in 3..8) image.setRGB(x, y, 0xFF336699.toInt())
        val trimmed = DesktopTray.trimTransparent(image)
        assertEquals(5, trimmed.width)
        assertEquals(6, trimmed.height)
    }
}
