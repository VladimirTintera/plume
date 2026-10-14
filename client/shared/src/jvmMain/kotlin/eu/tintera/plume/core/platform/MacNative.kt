package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Nativní knihovna pro macOS (`jvmMain/objc/`): notifikace
 * ([eu.tintera.plume.core.notifications.MacNotificationCenter]) i titulek okna ([MacTitleBar]).
 * Tady se jen nahraje — co kdo z ní používá, si inicializuje sám: notifikace fungují jen
 * v zabaleném `Plume.app`, titulek i v ladicím běhu.
 */
internal object MacNative {

    private val logger = Logger.withTag("MacNative")

    val loaded: Boolean by lazy { desktopOs == DesktopOs.MacOs && load() }

    private fun load(): Boolean = runCatching {
        val resource = MacNative::class.java.getResource("/macos/$LIBRARY") ?: run {
            logger.w { "Nativní knihovna chybí v jaru (build mimo Mac?)." }
            return false
        }
        val directory = createTempDirectory("plume-native").toFile().apply { deleteOnExit() }
        val file = File(directory, LIBRARY).apply { deleteOnExit() }
        resource.openStream().use { input -> file.outputStream().use(input::copyTo) }
        System.load(file.absolutePath)
        true
    }.getOrElse {
        logger.w(it) { "Nativní knihovnu nejde nahrát." }
        false
    }

    private const val LIBRARY = "libplumenotifications.dylib"
}
