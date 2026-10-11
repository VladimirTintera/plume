package eu.tintera.plume.core.ui

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual class TextClipboard {

    actual val confirmsCopy: Boolean = false

    actual suspend fun copy(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
