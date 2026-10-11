package eu.tintera.plume.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import eu.tintera.plume.App
import eu.tintera.plume.core.telegram.preloadTdlibNativeDependencies
import eu.tintera.plume.di.startKoin
import javax.imageio.ImageIO

/**
 * Vstupní bod desktopové aplikace. Stejně tenký jako `:client:androidApp`: všechno ostatní je
 * v `:client:shared`.
 *
 * Pořadí je povinné: OpenSSL pro TDLib se nahrává dřív, než ho `startKoin` poprvé použije
 * (spouští TDLib hned, viz `startKoin`), jinak se nativní knihovna na macOS nenačte.
 */
fun main() {
    preloadTdlibNativeDependencies()
    startKoin()

    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("plume-icon.png")
        ?.use { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            icon = icon,
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            App()
        }
    }
}

/** Jméno aplikace, ne text k překladu — stejné v každém jazyce. */
private const val APP_NAME = "Plume"
