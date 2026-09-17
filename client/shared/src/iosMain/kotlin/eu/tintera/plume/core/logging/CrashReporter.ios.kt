package eu.tintera.plume.core.logging

/**
 * iOS zatím hlásič pádů nemá — Firebase v Xcode projektu není (stejně jako u notifikací).
 *
 * Až přibude, patří sem implementace [CrashReporter], která pádům podrží ruku přes Swift:
 * `FirebaseCrashlytics` je ObjC knihovna, kterou Kotlin/Native nevidí, takže se dovnitř musí
 * podat z `iOSApp.swift` (protokol z Kotlin rozhraní) — přesně jako `IosCrashlyticsLogger`
 * v projektu com.profilog.telemetry.android. Kotlin výjimka navíc nenese symbolizovaný stack
 * trace, jen adresy (`Throwable.getStackTraceAddresses()`), takže se hlásí jako model
 * s adresami, ne jako `NSError`.
 *
 * Do té doby je to prázdná trubka — Kermit na iOS píše dál do NSLogu.
 */
internal actual fun createCrashReporter(): CrashReporter = NoopCrashReporter
