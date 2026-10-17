package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.util.Properties

/**
 * Poloha a velikost hlavního okna v logických bodech AWT (v Compose `dp`). [x] a [y] jsou
 * `null`, když se okno má postavit tam, kam ho dá systém (první spuštění, poloha mimo monitory).
 * [maximized] = okno bylo maximalizované; [width] a [height] jsou pak jeho velikost PŘED
 * maximalizací, na kterou se vrátí.
 */
data class SavedWindowBounds(
    val x: Int?,
    val y: Int?,
    val width: Int,
    val height: Int,
    val maximized: Boolean,
)

/**
 * Pamatovaná poloha a velikost hlavního okna (`window.properties` v datech aplikace, vedle
 * `instance.lock`). Bez ní se okno při každém spuštění otevřelo v 1200 × 800 tam, kam ho dal
 * systém, a kdo si ho roztáhl přes druhý monitor, roztahoval ho znovu.
 *
 * **Uložená poloha se před použitím ověří proti monitorům, které právě jsou** ([fitted]).
 * Okno uložené na odpojeném druhém monitoru by se jinak otevřelo mimo dosah a z hlavního panelu
 * by se nedalo ani přetáhnout. Velikost se zmenší na monitor, na kterém okno bude.
 */
object DesktopWindowBounds {

    private val logger = Logger.withTag("DesktopWindowBounds")

    private val file: File get() = File(desktopDataDirectory, FILE_NAME)

    /** Uložená poloha přizpůsobená dnešním monitorům; `null` = nic uloženého (první spuštění). */
    fun restore(): SavedWindowBounds? {
        val saved = runCatching { file.takeIf { it.isFile }?.reader()?.use { reader -> Properties().apply { load(reader) } } }
            .onFailure { logger.w(it) { "Polohu okna se nepodařilo přečíst." } }
            .getOrNull()
            ?.let(::parse)
            ?: return null
        val screens = runCatching { screenBounds() }.getOrDefault(emptyList())
        return fitted(saved, screens)
    }

    fun save(bounds: SavedWindowBounds) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writer().use { writer -> serialize(bounds).store(writer, null) }
        }.onFailure { logger.w(it) { "Polohu okna se nepodařilo uložit." } }
    }

    /** Pracovní plochy monitorů (bez hlavního panelu a Docku), v logických bodech. */
    private fun screenBounds(): List<Rectangle> {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        if (GraphicsEnvironment.isHeadless()) return emptyList()
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        return environment.screenDevices.map { device ->
            val configuration = device.defaultConfiguration
            val bounds = configuration.bounds
            val insets = toolkit.getScreenInsets(configuration)
            Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom,
            )
        }
    }

    internal fun parse(properties: Properties): SavedWindowBounds? {
        val width = properties.getProperty(KEY_WIDTH)?.toIntOrNull() ?: return null
        val height = properties.getProperty(KEY_HEIGHT)?.toIntOrNull() ?: return null
        return SavedWindowBounds(
            x = properties.getProperty(KEY_X)?.toIntOrNull(),
            y = properties.getProperty(KEY_Y)?.toIntOrNull(),
            width = width,
            height = height,
            maximized = properties.getProperty(KEY_MAXIMIZED).toBoolean(),
        )
    }

    internal fun serialize(bounds: SavedWindowBounds): Properties = Properties().apply {
        bounds.x?.let { setProperty(KEY_X, it.toString()) }
        bounds.y?.let { setProperty(KEY_Y, it.toString()) }
        setProperty(KEY_WIDTH, bounds.width.toString())
        setProperty(KEY_HEIGHT, bounds.height.toString())
        setProperty(KEY_MAXIMIZED, bounds.maximized.toString())
    }

    /**
     * Uložená poloha pro dnešní [screens]. Okno zůstane, kde bylo, když z něj na některém
     * monitoru je vidět aspoň [MIN_VISIBLE] bodů (dost na chycení za titulek); jinak polohu
     * vybere systém. Velikost se vejde do monitoru, na kterém okno bude (při neznámé poloze do
     * největšího), a neklesne pod [MIN_WIDTH] × [MIN_HEIGHT]. Bez známých monitorů (headless)
     * zůstane uložená hodnota.
     */
    internal fun fitted(saved: SavedWindowBounds, screens: List<Rectangle>): SavedWindowBounds {
        if (screens.isEmpty()) return saved
        val x = saved.x
        val y = saved.y
        val home = if (x != null && y != null) {
            val window = Rectangle(x, y, saved.width, saved.height)
            screens.firstOrNull { screen ->
                val visible = screen.intersection(window)
                !visible.isEmpty && visible.width >= MIN_VISIBLE && visible.height >= MIN_VISIBLE
            }
        } else {
            null
        }
        val limit = home ?: screens.maxBy { it.width.toLong() * it.height }
        val width = saved.width.coerceAtMost(limit.width).coerceAtLeast(MIN_WIDTH)
        val height = saved.height.coerceAtMost(limit.height).coerceAtLeast(MIN_HEIGHT)
        return if (home == null) {
            saved.copy(x = null, y = null, width = width, height = height)
        } else {
            // Okno přečnívající přes okraj monitoru se do něj posune celé, ať je vidět titulek.
            // Na monitoru menším než nejmenší okno zůstane levý horní roh na monitoru.
            saved.copy(
                x = x!!.coerceIn(home.x, maxOf(home.x, home.x + home.width - width)),
                y = y!!.coerceIn(home.y, maxOf(home.y, home.y + home.height - height)),
                width = width,
                height = height,
            )
        }
    }

    private const val FILE_NAME = "window.properties"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_MAXIMIZED = "maximized"

    /** Kolik bodů okna musí být na monitoru vidět, aby se dalo chytit a přesunout. */
    internal const val MIN_VISIBLE = 100

    /** Nejmenší velikost, na kterou se okno při obnově zmenší — menší by nebylo k použití. */
    internal const val MIN_WIDTH = 480
    internal const val MIN_HEIGHT = 360
}
