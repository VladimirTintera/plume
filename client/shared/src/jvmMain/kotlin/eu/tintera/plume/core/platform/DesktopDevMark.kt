package eu.tintera.plume.core.platform

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Značka ladicího běhu z Gradle (`./gradlew :client:desktopApp:run`, `plume.debug`). Ladicí běh
 * má vlastní data a s nainstalovanou aplikací běží vedle sebe (viz [DesktopInstance]) — bez
 * značky se ale nepoznalo, které okno je které, a testovalo se v té špatné.
 *
 * Značka je v názvu (titulek okna, popisek v oznamovací oblasti) i v ikoně (hlavní panel,
 * oznamovací oblast): oranžová tečka v pravém dolním rohu, poznat ji jde i bez čtení.
 * Nainstalovaná aplikace se nemění.
 */
object DesktopDevMark {
    /** Běží ladicí sestava? */
    val active: Boolean get() = desktopDebugBuild

    /** [base] s označením ladicí sestavy. Nepřekládá se — je to název aplikace, ne věta. */
    fun name(base: String): String = if (active) "$base (dev)" else base

    /** Ikona s oranžovou tečkou v rohu; u nainstalované aplikace beze změny. */
    fun icon(image: BufferedImage): BufferedImage {
        if (!active) return image
        val marked = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = marked.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(image, 0, 0, null)
            // Tečka přes 45 % šířky: na hlavním panelu (16–32 px) musí být vidět i zmenšená.
            val size = (image.width * 0.45f).toInt()
            val x = image.width - size
            val y = image.height - size
            graphics.color = DOT
            graphics.fillOval(x, y, size, size)
            // Bílý lem odliší tečku od tmavé i barevné ikony pod ní.
            graphics.color = Color.WHITE
            graphics.stroke = BasicStroke((size / 10f).coerceAtLeast(1f))
            graphics.drawOval(x, y, size - 1, size - 1)
        } finally {
            graphics.dispose()
        }
        return marked
    }

    private val DOT = Color(0xFF, 0x8F, 0x00)
}
