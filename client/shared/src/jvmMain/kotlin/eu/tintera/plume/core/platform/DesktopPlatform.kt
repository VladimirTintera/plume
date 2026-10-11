package eu.tintera.plume.core.platform

import java.io.File

/** Operační systém, na kterém desktopová aplikace běží. Rozhoduje o cestách a o trezoru tajemství. */
internal enum class DesktopOs { MacOs, Windows, Linux, Other }

internal val desktopOs: DesktopOs = System.getProperty("os.name").orEmpty().lowercase().let { name ->
    when {
        "mac" in name -> DesktopOs.MacOs
        "windows" in name -> DesktopOs.Windows
        "linux" in name -> DesktopOs.Linux
        else -> DesktopOs.Other
    }
}

/**
 * Adresář dat aplikace: databáze TDLib, stažené soubory, nastavení, tajemství.
 *
 * Kde ho systém čeká (`~/Library/Application Support`, `%APPDATA%`, `$XDG_DATA_HOME`), ne
 * v domovském adresáři jako tečková složka. Vlastní cestu jde podstrčit vlastností
 * `-Dplume.dataDir=…` — ladicí běh z Gradle tak nesahá na data nainstalované aplikace.
 */
internal val desktopDataDirectory: File by lazy {
    val override = System.getProperty("plume.dataDir")?.takeIf { it.isNotBlank() }
    val home = System.getProperty("user.home")
    val directory = when {
        override != null -> File(override)
        desktopOs == DesktopOs.MacOs -> File(home, "Library/Application Support/Plume")
        desktopOs == DesktopOs.Windows -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Plume")
        else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "plume")
    }
    directory.apply { mkdirs() }
}

/** Ladicí běh (`./gradlew :client:desktopApp:run`), ne zabalená aplikace. Nastavuje ho desktopApp. */
internal val desktopDebugBuild: Boolean = System.getProperty("plume.debug") == "true"
