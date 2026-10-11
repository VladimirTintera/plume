package eu.tintera.plume.core.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ui.openInSystem
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Skia na desktopu HEIC nedekóduje a přehrávač videa zatím není; takové soubory otevře systém. */
internal actual fun viewerUnsupportedFormats(): Set<DocumentFormat> =
    DocumentFormat.entries.filter { it.kind == DocumentKind.Video }.toSet() + DocumentFormat.Heic

/** Desktop: výchozí aplikace systému pro daný typ souboru. */
actual class FileOpener {

    private val logger = Logger.withTag("FileOpener")

    actual fun open(path: String, mimeType: String): Boolean = runCatching {
        val desktop = Desktop.getDesktop().takeIf { Desktop.isDesktopSupported() }
        if (desktop?.isSupported(Desktop.Action.OPEN) == true) desktop.open(File(path)) else openInSystem(File(path).toURI())
        true
    }.getOrElse {
        logger.w(it) { "Soubor nejde otevřít: $path" }
        false
    }
}

/**
 * Desktop galerii nemá; „Uložit do galerie" ukládá do Stažených souborů, pod jménem
 * [savedFileName]. Stejné jméno se nepřepíše, dostane pořadové číslo.
 */
actual class MediaSaver {

    private val logger = Logger.withTag("MediaSaver")

    actual suspend fun save(path: String, fileName: String, isVideo: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
            File(path).copyTo(uniqueFile(directory, fileName))
            true
        }.getOrElse {
            logger.w(it) { "Soubor se nepodařilo uložit: $path" }
            false
        }
    }
}

private fun uniqueFile(directory: File, fileName: String): File {
    val base = fileName.substringBeforeLast('.')
    val extension = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    return generateSequence(1) { it + 1 }
        .map { index -> File(directory, if (index == 1) fileName else "$base ($index)$extension") }
        .first { !it.exists() }
}

@Composable
actual fun rememberSavePermission(): SavePermission = remember {
    object : SavePermission {
        override fun request(onResult: (granted: Boolean) -> Unit) = onResult(true)
    }
}

/**
 * Systémový dialog sdílení desktop z JVM nedostane. Sdílení textu ho proto nahrazuje schránkou;
 * soubor se sdílet nedá — místo toho je „Uložit jako…" ([rememberSaveAs]).
 */
actual class MediaSharer {

    actual suspend fun share(path: String, fileName: String): Boolean = false

    actual fun shareText(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)
}

@Composable
actual fun rememberSaveAs(): SaveAsLauncher? = remember {
    object : SaveAsLauncher {
        override fun launch(path: String, fileName: String, onResult: (saved: Boolean?) -> Unit) {
            val dialog = FileDialog(null as Frame?, null, FileDialog.SAVE).apply { file = fileName }
            dialog.isVisible = true
            val name = dialog.file ?: return onResult(null)
            val target = File(dialog.directory, name)
            onResult(runCatching { File(path).copyTo(target, overwrite = true) }.isSuccess)
        }
    }
}

actual val saveAsSupported: Boolean = true
