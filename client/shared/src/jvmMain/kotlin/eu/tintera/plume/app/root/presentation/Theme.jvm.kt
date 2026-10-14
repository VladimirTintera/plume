package eu.tintera.plume.app.root.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import co.touchlab.kermit.Logger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import eu.tintera.plume.core.platform.DesktopWindow
import io.github.kdroidfilter.platformtools.darkmodedetector.windows.setWindowsAdaptiveTitleBar

/** Systémové lišty s ikonami má jen telefon; na desktopu je jejich obdobou titulek okna. */
@Composable
actual fun SystemBarsAppearance(isDark: Boolean) = Unit

/**
 * Titulek okna Windows ve zvoleném motivu, ne v systémovém: bílý systémový titulek nad tmavou
 * aplikací byl nejrušivější prvek okna.
 *
 * - **Tmavý režim titulku** (`DWMWA_USE_IMMERSIVE_DARK_MODE`) nastavuje platformtools. Podle něj
 *   kreslí systém tlačítka minimalizace / maximalizace / zavření.
 * - **Barva titulku a jeho textu** (`DWMWA_CAPTION_COLOR`, `DWMWA_TEXT_COLOR`, Windows 11) je
 *   plocha okna aplikace, takže titulek s obsahem splyne. Tmavý titulek sám o sobě je šedý
 *   (#202020) a vedle tónované palety působil jako cizí pruh. Na Windows 10 atribut neexistuje,
 *   volání vrátí chybu a zůstane tmavý titulek.
 *
 * macOS bere vzhled titulku ze systémové vlastnosti nastavené před vznikem okna (vstupní bod).
 */
@Composable
actual fun WindowTitleBarAppearance(isDark: Boolean, background: Color, content: Color) {
    if (!windows) return
    val window by DesktopWindow.window.collectAsState()
    val current = window ?: return
    current.setWindowsAdaptiveTitleBar(dark = isDark)
    LaunchedEffect(current, background, content) {
        val hwnd = Native.getComponentPointer(current) ?: return@LaunchedEffect
        runCatching {
            Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, IntByReference(background.colorRef()).pointer, 4)
            Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, IntByReference(content.colorRef()).pointer, 4)
        }.onFailure { Logger.withTag("TitleBar").w(it) { "Barvu titulku okna se nepodařilo nastavit" } }
    }
}

private val windows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")

/** COLORREF je `0x00BBGGRR`, tedy obrácené pořadí než ARGB. */
private fun Color.colorRef(): Int {
    val argb = toArgb()
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    return (b shl 16) or (g shl 8) or r
}

private const val DWMWA_CAPTION_COLOR = 35
private const val DWMWA_TEXT_COLOR = 36

@Suppress("FunctionName")
private interface Dwm : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int

    companion object {
        val INSTANCE: Dwm by lazy { Native.load("dwmapi", Dwm::class.java) }
    }
}
