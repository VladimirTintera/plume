package eu.tintera.plume.core.files

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * iOS: `UIActivityViewController`. Dialog sám nabízí „Uložit obrázek" i „Uložit do Souborů",
 * takže „Uložit jako…" tu zvlášť není ([rememberSaveAs] vrací `null`).
 *
 * Na iPadu se dialog ukazuje jako popover a **bez kotvy aplikace spadne** — kotví se na střed
 * horní hrany, kde je lišta prohlížeče.
 */
@OptIn(ExperimentalForeignApi::class)
actual class MediaSharer {

    private val logger = Logger.withTag("MediaSharer")

    actual suspend fun share(path: String, fileName: String): Boolean {
        val copy = withContext(Dispatchers.Default) {
            val manager = NSFileManager.defaultManager
            val directory = NSTemporaryDirectory() + "shared"
            // Předchozí sdílení už příjemce dočetl; hromadit kopie by jen plnilo dočasný adresář.
            manager.removeItemAtPath(directory, error = null)
            manager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
            val target = "$directory/$fileName"
            if (manager.copyItemAtPath(path, toPath = target, error = null)) target else null
        }
        if (copy == null) {
            logger.w { "Soubor $path se nepodařilo připravit ke sdílení." }
            return false
        }

        val top = topViewController() ?: return false
        val controller = UIActivityViewController(
            activityItems = listOf(NSURL.fileURLWithPath(copy)),
            applicationActivities = null,
        )
        controller.popoverPresentationController?.let { popover ->
            popover.sourceView = top.view
            val width = top.view.bounds.useContents { size.width }
            popover.sourceRect = CGRectMake(width / 2, 0.0, 1.0, 1.0)
        }
        top.presentViewController(controller, animated = true, completion = null)
        return true
    }

    /** Nejvýš položený kontroler — Compose obrazovka sama může něco prezentovat (sheet). */
    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: break
        return top
    }
}

@Composable
actual fun rememberSaveAs(): SaveAsLauncher? = null

actual val saveAsSupported: Boolean = false
