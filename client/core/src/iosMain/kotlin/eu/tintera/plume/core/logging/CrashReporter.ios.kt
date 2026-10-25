package eu.tintera.plume.core.logging

/**
 * Hlásič pádů na iOS dodává vstupní bod, ne `core`: `FirebaseCrashlytics` je ObjC knihovna ze
 * SPM, kterou Kotlin/Native nevidí, takže ji volá Swift (`FirebaseCrashlyticsBridge` v `iosApp`)
 * přes rozhraní `CrashlyticsBridge` v `:client:shared` (Swift vidí jen typy z `shared`, `core`
 * framework neexportuje). `initKoin` ho sem vloží dřív, než se zavolá [configureLogging].
 *
 * Bez `GoogleService-Info.plist` v buildu zůstane [reporter] `null` a hlásí se do
 * [NoopCrashReporter] — aplikace běží dál, jen bez hlášení pádů.
 */
object IosCrashReporting {
    var reporter: CrashReporter? = null
}

internal actual fun createCrashReporter(): CrashReporter = IosCrashReporting.reporter ?: NoopCrashReporter
