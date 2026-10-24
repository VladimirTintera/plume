package eu.tintera.plume.core.logging

import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Bez `google-services.json` se Firebase neinicializuje a `FirebaseCrashlytics.getInstance()`
 * by spadlo na `IllegalStateException` — proto se existence aplikace Firebase napřed ověří.
 * Je to tentýž důvod, proč se plugin `google-services` aplikuje podmíněně: repozitář musí jít
 * po klonu přeložit a spustit i bez Firebase.
 *
 * Ověřuje se `FirebaseApp.getInstance()`, ne `getApps(context)` — kvůli `Context` by hlásič
 * potřeboval platformní parametr a nešel by vyrobit dřív, než se sestaví graf závislostí.
 * Firebase se inicializuje vlastním `ContentProviderem` ještě před `Application.onCreate`,
 * takže v okamžiku startu Koinu je odpověď už definitivní.
 */
internal actual fun createCrashReporter(): CrashReporter {
    val firebaseReady = runCatching { FirebaseApp.getInstance() }.isSuccess
    return if (firebaseReady) FirebaseCrashReporter(FirebaseCrashlytics.getInstance()) else NoopCrashReporter
}

private class FirebaseCrashReporter(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {

    override fun log(message: String) = crashlytics.log(message)

    override fun recordException(throwable: Throwable) = crashlytics.recordException(throwable)
}
