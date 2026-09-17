package eu.tintera.plume.core.files

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * iOS: `UIDocumentInteractionController` — náhled souboru, a z něj i „Sdílet".
 *
 * **Kontroler i jeho delegát se musí držet**, dokud je náhled na obrazovce: UIKit je drží slabě
 * a bez reference by je uklidil garbage collector (stejná past jako u výběru příloh). Proto je
 * jich tu jen jeden a nahrazuje se až dalším otevřením.
 *
 * Když systém pro typ náhled nemá, vrací `presentPreview` `false` a ukáže se nabídka aplikací.
 */
@OptIn(ExperimentalForeignApi::class)
actual class FileOpener {

    private var controller: UIDocumentInteractionController? = null

    private val delegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
        override fun documentInteractionControllerViewControllerForPreview(
            controller: UIDocumentInteractionController,
        ): UIViewController = topViewController() ?: UIViewController()
    }

    actual fun open(path: String, mimeType: String): Boolean {
        val top = topViewController() ?: return false
        val created = UIDocumentInteractionController.interactionControllerWithURL(NSURL.fileURLWithPath(path))
        created.delegate = delegate
        controller = created

        if (created.presentPreviewAnimated(true)) return true

        // Bez náhledu aspoň nabídka „Otevřít v…"; `view` je potřeba jako kotva pro iPad.
        return created.presentOptionsMenuFromRect(
            rect = top.view.bounds,
            inView = top.view,
            animated = true,
        )
    }

    /** Nejvýš položený kontroler — Compose obrazovka sama může něco prezentovat (sheet). */
    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: break
        return top
    }
}
