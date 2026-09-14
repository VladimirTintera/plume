import SwiftUI
import ComposeApp

@main
struct iOSApp: App {

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
