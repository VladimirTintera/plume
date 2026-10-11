package eu.tintera.plume.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.clipboard_label

actual class TextClipboard(private val context: Context) {

    actual val confirmsCopy: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    actual suspend fun copy(text: String) {
        val manager = context.getSystemService<ClipboardManager>() ?: return
        // Popisek vidí uživatel v nabídce schránky na starších Androidech; od 13 ho systém
        // ukazuje v potvrzovacím okně nad klávesnicí.
        manager.setPrimaryClip(ClipData.newPlainText(readString(Res.string.clipboard_label), text))
    }
}
