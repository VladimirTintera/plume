package eu.tintera.plume.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.tray_open
import eu.tintera.plume.resources.tray_quit
import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
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
 * otevírá jedním kliknutím. Nabídka je nativní menu Windows ([WindowsTrayMenu]); AWT `PopupMenu`
 * zůstává jen jako záloha, kdyby se user32 přes JNA nenačetla.
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
}

/** Sdílená ikona: [eu.tintera.plume.core.notifications.DesktopNotifier] přes ni ukazuje bubliny AWT. */
internal object DesktopTray {

    private val logger = Logger.withTag("DesktopTray")

    @Volatile
    var icon: TrayIcon? = null
        private set

    /** Motiv aplikace pro nativní nabídku; `null`, dokud ho okno neohlásí (pak podle systému). */
    @Volatile
    var dark: Boolean? = null

    @Volatile
    private var openLabel = ""

    @Volatile
    private var quitLabel = ""

    // Záloha bez nativní nabídky.
    private val openItem = MenuItem()
    private val quitItem = MenuItem()

    fun add(onOpen: () -> Unit, onQuit: () -> Unit): TrayIcon? = runCatching {
        val image = DesktopTray::class.java.getResourceAsStream("/plume-icon.png")?.use(ImageIO::read)?.let(DesktopDevMark::icon)
            ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val native = WindowsTrayMenu.available
        val menu = if (native) {
            null
        } else {
            openItem.addActionListener { onOpen() }
            quitItem.addActionListener { onQuit() }
            PopupMenu().apply {
                add(openItem)
                addSeparator()
                add(quitItem)
            }
        }
        TrayIcon(image, DesktopDevMark.name(APP_NAME), menu).apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    // Jedno kliknutí levým; pravé otevírá nabídku.
                    if (event.button == MouseEvent.BUTTON1) EventQueue.invokeLater(onOpen)
                }

                override fun mouseReleased(event: MouseEvent) {
                    // Jako ve Windows: nabídka po puštění pravého tlačítka.
                    if (native && event.button == MouseEvent.BUTTON3) {
                        WindowsTrayMenu.show(
                            groups = listOf(
                                listOf(WindowsTrayMenu.Item(openLabel, default = true, onClick = onOpen)),
                                listOf(WindowsTrayMenu.Item(quitLabel, onClick = onQuit)),
                            ),
                            dark = dark,
                        )
                    }
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
        openLabel = open
        quitLabel = quit
        openItem.label = open
        quitItem.label = quit
    }

    /** Jméno aplikace, ne text k překladu. */
    private const val APP_NAME = "Plume"
}
