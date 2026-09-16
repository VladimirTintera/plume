package eu.tintera.plume.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService

actual class TextClipboard(private val context: Context) {

    actual fun copy(text: String) {
        val manager = context.getSystemService<ClipboardManager>() ?: return
        // Popisek vidí uživatel v nabídce schránky na starších Androidech; od 13 ho systém
        // ukazuje v potvrzovacím okně nad klávesnicí.
        manager.setPrimaryClip(ClipData.newPlainText("Zpráva", text))
    }
}
