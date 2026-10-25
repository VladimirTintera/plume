import ComposeApp
import FirebaseCore
import FirebaseCrashlytics

// Hlášení pádů (Crashlytics). Kotlin/Native knihovnu Firebase nevidí, proto ji volá Swift přes
// rozhraní CrashlyticsBridge z Kotlinu (core/logging v :client:shared). Logy a zachycené chyby do
// něj posílá Kermit, nezachycené Kotlin výjimky hook v IosCrashReporter a nativní pády (signály,
// ObjC výjimky) chytá Crashlytics sám. Viz docs/poznatky/release.md, „Hlášení pádů na iOS".
final class FirebaseCrashlyticsBridge: CrashlyticsBridge {

    /// Spustí Firebase a vrátí most, nebo nil, když build nemá GoogleService-Info.plist — aplikace
    /// pak jede bez hlášení pádů (FirebaseApp.configure() by bez něj shodil proces). Sběr v Debug
    /// buildu vypíná FirebaseCrashlyticsCollectionEnabled v Info.plist (build setting
    /// PLUME_CRASHLYTICS_COLLECTION_ENABLED).
    static func start() -> FirebaseCrashlyticsBridge? {
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            NSLog("GoogleService-Info.plist chybí – build bez hlášení pádů (Crashlytics).")
            return nil
        }
        FirebaseApp.configure()
        return FirebaseCrashlyticsBridge()
    }

    func log(message: String) {
        Crashlytics.crashlytics().log(message)
    }

    // Adresy z Kotlin stacku — Crashlytics je symbolizuje podle nahraného dSYM aplikace
    // (ComposeApp je statický framework, Kotlin kód je tedy v binárce aplikace).
    func recordException(name: String, reason: String, stackAddresses: [KotlinLong]) {
        let model = ExceptionModel(name: name, reason: reason)
        model.stackTrace = stackAddresses.map { StackFrame(address: $0.uintValue) }
        Crashlytics.crashlytics().record(exceptionModel: model)
    }
}
