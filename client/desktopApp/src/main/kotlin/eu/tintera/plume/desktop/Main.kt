package eu.tintera.plume.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import eu.tintera.plume.App
import eu.tintera.plume.core.platform.DesktopDevMark
import eu.tintera.plume.core.platform.DesktopInstance
import eu.tintera.plume.core.platform.DesktopStartup
import eu.tintera.plume.core.platform.DesktopTrayIcon
import eu.tintera.plume.core.platform.DesktopWindow
import eu.tintera.plume.core.platform.LaunchAtLogin
import eu.tintera.plume.core.platform.desktopClosesToTray
import eu.tintera.plume.core.telegram.preloadTdlibNativeDependencies
import eu.tintera.plume.core.ui.rememberDesktopShortcutHandler
import eu.tintera.plume.di.startKoin
import eu.tintera.plume.features.calls.IncomingCallWindow
import io.github.kdroidfilter.platformtools.darkmodedetector.mac.setMacOsAdaptiveTitleBar
import java.awt.Desktop
import java.awt.desktop.AppReopenedListener
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.imageio.ImageIO

/**
 * Vstupní bod desktopové aplikace. Stejně tenký jako `:client:androidApp`: všechno ostatní je
 * v `:client:shared`.
 *
 * Pořadí je povinné: napřed jediná instance (druhá by narazila na zamčenou databázi TDLib),
 * pak OpenSSL pro TDLib dřív, než ho `startKoin` poprvé použije (spouští TDLib hned, viz
 * `startKoin`), jinak se nativní knihovna na macOS nenačte.
 */
fun main(args: Array<String>) {
    // Plume už běží (třeba schovaná v oznamovací oblasti): ta vytáhne okno, tahle končí.
    if (!DesktopInstance.acquire()) return
    preloadTdlibNativeDependencies()
    // macOS: titulek podle systémového vzhledu do chvíle, než ho `WindowTitleBarAppearance`
    // přebarví podle motivu aplikace (jinak by do té doby svítil bílý). Musí být před vznikem okna.
    setMacOsAdaptiveTitleBar()
    startKoin()
    // Spouštění po přihlášení míří na spouštěč; po přeinstalaci jinam se cesta opraví.
    LaunchAtLogin().refreshCommand()
    // Po přihlášení (`--autostart`) rovnou do oznamovací oblasti — okno nemá vyskakovat.
    val startHidden = DesktopStartup.startsHidden(args)

    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("plume-icon.png")
        ?.use { BitmapPainter(DesktopDevMark.icon(ImageIO.read(it)).toComposeImageBitmap()) }
    val mac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    application {
        var visible by remember { mutableStateOf(!startHidden) }
        val state = rememberWindowState(width = 1200.dp, height = 800.dp)

        // Ťuknutí na ikonu v Docku vrátí schované okno (viz `onCloseRequest`).
        LaunchedEffect(Unit) {
            if (mac && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().addAppEventListener(AppReopenedListener { visible = true })
            }
        }

        // Popředí aplikace (online, zámek): okno je vidět, ne schované ani minimalizované.
        LaunchedEffect(Unit) {
            snapshotFlow { visible && !state.isMinimized }.collect(DesktopWindow::setShown)
        }

        // Windows: ikona v oznamovací oblasti, kam se okno schovává (viz `onCloseRequest`).
        DesktopTrayIcon(onOpen = DesktopWindow::activate, onQuit = { exitApplication() })

        // Příchozí hovor, když hlavní okno není vpředu: okénko navrchu s Přijmout / Odmítnout.
        IncomingCallWindow()

        // Klávesové zkratky (Ctrl/⌘+K, Alt+šipky…) dřív, než klávesu dostane zaostřený prvek.
        val shortcuts = rememberDesktopShortcutHandler()

        Window(
            onPreviewKeyEvent = shortcuts,
            // Na macOS a Windows zavření okna aplikaci neukončí, jen okno schová — jako
            // Telegram nebo Zprávy. TDLib běží dál a oznámení chodí. Vrací ho Dock, ikona
            // v oznamovací oblasti, klik na oznámení nebo další spuštění; ukončuje se Cmd+Q,
            // resp. z nabídky ikony. Na Linuxu oznamovací oblast být nemusí, tam zavření = konec.
            onCloseRequest = { if (mac || desktopClosesToTray) visible = false else exitApplication() },
            visible = visible,
            // Ladicí běh z Gradle se značkou v názvu i ikoně, ať se nesplete s nainstalovanou aplikací.
            title = DesktopDevMark.name(APP_NAME),
            icon = icon,
            state = state,
        ) {
            // Klik na oznámení: okno dopředu, i když bylo schované nebo minimalizované.
            LaunchedEffect(Unit) {
                DesktopWindow.activations.collect {
                    visible = true
                    state.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            DisposableEffect(window) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(event: WindowEvent) = DesktopWindow.setFocused(true)

                    override fun windowLostFocus(event: WindowEvent) = DesktopWindow.setFocused(false)
                }
                window.addWindowFocusListener(listener)
                DesktopWindow.setFocused(window.isFocused)
                // Tlačítko okna na hlavním panelu: odznak nepřečtených a blikání (DesktopTaskbar).
                DesktopWindow.attach(window)
                onDispose {
                    window.removeWindowFocusListener(listener)
                    DesktopWindow.setFocused(false)
                    DesktopWindow.attach(null)
                }
            }
            App()
        }
    }
}

/** Jméno aplikace, ne text k překladu — stejné v každém jazyce. */
private const val APP_NAME = "Plume"
