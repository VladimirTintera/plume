package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger

/**
 * Windows: záznam `Plume` v `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` s příkazem
 * `"…\Plume.exe" --autostart` — vstupní bod pak okno rovnou schová do oznamovací oblasti, ať po
 * přihlášení nevyskakuje. macOS a Linux zatím ne (macOS chce `SMAppService` v nativním mostu,
 * Linux soubor v `~/.config/autostart`; obojí neověřitelné na stroji, kde se to psalo).
 */
actual class LaunchAtLogin {

    private val logger = Logger.withTag("LaunchAtLogin")

    actual val supported: Boolean by lazy {
        desktopOs == DesktopOs.Windows && WindowsNative.launcher != null && WindowsNative.loaded
    }

    actual fun isEnabled(): Boolean = supported && WindowsStartup.nativeCommand(ENTRY) != null

    actual fun setEnabled(enabled: Boolean): Boolean {
        if (!supported) return false
        val command = if (enabled) autostartCommand(WindowsNative.launcher ?: return false) else null
        val result = WindowsStartup.nativeSetCommand(ENTRY, command)
        if (result != 0) logger.w { "Spouštění po přihlášení nejde ${if (enabled) "zapnout" else "vypnout"}: 0x${result.toUInt().toString(16)}." }
        return result == 0
    }

    /**
     * Záznam míří na spouštěč, ze kterého aplikace běží. Po přeinstalaci jinam by ukazoval do
     * prázdna, proto se při startu přepíše, když se liší — jen pokud je zapnutý.
     */
    fun refreshCommand() {
        if (!supported) return
        val current = WindowsStartup.nativeCommand(ENTRY) ?: return
        val expected = autostartCommand(WindowsNative.launcher ?: return)
        if (current != expected) WindowsStartup.nativeSetCommand(ENTRY, expected)
    }

    private companion object {
        /**
         * Jméno hodnoty v `Run` — pod ním aplikaci ukazuje i Nastavení → Aplikace → Po spuštění.
         * Ladicí balíček (`runDistributable -PdesktopDebug`) má vlastní, ať nepřepíše instalaci.
         */
        val ENTRY = if (desktopDebugBuild) "Plume (debug)" else "Plume"
    }
}

/** Příkaz pro spuštění po přihlášení; [DesktopStartup.AUTOSTART_ARGUMENT] čte vstupní bod. */
internal fun autostartCommand(launcher: String): String = "\"$launcher\" ${DesktopStartup.AUTOSTART_ARGUMENT}"

/** Nativní funkce v `PlumeToasts.cpp`; knihovnu nahrává [WindowsNative]. */
internal object WindowsStartup {

    /** Příkaz záznamu, nebo `null`, když záznam není nebo ho uživatel vypnul ve Windows. */
    @JvmStatic
    external fun nativeCommand(name: String): String?

    /** Zapíše záznam ([command] != null) nebo ho smaže. HRESULT, 0 = v pořádku. */
    @JvmStatic
    external fun nativeSetCommand(name: String, command: String?): Int
}

/** Jak byla aplikace spuštěná; čte vstupní bod. */
object DesktopStartup {
    const val AUTOSTART_ARGUMENT = "--autostart"

    /** Spuštěná po přihlášení: okno se rovnou schová do oznamovací oblasti, kde ta je. */
    fun startsHidden(args: Array<String>): Boolean = AUTOSTART_ARGUMENT in args && desktopClosesToTray
}
