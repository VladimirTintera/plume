package eu.tintera.plume.core.update

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopOs
import eu.tintera.plume.core.ui.openInSystem
import java.io.File
import java.nio.file.Files

/**
 * Instalace stažené aktualizace. Běžící aplikace sama sebe přepsat nemůže (Windows soubory
 * běžícího procesu zamyká, na macOS by se měnil bundle pod rukama), proto ji dělá samostatný
 * proces: počká, až aplikace skončí, nainstaluje a spustí ji znovu. Proces aplikaci přežije —
 * ani Windows, ani macOS děti s rodičem neukončují.
 *
 * - **Windows: `msiexec /i … /passive`** (jen proužek průběhu, bez otázek). Instalace je jen
 *   pro uživatele (`perUserInstall`), takže bez UAC; stejný `upgradeUuid` znamená, že MSI starou
 *   verzi nahradí. Čeká a spouští PowerShell — je v každých Windows 10/11 a `-Command` se
 *   execution policy netýká.
 * - **macOS: obsah DMG přes `hdiutil attach` a `ditto` místo starého bundlu**, výměna přes
 *   `.new` / `.old`, ať po chybě zůstane stará aplikace. Soubor stažený aplikací nemá atribut
 *   karantény (ten dávají prohlížeče), takže Gatekeeper se znovu neptá. Kam se psát nedá
 *   (aplikace mimo Programy bez práv, spuštěná rovnou z DMG nebo přemístěná systémem —
 *   App Translocation), vrátí `false` a DMG se jen otevře ve Finderu.
 *
 * Výstup skriptu jde do `install.log` vedle balíčku; po úspěšné aktualizaci ho smaže první
 * `check` nové verze spolu s balíčkem.
 */
internal object DesktopInstaller {

    private val logger = Logger.withTag("DesktopInstaller")

    /** `true` = instalátor běží a aplikace má skončit. */
    fun start(installer: File, directory: File): Boolean {
        val launcher = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile } ?: return false
        val pid = ProcessHandle.current().pid().toString()
        val command = when (desktopOs) {
            DesktopOs.Windows -> listOf(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-Command", windowsScript(pid, installer.absolutePath, launcher.absolutePath),
            )
            DesktopOs.MacOs -> {
                val bundle = macBundle(launcher) ?: return false
                if (!writable(bundle)) {
                    logger.i { "Do ${bundle.path} se zapsat nedá, DMG otevřu ve Finderu." }
                    return false
                }
                listOf("/bin/sh", "-c", MAC_SCRIPT, "plume-update", pid, installer.absolutePath, bundle.absolutePath)
            }
            else -> return false
        }
        val log = File(directory, "install.log")
        ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        return true
    }

    /** Záložní cesta: balíček otevře systém (MSI spustí instalátor, DMG připojí Finder). */
    fun openManually(installer: File) {
        runCatching { openInSystem(installer.toURI()) }
            .onFailure { logger.w(it) { "Balíček aktualizace nejde otevřít." } }
    }

    /** `…/Plume.app/Contents/MacOS/Plume` → `…/Plume.app`. */
    internal fun macBundle(launcher: File): File? =
        launcher.parentFile?.parentFile?.parentFile?.takeIf { it.name.endsWith(".app") && it.isDirectory }

    private fun writable(bundle: File): Boolean {
        val parent = bundle.parentFile ?: return false
        return Files.isWritable(parent.toPath()) && Files.isWritable(bundle.toPath()) && "/AppTranslocation/" !in bundle.path
    }

    /**
     * Počká na konec aplikace, nainstaluje MSI a spustí aplikaci znovu. Řetězce v apostrofech
     * (`'…'`), uvnitř zdvojený apostrof — v cestě se může objevit (`C:\Users\O'Brien`).
     */
    internal fun windowsScript(pid: String, msi: String, launcher: String): String = listOf(
        "Wait-Process -Id $pid -ErrorAction SilentlyContinue",
        "Start-Process -FilePath 'msiexec.exe' -ArgumentList ${ps("/i \"$msi\" /passive /norestart")} -Wait",
        "Start-Process -FilePath ${ps(launcher)}",
    ).joinToString("; ")

    private fun ps(text: String) = "'" + text.replace("'", "''") + "'"

    /** Argumenty: `$1` pid aplikace, `$2` DMG, `$3` bundle aplikace. */
    private val MAC_SCRIPT = """
        pid="${'$'}1"; dmg="${'$'}2"; app="${'$'}3"
        while kill -0 "${'$'}pid" 2>/dev/null; do sleep 0.2; done
        mnt="${'$'}(mktemp -d "${'$'}{TMPDIR:-/tmp}/plume-update.XXXXXX")" || exit 1
        if hdiutil attach -nobrowse -readonly -noautoopen -mountpoint "${'$'}mnt" "${'$'}dmg"; then
          src="${'$'}(find "${'$'}mnt" -maxdepth 1 -name '*.app' | head -n 1)"
          rm -rf "${'$'}app.new" "${'$'}app.old"
          if [ -n "${'$'}src" ] && ditto "${'$'}src" "${'$'}app.new"; then
            if mv "${'$'}app" "${'$'}app.old"; then
              if mv "${'$'}app.new" "${'$'}app"; then rm -rf "${'$'}app.old"; else mv "${'$'}app.old" "${'$'}app"; fi
            fi
          fi
          rm -rf "${'$'}app.new"
          hdiutil detach -quiet "${'$'}mnt" || hdiutil detach -quiet -force "${'$'}mnt"
        fi
        rmdir "${'$'}mnt" 2>/dev/null
        open "${'$'}app"
    """.trimIndent()
}
