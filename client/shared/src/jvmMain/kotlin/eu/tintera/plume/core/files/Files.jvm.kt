package eu.tintera.plume.core.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ui.openInSystem
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Skia na desktopu HEIC nedekóduje; takové soubory otevře systém. Video přehraje FFmpeg. */
internal actual fun viewerUnsupportedFormats(): Set<DocumentFormat> = setOf(DocumentFormat.Heic)

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
 * soubor se sdílet nedá — místo toho je kopírování do schránky ([copy]) a „Uložit jako…"
 * ([rememberSaveAs]).
 */
actual class MediaSharer {

    private val logger = Logger.withTag("MediaSharer")

    actual suspend fun share(path: String, fileName: String): Boolean = false

    actual fun shareText(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)

    /**
     * Do schránky jde **seznam souborů** (Průzkumník a Finder ho vloží jako soubor, Outlook
     * a webové aplikace jako přílohu) a u obrázku navíc **obrázek** (Malování, Word, editor
     * fotek). Obrázek se čte ImageIO; co nepřečte (HEIC, WebP), jde jen jako soubor.
     */
    actual suspend fun copy(path: String, fileName: String, isImage: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(System.getProperty("java.io.tmpdir"), CLIPBOARD_DIRECTORY)
            // Předchozí kopie ve schránce už není; hromadit je by jen plnilo dočasný adresář.
            directory.deleteRecursively()
            directory.mkdirs()
            val copy = File(path).copyTo(File(directory, fileName), overwrite = true)
            val image = if (isImage) runCatching { ImageIO.read(copy) }.getOrNull() else null
            Toolkit.getDefaultToolkit().systemClipboard.setContents(FileSelection(copy, image), null)
            true
        }.getOrElse {
            logger.w(it) { "Soubor $path se nepodařilo zkopírovat do schránky." }
            false
        }
    }

    private companion object {
        const val CLIPBOARD_DIRECTORY = "plume-clipboard"
    }
}

/** Soubor ve schránce: seznam s jedním souborem a k obrázku i obrázek sám. */
private class FileSelection(private val file: File, private val image: Image?) : Transferable {

    private val flavors = listOfNotNull(DataFlavor.javaFileListFlavor, DataFlavor.imageFlavor.takeIf { image != null })

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

    override fun getTransferData(flavor: DataFlavor): Any = when {
        flavor == DataFlavor.javaFileListFlavor -> listOf(file)
        flavor == DataFlavor.imageFlavor && image != null -> image
        else -> throw UnsupportedFlavorException(flavor)
    }
}

actual val fileShareSupported: Boolean = false

actual val copiesAnyFile: Boolean = true

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

actual val autosavesToGallery: Boolean = false
