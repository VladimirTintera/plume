package eu.tintera.plume.core.platform

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

    @JvmStatic
    private external fun nativeApply(title: String, dark: Boolean, red: Float, green: Float, blue: Float)
}
