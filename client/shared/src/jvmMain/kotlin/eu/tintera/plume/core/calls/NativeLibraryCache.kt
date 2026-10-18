package eu.tintera.plume.core.calls

import co.touchlab.kermit.Logger
import com.sun.jna.Platform
import java.io.File
import java.net.JarURLConnection
import java.net.URL

/**
 * Nativní knihovna přibalená v resources (pod prefixem JNA, `darwin-aarch64/libntgcalls.dylib`)
 * vybalená JEDNOU do dat aplikace, ne při každém spuštění.
 *
 * `Native.load(jméno)` najde knihovnu v resources a pokaždé ji zkopíruje do dočasné složky —
 * u `libntgcalls` 35 MB při každém prvním hovoru po startu, a dočasné kopie po pádu zůstávaly.
 * Tady se vybalí do `<data>/natives/<razítko>/` a další spuštění ji jen načte.
 *
 * - **Razítko je z položky v jaru** (velikost a CRC, `JarURLConnection.jarEntry`) — nová verze
 *   knihovny v nové verzi aplikace dostane nový adresář a stará se smaže. Počítat hash 35 MB
 *   při každém startu by stálo víc, než vybalení ušetří; CRC už v jaru je. Mimo jar (ladicí běh
 *   z Gradlu) velikost a čas změny souboru.
 * - **Kopie jde přes dočasný soubor a přejmenování** — přerušené vybalení nenechá napůl zapsanou
 *   knihovnu, kterou by příští start načetl.
 * - Když cokoli selže, vrátí `null` a volající nahraje knihovnu postaru přes JNA.
 */
internal fun cachedNativeLibrary(name: String, dataDirectory: File, logger: Logger): File? = runCatching {
    val fileName = System.mapLibraryName(name)
    val url = NativeLibraryAnchor::class.java.classLoader.getResource("${Platform.RESOURCE_PREFIX}/$fileName")
        ?: return null
    val root = File(dataDirectory, NATIVES_DIRECTORY)
    val target = File(File(root, stampOf(url)), fileName)
    if (!target.isFile) {
        target.parentFile.mkdirs()
        val partial = File(target.parentFile, "$fileName.part")
        url.openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        check(partial.renameTo(target)) { "Knihovnu $fileName se nepodařilo přejmenovat." }
        logger.i { "Knihovna $fileName vybalena do ${target.parentFile}" }
    }
    // Starší verze téže knihovny (z předchozí verze aplikace) už nikdo nenačte.
    root.listFiles()?.filter { it.isDirectory && it != target.parentFile && File(it, fileName).exists() }
        ?.forEach { it.deleteRecursively() }
    target
}.onFailure { logger.w(it) { "Knihovnu $name nešlo vybalit do dat aplikace, načte se přes JNA." } }.getOrNull()

private fun stampOf(url: URL): String = when (val connection = url.openConnection()) {
    is JarURLConnection -> connection.jarEntry.let { "${it.size}-${it.crc.toString(16)}" }
    else -> "${connection.contentLengthLong}-${connection.lastModified}"
}

/** Třída jen kvůli classloaderu, ze kterého se čtou resources. */
private object NativeLibraryAnchor

private const val NATIVES_DIRECTORY = "natives"
