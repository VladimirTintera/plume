package eu.tintera.plume.core.logging

import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate

/**
 * Most do Firebase Crashlytics, který implementuje **Swift** (`FirebaseCrashlyticsBridge` v `iosApp`)
 * a podá ho do `initKoin`. Kotlin/Native `FirebaseCrashlytics` nevidí (ObjC knihovna ze SPM, ne
 * z cinteropu), takže rozhraní je schválně jen z typů, které přejdou přes ObjC export bez převodu.
 *
 * Kotlin výjimka nenese symbolizovaný stack, jen adresy (`getStackTraceAddresses`). Ty Swift předá
 * jako rámce `ExceptionModel` a Crashlytics je symbolizuje podle dSYM — Kotlin kód je v něm,
 * framework `ComposeApp` je statický a linkuje se do binárky aplikace.
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
            stackAddresses = throwable.stackFromThrowSite(),
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
            // Crashlytics zápis záznamu dokončuje přes hlavní frontu. Bez chvilky času ukončení
            // procesu zápis předběhne a k pádu zůstane jen abort bez příčiny; na hlavním vlákně
            // nestačí ani `sleep` (frontu blokuje), proto se protočí run loop. Ověřeno
            // v simulátoru, viz docs/poznatky/release.md, „Hlášení pádů na iOS".
            if (NSThread.isMainThread) {
                NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(UNHANDLED_FLUSH_SECONDS))
            } else {
                NSThread.sleepForTimeInterval(UNHANDLED_FLUSH_SECONDS)
            }
            terminateWithUnhandledException(throwable)
        }
    }
}

/**
 * Adresy zásobníku od místa, kde výjimka vznikla: bez úvodních rámců jejích konstruktorů
 * (`kotlin.Throwable#<init>`, `Exception#<init>`… i vlastní výjimky). Crashlytics pojmenuje
 * a seskupí problém podle horního rámce, a ten byl u každé Kotlin výjimky `Throwable#<init>` —
 * všechny chyby by skončily pod jedním názvem. Jména rámců dává `getStackTrace()`; když je
 * nemá (ořezaná binárka) nebo nesedí počet, vrátí se zásobník celý.
 */
@OptIn(ExperimentalNativeApi::class)
internal fun Throwable.stackFromThrowSite(): List<Long> {
    val addresses = getStackTraceAddresses()
    val names = getStackTrace()
    if (names.size != addresses.size) return addresses
    val constructors = names.takeWhile { CONSTRUCTOR_FRAME in it }.size
    return if (constructors in 1 until addresses.size) addresses.drop(constructors) else addresses
}

private const val CONSTRUCTOR_FRAME = "#<init>("

/** Kolik vteřin dostane Crashlytics na zápis záznamu nezachycené výjimky před ukončením. */
private const val UNHANDLED_FLUSH_SECONDS = 0.5

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
