package eu.tintera.plume.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.tray_open
import eu.tintera.plume.resources.tray_quit
import eu.tintera.plume.resources.tray_tooltip_unread
import java.awt.AlphaComposite
import java.awt.EventQueue
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Zavření okna aplikaci neukončí, jen ji schová do oznamovací oblasti — jako Telegram Desktop.
 * TDLib běží dál a oznámení chodí; ukončuje se z nabídky ikony.
 *
 * Jen Windows: na macOS tutéž roli hraje Dock (okno se schová taky, viz vstupní bod) a na
 * Linuxu oznamovací oblast mít nemusí (GNOME bez rozšíření) — schované okno by pak nešlo vrátit.
 */
val desktopClosesToTray: Boolean by lazy {
    desktopOs == DesktopOs.Windows && runCatching { SystemTray.isSupported() }.getOrDefault(false)
}

/**
 * Ikona v oznamovací oblasti, dokud je ve složení. Vlastní AWT `TrayIcon`, ne `Tray`
 * z Compose: ten vrací okno jen dvojklikem (`ActionListener`), kdežto ve Windows se ikona
 * otevírá jedním kliknutím. Nabídka je AWT `PopupMenu` jako u `Tray` — jinou `TrayIcon` neumí.
 */
@Composable
fun DesktopTrayIcon(onOpen: () -> Unit, onQuit: () -> Unit) {
    if (!desktopClosesToTray) return
    val open = rememberUpdatedState(onOpen)
    val quit = rememberUpdatedState(onQuit)
    val openLabel = stringResource(Res.string.tray_open)
    val quitLabel = stringResource(Res.string.tray_quit)

    DisposableEffect(Unit) {
        val icon = DesktopTray.add(onOpen = { open.value() }, onQuit = { quit.value() })
        onDispose { DesktopTray.remove(icon) }
    }
    // Popisky se mění se jazykem aplikace; ikona kvůli tomu nevzniká znovu.
    SideEffect { DesktopTray.setLabels(openLabel, quitLabel) }

    // Odznak nepřečtených jako na hlavním panelu: týž počet, tytéž barvy motivu.
    val unread by DesktopTaskbar.unreadCount.collectAsState()
    val colors by DesktopTaskbar.badgeColors.collectAsState()
    val tooltip = if (unread > 0) pluralStringResource(Res.plurals.tray_tooltip_unread, unread, unread) else DesktopTray.APP_NAME
    LaunchedEffect(unread, colors, tooltip) { DesktopTray.setBadge(unread, colors, tooltip) }
}

/** Sdílená ikona: [eu.tintera.plume.core.notifications.DesktopNotifier] přes ni ukazuje bubliny AWT. */
internal object DesktopTray {

    private val logger = Logger.withTag("DesktopTray")

    @Volatile
    var icon: TrayIcon? = null
        private set

    private val openItem = MenuItem()
    private val quitItem = MenuItem()

    /**
     * Ikona aplikace v plném rozlišení, ořezaná o průhledný okraj; zmenšuje se pro každou
     * velikost zvlášť. Okraj (47 px z 512 na každé straně) je správně na hlavním panelu a ve
     * Startu, ale u hodin z 16 pixelů zbylo viditelných 13 a ikona byla menší než ostatní.
     */
    private val appIcon: BufferedImage? by lazy {
        runCatching { DesktopTray::class.java.getResourceAsStream("/plume-icon.png")?.use(ImageIO::read)?.let(::trimTransparent) }
            .getOrNull()
    }

    /** Výřez obrázku bez průhledného okraje; celý průhledný obrázek vrátí beze změny. */
    internal fun trimTransparent(image: BufferedImage): BufferedImage {
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (image.getRGB(x, y) ushr 24 != 0) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return image
        return image.getSubimage(left, top, right - left + 1, bottom - top + 1)
    }

    fun add(onOpen: () -> Unit, onQuit: () -> Unit): TrayIcon? = runCatching {
        val image = trayImage(count = 0, colors = null)
        openItem.addActionListener { onOpen() }
        quitItem.addActionListener { onQuit() }
        val menu = PopupMenu().apply {
            add(openItem)
            addSeparator()
            add(quitItem)
        }
        TrayIcon(image, APP_NAME, menu).apply {
            // Velikost řeší varianty obrázku (trayImage); automatické zmenšení by je obešlo.
            isImageAutoSize = false
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    // Jedno kliknutí levým; pravé otevírá nabídku samo.
                    if (event.button == MouseEvent.BUTTON1) EventQueue.invokeLater(onOpen)
                }
            })
            SystemTray.getSystemTray().add(this)
            icon = this
        }
    }.onFailure { logger.w(it) { "Ikonu do oznamovací oblasti nejde přidat." } }.getOrNull()

    fun remove(tray: TrayIcon?) {
        tray ?: return
        runCatching { SystemTray.getSystemTray().remove(tray) }
        if (icon === tray) icon = null
        openItem.actionListeners.forEach(openItem::removeActionListener)
        quitItem.actionListeners.forEach(quitItem::removeActionListener)
    }

    fun setLabels(open: String, quit: String) {
        openItem.label = open
        quitItem.label = quit
    }

    /** Odznak nepřečtených na ikoně a tooltip, který říká totéž slovy (i čtečce obrazovky). */
    fun setBadge(count: Int, colors: DesktopTaskbar.BadgeColors?, tooltip: String) {
        val tray = icon ?: return
        runCatching {
            tray.image = trayImage(count, colors)
            tray.toolTip = tooltip
        }.onFailure { logger.w(it) { "Odznak na ikoně v oznamovací oblasti nejde nastavit." } }
    }

    /**
     * Ikona s odznakem ve variantách pro běžná měřítka obrazovky (100–300 %). Ikona u hodin má
     * 16 bodů; Windows si z vícerozlišovacího obrázku vezme variantu pro své měřítko, takže
     * číslo zůstane ostré — zmenšený velký obrázek by ho rozmazal.
     */
    internal fun trayImage(count: Int, colors: DesktopTaskbar.BadgeColors?): Image {
        val base = runCatching { SystemTray.getSystemTray().trayIconSize.width }.getOrNull()?.takeIf { it > 0 } ?: TRAY_SIZE
        val variants = TRAY_SCALES.map { (base * it).roundToInt() }.distinct().map { size -> trayVariant(size, count, colors) }
        return BaseMultiResolutionImage(*variants.toTypedArray())
    }

    /**
     * Jedna velikost: ikona aplikace a vpravo dole kolečko s počtem. Kolem kolečka je ikona
     * vyříznutá (průhledný lem) — jinak by kolečko na ikoně podobné barvy splynulo.
     */
    internal fun trayVariant(size: Int, count: Int, colors: DesktopTaskbar.BadgeColors?): BufferedImage =
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
            val graphics = createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                appIcon?.let { graphics.drawImage(it.getScaledInstance(size, size, Image.SCALE_AREA_AVERAGING), 0, 0, null) }
                if (count > 0 && colors != null) {
                    val badge = (size * BADGE_FRACTION).roundToInt()
                    val ring = (size / RING_DIVISOR).coerceAtLeast(1)
                    val origin = size - badge
                    graphics.composite = AlphaComposite.Clear
                    graphics.fillOval(origin - ring, origin - ring, badge + 2 * ring, badge + 2 * ring)
                    graphics.composite = AlphaComposite.SrcOver
                    graphics.drawImage(DesktopTaskbar.badgeImage(count, colors, size = badge), origin, origin, null)
                }
            } finally {
                graphics.dispose()
            }
        }

    /** Jméno aplikace, ne text k překladu. */
    const val APP_NAME = "Plume"

    private const val TRAY_SIZE = 16
    private val TRAY_SCALES = listOf(1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)

    /** Průměr kolečka vůči ikoně: s menším by dvouciferné číslo v 16 bodech nebylo čitelné. */
    private const val BADGE_FRACTION = 0.68
    private const val RING_DIVISOR = 16
}
