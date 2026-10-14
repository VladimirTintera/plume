package eu.tintera.plume.core.calls

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.MacNative
import java.nio.ByteBuffer

/**
 * JNI most k AVFoundation (`jvmMain/objc/PlumeCamera.m`): náhled kamery do spojení hovoru
 * a oprávnění kamery a mikrofonu.
 *
 * Jen v zabaleném `Plume.app` s popisy použití v Info.plist ([available]); holý `java`
 * z ladicího běhu by macOS při sáhnutí na kameru ukončil.
 */
internal object MacCamera {

    private val logger = Logger.withTag("MacCamera")

    val available: Boolean by lazy {
        MacNative.loaded && runCatching { nativeInit() }.onFailure { logger.w(it) { "Most ke kameře nejde nahrát." } }.getOrDefault(false)
    }

    /** Kam jdou snímky náhledu (BGRA, [ByteBuffer] platí jen během volání). */
    @Volatile
    private var sink: ((pixels: ByteBuffer, width: Int, height: Int, stride: Int) -> Unit)? = null

    /** Systémový dotaz, když se uživatel ještě nerozhodl; čeká na odpověď — ne z hlavního vlákna. */
    fun requestAccess(video: Boolean): Boolean = available && nativeRequestAccess(video)

    /** Spustí náhled; blokuje, dokud kamera nenaběhne. `false` = kamera není nebo nejde otevřít. */
    fun start(onFrame: (pixels: ByteBuffer, width: Int, height: Int, stride: Int) -> Unit): Boolean {
        if (!available) return false
        sink = onFrame
        return nativeStart().also { if (!it) sink = null }
    }

    /** Zastaví náhled; po návratu je kamera volná a žádný snímek už nepřijde. */
    fun stop() {
        if (!available) return
        nativeStop()
        sink = null
    }

    @JvmStatic
    fun onFrame(pixels: ByteBuffer, width: Int, height: Int, stride: Int) {
        sink?.invoke(pixels, width, height, stride)
    }

    @JvmStatic
    private external fun nativeInit(): Boolean

    @JvmStatic
    private external fun nativeRequestAccess(video: Boolean): Boolean

    @JvmStatic
    private external fun nativeStart(): Boolean

    @JvmStatic
    private external fun nativeStop()
}
