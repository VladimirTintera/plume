package eu.tintera.plume.core.ui

import platform.UIKit.UIPasteboard

actual class TextClipboard {

    actual val confirmsCopy: Boolean = false

    actual suspend fun copy(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
