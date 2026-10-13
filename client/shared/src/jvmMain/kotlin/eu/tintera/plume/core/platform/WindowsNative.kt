package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger
import java.io.File

/**
 * Nativní knihovna pro Windows (`jvmMain/cpp/PlumeToasts.cpp`): toasty
 * ([eu.tintera.plume.core.notifications.WindowsToasts]) i spouštění po přihlášení
 * ([WindowsStartup]). Tady se jen nahraje — co kdo z ní používá, si inicializuje sám, aby
 * například čtení spouštění po přihlášení nezakládalo registraci toastů a nesahalo na zástupce.
 */
internal object WindowsNative {

    private val logger = Logger.withTag("WindowsNative")

    val loaded: Boolean by lazy { desktopOs == DesktopOs.Windows && load() }

    /**
     * Knihovna se rozbaluje do dat aplikace, ne do TEMPu: nahranou DLL Windows smazat nedovolí,
     * takže by `deleteOnExit` nezabralo a s každým spuštěním by v TEMPu přibyla kopie.
     */
    private fun load(): Boolean = runCatching {
        val file = File(desktopDataDirectory, "native/$LIBRARY")
        if (!copyResource("/windows/$LIBRARY", file)) {
            logger.w { "Nativní knihovna chybí v jaru (build bez MSVC?), oznámení půjdou přes AWT." }
            return false
        }
        System.load(file.absolutePath)
        true
    }.getOrElse {
        logger.w(it) { "Nativní knihovnu nejde nahrát." }
        false
    }

    /**
     * Zkopíruje prostředek z jaru, jen když se liší (nová verze aplikace). DLL, kterou má
     * nahranou jiná běžící instance, přepsat nejde; ta stejná se pak prostě použije.
     */
    fun copyResource(name: String, file: File): Boolean {
        val bytes = WindowsNative::class.java.getResourceAsStream(name)?.use { it.readBytes() } ?: return false
        if (!file.isFile || !file.readBytes().contentEquals(bytes)) {
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
        }
        return true
    }

    /**
     * Spouštěč z balíčku (`Plume.exe`), nebo `null` v ladicím běhu: holá `java` s classpath se
     * znovu spustit nedá — ani z toastu, ani po přihlášení.
     */
    val launcher: String? by lazy {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return@lazy null
        val name = File(command).name.lowercase()
        command.takeUnless { name == "java.exe" || name == "javaw.exe" }
    }

    private const val LIBRARY = "plumenotifications.dll"
}
