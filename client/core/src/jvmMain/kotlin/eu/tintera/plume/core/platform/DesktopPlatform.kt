package eu.tintera.plume.core.platform

import java.io.File

/** Operační systém, na kterém desktopová aplikace běží. Rozhoduje o cestách a o trezoru tajemství. */
enum class DesktopOs { MacOs, Windows, Linux, Other }

val desktopOs: DesktopOs = System.getProperty("os.name").orEmpty().lowercase().let { name ->
    when {
        "mac" in name -> DesktopOs.MacOs
        "windows" in name -> DesktopOs.Windows
        "linux" in name -> DesktopOs.Linux
        else -> DesktopOs.Other
    }
}

/** Architektura JVM (a tedy přibalených nativních knihoven): Apple Silicon / ARM Windows. */
val desktopArm64: Boolean = System.getProperty("os.arch").orEmpty().lowercase().let { "aarch64" in it || "arm64" in it }

/**
 * Adresář dat aplikace: databáze TDLib, stažené soubory, nastavení, tajemství.
 *
 * Kde ho systém čeká (`~/Library/Application Support`, `%APPDATA%`, `$XDG_DATA_HOME`), ne
 * v domovském adresáři jako tečková složka. Vlastní cestu jde podstrčit vlastností
 * `-Dplume.dataDir=…` — ladicí běh z Gradle tak nesahá na data nainstalované aplikace.
 */
val desktopDataDirectory: File by lazy {
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
val desktopDebugBuild: Boolean = System.getProperty("plume.debug") == "true"

/**
 * Tréninkový běh při balení (Compose 1.13, `aot` v `client/desktopApp/build.gradle.kts`): plugin
 * spustí zabalenou aplikaci s `-Dcompose.aot.training-run=true`, JVM si zapíše, které třídy
 * načetla, a z toho vznikne AOT cache, se kterou pak aplikace startuje rychleji.
 *
 * Běh se nesmí dotknout ničeho mimo vlastní dočasný adresář dat — na vývojářském Macu by jinak
 * sahal na Klíčenku, oznámení a spouštění po přihlášení nainstalované aplikace. Viz
 * „Rychlejší start (AOT cache)" v `docs/poznatky/desktop.md`.
 */
val desktopTrainingRun: Boolean = System.getProperty("compose.aot.training-run") == "true"
