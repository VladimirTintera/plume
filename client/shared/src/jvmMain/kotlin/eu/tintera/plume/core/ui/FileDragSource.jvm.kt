package eu.tintera.plume.core.ui

import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.files.FileSelection
import java.io.File
import java.nio.file.Files

private val logger = Logger.withTag("FileDragSource")

actual val fileDragSupported: Boolean = true

/**
 * Desktop: `dragAndDropSource` Compose (tah levým tlačítkem myši po překročení slopu) s AWT
 * seznamem souborů ([FileSelection]) — Průzkumník, Finder i prohlížeč ho vezmou jako soubor.
 *
 * - **Jen kopírování** (`Copy`): přesun by cíl smazal zdroj, a to je soubor v úložišti TDLib.
 * - **Pod čitelným jménem jako tvrdý odkaz** ([stageDraggedFile]). Soubor musí být na disku hned při
 *   začátku tahu a ten běží na hlavním vlákně, takže kopírovat velké video nejde. Kde odkaz
 *   nejde (jiný svazek, souborový systém bez odkazů), táhne se soubor z TDLib pod jeho jménem.
 * - **Vlastní okno tah nepřijme** ([OutgoingFileDrag]): konverzace přijímá puštěné soubory jako
 *   přílohu a bez toho by se nad ní při každém tahu ven rozsvítila výzva k puštění přílohy
 *   a puštění zpátky do okna by soubor přiložilo ke zprávě.
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.fileDragSource(file: () -> DraggedFile?): Modifier = dragAndDropSource { offset ->
    val dragged = file() ?: return@dragAndDropSource null
    val staged = stageDraggedFile(dragged) ?: return@dragAndDropSource null
    OutgoingFileDrag.active = true
    DragAndDropTransferData(
        transferable = DragAndDropTransferable(FileSelection(staged)),
        supportedActions = listOf(DragAndDropTransferAction.Copy),
        // Náhled visí pod kurzorem tam, kde se ho uživatel chytil.
        dragDecorationOffset = offset,
        onTransferCompleted = { OutgoingFileDrag.active = false },
    )
}

/** Právě běží tah souboru z tohoto okna ven; drop target konverzace ho odmítne. */
internal object OutgoingFileDrag {
    @Volatile
    var active: Boolean = false
}

/**
 * Tvrdý odkaz pod jménem [DraggedFile.fileName] v `%TEMP%/plume-drag/<tah>/`. Každý tah má
 * adresář vlastní: cíl může soubor kopírovat ještě po puštění (Průzkumník na pozadí) a další tah
 * by mu ho jinak smazal pod rukama. Starší adresáře se mažou při dalším tahu — co cíl ještě
 * drží otevřené, se smazat nepovede a zůstane do příště.
 */
internal fun stageDraggedFile(file: DraggedFile, root: File = File(System.getProperty("java.io.tmpdir"), DRAG_DIRECTORY)): File? {
    val source = File(file.path)
    if (!source.isFile) return null
    return runCatching {
        root.listFiles()?.forEach { it.deleteRecursively() }
        val directory = File(root, System.nanoTime().toString()).apply { mkdirs() }
        val target = File(directory, file.fileName)
        Files.createLink(target.toPath(), source.toPath()).toFile()
    }.getOrElse {
        logger.i { "Odkaz na ${file.path} nejde vytvořit (${it.message}), táhne se soubor pod jménem z TDLib." }
        source
    }
}

private const val DRAG_DIRECTORY = "plume-drag"
