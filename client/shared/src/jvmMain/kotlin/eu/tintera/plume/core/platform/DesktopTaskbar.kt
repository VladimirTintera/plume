package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger
import java.awt.Color
import java.awt.EventQueue
import java.awt.Font
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Počet nepřečtených na ikoně aplikace a upozornění na novou zprávu.
 *
 * - **macOS: odznak v Docku** (`ICON_BADGE_TEXT`), text kreslí systém.
 * - **Windows: overlay ikona na tlačítku okna** (`ICON_BADGE_IMAGE_WINDOW`, pod tím
 *   `ITaskbarList3::SetOverlayIcon`). Text v odznaku Windows u nezabalené aplikace neumí, obrázek
 *   kreslíme sami — kolečko v barvách motivu ([setColors]) s číslem.
 * - **Windows: blikání tlačítka** při nové zprávě ([requestAttention], `FlashWindowEx`), dokud
 *   se okno neaktivuje. Schované okno tlačítko nemá, takže nebliká — upozorní toast.
 *
 * Overlay patří k tlačítku okna: schované okno ho ztratí, proto se po každém ukázání nastavuje
 * znovu (sleduje [DesktopWindow.shown]).
 */
internal object DesktopTaskbar {

    private val logger = Logger.withTag("DesktopTaskbar")

    private val taskbar: Taskbar? by lazy {
        runCatching { Taskbar.getTaskbar() }.getOrNull()
    }

    private fun supports(feature: Taskbar.Feature) = runCatching { taskbar?.isSupported(feature) == true }.getOrDefault(false)

    private val unread = MutableStateFlow(0)

    /** Nepřečtené po účtech (slot → počet); odznak je jejich součet, nezávisle na filtru seznamu. */
    private val unreadByAccount = mutableMapOf<String, Int>()

    private var started = false

    /** Barvy odznaku z motivu aplikace; do první kompozice motivu se overlay nekreslí. */
    private val colors = MutableStateFlow<BadgeColors?>(null)

    data class BadgeColors(val container: Int, val content: Int)

    private data class Badge(val count: Int, val colors: BadgeColors?, val onScreen: Boolean)

    /** Nepřečtené účtu [account] (slot); odznak ukazuje součet všech účtů. */
    fun setUnread(account: String, count: Int) {
        unread.value = synchronized(unreadByAccount) {
            unreadByAccount[account] = count
            unreadByAccount.values.sum()
        }
    }

    fun setColors(container: Int, content: Int) {
        colors.value = BadgeColors(container, content)
    }

    /** Spustí kreslení odznaku; volá ho presenter každého účtu, kreslí se jednou. */
    fun start(scope: CoroutineScope) {
        if (synchronized(this) { started.also { started = true } }) return
        val text = supports(Taskbar.Feature.ICON_BADGE_TEXT)
        val image = supports(Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW)
        if (!text && !image) return
        scope.launch {
            var buttonReady = false
            combine(unread, colors, DesktopWindow.shown, DesktopWindow.window) { count, colors, shown, window ->
                Badge(count, colors, shown && window != null)
            }
                .distinctUntilChanged()
                .collectLatest { badge ->
                    if (text) runCatching { taskbar?.setIconBadge(badge.count.takeIf { it > 0 }?.toString()) }
                    if (!image) return@collectLatest
                    if (!badge.onScreen) {
                        buttonReady = false
                        return@collectLatest
                    }
                    // Tlačítko na hlavním panelu vzniká až chvíli po ukázání okna (zpráva
                    // `TaskbarButtonCreated`) a overlay nastavený dřív Windows tiše zahodí —
                    // ověřeno: hned po startu se neukázal, za 1,5 s ano. Po schování okna
                    // tlačítko zaniká, takže se čeká po každém ukázání.
                    if (!buttonReady) delay(BUTTON_SETTLE_MS)
                    buttonReady = true
                    EventQueue.invokeLater { applyOverlay(badge.count, badge.colors) }
                }
        }
    }

    /** Blikne tlačítkem okna, jen když okno je vidět a nemá fokus. */
    fun requestAttention() {
        if (!supports(Taskbar.Feature.USER_ATTENTION_WINDOW)) return
        EventQueue.invokeLater {
            val window = DesktopWindow.awtWindow ?: return@invokeLater
            if (!window.isVisible || window.isFocused) return@invokeLater
            runCatching { taskbar?.requestWindowUserAttention(window) }
                .onFailure { logger.w(it) { "Tlačítko okna nejde rozblikat." } }
        }
    }

    private fun applyOverlay(count: Int, colors: BadgeColors?) {
        val window = DesktopWindow.awtWindow ?: return
        logger.d { "Odznak na hlavním panelu: $count${if (colors == null) " (bez barev motivu)" else ""}." }
        runCatching {
            val badge = if (count > 0 && colors != null) badgeImage(count, colors, scale(window)) else null
            taskbar?.setWindowIconBadge(window, badge)
        }.onFailure { logger.w(it) { "Odznak na hlavním panelu nejde nastavit." } }
    }

    private fun scale(window: java.awt.Window): Double =
        window.graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0 } ?: 1.0

    /**
     * Kolečko s číslem pro overlay. Windows ho kreslí v rohu ikony v 16 × 16 bodech, takže se
     * kreslí rovnou v pixelech podle měřítka obrazovky — zmenšený větší obrázek by měl
     * rozmazané číslice. Nad 99 jen „99+": tři číslice se do 16 bodů nevejdou čitelně.
     * Písmo je systémové (Segoe UI), ne písmo aplikace: odznak je součást hlavního panelu.
     */
    internal fun badgeImage(count: Int, colors: BadgeColors, scale: Double): BufferedImage {
        val size = (OVERLAY_SIZE * scale).roundToInt().coerceAtLeast(OVERLAY_SIZE)
        val label = if (count > MAX_COUNT) "$MAX_COUNT+" else count.toString()
        return BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
            val graphics = createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
                graphics.color = Color(colors.container, true)
                graphics.fillOval(0, 0, size, size)
                // Čím víc znaků, tím menší písmo, ať se vejde do kolečka.
                val fontSize = size * when (label.length) {
                    1 -> 0.72
                    2 -> 0.6
                    else -> 0.42
                }
                graphics.font = Font("Segoe UI", Font.BOLD, 1).deriveFont(fontSize.toFloat())
                val metrics = graphics.fontMetrics
                val bounds = graphics.font.createGlyphVector(graphics.fontRenderContext, label).visualBounds
                graphics.color = Color(colors.content, true)
                graphics.drawString(
                    label,
                    ((size - metrics.stringWidth(label)) / 2f),
                    ((size - bounds.height) / 2 - bounds.y).toFloat(),
                )
            } finally {
                graphics.dispose()
            }
        }
    }

    private const val BUTTON_SETTLE_MS = 1_500L
    private const val OVERLAY_SIZE = 16
    private const val MAX_COUNT = 99
}
