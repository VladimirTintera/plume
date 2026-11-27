package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Titulek okna na macOS ve zvoleném motivu (`jvmMain/objc/PlumeWindow.m`): světlý / tmavý
 * vzhled a barva plochy okna, se kterou titulek splyne. Obdoba DWM na Windows.
 */
internal object MacTitleBar {

    /**
     * Počká, až má okno `NSWindow` (AWT ho zakládá až při prvním zobrazení — okno spuštěné
     * schované po přihlášení ho zpočátku nemá), a nastaví mu vzhled. Barva je sRGB.
     */
    suspend fun apply(window: Window, dark: Boolean, red: Float, green: Float, blue: Float) {
        if (!MacNative.loaded) return
        window.awaitDisplayable()
        val title = (window as? java.awt.Frame)?.title ?: return
        nativeApply(title, dark, red, green, blue)
    }

    private suspend fun Window.awaitDisplayable() {
        if (isDisplayable) return
        suspendCancellableCoroutine { continuation ->
            val listener = object : WindowAdapter() {
                override fun windowOpened(event: WindowEvent) {
                    removeWindowListener(this)
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            addWindowListener(listener)
            continuation.invokeOnCancellation { removeWindowListener(listener) }
            // Mohlo se otevřít mezi kontrolou a přidáním posluchače.
            if (isDisplayable) {
                removeWindowListener(listener)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    /**
     * Začne tah oknem za myší, dokud je stisknuté levé tlačítko. Na Macu jediná cesta, jak tah
     * za pruh titulku začíná (`DesktopTitleBar`).
     */
    fun startDrag(window: java.awt.Frame) = call("Tah oknem") { nativeStartDrag(window.title) }

    /** Dvojklik na pruh titulku: zvětšení / minimalizace podle nastavení systému. */
    fun titleBarDoubleClick(window: java.awt.Frame) = call("Dvojklik na titulek") { nativeTitleBarDoubleClick(window.title) }

    private inline fun call(what: String, block: () -> Unit) {
        if (!MacNative.loaded) return
        runCatching(block).onFailure { Logger.withTag("TitleBar").w(it) { "$what: nativní volání selhalo" } }
    }

    @JvmStatic
    private external fun nativeStartDrag(title: String)

    @JvmStatic
    private external fun nativeTitleBarDoubleClick(title: String)

    @JvmStatic
    private external fun nativeApply(title: String, dark: Boolean, red: Float, green: Float, blue: Float)
}
