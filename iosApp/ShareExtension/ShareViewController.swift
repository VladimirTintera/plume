import SwiftUI
import UIKit

/// Vstupní bod rozšíření sdílení: list s výběrem chatu (`ShareView`).
///
/// Po výběru rozšíření otevře Plume, protože odeslat umí jen aplikace — TDLib v rozšíření
/// neběží (samostatný proces, vlastní limit paměti, databáze patří aplikaci).
final class ShareViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        let model = ShareModel(context: extensionContext) { [weak self] url in
            self?.openContainingApp(url)
        }
        let host = UIHostingController(rootView: ShareView(model: model))
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
    }

    /// Otevře Plume.
    ///
    /// Rozšíření sdílení aplikaci oficiálně otevřít nesmí: `UIApplication.shared` ani `open`
    /// v něm nejsou a Xcode u rozšíření vynucuje `APPLICATION_EXTENSION_API_ONLY = YES`.
    /// Instance `UIApplication` ale v řetězci responderů je a `openURL:options:completionHandler:`
    /// na ní funguje — volá se přes Objective-C runtime, dělá to tak řada aplikací. Když to
    /// jednou přestane fungovat, sdílení počká v inboxu na nejbližší spuštění aplikace.
    private func openContainingApp(_ url: URL) {
        let selector = NSSelectorFromString("openURL:options:completionHandler:")
        var responder: UIResponder? = self
        while let current = responder {
            if current is UIApplication, current.responds(to: selector) {
                typealias OpenURL = @convention(c) (AnyObject, Selector, NSURL, NSDictionary, AnyObject?) -> Void
                let open = unsafeBitCast(current.method(for: selector), to: OpenURL.self)
                open(current, selector, url as NSURL, NSDictionary(), nil)
                return
            }
            responder = current.next
        }
    }
}
