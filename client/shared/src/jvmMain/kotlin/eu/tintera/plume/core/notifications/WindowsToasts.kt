package eu.tintera.plume.core.notifications

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopDebugBuild
import eu.tintera.plume.core.platform.desktopOs
import java.io.File

/**
 * JNI most k toastům Windows (`jvmMain/cpp/PlumeToasts.cpp`).
 *
 * Aplikace z MSI není zabalená (MSIX), takže si identitu pro toasty zakládá sama: AUMID na
 * zástupci ve Startu (bez něj Windows 11 toast nevykreslí), v `HKCU\Software\Classes\AppUserModelId`
 * a COM třídu, kterou Windows zavolá po kliknutí. Ladicí běh má vlastní AUMID, třídu i zástupce,
 * ať nepřepisuje registraci nainstalované aplikace (viz docs/poznatky/desktop.md,
 * „Notifikace na Windows"). Inicializace musí proběhnout dřív, než vznikne okno.
 */
internal object WindowsToasts {

    private val logger = Logger.withTag("WindowsToasts")

    /**
     * Klik na toast: argumenty `launch` z XML. Kliknutí, které přijde dřív, než si ho někdo
     * převezme (studený start z Centra oznámení), počká v [pending].
     */
    var onActivated: ((String) -> Unit)? = null
        set(value) {
            val waiting = synchronized(lock) {
                field = value
                pending.also { pending = null }
            }
            if (value != null && waiting != null) value(waiting)
        }

    private val lock = Any()
    private var pending: String? = null

    val available: Boolean by lazy { desktopOs == DesktopOs.Windows && load() && init() }

    /**
     * Knihovna se rozbaluje do dat aplikace, ne do TEMPu: nahranou DLL Windows smazat nedovolí,
     * takže by `deleteOnExit` nezabralo a s každým spuštěním by v TEMPu přibyla kopie.
     */
    private fun load(): Boolean = runCatching {
        val file = File(desktopDataDirectory, "native/$LIBRARY")
        if (!copyResource("/windows/$LIBRARY", file)) {
            logger.w { "Knihovna toastů chybí v jaru (build bez MSVC?), oznámení půjdou přes AWT." }
            return false
        }
        System.load(file.absolutePath)
        true
    }.getOrElse {
        logger.w(it) { "Knihovnu toastů nejde nahrát." }
        false
    }

    private fun init(): Boolean {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return false
        val result = nativeInit(
            aumid = if (desktopDebugBuild) "$AUMID.debug" else AUMID,
            displayName = DISPLAY_NAME,
            iconPath = iconFile()?.absolutePath,
            clsid = if (desktopDebugBuild) DEBUG_ACTIVATOR else ACTIVATOR,
            serverCommand = serverCommand(command),
            shortcutPath = shortcut().absolutePath,
            shortcutTarget = command,
        )
        if (result != 0) logger.w { "Toasty nejde zaregistrovat: 0x${result.toUInt().toString(16)}." }
        return result == 0
    }

    /**
     * Zástupce ve Startu, který ponese AUMID. Instalace z MSI ho zakládá sama
     * (`Programs\Plume\Plume.lnk`) a ten se jen doplní; bez MSI (rozbalená distribuce, ladicí
     * běh) vznikne vlastní. Ladicí běh má zástupce zvlášť, aby nepřepsal identitu instalace.
     */
    private fun shortcut(): File {
        val appData = System.getenv("APPDATA") ?: "${System.getProperty("user.home")}\\AppData\\Roaming"
        val programs = File(appData, "Microsoft\\Windows\\Start Menu\\Programs")
        val name = "$DISPLAY_NAME.lnk"
        if (desktopDebugBuild) return File(programs, name)
        return programs.walk().maxDepth(2).firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }
            ?: File(programs, name)
    }

    /**
     * Ikona do nastavení oznámení ve Windows a do hlavičky toastu. Registr chce cestu na disku,
     * takže se ikona z jaru jednou zkopíruje do dat aplikace.
     */
    private fun iconFile(): File? = runCatching {
        // Ikonu nese vstupní bod (`:client:desktopApp`), tady je na classpath za běhu.
        File(desktopDataDirectory, "notification-icon.png").takeIf { copyResource("/plume-icon.png", it) }
    }.getOrNull()

    /**
     * Zkopíruje prostředek z jaru, jen když se liší (nová verze aplikace). DLL, kterou má
     * nahranou jiná běžící instance, přepsat nejde; ta stejná se pak prostě použije.
     */
    private fun copyResource(name: String, file: File): Boolean {
        val bytes = WindowsToasts::class.java.getResourceAsStream(name)?.use { it.readBytes() } ?: return false
        if (!file.isFile || !file.readBytes().contentEquals(bytes)) {
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
        }
        return true
    }

    /**
     * Čím Windows aplikaci spustí po kliknutí na toast, když neběží: spouštěč z MSI
     * (`Plume.exe`). Ladicí běh z Gradle je holý `java` s classpath, ten se spustit znovu nedá —
     * tam kliknutí funguje jen za běhu.
     */
    private fun serverCommand(command: String): String? {
        val name = File(command).name.lowercase()
        if (name == "java.exe" || name == "javaw.exe") return null
        return "\"$command\" $ACTIVATION_ARGUMENT"
    }

    @JvmStatic
    fun onActivated(arguments: String) {
        val handler = synchronized(lock) { onActivated.also { if (it == null) pending = arguments } }
        handler?.invoke(arguments)
    }

    /** Vrací HRESULT, 0 = v pořádku. */
    @JvmStatic
    external fun nativeInit(
        aumid: String,
        displayName: String,
        iconPath: String?,
        clsid: String,
        serverCommand: String?,
        shortcutPath: String,
        shortcutTarget: String,
    ): Int

    /** 0 = povoleno, 1 = vypnuto pro aplikaci, 2 = vypnutá všechna oznámení. */
    @JvmStatic
    external fun nativeSetting(): Int

    @JvmStatic
    external fun nativeShow(xml: String, tag: String, group: String): Int

    @JvmStatic
    external fun nativeRemove(tags: Array<String>, group: String): Int

    @JvmStatic
    external fun nativeRemoveAll(): Int

    private const val LIBRARY = "plumenotifications.dll"
    private const val AUMID = "eu.tintera.plume"
    private val DISPLAY_NAME = if (desktopDebugBuild) "Plume (debug)" else "Plume"

    /** COM třídy aktivátoru. NESMÍ SE MĚNIT — toasty v Centru oznámení by po aktualizaci nešly otevřít. */
    private const val ACTIVATOR = "{c5ef2be3-fe5d-474b-803c-dd0c88ca0d7d}"
    private const val DEBUG_ACTIVATOR = "{59c8b0d6-81e8-4c96-98b2-7176a54dc380}"

    /** Argument, se kterým Windows spouští COM server. Aplikace ho jen ignoruje. */
    const val ACTIVATION_ARGUMENT = "-ToastActivated"
}
