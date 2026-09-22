import SwiftUI
import ComposeApp

@main
struct iOSApp: App {

    // Token APNs dostane jen `UIApplicationDelegate` — SwiftUI na to vlastní obsluhu nemá.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        // Koin musí běžet dřív, než se složí první obrazovka — MainViewController hned
        // resolvuje AuthViewModel.
        MainViewControllerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Předává token APNs do Kotlinu (`IosPush`), který ho zaregistruje u Telegramu.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // Build z Xcode (Debug) dostane token vývojového prostředí APNs, TestFlight a App Store
        // (Release) produkčního. Telegram musí vědět, do kterého push posílat — jinak ho APNs
        // tiše zahodí.
        #if DEBUG
        let sandbox = true
        #else
        let sandbox = false
        #endif
        IosPush.shared.didRegister(deviceToken: deviceToken, sandbox: sandbox)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        IosPush.shared.didFailToRegister(error: error)
    }
}
