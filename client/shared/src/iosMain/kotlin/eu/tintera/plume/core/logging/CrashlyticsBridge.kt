package eu.tintera.plume.core.logging

import kotlin.experimental.ExperimentalNativeApi

/**
 * Most do Firebase Crashlytics, který implementuje **Swift** (`FirebaseCrashlyticsBridge` v `iosApp`)
 * a podá ho do `initKoin`. Kotlin/Native `FirebaseCrashlytics` nevidí (ObjC knihovna ze SPM, ne
 * z cinteropu), takže rozhraní je schválně jen z typů, které přejdou přes ObjC export bez převodu.
 *
 * Kotlin výjimka nenese symbolizovaný stack, jen adresy (`getStackTraceAddresses`). Ty Swift předá
 * jako rámce `ExceptionModel` a Crashlytics je symbolizuje podle dSYM — Kotlin kód je v něm,
 * framework `ComposeApp` je statický a linkuje se do binárky aplikace. Převzato z projektu mlog
 * (tam podle `IosCrashlyticsLogger` z Profilogu).
 */
interface CrashlyticsBridge {
    fun log(message: String)
    fun recordException(name: String, reason: String, stackAddresses: List<Long>)
}

internal class IosCrashReporter(private val bridge: CrashlyticsBridge) : CrashReporter {

    override fun log(message: String) = bridge.log(message)

    @OptIn(ExperimentalNativeApi::class)
    override fun recordException(throwable: Throwable) {
        bridge.recordException(
            name = throwable::class.qualifiedName ?: "KotlinException",
            reason = throwable.reasonWithCauses(),
            stackAddresses = throwable.getStackTraceAddresses(),
        )
    }

    /**
     * Nezachycená Kotlin výjimka ukončí proces přes `abort` z runtime a Crashlytics pak vidí jen
     * ten — bez skutečné výjimky, takže by všechny pády z Kotlinu skončily v jednom problému.
     * Proto se výjimka před ukončením pošle ještě jako non-fatal (seskupí se podle skutečné
     * příčiny) a celý `stackTraceToString()` jako řádek logu k pádu (`Caused by` a `Suppressed`
     * model výjimky nenese).
     */
    @OptIn(ExperimentalNativeApi::class)
    fun installUnhandledExceptionHook() {
        setUnhandledExceptionHook { throwable ->
            bridge.log("Unhandled exception: ${throwable.stackTraceToString()}")
            recordException(throwable)
            terminateWithUnhandledException(throwable)
        }
    }
}

/** Zpráva výjimky i s řetězem příčin — model výjimky v Crashlytics nese jen jeden stack. */
internal fun Throwable.reasonWithCauses(): String = buildString {
    append(message ?: "")
    var cause = cause
    var depth = 0
    while (cause != null && cause !== this@reasonWithCauses && depth < MAX_CAUSES) {
        append("\nCaused by: ").append(cause::class.qualifiedName).append(": ").append(cause.message ?: "")
        cause = cause.cause
        depth++
    }
}

private const val MAX_CAUSES = 5
